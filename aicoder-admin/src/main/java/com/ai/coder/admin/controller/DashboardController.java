package com.ai.coder.admin.controller;

import com.ai.coder.admin.dto.DashboardStatsResponse;
import com.ai.coder.admin.service.DashboardService;
import com.ai.coder.admin.util.JwtUtil;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final JwtUtil jwtUtil;

    @GetMapping("/stats")
    public ResponseEntity<DashboardStatsResponse> getStats(
            @RequestHeader("Authorization") String authorization) {
        String token = authorization.replace("Bearer ", "");
        DecodedJWT jwt = jwtUtil.parseToken(token);
        Long userId = jwt.getClaim("userId").asLong();
        return ResponseEntity.ok(dashboardService.getStats(userId));
    }
}
