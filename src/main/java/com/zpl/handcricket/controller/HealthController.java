package com.zpl.handcricket.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping()
    public ResponseEntity<?> health() {
        Map<String, String> map = new HashMap<>();
        map.put("status", "ok");
        map.put("service", "zpl-handcricket-backend");
        map.put("timestamp", "" + LocalDateTime.now());
        return ResponseEntity.ok(map);
    }
}
