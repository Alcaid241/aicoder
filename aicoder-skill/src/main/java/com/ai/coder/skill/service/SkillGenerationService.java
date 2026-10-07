package com.ai.coder.skill.service;

import com.ai.coder.skill.entity.Skill;
import com.ai.coder.skill.repository.SkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 意图→技能草稿生成：按 generate-skill 元技能模板 + 用户意图调 DeepSeek → 解析 frontmatter →
 * 经 SkillService.submitDraft（打分+闸门+版本化）入库。与 chat 自主生成同 sink，行为一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillGenerationService {

    private static final Pattern FRONTMATTER = Pattern.compile("(?s)^---\\s*\\n(.*?)\\n---\\s*\\n?(.*)$");
    private static final Pattern NAME_LINE = Pattern.compile("(?m)^name:\\s*(.+?)\\s*$");
    private static final Pattern DESC_LINE = Pattern.compile("(?m)^description:\\s*(.+?)\\s*$");

    private final ChatModel chatModel;
    private final SkillService skillService;
    private final SkillRepository skillRepository;

    public Skill generate(String intent, Long userId) {
        String instruction = skillRepository.findByName("generate-skill")
                .map(Skill::getContent)
                .orElse("起草一个标准化 SKILL.md（含 frontmatter: name + description）。");

        String output = ChatClient.builder(chatModel).build()
                .prompt()
                .system(instruction)
                .user("## 技能意图\n" + intent + "\n\n请严格按上面的指引起草一个 SKILL.md 并完整输出（含 frontmatter: name + description）。")
                .call()
                .content();
        String content = stripCodeFences(output);

        Parsed parsed = parse(content, intent);
        log.info("生成技能草稿：intent='{}' → name='{}'", intent, parsed.name);
        return skillService.submitDraft(parsed.name, parsed.description, content, userId);
    }

    private Parsed parse(String output, String intent) {
        Matcher fm = FRONTMATTER.matcher(output == null ? "" : output.stripLeading());
        if (fm.find()) {
            String block = fm.group(1);
            String name = first(NAME_LINE, block);
            String desc = first(DESC_LINE, block);
            if (name != null) {
                return new Parsed(name, desc != null ? desc : intent);
            }
        }
        // 降级：无合法 frontmatter → intent 转 slug 作 name，description 用 intent（交打分器判 REJECTED）
        return new Parsed(slug(intent), intent);
    }

    private static String first(Pattern p, String text) {
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    /** 剥离开头 ```<lang> 与结尾 ``` 围栏（模型常把 SKILL.md 包在代码块里）。 */
    private static String stripCodeFences(String text) {
        if (text == null) return null;
        String s = text.stripLeading();
        if (!s.startsWith("```")) return text;
        // 去掉首行 ```<lang>
        int firstNl = s.indexOf('\n');
        if (firstNl < 0) return text;
        String body = s.substring(firstNl + 1);
        // 去掉结尾 ```
        int fence = body.lastIndexOf("```");
        if (fence >= 0) {
            body = body.substring(0, fence);
        }
        return body.stripLeading();
    }

    private static String slug(String intent) {
        String s = intent == null ? "skill" : intent.trim().toLowerCase();
        String ascii = s.replaceAll("[^a-z0-9\\s-]", "");
        if (ascii.isBlank()) return "skill";
        String[] parts = ascii.trim().split("\\s+");
        String slug = String.join("-", parts);
        return slug.isBlank() ? "skill" : slug;
    }

    private record Parsed(String name, String description) {}
}
