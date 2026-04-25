package com.zpl.handcricket.repository;

import com.zpl.handcricket.model.Team;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class TeamRepository {

    private final JdbcTemplate jdbc;

    private final RowMapper<Team> mapper = (rs, i) -> new Team(
            rs.getInt("id"),
            rs.getString("code"),
            rs.getString("name"),
            rs.getString("landmark"),
            rs.getString("primary_color")
    );

    public List<Team> findAll() {
        return jdbc.query("select * from teams order by id", mapper);
    }

    public Optional<Team> findById(int id) {
        return jdbc.query("select * from teams where id = ?", mapper, id).stream().findFirst();
    }
}
