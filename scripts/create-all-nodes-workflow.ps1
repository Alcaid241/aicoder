param(
    [Parameter(Mandatory = $true)]
    [string]$Token,

    [Parameter(Mandatory = $true)]
    [long]$RagKnowledgeBaseId,

    [Parameter(Mandatory = $true)]
    [long]$DdlKnowledgeBaseId,

    [string]$DatabaseName = "test_ai",
    [string]$Model = "deepseek-v4-flash",
    [string]$GatewayUrl = "http://localhost:8080",
    [string]$WebUrl = "http://localhost:8888",
    [string]$McpCalculatorUrl = "http://localhost:8087/api/mcp/call/calculator"
)

$ErrorActionPreference = "Stop"

$headers = @{
    Authorization = "Bearer $Token"
    "Content-Type" = "application/json"
}

function New-Workflow {
    param(
        [string]$Name,
        [string]$Description,
        [string]$Type,
        [string]$ReplyRequirements,
        [hashtable]$Graph
    )

    $payload = @{
        name = $Name
        description = $Description
        type = $Type
        replyRequirements = $ReplyRequirements
        graphData = ($Graph | ConvertTo-Json -Depth 30 -Compress)
    } | ConvertTo-Json -Depth 35

    Invoke-RestMethod `
        -Method Post `
        -Uri "$($GatewayUrl.TrimEnd('/'))/api/workflow/create" `
        -Headers $headers `
        -Body $payload
}

# 子工作流必须先创建，主工作流才能引用一个真实的 workflowId。
$subGraph = @{
    nodes = @(
        @{
            id = "start_sub"
            type = "start"
            position = @{ x = 260; y = 40 }
            data = @{ label = "开始"; config = @{} }
        },
        @{
            id = "llm_sub"
            type = "llm"
            position = @{ x = 260; y = 190 }
            data = @{
                label = "统一整理结果"
                config = @{
                    model = $Model
                    promptTemplate = "请把下面的节点结果整理成简洁、准确的中文回答。保留关键数据；如果是 JSON，请解释其核心字段：`n`n{{input}}"
                    inputKey = "query"
                    outputKey = "subAnswer"
                }
            }
        },
        @{
            id = "end_sub"
            type = "end"
            position = @{ x = 260; y = 350 }
            data = @{ label = "结束"; config = @{} }
        }
    )
    edges = @(
        @{ id = "e_sub_1"; source = "start_sub"; target = "llm_sub" },
        @{ id = "e_sub_2"; source = "llm_sub"; target = "end_sub" }
    )
}

$subWorkflow = New-Workflow `
    -Name "全节点演示-结果整理子工作流" `
    -Description "由主工作流调用，将 RAG、NL2SQL 或 MCP 工具结果整理为最终答案。" `
    -Type "WORKFLOW" `
    -ReplyRequirements "" `
    -Graph $subGraph

if (-not $subWorkflow.id) {
    throw "子工作流创建成功但响应中没有 id，无法建立主工作流引用。"
}

$mainGraph = @{
    nodes = @(
        @{
            id = "start_main"
            type = "start"
            position = @{ x = 520; y = 20 }
            data = @{ label = "用户问题"; config = @{} }
        },
        @{
            id = "llm_intent"
            type = "llm"
            position = @{ x = 520; y = 150 }
            data = @{
                label = "LLM 意图识别"
                config = @{
                    model = $Model
                    promptTemplate = "判断用户问题类型，只输出下列一个词：知识、数据、计算、其他。涉及文档或制度输出知识；涉及数据库查询或统计输出数据；纯数学表达式输出计算；否则输出其他。用户问题：{{input}}"
                    inputKey = "query"
                    outputKey = "intent"
                }
            }
        },
        @{
            id = "condition_route"
            type = "condition"
            position = @{ x = 520; y = 300 }
            data = @{
                label = "条件路由"
                config = @{
                    inputKey = "intent"
                    conditions = @(
                        @{ label = "知识问答"; match = "知识" },
                        @{ label = "数据查询"; match = "数据" },
                        @{ label = "数学计算"; match = "计算" },
                        @{ label = "默认"; match = "__default__" }
                    )
                }
            }
        },
        @{
            id = "rag_search"
            type = "rag"
            position = @{ x = 80; y = 500 }
            data = @{
                label = "RAG 知识检索"
                config = @{
                    knowledgeBaseId = [string]$RagKnowledgeBaseId
                    topK = 5
                    inputKey = "query"
                    outputKey = "branchResult"
                }
            }
        },
        @{
            id = "nl2sql_query"
            type = "nl2sql"
            position = @{ x = 360; y = 500 }
            data = @{
                label = "NL2SQL 数据查询"
                config = @{
                    knowledgeBaseId = [string]$DdlKnowledgeBaseId
                    model = $Model
                    databaseName = $DatabaseName
                    inputKey = "query"
                    outputKey = "branchResult"
                }
            }
        },
        @{
            id = "mcp_calculator"
            type = "tool"
            position = @{ x = 650; y = 500 }
            data = @{
                label = "HTTP 调用 MCP 计算器"
                config = @{
                    url = $McpCalculatorUrl
                    method = "POST"
                    headers = @{ "Content-Type" = "application/json" }
                    bodyTemplate = '{"expression":"{{input}}"}'
                    inputKey = "query"
                    outputKey = "branchResult"
                }
            }
        },
        @{
            id = "llm_fallback"
            type = "llm"
            position = @{ x = 930; y = 500 }
            data = @{
                label = "LLM 通用回答"
                config = @{
                    model = $Model
                    promptTemplate = "请直接回答用户问题：{{input}}"
                    inputKey = "query"
                    outputKey = "branchResult"
                }
            }
        },
        @{
            id = "loop_guard"
            type = "loop"
            position = @{ x = 520; y = 690 }
            data = @{
                label = "循环迭代守卫"
                config = @{
                    maxIterations = 1
                    exitCondition = ""
                    inputKey = "branchResult"
                    outputKey = "normalizedResult"
                }
            }
        },
        @{
            id = "subworkflow_format"
            type = "subworkflow"
            position = @{ x = 520; y = 840 }
            data = @{
                label = "调用结果整理子工作流"
                config = @{
                    workflowId = [string]$subWorkflow.id
                    inputKey = "normalizedResult"
                    outputKey = "finalResult"
                }
            }
        },
        @{
            id = "end_main"
            type = "end"
            position = @{ x = 520; y = 1000 }
            data = @{
                label = "结束"
                config = @{ replyRequirements = "优先输出 finalResult，并说明本次走的是知识、数据、计算还是默认分支。" }
            }
        }
    )
    edges = @(
        @{ id = "e_main_1"; source = "start_main"; target = "llm_intent" },
        @{ id = "e_main_2"; source = "llm_intent"; target = "condition_route" },
        @{ id = "e_main_knowledge"; source = "condition_route"; sourceHandle = "知识问答"; target = "rag_search" },
        @{ id = "e_main_data"; source = "condition_route"; sourceHandle = "数据查询"; target = "nl2sql_query" },
        @{ id = "e_main_calc"; source = "condition_route"; sourceHandle = "数学计算"; target = "mcp_calculator" },
        @{ id = "e_main_default"; source = "condition_route"; sourceHandle = "默认"; target = "llm_fallback" },
        @{ id = "e_main_rag_join"; source = "rag_search"; target = "loop_guard" },
        @{ id = "e_main_sql_join"; source = "nl2sql_query"; target = "loop_guard" },
        @{ id = "e_main_mcp_join"; source = "mcp_calculator"; target = "loop_guard" },
        @{ id = "e_main_default_join"; source = "llm_fallback"; target = "loop_guard" },
        @{ id = "e_main_loop_sub"; source = "loop_guard"; target = "subworkflow_format" },
        @{ id = "e_main_end"; source = "subworkflow_format"; target = "end_main" }
    )
}

$mainWorkflow = New-Workflow `
    -Name "全节点智能问答工作流" `
    -Description "LLM 识别意图后，按条件路由到 RAG、NL2SQL、MCP 计算器或通用回答，再经过循环守卫和子工作流统一整理。" `
    -Type "CHAT" `
    -ReplyRequirements "优先输出 finalResult，并用简洁中文给出结论。" `
    -Graph $mainGraph

Write-Output "创建完成"
Write-Output "子工作流 ID: $($subWorkflow.id)"
Write-Output "主工作流 ID: $($mainWorkflow.id)"
Write-Output "打开地址: $($WebUrl.TrimEnd('/'))/#/workflow/$($mainWorkflow.id)"
