/* (c) 2026 | 15/09/2026 */
package net.ddns.adambravo79.tmill.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.model.Birthday;

@Repository
@RequiredArgsConstructor
@Slf4j
public class BirthdayRepository {

    private final JdbcTemplate jdbcTemplate;

    /** Insere ou atualiza o aniversário de um usuário (upsert por user_id). */
    public void upsert(long userId, String userName, int day, int month) {
        String sql =
                """
                INSERT INTO birthdays (user_id, user_name, day, month, updated_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT(user_id) DO UPDATE SET
                    user_name = excluded.user_name,
                    day = excluded.day,
                    month = excluded.month,
                    updated_at = CURRENT_TIMESTAMP
                """;
        jdbcTemplate.update(sql, userId, userName, day, month);
        log.info("🎂 Aniversário registrado: userId={} {} {}/{}", userId, userName, day, month);
    }

    /** Busca o aniversário de um usuário, se existir. */
    public Optional<Birthday> findByUserId(long userId) {
        String sql =
                "SELECT id, user_id, user_name, day, month, last_sent_year FROM birthdays WHERE"
                        + " user_id = ?";
        List<Birthday> result = jdbcTemplate.query(sql, (rs, rowNum) -> mapRow(rs), userId);
        return result.stream().findFirst();
    }

    /** Retorna todos os aniversariantes de um dia/mês. */
    public List<Birthday> findByDayAndMonth(int day, int month) {
        String sql =
                "SELECT id, user_id, user_name, day, month, last_sent_year FROM birthdays WHERE day"
                        + " = ? AND month = ?";
        return jdbcTemplate.query(sql, (rs, rowNum) -> mapRow(rs), day, month);
    }

    /** Lista todos os aniversários cadastrados. */
    public List<Birthday> findAll() {
        String sql =
                "SELECT id, user_id, user_name, day, month, last_sent_year FROM birthdays ORDER BY"
                        + " month, day, user_name";
        return jdbcTemplate.query(sql, (rs, rowNum) -> mapRow(rs));
    }

    /** Marca que o usuário já recebeu os parabéns em determinado ano. */
    public void markSent(long userId, int year) {
        String sql = "UPDATE birthdays SET last_sent_year = ? WHERE user_id = ?";
        jdbcTemplate.update(sql, year, userId);
        log.info("🎂 Marcado como enviado: userId={} year={}", userId, year);
    }

    /** Remove o aniversário de um usuário. */
    public int deleteByUserId(long userId) {
        return jdbcTemplate.update("DELETE FROM birthdays WHERE user_id = ?", userId);
    }

    /** Remove todos os registros (usado no admin). */
    public int deleteAll() {
        return jdbcTemplate.update("DELETE FROM birthdays");
    }

    public int count() {
        Integer n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM birthdays", Integer.class);
        return n != null ? n : 0;
    }

    private Birthday mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        int lastSent = rs.getInt("last_sent_year");
        return new Birthday(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getString("user_name"),
                rs.getInt("day"),
                rs.getInt("month"),
                rs.wasNull() ? null : lastSent);
    }
}
