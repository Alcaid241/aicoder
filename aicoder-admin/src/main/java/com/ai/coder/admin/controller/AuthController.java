package com.ai.coder.admin.controller;

import com.ai.coder.admin.dto.LoginRequest;
import com.ai.coder.admin.dto.LoginResponse;
import com.ai.coder.admin.dto.RegisterRequest;
import com.ai.coder.admin.dto.UpdateUserRequest;
import com.ai.coder.admin.dto.UserInfoResponse;
import com.ai.coder.admin.service.AuthService;
import com.ai.coder.admin.util.JwtUtil;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtUtil jwtUtil;

    @PostMapping("/register")
    public ResponseEntity<Void> register(@RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/user/info")
    public ResponseEntity<UserInfoResponse> getUserInfo(@RequestHeader("Authorization") String authorization) {
        String token = authorization.replace("Bearer ", "");
        DecodedJWT jwt = jwtUtil.parseToken(token);
        Long userId = jwt.getClaim("userId").asLong();
        UserInfoResponse userInfo = authService.getUserInfo(userId);
        return ResponseEntity.ok(userInfo);
    }

    @PutMapping("/user/info")
    public ResponseEntity<UserInfoResponse> updateUserInfo(
            @RequestHeader("Authorization") String authorization,
            @RequestBody UpdateUserRequest request) {
        String token = authorization.replace("Bearer ", "");
        DecodedJWT jwt = jwtUtil.parseToken(token);
        Long userId = jwt.getClaim("userId").asLong();
        authService.updateUserInfo(userId, request.getNickname(), request.getEmail(), request.getAvatar());
        UserInfoResponse userInfo = authService.getUserInfo(userId);
        return ResponseEntity.ok(userInfo);
    }
}
