package com.zpl.handcricket.repository;

import com.zpl.handcricket.model.Room;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class RoomRepository {

    private final JdbcTemplate jdbc;

    private final RowMapper<Room> mapper = (rs, i) -> Room.builder()
            .id(rs.getObject("id", UUID.class))
            .code(rs.getString("code"))
            .hostUserId(rs.getObject("host_user_id", UUID.class))
            .guestUserId((UUID) rs.getObject("guest_user_id"))
            .status(rs.getString("status"))
            .createdAt(toOdt(rs.getTimestamp("created_at")))
            .build();

    private OffsetDateTime toOdt(Timestamp ts) {
        return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
    }

    public Room create(UUID hostId, String code) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into rooms (id, code, host_user_id) values (?, ?, ?)", id, code, hostId);
        return findById(id).orElseThrow();
    }

    public Optional<Room> findById(UUID id) {
        return jdbc.query("select * from rooms where id = ?", mapper, id).stream().findFirst();
    }

    public Optional<Room> findByCode(String code) {
        return jdbc.query("select * from rooms where code = ?", mapper, code).stream().findFirst();
    }

    public boolean setGuest(UUID roomId, UUID guestId) {
        int updated = jdbc.update("""
                update rooms
                   set guest_user_id = ?,
                       status = 'READY'
                 where id = ?
                   and status = 'WAITING'
                   and guest_user_id is null
                """, guestId, roomId);
        return updated > 0;
    }

    public void close(UUID roomId) {
        jdbc.update("update rooms set status = 'CLOSED' where id = ?", roomId);
    }

    public boolean codeExists(String code) {
        Integer c = jdbc.queryForObject("select count(*) from rooms where code = ?", Integer.class, code);
        return c != null && c > 0;
    }
}
