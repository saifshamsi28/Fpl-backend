package com.zpl.handcricket.controller;

import com.zpl.handcricket.model.User;
import com.zpl.handcricket.repository.UserRepository;
import com.zpl.handcricket.service.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository users;
    private final JwtService jwt;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public record AuthRequest(String username, String password) {}

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody AuthRequest req) {
        if (req.username() == null || req.username().isBlank() || req.password() == null || req.password().length() < 4) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid username/password"));
        }
        if (users.findByUsername(req.username()).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username already taken"));
        }
        User u = users.save(req.username(), encoder.encode(req.password()));
        String token = jwt.issue(u.getId(), u.getUsername());
        return ResponseEntity.ok(Map.of(
                "token", token,
                "user", u
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest req) {
        var opt = users.findByUsername(req.username());
        if (opt.isEmpty() || !encoder.matches(req.password(), opt.get().getPasswordHash())) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }
        User u = opt.get();
        String token = jwt.issue(u.getId(), u.getUsername());
        return ResponseEntity.ok(Map.of("token", token, "user", u));
    }
}
