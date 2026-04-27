package com.zpl.handcricket.controller;

import com.zpl.handcricket.model.User;
import com.zpl.handcricket.repository.UserRepository;
import com.zpl.handcricket.service.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository users;
    private final JwtService jwt;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,24}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);

    public record SignupRequest(
            String username,
            String password,
            String fullName,
            String email,
            String city,
            String favoritePlayer
    ) {}

    public record LoginRequest(String username, String password) {}

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody SignupRequest req) {
        String username = normalize(req.username());
        String password = req.password() == null ? "" : req.password();
        String fullName = normalize(req.fullName());
        String email = normalize(req.email());
        String city = normalize(req.city());
        String favoritePlayer = normalize(req.favoritePlayer());

        if (username == null || !USERNAME_PATTERN.matcher(username).matches()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username must be 3-24 characters and can contain letters, numbers, and underscore"));
        }
        if (password.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "Password must be at least 6 characters"));
        }
        if (fullName == null || fullName.length() < 2) {
            return ResponseEntity.badRequest().body(Map.of("error", "Full name must be at least 2 characters"));
        }
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Enter a valid email address"));
        }
        if (city == null || city.length() < 2) {
            return ResponseEntity.badRequest().body(Map.of("error", "City must be at least 2 characters"));
        }
        if (users.findByUsername(username).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Username already exists"));
        }
        User u;
        try {
            u = users.save(
                username,
                fullName,
                email.toLowerCase(Locale.ROOT),
                city,
                favoritePlayer,
                encoder.encode(password)
            );
        } catch (DataIntegrityViolationException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "That email is already registered"));
        }
        u.setRank(users.findRank(u.getId()));
        String token = jwt.issue(u.getId(), u.getUsername());
        return ResponseEntity.ok(Map.of(
                "token", token,
                "user", u
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        String username = normalize(req.username());
        String password = req.password() == null ? "" : req.password();
        if (username == null || username.isBlank() || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username and password are required"));
        }
        var opt = users.findByUsername(username);
        if (opt.isEmpty() || !encoder.matches(password, opt.get().getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid username or password"));
        }
        User u = opt.get();
        u.setRank(users.findRank(u.getId()));
        String token = jwt.issue(u.getId(), u.getUsername());
        return ResponseEntity.ok(Map.of("token", token, "user", u));
    }

    private String normalize(String value) {
        if (value == null) return null;
        String v = value.trim();
        return v.isEmpty() ? null : v;
    }
}
