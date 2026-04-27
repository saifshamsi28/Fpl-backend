package com.zpl.handcricket.controller;

import com.zpl.handcricket.model.User;
import com.zpl.handcricket.repository.UserRepository;
import com.zpl.handcricket.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class UserController {

    private final AuthService auth;
    private final UserRepository users;
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
            Pattern.CASE_INSENSITIVE
    );

    public record UpdateProfileRequest(
            String fullName,
            String email,
            String city,
            String favoritePlayer
    ) {}

    @GetMapping
    public ResponseEntity<User> me(@RequestHeader("Authorization") String auth) {
        User user = this.auth.requireUser(auth);
        user.setRank(users.findRank(user.getId()));
        return ResponseEntity.ok(user);
    }

    @PutMapping
    public ResponseEntity<?> updateMe(@RequestHeader("Authorization") String authorization,
                                      @RequestBody UpdateProfileRequest req) {
        User me = auth.requireUser(authorization);

        String fullName = normalize(req.fullName());
        String email = normalize(req.email());
        String city = normalize(req.city());
        String favoritePlayer = normalize(req.favoritePlayer());

        if (fullName == null || fullName.length() < 2) {
            return ResponseEntity.badRequest().body(Map.of("error", "Full name must be at least 2 characters"));
        }
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Enter a valid email address"));
        }
        if (city == null || city.length() < 2) {
            return ResponseEntity.badRequest().body(Map.of("error", "City must be at least 2 characters"));
        }

        String normalizedEmail = email.toLowerCase(Locale.ROOT);
        var existing = users.findByEmail(normalizedEmail);
        if (existing.isPresent() && !existing.get().getId().equals(me.getId())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "That email is already registered"));
        }

        try {
            users.updateProfile(me.getId(), fullName, normalizedEmail, city, favoritePlayer);
        } catch (DataIntegrityViolationException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "That email is already registered"));
        }

        User updated = users.findById(me.getId()).orElseThrow();
        updated.setRank(users.findRank(updated.getId()));
        return ResponseEntity.ok(updated);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
