package com.zpl.handcricket.controller;

import com.zpl.handcricket.model.Team;
import com.zpl.handcricket.repository.TeamRepository;
import com.zpl.handcricket.repository.UserRepository;
import com.zpl.handcricket.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamRepository teams;
    private final UserRepository users;
    private final AuthService auth;

    @GetMapping
    public List<Team> list() {
        return teams.findAll();
    }

    public record PickRequest(Integer teamId) {}

    @PostMapping("/pick")
    public ResponseEntity<?> pick(@RequestHeader("Authorization") String authorization,
                                  @RequestBody PickRequest req) {
        var user = auth.requireUser(authorization);
        if (req.teamId() == null || teams.findById(req.teamId()).isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid teamId"));
        }
        users.setTeam(user.getId(), req.teamId());
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
