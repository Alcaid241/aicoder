package com.ai.coder.skill.controller;

import com.ai.coder.skill.dto.GenerateSkillRequest;
import com.ai.coder.skill.dto.SkillDTO;
import com.ai.coder.skill.dto.SkillDraftRequest;
import com.ai.coder.skill.entity.Skill;
import com.ai.coder.skill.service.SkillGenerationService;
import com.ai.coder.skill.service.SkillService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/skill")
@RequiredArgsConstructor
public class SkillController {

    private final SkillService skillService;
    private final SkillGenerationService generationService;

    @GetMapping
    public ResponseEntity<List<Skill>> list() {
        return ResponseEntity.ok(skillService.listAll());
    }

    @GetMapping("/pending")
    public ResponseEntity<List<Skill>> listPending() {
        return ResponseEntity.ok(skillService.listPending());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Skill> getById(@PathVariable Long id) {
        return ResponseEntity.ok(skillService.getById(id));
    }

    @GetMapping("/name/{name}")
    public ResponseEntity<Skill> getByName(@PathVariable String name) {
        return ResponseEntity.ok(skillService.getByName(name));
    }

    @PostMapping
    public ResponseEntity<Skill> create(@RequestBody SkillDTO dto,
                                        @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ResponseEntity.ok(skillService.create(dto, userId));
    }

    @PostMapping("/draft")
    public ResponseEntity<Skill> submitDraft(@RequestBody SkillDraftRequest req,
                                             @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ResponseEntity.ok(skillService.submitDraft(
                req.getName(), req.getDescription(), req.getContent(), userId));
    }

    @PostMapping("/generate")
    public ResponseEntity<Skill> generate(@RequestBody GenerateSkillRequest req,
                                          @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ResponseEntity.ok(generationService.generate(req.getIntent(), userId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Skill> update(@PathVariable Long id, @RequestBody SkillDTO dto) {
        return ResponseEntity.ok(skillService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        skillService.delete(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/submit")
    public ResponseEntity<Skill> submitForApproval(@PathVariable Long id) {
        return ResponseEntity.ok(skillService.submitForApproval(id));
    }

    @PutMapping("/{id}/approve")
    public ResponseEntity<Skill> approve(@PathVariable Long id,
                                         @RequestHeader(value = "X-User-Id", required = false) Long approverId) {
        return ResponseEntity.ok(skillService.approve(id, approverId));
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<Skill> reject(@PathVariable Long id) {
        return ResponseEntity.ok(skillService.reject(id));
    }

    @PutMapping("/{id}/archive")
    public ResponseEntity<Skill> archive(@PathVariable Long id) {
        return ResponseEntity.ok(skillService.archive(id));
    }
}
