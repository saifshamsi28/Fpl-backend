package com.zpl.handcricket.service;

import com.zpl.handcricket.model.User;
import com.zpl.handcricket.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtService jwt;
    private final UserRepository users;

    public User requireUser(String authorizationHeader) {
        String token = extractBearerToken(authorizationHeader);
        try {
            var userId = jwt.verify(token);
            return users.findById(userId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.UNAUTHORIZED,
                            "User not found. Please log in again"
                    ));
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired token");
        }
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing Authorization header");
        }
        if (!authorizationHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authorization header must use Bearer token");
        }
        String token = authorizationHeader.substring("Bearer ".length()).trim();
        if (token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bearer token is empty");
        }
        return token;
    }
}
