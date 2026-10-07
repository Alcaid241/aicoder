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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SkillController.class)
class SkillControllerDraftTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    SkillService skillService;

    @MockBean
    SkillGenerationService generationService; // SkillController 现有两个依赖，必须都 mock

    @Test
    void submitDraft_returns_ok_delegates_with_user_header() throws Exception {
        Skill drafted = new Skill();
        drafted.setId(1L);
        drafted.setName("code-review");
        drafted.setStatus(SkillStatus.PENDING_APPROVAL);
        when(skillService.submitDraft(eq("code-review"), anyString(), anyString(), eq(7L)))
                .thenReturn(drafted);

        mockMvc.perform(post("/api/skill/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "7")
                        .content("{\"name\":\"code-review\",\"description\":\"审查代码\",\"content\":\"x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("code-review"))
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));

        verify(skillService).submitDraft(eq("code-review"), eq("审查代码"), eq("x"), eq(7L));
    }
}
