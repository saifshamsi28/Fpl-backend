package com.zpl.handcricket.controller;

import com.zpl.handcricket.model.Room;
import com.zpl.handcricket.repository.RoomRepository;
import com.zpl.handcricket.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Random;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomRepository rooms;
    private final AuthService auth;
    private final Random random = new Random();

    @PostMapping("/create")
    public ResponseEntity<?> create(@RequestHeader("Authorization") String auth) {
        var user = this.auth.requireUser(auth);
        Room r = rooms.create(user.getId(), generateUniqueRoomCode());
        return ResponseEntity.ok(r);
    }

    private String generateUniqueRoomCode() {
        for (int i = 0; i < 100; i++) {
            String code = String.format("%05d", random.nextInt(100000));
            if (!rooms.codeExists(code)) {
                return code;
            }
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Unable to allocate room code. Please retry");
    }

    public record JoinRequest(String code) {}

    @PostMapping("/join")
    public ResponseEntity<?> join(@RequestHeader("Authorization") String auth, @RequestBody JoinRequest req) {
        var user = this.auth.requireUser(auth);
        String code = req == null ? null : req.code();
        if (code == null || !code.matches("\\d{5}")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid room code"));
        }

        var userId = user.getId();
        var opt = rooms.findByCode(code);
        if (opt.isEmpty()) return ResponseEntity.status(404).body(Map.of("error", "Room not found"));
        Room r = opt.get();
        if (!"WAITING".equals(r.getStatus())) return ResponseEntity.badRequest().body(Map.of("error", "Room not available"));
        if (r.getHostUserId().equals(userId)) return ResponseEntity.badRequest().body(Map.of("error", "Cannot join your own room"));
        if (!rooms.setGuest(r.getId(), userId)) {
            return ResponseEntity.status(409).body(Map.of("error", "Room is no longer available"));
        }
        return ResponseEntity.ok(rooms.findById(r.getId()).orElseThrow());
    }

    @GetMapping("/{code}")
    public ResponseEntity<?> byCode(@PathVariable String code) {
        return rooms.findByCode(code)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(404).build());
    }
}
