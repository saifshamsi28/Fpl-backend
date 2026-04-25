package com.zpl.handcricket.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zpl.handcricket.model.Room;
import com.zpl.handcricket.repository.RoomRepository;
import com.zpl.handcricket.repository.UserRepository;
import com.zpl.handcricket.service.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Inbound message types:
 *   { "type": "auth",           "data": { "token": "<jwt>" } }
 *   { "type": "queue_ranked" }
 *   { "type": "cancel_queue" }
 *   { "type": "host_friendly",  "data": { "roomId": "..." } }
 *   { "type": "join_friendly",  "data": { "roomId": "..." } }
 *   { "type": "pick",           "data": { "pick": 1..6 } }
 *   { "type": "leave" }
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class GameWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper mapper = new ObjectMapper();
    private final JwtService jwt;
    private final UserRepository users;
    private final RoomRepository rooms;
    private final Matchmaker matchmaker;
    private final GameEngine engine;

    private final Map<String, GameSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession ws) {
        sessions.put(ws.getId(), new GameSession(ws));
    }

    @Override
    protected void handleTextMessage(WebSocketSession ws, TextMessage message) throws Exception {
        JsonNode root = mapper.readTree(message.getPayload());
        String type = root.path("type").asText();
        JsonNode data = root.path("data");
        GameSession s = sessions.get(ws.getId());
        if (s == null) return;

        switch (type) {
            case "auth" -> {
                String token = data.path("token").asText();
                try {
                    if (token == null || token.isBlank()) {
                        sendEnv(ws, "auth_err", Map.of("error", "Missing token"));
                        ws.close(CloseStatus.POLICY_VIOLATION);
                        return;
                    }
                    UUID uid = jwt.verify(token);
                    var user = users.findById(uid).orElse(null);
                    if (user == null) {
                        sendEnv(ws, "auth_err", Map.of("error", "User not found. Please log in again"));
                        ws.close(CloseStatus.POLICY_VIOLATION);
                        return;
                    }
                    s.setUserId(user.getId());
                    s.setUsername(user.getUsername());
                    s.setTeamId(user.getTeamId());
                    sendEnv(ws, "auth_ok", Map.of("userId", s.getUserId().toString(),
                            "username", s.getUsername()));
                } catch (Exception e) {
                    sendEnv(ws, "auth_err", Map.of("error", "Invalid or expired token"));
                    ws.close(CloseStatus.POLICY_VIOLATION);
                }
            }
            case "queue_ranked" -> {
                if (s.getUserId() == null) return;
                sendEnv(ws, "queued", Map.of());
                matchmaker.enqueueRanked(s);
            }
            case "cancel_queue" -> matchmaker.cancel(s);
            case "host_friendly" -> {
                if (s.getUserId() == null) return;
                UUID roomId = UUID.fromString(data.path("roomId").asText());
                Room r = rooms.findById(roomId).orElse(null);
                if (r == null) {
                    sendEnv(ws, "error", Map.of("message", "Room not found"));
                    return;
                }
                if (!"WAITING".equals(r.getStatus())) {
                    sendEnv(ws, "error", Map.of("message", "Room is not joinable"));
                    return;
                }
                if (!s.getUserId().equals(r.getHostUserId())) {
                    sendEnv(ws, "error", Map.of("message", "Only room host can start hosting"));
                    return;
                }
                matchmaker.hostFriendly(roomId, s);
                sendEnv(ws, "hosting", Map.of("roomId", roomId.toString()));
            }
            case "join_friendly" -> {
                if (s.getUserId() == null) return;
                UUID roomId = UUID.fromString(data.path("roomId").asText());
                Room r = rooms.findById(roomId).orElse(null);
                if (r == null) { sendEnv(ws, "error", Map.of("message", "Room not found")); return; }
                if (!"READY".equals(r.getStatus())) {
                    sendEnv(ws, "error", Map.of("message", "Room is not ready yet"));
                    return;
                }
                if (r.getGuestUserId() == null || !s.getUserId().equals(r.getGuestUserId())) {
                    sendEnv(ws, "error", Map.of("message", "You are not the invited guest for this room"));
                    return;
                }
                if (!matchmaker.hasFriendlyHost(roomId)) {
                    sendEnv(ws, "error", Map.of("message", "Host is not connected yet"));
                    return;
                }
                if (!matchmaker.joinFriendly(roomId, s)) {
                    sendEnv(ws, "error", Map.of("message", "Unable to start match. Please retry"));
                }
            }
            case "pick" -> engine.onPick(s, data.path("pick").asInt(0));
            case "leave" -> {
                matchmaker.cancel(s);
                engine.handleDisconnect(s);
            }
            default -> log.debug("unknown type {}", type);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession ws, CloseStatus status) {
        GameSession s = sessions.remove(ws.getId());
        if (s != null) {
            matchmaker.cancel(s);
            engine.handleDisconnect(s);
        }
    }

    private void sendEnv(WebSocketSession ws, String type, Object data) throws Exception {
        String payload = mapper.writeValueAsString(Map.of("type", type, "data", data));
        if (ws.isOpen()) ws.sendMessage(new TextMessage(payload));
    }
}
