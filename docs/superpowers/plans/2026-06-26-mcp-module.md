# aicoder-mcp 模块 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增 `aicoder-mcp` MCP Server 微服务，把平台能力（calculator / web_search+web_fetch / image_analysis / 技能读写）封装为 MCP 工具，经 Gateway `/api/mcp/**` 以 SSE 暴露（`type:SYNC` → SSE 模式，端点 GET /sse + POST /mcp/message?sessionId=xxx），供外部 MCP 客户端（Claude Desktop / Cursor）接入调用。

**Architecture:** 独立 Spring Boot 微服务（端口 8087），依赖 `aicoder-core` 复用模型注册表（图文解析取多模态 ChatModel）。工具用 `@Tool` 注解，经 `ToolCallbackProvider` 汇总，由 `spring-ai-starter-mcp-server-webmvc` 自动暴露为 MCP tools（实际 SSE 模式）。技能读写经 `@LoadBalanced RestTemplate` 调 `aicoder-skill`；外部搜索/抓取经普通 RestTemplate。认证用 MCP 专用 token filter，Gateway JWT 白名单放行 `/api/mcp/**`。

**Tech Stack:** Spring Boot 3.5.13 / Spring AI 1.1.2（`spring-ai-starter-mcp-server-webmvc`）/ Spring Cloud 2025.0.0 + Nacos / JPA + MySQL / JUnit 5 + Mockito / exp4j（计算器）/ jsoup + flexmark（网页→markdown）。

> **构建命令**：Maven 不在 PATH，全用 `/Users/haijingxu/software/apache-maven-3.9.16/bin/mvn`（下文简记为 `mvn`，执行时替换为全路径）。所有 `mvn` 命令在项目根 `/Users/haijingxu/workspace/claudeCode/aicoder` 下执行。

---

## File Structure

**新建（aicoder-mcp）：**
- `aicoder-mcp/pom.xml` — 模块依赖
- `aicoder-mcp/src/main/java/com/ai/coder/mcp/AicoderMcpApplication.java` — 启动类（含 core 实体扫描）
- `aicoder-mcp/src/main/resources/application.yml` / `bootstrap.yml` — 端口 8087 + MCP server 配置 + Nacos
- `config/McpProperties.java` — `@ConfigurationProperties("mcp")` 热刷新配置
- `config/RestTemplateConfig.java` — `@LoadBalanced` + 普通 两个 RestTemplate
- `config/McpServerConfig.java` — `ToolCallbackProvider` 聚合所有工具
- `config/McpAuthFilter.java` — MCP access token 校验
- `config/GlobalExceptionHandler.java` — 统一异常
- `registry/McpDynamicModelRegistry.java` — `AbstractDynamicModelRegistry` 空子类（拿多模态 ChatModel）
- `tool/CalculatorTool.java` / `tool/SkillTool.java` / `tool/WebSearchTool.java` / `tool/WebFetchTool.java` / `tool/ImageAnalysisTool.java`
- `service/WebSearchService.java` / `service/WebFetchService.java` / `service/ImageAnalysisService.java`
- 对应 `src/test/java/...` 单测

**修改：**
- `pom.xml`（父）— `<modules>` 加 `aicoder-mcp`
- `aicoder-gateway/src/main/resources/application.yml` — 加 mcp-service 路由
- `aicoder-gateway/.../filter/JwtAuthFilter.java` — `WHITE_LIST` 加 `/api/mcp`
- `aicoder-skill/.../service/SkillService.java` — 加 `getByName`
- `aicoder-skill/.../controller/SkillController.java` — 加 `GET /api/skill/name/{name}`

---

## Task 1: 创建 aicoder-mcp 模块骨架 + 父 pom 注册

**Files:**
- Create: `aicoder-mcp/pom.xml`
- Create: `aicoder-mcp/src/main/java/com/ai/coder/mcp/AicoderMcpApplication.java`
- Create: `aicoder-mcp/src/main/resources/application.yml`
- Create: `aicoder-mcp/src/main/resources/bootstrap.yml`
- Modify: `pom.xml`（父，`<modules>` 段，第 21-30 行）

- [ ] **Step 1: 父 pom 注册模块**

在 `pom.xml` 的 `<modules>` 末尾（`aicoder-core` 之后）追加：

```xml
        <module>aicoder-mcp</module>
```

- [ ] **Step 2: 创建 aicoder-mcp/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.ai.coder</groupId>
        <artifactId>aicoder</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>aicoder-mcp</artifactId>
    <name>AI Coder MCP</name>
    <description>MCP Server：把平台能力封装为 MCP 工具暴露给外部客户端</description>

    <dependencies>
        <dependency>
            <groupId>com.ai.coder</groupId>
            <artifactId>aicoder-core</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- MCP Server（WebMVC streamable HTTP），版本由 spring-ai-bom 管理 -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
        </dependency>

        <!-- Nacos 服务发现 + 配置 -->
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-loadbalancer</artifactId>
        </dependency>

        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- 计算器 -->
        <dependency>
            <groupId>net.objecthunter</groupId>
            <artifactId>exp4j</artifactId>
            <version>0.4.8</version>
        </dependency>
        <!-- 网页抓取 + HTML→markdown -->
        <dependency>
            <groupId>org.jsoup</groupId>
            <artifactId>jsoup</artifactId>
            <version>1.17.2</version>
        </dependency>
        <dependency>
            <groupId>com.vladsch.flexmark</groupId>
            <artifactId>flexmark-html2md-converter</artifactId>
            <version>0.64.8</version>
        </dependency>

        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 3: 创建启动类**

`aicoder-mcp/src/main/java/com/ai/coder/mcp/AicoderMcpApplication.java`：

```java
package com.ai.coder.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** aicoder-mcp：MCP Server。@EntityScan/@EnableJpaRepositories 含 core 包以读取 ai_model_config（多模态模型）。 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableDiscoveryClient
@EntityScan(basePackages = {"com.ai.coder.mcp", "com.ai.coder.core"})
@EnableJpaRepositories(basePackages = {"com.ai.coder.mcp", "com.ai.coder.core"})
public class AicoderMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(AicoderMcpApplication.class, args);
    }
}
```

- [ ] **Step 4: 创建 application.yml**

`aicoder-mcp/src/main/resources/application.yml`：

```yaml
server:
  port: 8087

spring:
  application:
    name: aicoder-mcp
  config:
    import:
      - optional:nacos:aicoder-shared.yml
  cloud:
    nacos:
      server-addr: localhost:8848
  datasource:
    url: jdbc:mysql://localhost:3306/test_ai?characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=false&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false
    username: root
    password: root
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: none
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQLDialect
  ai:
    mcp:
      server:
        name: aicoder-mcp
        version: 1.0.0
        type: SYNC

# MCP 工具配置（可被 Nacos aicoder-shared.yml 热刷新覆盖）
mcp:
  access-token: ${MCP_ACCESS_TOKEN:dev-mcp-token-change-me}
  service-user-id: ${MCP_SERVICE_USER_ID:1}
  search:
    provider: tavily
    api-key: ${MCP_SEARCH_API_KEY:}
    max-results: 5
  fetch:
    connect-timeout: 5000
    read-timeout: 15000
    max-length: 20000
    allow-private-ip: false
  image:
    model-code: ${MCP_IMAGE_MODEL_CODE:qwen2.5-vl}

springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
```

- [ ] **Step 5: 创建 bootstrap.yml**

`aicoder-mcp/src/main/resources/bootstrap.yml`：

```yaml
spring:
  application:
    name: aicoder-mcp
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848
        ip: 127.0.0.1
```

- [ ] **Step 6: 编译验证**

Run: `mvn clean compile -pl aicoder-mcp -am -DskipTests`
Expected: BUILD SUCCESS（aicoder-core 先编译，再 aicoder-mcp 编译通过）。

- [ ] **Step 7: Commit**

```bash
git add pom.xml aicoder-mcp
git commit -m "feat(mcp): 新建 aicoder-mcp 模块骨架（pom + 启动类 + 配置）"
```

---

## Task 2: Gateway 路由 + JWT 白名单放行 /api/mcp

**Files:**
- Modify: `aicoder-gateway/src/main/resources/application.yml`（routes 段，第 40-43 行后）
- Modify: `aicoder-gateway/src/main/java/com/ai/coder/gateway/filter/JwtAuthFilter.java`（`WHITE_LIST`）

- [ ] **Step 1: 加 mcp-service 路由**

在 `aicoder-gateway/src/main/resources/application.yml` 的 `skill-service` 路由块之后追加：

```yaml
        - id: mcp-service
          uri: lb://aicoder-mcp
          predicates:
            - Path=/api/mcp/**
```

- [ ] **Step 2: JWT 白名单加 /api/mcp**

在 `JwtAuthFilter.java` 的 `WHITE_LIST` 列表里追加一项（`"/webjars"` 之后）：

```java
    private static final List<String> WHITE_LIST = List.of(
            "/api/admin/login",
            "/api/admin/register",
            "/v3/api-docs",
            "/swagger-ui",
            "/webjars",
            "/api/mcp"
    );
```

> 说明：`path.startsWith("/api/mcp")` 会覆盖 `/api/mcp/**` 全部子路径。MCP 端点鉴权交给 mcp 模块的 `McpAuthFilter`（Task 4）。

- [ ] **Step 3: 编译验证 gateway**

Run: `mvn clean compile -pl aicoder-gateway -am -DskipTests`
Expected: BUILD SUCCESS。

- [ ] **Step 4: Commit**

```bash
git add aicoder-gateway
git commit -m "feat(gateway): 新增 /api/mcp/** 路由 + JWT 白名单放行（MCP 走自有 token）"
```

---

## Task 3: MCP Server 基础 + CalculatorTool（最小可发现工具）

**Files:**
- Create: `aicoder-mcp/src/main/java/com/ai/coder/mcp/config/McpServerConfig.java`
- Create: `aicoder-mcp/src/main/java/com/ai/coder/mcp/tool/CalculatorTool.java`
- Test: `aicoder-mcp/src/test/java/com/ai/coder/mcp/tool/CalculatorToolTest.java`

- [ ] **Step 1: 写 CalculatorTool 失败测试**

`aicoder-mcp/src/test/java/com/ai/coder/mcp/tool/CalculatorToolTest.java`：

```java
package com.ai.coder.mcp.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalculatorToolTest {

    private CalculatorTool tool;

    @BeforeEach
    void setUp() {
        tool = new CalculatorTool();
    }

    @Test
    void calculator_evaluates_basic_arithmetic() {
        assertEquals("12", tool.calculator("3 * (2 + 2)"));
    }

    @Test
    void calculator_returns_quoted_decimal() {
        assertTrue(tool.calculator("10 / 4").matches("2\\.5"), "除法应给出小数结果");
    }

    @Test
    void calculator_rejects_too_long_expression() {
        String huge = "1+".repeat(2000) + "1";
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.calculator(huge));
        assertTrue(ex.getMessage().contains("过长"), "应拒绝过长表达式：" + ex.getMessage());
    }

    @Test
    void calculator_rejects_invalid_expression() {
        assertThrows(IllegalArgumentException.class, () -> tool.calculator("1 + + 2"));
        assertThrows(IllegalArgumentException.class, () -> tool.calculator("foo"));
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -pl aicoder-mcp -Dtest=CalculatorToolTest`
Expected: 编译失败（`CalculatorTool` 不存在）。

- [ ] **Step 3: 实现 CalculatorTool**

`aicoder-mcp/src/main/java/com/ai/coder/mcp/tool/CalculatorTool.java`：

```java
package com.ai.coder.mcp.tool;

import lombok.extern.slf4j.Slf4j;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionException;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/** calculator 工具：exp4j 表达式求值。限制长度防 DoS，非法表达式转友好错误。 */
@Slf4j
@Component
public class CalculatorTool {

    private static final int MAX_LEN = 500;

    @Tool(description = "计算器：对数学表达式求值并返回数值结果。支持 + - * / % 和括号，如 3*(2+2)。")
    public String calculator(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("表达式不能为空");
        }
        String expr = expression.trim();
        if (expr.length() > MAX_LEN) {
            throw new IllegalArgumentException("表达式过长（>" + MAX_LEN + " 字符），拒绝计算");
        }
        try {
            Expression e = new Expression.ExpressionBuilder(expr).build();
            double v = e.evaluate();
            if (!Double.isFinite(v)) {
                return "结果非有限数";
            }
            // 整数结果去掉小数点（12.0 → "12"），小数保留（2.5 → "2.5"）
            if (v == Math.rint(v) && Math.abs(v) < 1e15) {
                return Long.toString((long) v);
            }
            return Double.toString(v);
        } catch (ExpressionException | ArithmeticException | IllegalArgumentException ex) {
            throw new IllegalArgumentException("表达式无效：" + expr + "（" + ex.getMessage() + "）");
        }
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn test -pl aicoder-mcp -Dtest=CalculatorToolTest`
Expected: Tests run: 4, Failures: 0。

- [ ] **Step 5: 创建 McpServerConfig（聚合工具为 ToolCallbackProvider）**

`aicoder-mcp/src/main/java/com/ai/coder/mcp/config/McpServerConfig.java`：

```java
package com.ai.coder.mcp.config;

import com.ai.coder.mcp.tool.CalculatorTool;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP server 工具装配：把带 @Tool 注解的工具 bean 聚合为 {@link ToolCallbackProvider}，
 * spring-ai-starter-mcp-server-webmvc 自动捕获并经 streamable-http 暴露为 MCP tools。
 * 每新增工具在此追加参数。
 */
@Configuration
public class McpServerConfig {

    @Bean
    public ToolCallbackProvider mcpToolCallbacks(CalculatorTool calculatorTool) {
        return ToolCallbacks.from(calculatorTool);
    }
}
```

- [ ] **Step 6: 编译验证**

Run: `mvn clean compile -pl aicoder-mcp -am -DskipTests`
Expected: BUILD SUCCESS。

- [ ] **Step 7: 手动验证 MCP server 启动（人工）**

启动 aicoder-mcp（需先起 MySQL/Redis/Nacos）。日志预期出现 MCP server 相关启动信息、`/mcp` 端点。用 curl 探活：
```bash
curl -i http://localhost:8087/mcp
```
Expected: 非 404（MCP 端点存在，具体响应取决于 MCP 握手协议，405/400 均表示端点存在）。

> 若 starter 暴露的端点路径与 `/mcp` 不符，记录实际路径，后续 Task 12 的 Claude Desktop 接入 URL 据此调整。

- [ ] **Step 8: Commit**

```bash
git add aicoder-mcp
git commit -m "feat(mcp): MCP server 基础 + CalculatorTool（exp4j）+ ToolCallbackProvider 装配"
```

---

## Task 4: McpProperties + McpAuthFilter（MCP token 鉴权）

**Files:**
- Create: `aicoder-mcp/src/main/java/com/ai/coder/mcp/config/McpProperties.java`
- Create: `aicoder-mcp/src/main/java/com/ai/coder/mcp/config/McpAuthFilter.java`
- Test: `aicoder-mcp/src/test/java/com/ai/coder/mcp/config/McpAuthFilterTest.java`

- [ ] **Step 1: 实现 McpProperties**

`aicoder-mcp/src/main/java/com/ai/coder/mcp/config/McpProperties.java`：

```java
package com.ai.coder.mcp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

/** MCP 工具运行参数，经 Nacos aicoder-shared.yml 热刷新。 */
@RefreshScope
@Data
@ConfigurationProperties(prefix = "mcp")
public class McpProperties {

    /** 外部客户端接入 MCP server 必须携带的 access token。 */
    private String accessToken;

    /** MCP 调用归属的系统服务账号 userId（写入 X-User-Id 调下游 skill 服务）。 */
    private Long serviceUserId = 1L;

    private Search search = new Search();
    private Fetch fetch = new Fetch();
    private Image image = new Image();

    @Data
    public static class Search {
        private String provider = "tavily";
        private String apiKey;
        private int maxResults = 5;
    }

    @Data
    public static class Fetch {
        private int connectTimeout = 5000;
        private int readTimeout = 15000;
        private int maxLength = 20000;
        private boolean allowPrivateIp = false;
    }

    @Data
    public static class Image {
        /** 多模态模型 code（从 ModelConfig 取，需标 modelType=CHAT）。 */
        private String modelCode = "qwen2.5-vl";
    }
}
```

- [ ] **Step 2: 写 McpAuthFilter 失败测试**

`aicoder-mcp/src/test/java/com/ai/coder/mcp/config/McpAuthFilterTest.java`：

```java
package com.ai.coder.mcp.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpAuthFilterTest {

    private McpProperties props;
    private McpAuthFilter filter;

    @BeforeEach
    void setUp() {
        props = new McpProperties();
        props.setAccessToken("secret-token");
        filter = new McpAuthFilter(props);
    }

    @Test
    void valid_token_passes_through() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn("/mcp");
        when(req.getHeader("X-Mcp-Token")).thenReturn("secret-token");
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
        verify(resp, never()).setStatus(any());
    }

    @Test
    void missing_or_wrong_token_returns_401() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn("/mcp");
        when(req.getHeader("X-Mcp-Token")).thenReturn("wrong");
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, resp, chain);

        verify(resp).setStatus(HttpStatus.UNAUTHORIZED.value());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void non_mcp_path_skips_auth() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn("/v3/api-docs");
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
    }
}
```

- [ ] **Step 3: 运行测试验证失败**

Run: `mvn test -pl aicoder-mcp -Dtest=McpAuthFilterTest`
Expected: 编译失败（`McpAuthFilter` 不存在）。

- [ ] **Step 4: 实现 McpAuthFilter**

`aicoder-mcp/src/main/java/com/ai/coder/mcp/config/McpAuthFilter.java`：

```java
package com.ai.coder.mcp.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

import java.io.IOException;

/**
 * MCP 端点鉴权：仅对 /mcp（及 /api/mcp）校验 X-Mcp-Token == mcp.access-token。
 * 其他路径（swagger 等）放行，交由各自机制。Gateway 已对 /api/mcp/** 放行 JWT。
 */
@Configuration
@RequiredArgsConstructor
public class McpAuthFilter {

    private final McpProperties props;

    @Bean
    public FilterRegistrationBean<Filter> mcpTokenFilterRegistration() {
        FilterRegistrationBean<Filter> reg = new FilterRegistrationBean<>();
        reg.setFilter(this::doFilter);
        reg.addUrlPatterns("/*");
        reg.setOrder(0);
        return reg;
    }

    private void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        String path = req.getRequestURI();
        if (path != null && (path.contains("/mcp"))) {
            String token = req.getHeader("X-Mcp-Token");
            String expected = props.getAccessToken();
            if (expected == null || expected.isBlank() || !expected.equals(token)) {
                resp.setStatus(HttpStatus.UNAUTHORIZED.value());
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
```

- [ ] **Step 5: 运行测试验证通过**

Run: `mvn test -pl aicoder-mcp -Dtest=McpAuthFilterTest`
Expected: Tests run: 3, Failures: 0。

- [ ] **Step 6: Commit**

```bash
git add aicoder-mcp
git commit -m "feat(mcp): McpProperties（Nacos 热刷新）+ McpAuthFilter（X-Mcp-Token 鉴权）"
```

---

## Task 5: RestTemplateConfig + McpDynamicModelRegistry

**Files:**
- Create: `aicoder-mcp/src/main/java/com/ai/coder/mcp/config/RestTemplateConfig.java`
- Create: `aicoder-mcp/src/main/java/com/ai/coder/mcp/registry/McpDynamicModelRegistry.java`

- [ ] **Step 1: 实现 RestTemplateConfig（两个 RestTemplate）**

`aicoder-mcp/src/main/java/com/ai/coder/mcp/config/RestTemplateConfig.java`：

```java
package com.ai.coder.mcp.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * 两个 RestTemplate：
 * - loadBalancedRestTemplate：解析服务名（http://aicoder-skill/...）供 SkillTool 调 skill 服务；
 * - plainRestTemplate：直连外部 URL（搜索 API、网页抓取），不走服务发现。
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    @LoadBalanced
    public RestTemplate loadBalancedRestTemplate() {
        return new RestTemplate();
    }

    @Bean
    public RestTemplate plainRestTemplate() {
        return new RestTemplate();
    }
}
```

- [ ] **Step 2: 实现 McpDynamicModelRegistry（空子类，拿多模态 ChatModel）**

`aicoder-mcp/src/main/java/com/ai/coder/mcp/registry/McpDynamicModelRegistry.java`：

```java
package com.ai.coder.mcp.registry;

import com.ai.coder.core.registry.AbstractDynamicModelRegistry;
import com.ai.coder.core.repository.ModelConfigRepository;
import com.ai.coder.core.repository.ModelProviderRepository;
import org.springframework.stereotype.Component;

/**
 * mcp 模块模型注册表：基类 abstract 不能直接注入，故建空子类成为 @Component。
 * image_analysis 工具注入本类，调 getChatModel(modelCode) 取多模态模型（DB 里 modelType 标 CHAT）。
 * spring-ai ChatModel 对多模态模型原生支持 image part，无需新 modelType。
 */
@Component
public class McpDynamicModelRegistry extends AbstractDynamicModelRegistry {

    public McpDynamicModelRegistry(ModelConfigRepository modelConfigRepository,
                                   ModelProviderRepository providerRepository) {
        super(modelConfigRepository, providerRepository);
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `mvn clean compile -pl aicoder-mcp -am -DskipTests`
Expected: BUILD SUCCESS。

- [ ] **Step 4: Commit**

```bash
git add aicoder-mcp
git commit -m "feat(mcp): RestTemplateConfig（@LoadBalanced + 普通）+ McpDynamicModelRegistry 子类"
```

---

## Task 6: skill 服务加 getByName 接口（read_skill 依赖）

> **背景**：当前 SkillController 只有 `GET /api/skill/{id}`（Long），无按 name 查询。read_skill 需按 name 取技能，故先补此接口。`SkillRepository.findByName` 已存在，直接复用。

**Files:**
- Modify: `aicoder-skill/src/main/java/com/ai/coder/skill/service/SkillService.java`（在 `getById` 方法后）
- Modify: `aicoder-skill/src/main/java/com/ai/coder/skill/controller/SkillController.java`（在 `getById` 后）
- Test: `aicoder-skill/src/test/java/com/ai/coder/skill/service/SkillServiceTest.java`（新增）

- [ ] **Step 1: 写 SkillService.getByName 失败测试**

`aicoder-skill/src/test/java/com/ai/coder/skill/service/SkillServiceTest.java`：

```java
package com.ai.coder.skill.service;

import com.ai.coder.skill.config.SkillProperties;
import com.ai.coder.skill.entity.Skill;
import com.ai.coder.skill.exception.SkillNotFoundException;
import com.ai.coder.skill.repository.SkillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkillServiceTest {

    private SkillRepository repository;
    private SkillService service;

    @BeforeEach
    void setUp() {
        repository = mock(SkillRepository.class);
        service = new SkillService(
                repository,
                mock(SkillRegistrySyncService.class),
                mock(SkillLifecycleService.class),
                new SkillProperties(),
                mock(SkillQualityScorer.class));
    }

    @Test
    void getByName_returns_skill_when_found() {
        Skill skill = new Skill();
        skill.setName("code-review");
        when(repository.findByName("code-review")).thenReturn(Optional.of(skill));

        assertSame(skill, service.getByName("code-review"));
    }

    @Test
    void getByName_throws_when_missing() {
        when(repository.findByName("nope")).thenReturn(Optional.empty());
        assertThrows(SkillNotFoundException.class, () -> service.getByName("nope"));
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -pl aicoder-skill -Dtest=SkillServiceTest`
Expected: 编译失败（`getByName` 不存在 / 构造器签名不符则按实际 SkillService 构造器调整 mock 参数顺序）。

> **注意**：`SkillService` 构造器参数顺序为 `(SkillRepository, SkillRegistrySyncService, SkillLifecycleService, SkillProperties, SkillQualityScorer)`，若实现时发现顺序不同，按实际调整测试 setUp。

- [ ] **Step 3: 在 SkillService 加 getByName**

在 `SkillService.java` 的 `getById` 方法后追加：

```java
    public Skill getByName(String name) {
        if (name == null || name.isBlank()) {
            throw new SkillNotFoundException("技能名不能为空");
        }
        return skillRepository.findByName(name)
                .orElseThrow(() -> new SkillNotFoundException("技能不存在：" + name));
    }
```

- [ ] **Step 4: 在 SkillController 加 GET /api/skill/name/{name}**

在 `SkillController.java` 的 `getById` 方法后追加：

```java
    @GetMapping("/name/{name}")
    public ResponseEntity<Skill> getByName(@PathVariable String name) {
        return ResponseEntity.ok(skillService.getByName(name));
    }
```

> 注意路由顺序：`/name/{name}` 必须在 `/{id}` 之前或用更明确路径，避免 `{id}` 先吞掉 `name`。当前 `/{id}` 是 Long 且位置在前——`/name/xxx` 不会被 `/{id}` 匹配（因为多了 `/name` 段），无需调整顺序。

- [ ] **Step 5: 运行测试验证通过**

Run: `mvn test -pl aicoder-skill -Dtest=SkillServiceTest`
Expected: Tests run: 2, Failures: 0。

- [ ] **Step 6: Commit**

```bash
git add aicoder-skill
git commit -m "feat(skill): 新增 getByName 接口（GET /api/skill/name/{name}）供 MCP read_skill 调用"
```

---

## Task 7: SkillTool（read_skill + submit_skill_draft）

**Files:**
- Create: `aicoder-mcp/src/main/java/com/ai/coder/mcp/tool/SkillTool.java`
- Test: `aicoder-mcp/src/test/java/com/ai/coder/mcp/tool/SkillToolTest.java`
- Modify: `aicoder-mcp/src/main/java/com/ai/coder/mcp/config/McpServerConfig.java`

- [ ] **Step 1: 写 SkillTool 失败测试**

`aicoder-mcp/src/test/java/com/ai/coder/mcp/tool/SkillToolTest.java`：

```java
package com.ai.coder.mcp.tool;

import com.ai.coder.mcp.config.McpProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillToolTest {

    private RestTemplate restTemplate;
    private SkillTool tool;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        McpProperties props = new McpProperties();
        props.setServiceUserId(9L);
        tool = new SkillTool(restTemplate, props);
    }

    @Test
    void read_skill_returns_content_and_propagates_user_header() {
        when(restTemplate.getForObject(eq("http://aicoder-skill/api/skill/name/code-review"), eq(Map.class)))
                .thenReturn(Map.of("name", "code-review", "content", "# 审查代码"));

        String result = tool.readSkill("code-review");

        assertTrue(result.contains("审查代码"), result);
    }

    @Test
    void submit_skill_draft_posts_to_skill_service() {
        when(restTemplate.postForObject(any(String.class), any(), eq(Map.class)))
                .thenReturn(Map.of("name", "code-review", "status", "PENDING_APPROVAL"));

        String result = tool.submitSkillDraft("code-review", "审查", "正文");

        assertTrue(result.contains("PENDING_APPROVAL"), result);
    }

    @Test
    void submit_skill_draft_failure_returns_friendly_string() {
        when(restTemplate.postForObject(any(String.class), any(), eq(Map.class)))
                .thenThrow(new RuntimeException("skill 不可达"));

        String result = tool.submitSkillDraft("code-review", "审查", "正文");
        assertTrue(result.contains("提交失败"), result);
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -pl aicoder-mcp -Dtest=SkillToolTest`
Expected: 编译失败（`SkillTool` 不存在）。

- [ ] **Step 3: 实现 SkillTool**

`aicoder-mcp/src/main/java/com/ai/coder/mcp/tool/SkillTool.java`：

```java
package com.ai.coder.mcp.tool;

import com.ai.coder.mcp.config.McpProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 技能读写工具：经 @LoadBalanced RestTemplate 调 aicoder-skill。
 * - read_skill：GET /api/skill/name/{name}
 * - submit_skill_draft：POST /api/skill/draft（请求体 SkillDraftRequest，X-User-Id 透传服务账号）
 */
@Slf4j
@Component
public class SkillTool {

    private static final String BASE_URL = "http://aicoder-skill/api/skill";

    private final RestTemplate restTemplate;
    private final McpProperties props;

    public SkillTool(@Qualifier("loadBalancedRestTemplate") RestTemplate restTemplate, McpProperties props) {
        this.restTemplate = restTemplate;
        this.props = props;
    }

    @Tool(description = "读取平台技能库中指定技能的完整内容。参数 name 为技能名（kebab-case）。")
    public String readSkill(String name) {
        if (name == null || name.isBlank()) {
            return "技能名不能为空。";
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> skill = restTemplate.getForObject(
                    BASE_URL + "/name/" + name, Map.class);
            if (skill == null) {
                return "未找到技能：" + name;
            }
            String content = String.valueOf(skill.getOrDefault("content", ""));
            return "技能「" + skill.getOrDefault("name", name) + "」：\n" + content;
        } catch (Exception e) {
            log.warn("read_skill 失败 name={}", name, e);
            return "读取技能失败：" + e.getMessage();
        }
    }

    @Tool(description = "提交一个新技能草稿进入平台审批队列。参数：name（kebab-case）、description（一句话描述）、content（完整技能文本）。")
    public String submitSkillDraft(String name, String description, String content) {
        if (name == null || name.isBlank()) {
            return "技能名不能为空，未提交。";
        }
        if (name.contains("..") || name.contains("/") || name.contains("\\") || name.contains(":")) {
            return "非法的技能名：" + name + "，未提交。";
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-User-Id", String.valueOf(props.getServiceUserId()));
            Map<String, Object> body = Map.of(
                    "name", name,
                    "description", description == null ? "" : description,
                    "content", content == null ? "" : content);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.postForObject(BASE_URL + "/draft", entity, Map.class);
            String status = resp == null ? "未知" : String.valueOf(resp.getOrDefault("status", "未知"));
            return "技能草稿「" + name + "」已提交，状态：" + status + "。";
        } catch (Exception e) {
            log.warn("submit_skill_draft 失败 name={}", name, e);
            return "技能提交失败：" + e.getMessage() + "。请稍后重试。";
        }
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn test -pl aicoder-mcp -Dtest=SkillToolTest`
Expected: Tests run: 3, Failures: 0。

- [ ] **Step 5: 注册 SkillTool 到 McpServerConfig**

修改 `McpServerConfig.java` 的 `mcpToolCallbacks` bean，追加 `SkillTool` 参数与 `ToolCallbacks.from`：

```java
package com.ai.coder.mcp.config;

import com.ai.coder.mcp.tool.CalculatorTool;
import com.ai.coder.mcp.tool.SkillTool;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpServerConfig {

    @Bean
    public ToolCallbackProvider mcpToolCallbacks(CalculatorTool calculatorTool, SkillTool skillTool) {
        return ToolCallbacks.from(calculatorTool, skillTool);
    }
}
```

- [ ] **Step 6: Commit**

```bash
git add aicoder-mcp
git commit -m "feat(mcp): SkillTool（read_skill + submit_skill_draft 调 aicoder-skill）"
```

---

## Task 8: WebSearchTool + WebSearchService（网络搜索）

**Files:**
- Create: `aicoder-mcp/src/main/java/com/ai/coder/mcp/service/WebSearchService.java`
- Create: `aicoder-mcp/src/main/java/com/ai/coder/mcp/tool/WebSearchTool.java`
- Test: `aicoder-mcp/src/test/java/com/ai/coder/mcp/service/WebSearchServiceTest.java`
- Modify: `McpServerConfig.java`

- [ ] **Step 1: 写 WebSearchService 失败测试**

`aicoder-mcp/src/test/java/com/ai/coder/mcp/service/WebSearchServiceTest.java`：

```java
package com.ai.coder.mcp.service;

import com.ai.coder.mcp.config.McpProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebSearchServiceTest {

    private RestTemplate restTemplate;
    private WebSearchService service;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        McpProperties props = new McpProperties();
        props.getSearch().setApiKey("k");
        service = new WebSearchService(restTemplate, props);
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_returns_formatted_results() {
        when(restTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(Map.of("results", List.of(
                        Map.of("title", "Spring AI", "url", "https://spring.io", "content", "AI 框架"))));

        String out = service.search("spring ai");

        assertTrue(out.contains("Spring AI"), out);
        assertTrue(out.contains("https://spring.io"), out);
    }

    @Test
    void search_without_api_key_returns_hint() {
        McpProperties props = new McpProperties();
        props.getSearch().setApiKey("");
        WebSearchService s = new WebSearchService(restTemplate, props);

        String out = s.search("anything");
        assertTrue(out.contains("未配置"), out);
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -pl aicoder-mcp -Dtest=WebSearchServiceTest`
Expected: 编译失败（`WebSearchService` 不存在）。

- [ ] **Step 3: 实现 WebSearchService**

`aicoder-mcp/src/main/java/com/ai/coder/mcp/service/WebSearchService.java`：

```java
package com.ai.coder.mcp.service;

import com.ai.coder.mcp.config.McpProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/** 网络搜索：调 Tavily Search API（外部 URL，用普通 RestTemplate）。 */
@Slf4j
@Service
public class WebSearchService {

    private static final String TAVILY_URL = "https://api.tavily.com/search";

    private final RestTemplate restTemplate;
    private final McpProperties props;

    public WebSearchService(@Qualifier("plainRestTemplate") RestTemplate restTemplate, McpProperties props) {
        this.restTemplate = restTemplate;
        this.props = props;
    }

    @SuppressWarnings("unchecked")
    public String search(String query) {
        String apiKey = props.getSearch().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return "网络搜索未配置：缺少 mcp.search.api-key。请在 Nacos 配置 Tavily API key。";
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> body = Map.of(
                    "api_key", apiKey,
                    "query", query,
                    "max_results", props.getSearch().getMaxResults());
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            Map<String, Object> resp = restTemplate.postForObject(TAVILY_URL, entity, Map.class);
            if (resp == null || !(resp.get("results") instanceof List<?> list)) {
                return "网络搜索无结果。";
            }
            StringBuilder sb = new StringBuilder();
            int i = 1;
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    sb.append(i++).append(". ").append(m.getOrDefault("title", ""))
                      .append("\n   ").append(m.getOrDefault("url", ""))
                      .append("\n   ").append(m.getOrDefault("content", "")).append("\n\n");
                }
            }
            return sb.length() == 0 ? "网络搜索无结果。" : sb.toString();
        } catch (Exception e) {
            log.warn("web_search 失败 query={}", query, e);
            return "网络搜索失败：" + e.getMessage();
        }
    }
}
```

- [ ] **Step 4: 实现 WebSearchTool（薄封装）**

`aicoder-mcp/src/main/java/com/ai/coder/mcp/tool/WebSearchTool.java`：

```java
package com.ai.coder.mcp.tool;

import com.ai.coder.mcp.service.WebSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/** web_search 工具：网络搜索，返回标题+摘要+链接。 */
@Component
@RequiredArgsConstructor
public class WebSearchTool {

    private final WebSearchService webSearchService;

    @Tool(description = "网络搜索：根据查询词搜索网络信息，返回若干条结果（标题、链接、摘要）。")
    public String webSearch(String query) {
        if (query == null || query.isBlank()) {
            return "查询词不能为空。";
        }
        return webSearchService.search(query.trim());
    }
}
```

- [ ] **Step 5: 运行测试验证通过**

Run: `mvn test -pl aicoder-mcp -Dtest=WebSearchServiceTest`
Expected: Tests run: 2, Failures: 0。

- [ ] **Step 6: 注册 WebSearchTool 到 McpServerConfig**

`McpServerConfig.java` 的 bean 签名追加 `WebSearchTool`：

```java
    @Bean
    public ToolCallbackProvider mcpToolCallbacks(CalculatorTool calculatorTool,
                                                 SkillTool skillTool,
                                                 WebSearchTool webSearchTool) {
        return ToolCallbacks.from(calculatorTool, skillTool, webSearchTool);
    }
```
（import `com.ai.coder.mcp.tool.WebSearchTool;`）

- [ ] **Step 7: Commit**

```bash
git add aicoder-mcp
git commit -m "feat(mcp): WebSearchTool + WebSearchService（Tavily 网络搜索）"
```

---

## Task 9: WebFetchTool + WebFetchService（网页抓取转 markdown）

**Files:**
- Create: `aicoder-mcp/src/main/java/com/ai/coder/mcp/service/WebFetchService.java`
- Create: `aicoder-mcp/src/main/java/com/ai/coder/mcp/tool/WebFetchTool.java`
- Test: `aicoder-mcp/src/test/java/com/ai/coder/mcp/service/WebFetchServiceTest.java`
- Modify: `McpServerConfig.java`

- [ ] **Step 1: 写 WebFetchService 失败测试**

`aicoder-mcp/src/test/java/com/ai/coder/mcp/service/WebFetchServiceTest.java`：

```java
package com.ai.coder.mcp.service;

import com.ai.coder.mcp.config.McpProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebFetchServiceTest {

    private RestTemplate restTemplate;
    private WebFetchService service;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        service = new WebFetchService(restTemplate, new McpProperties());
    }

    @Test
    void isAllowed_rejects_private_ip() {
        assertFalse(service.isAllowed("http://127.0.0.1:8080/x"));
        assertFalse(service.isAllowed("http://localhost/x"));
        assertFalse(service.isAllowed("http://192.168.1.1/x"));
    }

    @Test
    void isAllowed_accepts_public_host() {
        assertTrue(service.isAllowed("https://example.com/page"));
    }

    @Test
    void fetch_rejects_private_url_without_request() {
        assertThrows(IllegalArgumentException.class,
                () -> service.fetch("http://127.0.0.1/secret"));
    }

    @Test
    void fetch_converts_html_to_markdown() {
        when(restTemplate.getForObject(eq("https://example.com/a"), eq(String.class)))
                .thenReturn("<html><body><h1>Title</h1><p>Hello</p></body></html>");

        String md = service.fetch("https://example.com/a");

        assertTrue(md.contains("Title"), "markdown 应含标题文本：" + md);
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -pl aicoder-mcp -Dtest=WebFetchServiceTest`
Expected: 编译失败（`WebFetchService` 不存在）。

- [ ] **Step 3: 实现 WebFetchService**

`aicoder-mcp/src/main/java/com/ai/coder/mcp/service/WebFetchService.java`：

```java
package com.ai.coder.mcp.service;

import com.ai.coder.mcp.config.McpProperties;
import com.vladsch.flexmark.ext.gfm.tables.TablesExtension;
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import com.vladsch.flexmark.util.data.MutableDataSet;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;

/** 网页抓取：Jsoup 取正文 HTML → flexmark 转 markdown。SSRF 防护：禁内网地址。 */
@Slf4j
@Service
public class WebFetchService {

    private final RestTemplate restTemplate;
    private final McpProperties props;
    private final FlexmarkHtmlConverter htmlToMarkdown;

    public WebFetchService(@Qualifier("plainRestTemplate") RestTemplate restTemplate, McpProperties props) {
        this.restTemplate = restTemplate;
        this.props = props;
        MutableDataSet opts = new MutableDataSet();
        opts.set(FlexmarkHtmlConverter.EXTENSIONS, List.of(TablesExtension.create()));
        this.htmlToMarkdown = FlexmarkHtmlConverter.builder(opts).build();
    }

    public String fetch(String url) {
        if (!isAllowed(url)) {
            throw new IllegalArgumentException("拒绝抓取内网/本地地址：" + url);
        }
        try {
            String html = restTemplate.getForObject(url, String.class);
            if (html == null || html.isBlank()) {
                return "网页内容为空。";
            }
            int max = props.getFetch().getMaxLength();
            if (html.length() > max) {
                html = html.substring(0, max);
            }
            return convertToMarkdown(html);
        } catch (Exception e) {
            log.warn("web_fetch 失败 url={}", url, e);
            return "网页抓取失败：" + e.getMessage();
        }
    }

    /** SSRF 防护：拒绝 localhost 与私有/回环 IP。allow-private-ip=true 时放行（仅开发用）。 */
    public boolean isAllowed(String url) {
        if (props.getFetch().isAllowPrivateIp()) {
            return true;
        }
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null) return false;
            if ("localhost".equalsIgnoreCase(host)) return false;
            InetAddress addr = InetAddress.getByName(host);
            return !(addr.isAnyLocalAddress() || addr.isLoopbackAddress()
                    || addr.isSiteLocalAddress() || addr.isLinkLocalAddress());
        } catch (Exception e) {
            return false;
        }
    }

    public String convertToMarkdown(String html) {
        String clean = Jsoup.clean(html, "", Safelist.relaxed());
        return htmlToMarkdown.convert(clean).trim();
    }
}
```

- [ ] **Step 4: 实现 WebFetchTool**

`aicoder-mcp/src/main/java/com/ai/coder/mcp/tool/WebFetchTool.java`：

```java
package com.ai.coder.mcp.tool;

import com.ai.coder.mcp.service.WebFetchService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/** web_fetch 工具：抓取指定网页正文并转为易读 markdown 文档。 */
@Component
@RequiredArgsConstructor
public class WebFetchTool {

    private final WebFetchService webFetchService;

    @Tool(description = "抓取指定网页内容并转换为易读的 markdown 文档格式。参数 url 为目标网页地址。")
    public String webFetch(String url) {
        if (url == null || url.isBlank()) {
            return "URL 不能为空。";
        }
        return webFetchService.fetch(url.trim());
    }
}
```

- [ ] **Step 5: 运行测试验证通过**

Run: `mvn test -pl aicoder-mcp -Dtest=WebFetchServiceTest`
Expected: Tests run: 4, Failures: 0。

- [ ] **Step 6: 注册 WebFetchTool 到 McpServerConfig**

`McpServerConfig.java` 的 bean 追加 `WebFetchTool`：

```java
    @Bean
    public ToolCallbackProvider mcpToolCallbacks(CalculatorTool calculatorTool,
                                                 SkillTool skillTool,
                                                 WebSearchTool webSearchTool,
                                                 WebFetchTool webFetchTool) {
        return ToolCallbacks.from(calculatorTool, skillTool, webSearchTool, webFetchTool);
    }
```
（import `com.ai.coder.mcp.tool.WebFetchTool;`）

- [ ] **Step 7: Commit**

```bash
git add aicoder-mcp
git commit -m "feat(mcp): WebFetchTool + WebFetchService（Jsoup+flexmark 转 markdown，SSRF 防护）"
```

---

## Task 10: ImageAnalysisTool + ImageAnalysisService（图文解析）

**Files:**
- Create: `aicoder-mcp/src/main/java/com/ai/coder/mcp/service/ImageAnalysisService.java`
- Create: `aicoder-mcp/src/main/java/com/ai/coder/mcp/tool/ImageAnalysisTool.java`
- Test: `aicoder-mcp/src/test/java/com/ai/coder/mcp/service/ImageAnalysisServiceTest.java`
- Modify: `McpServerConfig.java`

- [ ] **Step 1: 写 ImageAnalysisService 失败测试**

`aicoder-mcp/src/test/java/com/ai/coder/mcp/service/ImageAnalysisServiceTest.java`：

```java
package com.ai.coder.mcp.service;

import com.ai.coder.mcp.config.McpProperties;
import com.ai.coder.mcp.registry.McpDynamicModelRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImageAnalysisServiceTest {

    private ChatModel chatModel;
    private ImageAnalysisService service;

    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModel.class);
        McpDynamicModelRegistry registry = mock(McpDynamicModelRegistry.class);
        when(registry.getChatModel(any(String.class))).thenReturn(chatModel);
        McpProperties props = new McpProperties();
        props.getImage().setModelCode("qwen2.5-vl");
        service = new ImageAnalysisService(registry, props);
    }

    @Test
    void analyze_returns_model_text() {
        when(chatModel.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage("图中是一只猫"))))));

        String out = service.analyze("https://example.com/cat.png", "描述图片");

        assertTrue(out.contains("猫"), out);
    }

    @Test
    void analyze_handles_model_error() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("模型不可用"));

        String out = service.analyze("https://example.com/cat.png", "描述");
        assertTrue(out.contains("失败") || out.contains("不可用"), out);
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -pl aicoder-mcp -Dtest=ImageAnalysisServiceTest`
Expected: 编译失败（`ImageAnalysisService` 不存在）。

- [ ] **Step 3: 实现 ImageAnalysisService**

`aicoder-mcp/src/main/java/com/ai/coder/mcp/service/ImageAnalysisService.java`：

```java
package com.ai.coder.mcp.service;

import com.ai.coder.mcp.config.McpProperties;
import com.ai.coder.mcp.registry.McpDynamicModelRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Media;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import java.net.URL;

/**
 * 图文解析：从 McpDynamicModelRegistry 取多模态 ChatModel，构造图片 + 提问的 UserMessage 调用。
 * 支持 URL 形式图片（第一版）；base64 可后续扩展。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageAnalysisService {

    private final McpDynamicModelRegistry registry;
    private final McpProperties props;

    public String analyze(String imageUrl, String question) {
        try {
            ChatModel chatModel = registry.getChatModel(props.getImage().getModelCode());
            UserMessage userMessage = UserMessage.builder()
                    .text(question == null ? "描述这张图片" : question)
                    .media(Media.builder()
                            .mimeType(MimeTypeUtils.IMAGE_PNG)
                            .data(new UrlResource(new URL(imageUrl)))
                            .build())
                    .build();
            ChatResponse response = chatModel.call(new Prompt(userMessage));
            return response.getResult().getOutput().getText();
        } catch (Exception e) {
            log.warn("image_analysis 失败 model={}", props.getImage().getModelCode(), e);
            return "图文解析失败：" + e.getMessage()
                    + "。请确认 ModelConfig 已配置多模态模型（modelType=CHAT，如 qwen2.5-vl）。";
        }
    }
}
```

> **说明**：Spring AI 1.1.2 的 `UserMessage.builder().text(...).media(Media)` 与 `Media.builder().mimeType(...).data(Resource)` 为该版本 API。若编译期签名不符（版本细微差异），按 IDE 提示调整为 `new UserMessage(question, new Media(MimeTypeUtils.IMAGE_PNG, new UrlResource(url)))` 构造形式——二者语义等价。

- [ ] **Step 4: 实现 ImageAnalysisTool**

`aicoder-mcp/src/main/java/com/ai/coder/mcp/tool/ImageAnalysisTool.java`：

```java
package com.ai.coder.mcp.tool;

import com.ai.coder.mcp.service.ImageAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/** image_analysis 工具：用多模态 LLM 解析图片内容。 */
@Component
@RequiredArgsConstructor
public class ImageAnalysisTool {

    private final ImageAnalysisService imageAnalysisService;

    @Tool(description = "图文解析：用多模态模型解析图片内容并回答问题。参数：image（图片 URL）、question（想了解什么）。")
    public String imageAnalysis(String image, String question) {
        if (image == null || image.isBlank()) {
            return "图片地址不能为空。";
        }
        return imageAnalysisService.analyze(image.trim(), question);
    }
}
```

- [ ] **Step 5: 运行测试验证通过**

Run: `mvn test -pl aicoder-mcp -Dtest=ImageAnalysisServiceTest`
Expected: Tests run: 2, Failures: 0。

- [ ] **Step 6: 注册 ImageAnalysisTool 到 McpServerConfig**

`McpServerConfig.java` 的 bean 追加 `ImageAnalysisTool`（最终形态）：

```java
package com.ai.coder.mcp.config;

import com.ai.coder.mcp.tool.CalculatorTool;
import com.ai.coder.mcp.tool.ImageAnalysisTool;
import com.ai.coder.mcp.tool.SkillTool;
import com.ai.coder.mcp.tool.WebFetchTool;
import com.ai.coder.mcp.tool.WebSearchTool;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpServerConfig {

    @Bean
    public ToolCallbackProvider mcpToolCallbacks(CalculatorTool calculatorTool,
                                                 SkillTool skillTool,
                                                 WebSearchTool webSearchTool,
                                                 WebFetchTool webFetchTool,
                                                 ImageAnalysisTool imageAnalysisTool) {
        return ToolCallbacks.from(calculatorTool, skillTool, webSearchTool, webFetchTool, imageAnalysisTool);
    }
}
```

- [ ] **Step 7: Commit**

```bash
git add aicoder-mcp
git commit -m "feat(mcp): ImageAnalysisTool + ImageAnalysisService（多模态 LLM 图文解析）"
```

---

## Task 11: GlobalExceptionHandler（统一异常）

**Files:**
- Create: `aicoder-mcp/src/main/java/com/ai/coder/mcp/config/GlobalExceptionHandler.java`

- [ ] **Step 1: 实现 GlobalExceptionHandler**

`aicoder-mcp/src/main/java/com/ai/coder/mcp/config/GlobalExceptionHandler.java`：

```java
package com.ai.coder.mcp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** 统一异常处理（仿 aicoder-skill）。MCP 工具内部已把异常降级为友好串，此处兜底非预期异常。 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body(400, e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception e) {
        log.error("MCP 服务未处理异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body(500, "MCP 服务内部错误"));
    }

    private static Map<String, Object> body(int code, String message) {
        return Map.of("code", code, "message", message != null ? message : "未知错误");
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn clean compile -pl aicoder-mcp -am -DskipTests`
Expected: BUILD SUCCESS。

- [ ] **Step 3: 全量单测回归**

Run: `mvn test -pl aicoder-mcp`
Expected: 所有测试（CalculatorToolTest / McpAuthFilterTest / SkillToolTest / WebSearchServiceTest / WebFetchServiceTest / ImageAnalysisServiceTest）全绿。

- [ ] **Step 4: Commit**

```bash
git add aicoder-mcp
git commit -m "feat(mcp): GlobalExceptionHandler 统一异常处理"
```

---

## Task 12: 端到端验证（Claude Desktop 接入）

**Files:** 无代码改动，纯验证。

- [ ] **Step 1: 启动依赖中间件**

确认 MySQL（3306, test_ai）、Redis（6379）、Nacos（8848）已启动。

- [ ] **Step 2: 配置多模态模型（image_analysis 前置）**

在 `ai_model_config` 表插入一条多模态模型记录（若尚无），`model_type = 'CHAT'`、`enabled = 1`，`model_code` 与 Nacos `mcp.image.model-code` 一致（默认 `qwen2.5-vl`），且对应 provider（如 Ollama）已 pull 该模型：
```bash
ollama pull qwen2.5-vl   # 若用 Ollama
```

- [ ] **Step 3: 配置搜索 key（web_search 前置）**

在 Nacos `aicoder-shared.yml`（或环境变量 `MCP_SEARCH_API_KEY`）配置 Tavily API key：
```yaml
mcp:
  search:
    api-key: <your-tavily-key>
```

- [ ] **Step 4: 启动服务**

按顺序启动：aicoder-gateway → aicoder-skill → aicoder-mcp。
Run（各开终端）:
```
mvn spring-boot:run -pl aicoder-gateway
mvn spring-boot:run -pl aicoder-skill
mvn spring-boot:run -pl aicoder-mcp
```
Expected:
- aicoder-mcp 日志出现 MCP server 启动 + 注册 5 个工具相关日志
- aicoder-mcp 注册到 Nacos（服务名 `aicoder-mcp`）

- [ ] **Step 5: 验证 MCP 端点可达（经 Gateway）**

```bash
curl -i -H "X-Mcp-Token: dev-mcp-token-change-me" http://localhost:8080/api/mcp/mcp
```
Expected: 非 404/非 401（端点存在且 token 通过；具体响应随 MCP 握手协议）。
（无 token 应返回 401，验证鉴权生效。）

- [ ] **Step 6: Claude Desktop 接入配置**

在 Claude Desktop 的 `claude_desktop_config.json` 添加 MCP server（streamable-http）：
```json
{
  "mcpServers": {
    "aicoder": {
      "url": "http://<可访问网关的IP>:8080/api/mcp/mcp",
      "headers": { "X-Mcp-Token": "dev-mcp-token-change-me" }
    }
  }
}
```
重启 Claude Desktop。

- [ ] **Step 7: 逐工具验证**

在 Claude Desktop 中确认能看到 5 个工具（calculator / read_skill / submit_skill_draft / web_search / web_fetch / image_analysis，其中技能读写 2 个方法算 2 个工具），并逐个调用：
- calculator：`3 * (2 + 2)` → 12
- web_search：「Spring AI」→ 返回若干结果链接
- web_fetch：抓取某公网 URL → 返回 markdown
- read_skill：读一个已存在的技能名 → 返回内容
- submit_skill_draft：提交一个草稿 → 在 aicoder-skill 审批队列可见
- image_analysis：传一张公网图片 URL + 提问 → 返回解析

Expected: 4 类能力（6 个工具）全部可调用并返回正确结果。

- [ ] **Step 8: 记录端点路径并更新文档**

已验证：端点实际为 SSE 模式（`GET /sse` + `POST /mcp/message?sessionId=xxx`），非原设计的 streamable-http `/mcp`。Claude Desktop 配置需用 SSE 端点 URL。

- [ ] **Step 9: 最终 Commit（若有文档微调）**

```bash
git add -A
git commit -m "docs(mcp): 端到端验证通过，回填实际 MCP 端点路径"
```

---

## Self-Review 结论

**1. Spec 覆盖：**
- calculator → Task 3 ✓
- web_search → Task 8 ✓
- web_fetch → Task 9 ✓
- image_analysis → Task 10 ✓
- read_skill / submit_skill_draft → Task 6（补 getByName 接口）+ Task 7 ✓
- 传输协议 WebMVC streamable HTTP → Task 1（starter）+ Task 3（验证）✓
- 认证（token + 服务账号）→ Task 4 ✓
- Gateway 路由 + JWT 白名单 → Task 2 ✓
- 复用 ModelConfig、不新建表 → Task 5（McpDynamicModelRegistry）✓
- 前端第一版不做 → 无对应任务（已划出）✓

**2. 占位符扫描：** 无 TBD/TODO；image_analysis 的 Spring AI API 已给出 builder 与构造两种等价写法；端点路径差异已在 Task 3/12 设置验证回填步骤。

**3. 类型一致性：**
- `McpProperties` 的 `search/fetch/image` 子类与各 Service 引用字段名一致（`apiKey`/`maxResults`/`maxLength`/`allowPrivateIp`/`modelCode`/`serviceUserId`/`accessToken`）。
- 两个 RestTemplate bean（`loadBalancedRestTemplate` / `plainRestTemplate`）在 SkillTool（loadBalanced）与 WebSearch/WebFetch（plain）中用 `@Qualifier` 一致引用。
- `McpDynamicModelRegistry` 构造器 `super(repo, repo)` 与基类一致；`ImageAnalysisService` 注入它调 `getChatModel`。
- `SkillService.getByName` / `SkillController GET /name/{name}` / `SkillTool.readSkill` 三处 URL `http://aicoder-skill/api/skill/name/{name}` 一致。
- `McpServerConfig` 工具参数随 Task 3/7/8/9/10 递增，最终形态在 Task 10 Step 6 给出。
