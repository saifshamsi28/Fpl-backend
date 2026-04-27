package com.zpl.handcricket.controller;

import com.zpl.handcricket.model.User;
import com.zpl.handcricket.repository.UserRepository;
import com.zpl.handcricket.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class UserController {

    private final AuthService auth;
    private final UserRepository users;

    @GetMapping
    public ResponseEntity<User> me(@RequestHeader("Authorization") String auth) {
        User user = this.auth.requireUser(auth);
        user.setRank(users.findRank(user.getId()));
        return ResponseEntity.ok(user);
    }
}
