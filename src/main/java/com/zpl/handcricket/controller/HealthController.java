package com.zpl.handcricket.controller;

import com.zpl.handcricket.model.Room;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping()
    public ResponseEntity<?> create(@RequestHeader("Authorization") String auth) {
        Map<String,String> map = new HashMap<>();
        map.put("status","ok");
        map.put("timestamp",""+ LocalDateTime.now());
        return ResponseEntity.ok(map);
    }
}
