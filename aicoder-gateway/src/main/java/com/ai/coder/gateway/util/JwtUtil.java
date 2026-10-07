package com.ai.coder.gateway.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    public DecodedJWT verify(String token) {
        return JWT.require(Algorithm.HMAC256(secret))
                .build()
                .verify(token);
    }

    public String getUserId(String token) {
        DecodedJWT jwt = verify(token);
        return String.valueOf(jwt.getClaim("userId").asLong());
    }

    public String getUsername(String token) {
        DecodedJWT jwt = verify(token);
        return jwt.getClaim("username").asString();
    }
}
