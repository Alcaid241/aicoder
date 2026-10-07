package com.ai.coder.skill.controller;

import com.ai.coder.skill.entity.Skill;
import com.ai.coder.skill.entity.SkillStatus;
import com.ai.coder.skill.service.SkillGenerationService;
import com.ai.coder.skill.service.SkillService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SkillController.class)
class SkillControllerGenerateTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    SkillGenerationService generationService;

    @MockBean
    SkillService skillService; // SkillController 现有两个依赖，必须都 mock

    @Test
    void generate_returns_ok_and_delegates() throws Exception {
        Skill drafted = new Skill();
        drafted.setId(9L);
        drafted.setName("code-review-checklist");
        drafted.setStatus(SkillStatus.PENDING_APPROVAL);
        when(generationService.generate(eq("帮我审查代码"), eq(7L))).thenReturn(drafted);

        mockMvc.perform(post("/api/skill/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "7")
                        .content("{\"intent\":\"帮我审查代码\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("code-review-checklist"))
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));

        verify(generationService).generate(eq("帮我审查代码"), eq(7L));
    }
}
