/* (c) 2026 | 15/09/2026 */
package net.ddns.adambravo79.tmill.model;

/**
 * Representa o aniversário de um usuário.
 *
 * @param id identificador único no banco
 * @param userId identificador Telegram do usuário
 * @param userName nome exibido
 * @param day dia do mês (1-31)
 * @param month mês (1-12)
 * @param lastSentYear último ano em que recebeu parabéns (evita duplicação)
 */
public record Birthday(
        Long id, long userId, String userName, int day, int month, Integer lastSentYear) {}
