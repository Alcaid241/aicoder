package com.ai.coder.skill.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 启发式技能草稿质量打分（0–100），纯函数无 IO。四项各 25 分：
 * kebab 名格式 / 描述长度 10–200 / frontmatter 合法（成对 --- 且含 name+description）/ 正文（剥离 frontmatter 后）≥50 字。
 * 用于 submit_skill_draft 写回时的自动闸门：低于阈值的草稿直接 REJECTED，不进审批队列。
 */
@Component
public class SkillQualityScorer {

    private static final Pattern KEBAB_NAME = Pattern.compile("^[a-z][a-z0-9]*(?:-[a-z0-9]+)*$");
    private static final int MIN_DESC = 10;
    private static final int MAX_DESC = 200;
    private static final int MIN_BODY_LENGTH = 50;

    public ScoreResult score(String name, String description, String content) {
        List<String> notes = new ArrayList<>();
        int score = 0;

        if (name != null && KEBAB_NAME.matcher(name).matches()) {
            score += 25;
        } else {
            notes.add("名字非 kebab-case");
        }

        int descLen = description == null ? 0 : description.trim().length();
        if (descLen >= MIN_DESC && descLen <= MAX_DESC) {
            score += 25;
        } else {
            notes.add("描述长度应在 " + MIN_DESC + "-" + MAX_DESC + " 字");
        }

        if (hasValidFrontmatter(content)) {
            score += 25;
        } else {
            notes.add("缺少合法 frontmatter（成对 --- 且含 name/description）");
        }

        if (bodyLength(content) >= MIN_BODY_LENGTH) {
            score += 25;
        } else {
            notes.add("正文过短（< " + MIN_BODY_LENGTH + " 字）");
        }

        String rationale = notes.isEmpty() ? "全部通过" : String.join("；", notes);
        return new ScoreResult(score, rationale);
    }

    /** 剥离开头 frontmatter 后的正文长度；无合法 frontmatter 则按整段计。 */
    private int bodyLength(String content) {
        if (content == null) return 0;
        String stripped = content.stripLeading();
        if (!stripped.startsWith("---")) return content.trim().length();
        int end = stripped.indexOf("\n---", 3);
        if (end < 0) return content.trim().length();
        return stripped.substring(end + 4).trim().length();
    }

    private boolean hasValidFrontmatter(String content) {
        if (content == null) return false;
        String stripped = content.stripLeading();
        if (!stripped.startsWith("---")) return false;
        int end = stripped.indexOf("\n---", 3);
        if (end < 0) return false;
        String fm = stripped.substring(3, end);
        return fm.contains("name:") && fm.contains("description:");
    }
}
