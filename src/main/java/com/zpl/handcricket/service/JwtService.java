package com.zpl.handcricket.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final Algorithm algorithm;
    private final long expirationMs;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expirationHours}") long expirationHours) {
        this.algorithm = Algorithm.HMAC256(secret);
        this.expirationMs = expirationHours * 3600L * 1000L;
    }

    public String issue(UUID userId, String username) {
        return JWT.create()
                .withSubject(userId.toString())
                .withClaim("username", username)
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + expirationMs))
                .sign(algorithm);
    }

    public UUID verify(String token) {
        DecodedJWT dj = JWT.require(algorithm).build().verify(token);
        return UUID.fromString(dj.getSubject());
    }
}
