package com.ai.coder.skill.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillQualityScorerTest {

    private final SkillQualityScorer scorer = new SkillQualityScorer();

    private static final String GOOD_CONTENT = """
            ---
            name: code-review
            description: 对代码变更进行清单式审查并给出修改建议。
            ---
            # Code Review
            当用户提交代码变更时，按以下清单逐项审查：可读性、命名、错误处理、测试覆盖、性能与安全。
            发现问题后给出具体的修改建议与示例代码，避免空泛评价。
            """;

    @Test
    void score_full_for_well_formed_draft() {
        ScoreResult r = scorer.score("code-review",
                "对代码变更进行清单式审查并给出修改建议。", GOOD_CONTENT);
        assertEquals(100, r.score(), "规范草稿应满分：" + r.rationale());
    }

    @Test
    void score_deducts_for_non_kebab_name() {
        ScoreResult r = scorer.score("Code_Review",
                "对代码变更进行清单式审查并给出修改建议。", GOOD_CONTENT);
        assertEquals(75, r.score(), "非 kebab 名扣 25：" + r.rationale());
        assertTrue(r.rationale().contains("kebab"));
    }

    @Test
    void score_deducts_for_missing_or_invalid_frontmatter() {
        // 无 frontmatter：扣 25（正文仍足长，所以只扣这一项）
        String noFm = "# Code Review\n当用户提交代码变更时，按清单逐项审查可读性、命名、错误处理、测试覆盖与安全。\n";
        ScoreResult r = scorer.score("code-review",
                "对代码变更进行清单式审查并给出修改建议。", noFm);
        assertEquals(75, r.score(), "缺合法 frontmatter 扣 25：" + r.rationale());
    }

    @Test
    void score_deducts_for_short_body() {
        // 合法 frontmatter 但正文过短：扣 25
        String shortBody = "---\nname: code-review\ndescription: 审查代码。\n---\n短";
        ScoreResult r = scorer.score("code-review",
                "对代码变更进行清单式审查并给出修改建议。", shortBody);
        assertEquals(75, r.score(), "正文过短扣 25：" + r.rationale());
    }

    @Test
    void score_low_for_malformed_draft() {
        // 名字非 kebab + 描述过短 + 无 frontmatter + 正文过短 → 0
        ScoreResult r = scorer.score("Bad Name", "短", "x");
        assertEquals(0, r.score(), "四项全失应 0 分：" + r.rationale());
    }
}
