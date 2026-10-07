package com.ai.coder.skill.service;

import com.ai.coder.skill.entity.Skill;
import com.ai.coder.skill.entity.SkillStatus;
import com.ai.coder.skill.repository.SkillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillGenerationServiceTest {

    private ChatModel chatModel;
    private SkillService skillService;
    private SkillRepository skillRepository;
    private SkillGenerationService service;

    private static final String META_CONTENT = """
            ---
            name: generate-skill
            description: 元技能
            ---
            # Generate Skill
            指令...
            """;

    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModel.class);
        skillService = mock(SkillService.class);
        skillRepository = mock(SkillRepository.class);
        Skill meta = new Skill();
        meta.setName("generate-skill");
        meta.setContent(META_CONTENT);
        when(skillRepository.findByName("generate-skill")).thenReturn(Optional.of(meta));
        service = new SkillGenerationService(chatModel, skillService, skillRepository);
    }

    @Test
    void generate_parses_frontmatter_and_submits_draft() {
        String modelOutput = """
                ---
                name: code-review-checklist
                description: 对代码变更做清单式审查。
                ---
                # Code Review Checklist
                审查可读性、命名、错误处理、测试覆盖、性能与安全，给出修改建议与示例。
                """;
        when(chatModel.call(any(Prompt.class))).thenReturn(resp(modelOutput));
        Skill submitted = new Skill();
        submitted.setName("code-review-checklist");
        submitted.setStatus(SkillStatus.PENDING_APPROVAL);
        when(skillService.submitDraft(any(), any(), any(), any())).thenReturn(submitted);

        Skill result = service.generate("帮我审查代码", 7L);

        verify(skillService).submitDraft(
                org.mockito.ArgumentMatchers.eq("code-review-checklist"),
                org.mockito.ArgumentMatchers.eq("对代码变更做清单式审查。"),
                org.mockito.ArgumentMatchers.contains("# Code Review Checklist"),
                org.mockito.ArgumentMatchers.eq(7L));
        assertEquals("code-review-checklist", result.getName());
    }

    @Test
    void generate_strips_code_fences_and_parses_frontmatter_inside() {
        // 模型常把 SKILL.md 包在 ```markdown 围栏里；应剥围栏后正确解析 frontmatter name
        String fenced = """
                ```markdown
                ---
                name: nl-to-3nf-sql
                description: 自然语言转第三范式建表 SQL。
                ---
                # NL to 3NF SQL
                分析需求、识别实体与关系、生成含主外键索引注释的建表 SQL。
                ```
                """;
        when(chatModel.call(any(Prompt.class))).thenReturn(resp(fenced));
        Skill submitted = new Skill();
        submitted.setName("nl-to-3nf-sql");
        submitted.setStatus(SkillStatus.PENDING_APPROVAL);
        when(skillService.submitDraft(any(), any(), any(), any())).thenReturn(submitted);

        service.generate("建表 SQL", 7L);

        verify(skillService).submitDraft(
                org.mockito.ArgumentMatchers.eq("nl-to-3nf-sql"),   // 剥围栏后取 frontmatter name
                org.mockito.ArgumentMatchers.eq("自然语言转第三范式建表 SQL。"),
                org.mockito.ArgumentMatchers.contains("# NL to 3NF SQL"),  // 存储内容含正文
                org.mockito.ArgumentMatchers.eq(7L));
    }

    @Test
    void generate_falls_back_when_no_frontmatter() {
        // 模型输出无 frontmatter → name 降级为 intent 的 slug，仍调 submitDraft（交打分器判 REJECTED）。
        // 中文 intent 无 ascii，slug() 全部被剥为空 → fallback "skill"。
        when(chatModel.call(any(Prompt.class))).thenReturn(resp("一段没有 frontmatter 的杂乱文本"));
        Skill submitted = new Skill();
        submitted.setName("skill");
        submitted.setStatus(SkillStatus.REJECTED);
        when(skillService.submitDraft(any(), any(), any(), any())).thenReturn(submitted);

        service.generate("帮我审查代码", 7L);

        verify(skillService).submitDraft(
                org.mockito.ArgumentMatchers.eq("skill"),
                any(), any(), org.mockito.ArgumentMatchers.eq(7L));
    }

    private static ChatResponse resp(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
