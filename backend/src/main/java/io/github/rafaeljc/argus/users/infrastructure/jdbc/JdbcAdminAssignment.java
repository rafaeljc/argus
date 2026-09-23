package io.github.rafaeljc.argus.users.infrastructure.jdbc;

import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.users.application.port.AdminAssignment;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAdminAssignment implements AdminAssignment {

    // Grants the target and demotes every other admin in one statement, which is what makes this
    // both idempotent and safe to run from two instances at once: a re-run matches no rows, and a
    // concurrent run blocks on the row locks, then re-evaluates the predicate and matches nothing.
    // A read-then-write would have neither property. RETURNING id reports every user whose flag
    // actually flipped, so callers can react to both the grant and any demotion.
    private static final String MAKE_SOLE_ADMIN_SQL =
            """
            UPDATE users
               SET is_admin = (id = CAST(:adminId AS uuid)),
                   updated_at = :now
             WHERE is_admin <> (id = CAST(:adminId AS uuid))
             RETURNING id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcAdminAssignment(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<UserId> makeSoleAdmin(UserId adminId, Instant now) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("adminId", adminId.value())
                .addValue("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC));
        return jdbc.query(MAKE_SOLE_ADMIN_SQL, params,
                (rs, rowNum) -> new UserId(rs.getObject("id", UUID.class)));
    }
}
