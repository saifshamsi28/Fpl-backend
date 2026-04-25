package com.zpl.handcricket.controller;

import com.zpl.handcricket.model.User;
import com.zpl.handcricket.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class UserController {

    private final AuthService auth;

    @GetMapping
    public ResponseEntity<User> me(@RequestHeader("Authorization") String auth) {
        return ResponseEntity.ok(this.auth.requireUser(auth));
    }
}
