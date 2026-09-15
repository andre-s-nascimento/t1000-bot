package net.ddns.adambravo79.tmill.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import net.ddns.adambravo79.tmill.model.Birthday;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class BirthdayRepositoryTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @InjectMocks private BirthdayRepository repository;

    // ===================== upsert =====================

    @Test
    void upsert_executaInsert() {
        repository.upsert(1L, "Fulano", 5, 10);
        verify(jdbcTemplate)
                .update(contains("INSERT INTO birthdays"), eq(1L), eq("Fulano"), eq(5), eq(10));
    }

    // ===================== findByUserId =====================

    @Test
    void findByUserId_quandoExiste_retorna() throws SQLException {
        ResultSet rs = mockRow(1L, 100L, "Fulano", 5, 10, 2026);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(100L)))
                .thenAnswer(
                        inv -> List.of(((RowMapper<Birthday>) inv.getArgument(1)).mapRow(rs, 1)));

        Optional<Birthday> b = repository.findByUserId(100L);

        assertThat(b).isPresent();
        assertThat(b.get().day()).isEqualTo(5);
        assertThat(b.get().month()).isEqualTo(10);
        assertThat(b.get().lastSentYear()).isEqualTo(2026);
        assertThat(b.get().userName()).isEqualTo("Fulano");
    }

    @Test
    void findByUserId_quandoNaoExiste_retornaEmpty() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(1L))).thenReturn(List.of());
        assertThat(repository.findByUserId(1L)).isEmpty();
    }

    // ===================== findByDayAndMonth =====================

    @Test
    void findByDayAndMonth_retornaLista() throws SQLException {
        ResultSet rs = mockRow(1L, 100L, "Fulano", 5, 10, null);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(5), eq(10)))
                .thenAnswer(
                        inv -> List.of(((RowMapper<Birthday>) inv.getArgument(1)).mapRow(rs, 1)));

        List<Birthday> result = repository.findByDayAndMonth(5, 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).userName()).isEqualTo("Fulano");
        assertThat(result.get(0).lastSentYear()).isNull();
    }

    @Test
    void findByDayAndMonth_semResultado_retornaListaVazia() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(5), eq(10)))
                .thenReturn(List.of());
        assertThat(repository.findByDayAndMonth(5, 10)).isEmpty();
    }

    // ===================== findAll =====================

    @Test
    void findAll_retornaLista() throws SQLException {
        ResultSet rs1 = mockRow(1L, 100L, "Fulano", 5, 10, null);
        ResultSet rs2 = mockRow(2L, 200L, "Beltrano", 20, 12, 2026);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class)))
                .thenAnswer(
                        inv -> {
                            RowMapper<Birthday> mapper = inv.getArgument(1);
                            return List.of(mapper.mapRow(rs1, 1), mapper.mapRow(rs2, 2));
                        });

        List<Birthday> result = repository.findAll();

        assertThat(result).hasSize(2);
    }

    // ===================== markSent =====================

    @Test
    void markSent_executaUpdate() {
        repository.markSent(100L, 2026);
        verify(jdbcTemplate).update(contains("last_sent_year"), eq(2026), eq(100L));
    }

    // ===================== delete =====================

    @Test
    void deleteByUserId_executaDelete() {
        when(jdbcTemplate.update("DELETE FROM birthdays WHERE user_id = ?", 1L)).thenReturn(1);
        assertThat(repository.deleteByUserId(1L)).isEqualTo(1);
    }

    @Test
    void deleteByUserId_quandoNaoExiste_retornaZero() {
        when(jdbcTemplate.update("DELETE FROM birthdays WHERE user_id = ?", 999L)).thenReturn(0);
        assertThat(repository.deleteByUserId(999L)).isZero();
    }

    @Test
    void deleteAll_executaDelete() {
        when(jdbcTemplate.update("DELETE FROM birthdays")).thenReturn(5);
        assertThat(repository.deleteAll()).isEqualTo(5);
    }

    // ===================== count =====================

    @Test
    void count_retornaNumero() {
        when(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM birthdays", Integer.class))
                .thenReturn(7);
        assertThat(repository.count()).isEqualTo(7);
    }

    @Test
    void count_quandoNulo_retornaZero() {
        when(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM birthdays", Integer.class))
                .thenReturn(null);
        assertThat(repository.count()).isZero();
    }

    // ===================== HELPERS =====================

    private ResultSet mockRow(
            long id, long userId, String userName, int day, int month, Integer lastSent)
            throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(id);
        when(rs.getLong("user_id")).thenReturn(userId);
        when(rs.getString("user_name")).thenReturn(userName);
        when(rs.getInt("day")).thenReturn(day);
        when(rs.getInt("month")).thenReturn(month);
        if (lastSent != null) {
            when(rs.getInt("last_sent_year")).thenReturn(lastSent);
            when(rs.wasNull()).thenReturn(false);
        } else {
            when(rs.getInt("last_sent_year")).thenReturn(0);
            when(rs.wasNull()).thenReturn(true);
        }
        return rs;
    }
}
