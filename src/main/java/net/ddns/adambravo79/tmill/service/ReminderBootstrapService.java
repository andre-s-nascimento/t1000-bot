/* (c) 2026 | 07/11/2026 */
package net.ddns.adambravo79.tmill.service;

import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.dto.ReminderJson;
import net.ddns.adambravo79.tmill.model.Reminder;
import net.ddns.adambravo79.tmill.repository.ReminderRepository;
import net.ddns.adambravo79.tmill.service.config.JsonConfigLoader;

/**
 * Bootstrap: importa {@code config/reminders.json} para o banco no boot, se:
 *
 * <ol>
 *   <li>A flag {@code reminders.bootstrap.from-json.enabled=true} estiver ligada.
 *   <li>A tabela {@code reminders} estiver vazia.
 *   <li>O arquivo {@code reminders.json} existir (em qualquer local do {@link JsonConfigLoader}).
 * </ol>
 *
 * <p><b>Idempotente:</b> em boots subsequentes, a tabela já tem reminders → não faz nada.
 * Para forçar reimport, use o endpoint {@code POST /admin/reminders/import} com
 * {@code replace=true}.
 *
 * <p><b>Não substitui</b> um restore manual: se a tabela já tem dados, o arquivo é
 * ignorado. Isso evita que um deploy acidental sobrescreva o banco com um JSON
 * desatualizado.
 *
 * <h2>Resolução de placeholders {@code ${VAR}}</h2>
 *
 * <p>O JSON pode conter placeholders no formato {@code ${NOME_DA_ENV}} (ex.:
 * {@code "${QUATRO_HORAS_QUARTA_FILE}"}) em qualquer campo string. O bootstrap
 * resolve os placeholders via {@link Environment#getProperty(String)} antes de
 * importar. Se a env var não existir, o placeholder é substituído por string vazia
 * (e o operador pode editar o reminder depois via admin).
 *
 * <p>Isso permite que o {@code reminders.json} versionado no repositório tenha
 * {@code "mediaUrl": "${QUATRO_HORAS_QUARTA_FILE}"} sem commitar a URL real.
 *
 * <h2>Ordem de execução</h2>
 *
 * <p>{@link ApplicationRunner} roda <b>depois</b> de todos os {@code @PostConstruct},
 * portanto depois do {@link ReminderService#init()} e do {@link ReminderScheduler#init()}.
 * Como o {@link ReminderService#importAll(List, boolean)} notifica o listener, o scheduler
 * já registrado reagenda os reminders importados imediatamente — sem esperar o próximo
 * {@code reload()}.
 *
 * <h2>Falha não bloqueia o boot</h2>
 *
 * <p>Se o JSON estiver ausente, malformado ou inválido, apenas loga um warning. O bot sobe
 * com a tabela vazia (ou com o que já tinha), e o operador pode usar o endpoint de import
 * manual depois.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderBootstrapService implements ApplicationRunner {

    private static final String CONFIG_FILE_NAME = "reminders.json";

    /** Regex para placeholders no formato ${NOME_DA_ENV}. */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    private final ReminderRepository repository;
    private final ReminderService reminderService;
    private final JsonConfigLoader jsonConfigLoader;
    private final Environment environment;

    @Value("${reminders.bootstrap.from-json.enabled:false}")
    private boolean bootstrapEnabled;

    @Value("${reminders.data.file:classpath:reminders.json}")
    private String remindersFile;

    @Override
    public void run(ApplicationArguments args) {
        if (!bootstrapEnabled) {
            log.debug(
                    "📴 Bootstrap JSON desabilitado (reminders.bootstrap.from-json.enabled=false)");
            return;
        }

        int existing = repository.count();
        if (existing > 0) {
            log.info("ℹ️ Tabela reminders já tem {} registros — bootstrap JSON ignorado", existing);
            return;
        }

        try {
            var loaded =
                    jsonConfigLoader.loadConfig(
                            CONFIG_FILE_NAME,
                            ReminderJson.class,
                            (remindersFile == null || remindersFile.isBlank())
                                    ? null
                                    : remindersFile);

            if (loaded.isEmpty()) {
                log.warn("⚠️ {} não encontrado — bootstrap JSON ignorado", CONFIG_FILE_NAME);
                return;
            }

            List<Reminder> reminders = loaded.get().reminders();
            if (reminders.isEmpty()) {
                log.info("ℹ️ {} vazio — bootstrap JSON ignorado", CONFIG_FILE_NAME);
                return;
            }

            // 🔧 FIX (item 3): resolve ${VAR} em todos os campos string dos reminders.
            // Sem isso, o JSON versionado no repo (que tem "${QUATRO_HORAS_QUARTA_FILE}")
            // gravaria a string literal no banco, quebrando o enviarMidia.
            List<Reminder> resolvidos = reminders.stream().map(this::resolverPlaceholders).toList();

            int imported = reminderService.importAll(resolvidos, false);
            log.info(
                    "✅ Bootstrap: {} reminders importados de {} para o PostgreSQL",
                    imported,
                    remindersFile);

        } catch (Exception e) {
            // Falha no bootstrap não deve impedir o boot — o bot sobe com tabela vazia
            // e o operador pode usar POST /admin/reminders/import depois.
            log.error("❌ Falha no bootstrap JSON → banco (bot continua com tabela vazia)", e);
        }
    }

    // =========================================================================
    // RESOLUÇÃO DE PLACEHOLDERS
    // =========================================================================

    /**
     * Resolve placeholders {@code ${VAR}} em todos os campos string de um reminder.
     *
     * <p>Se a env var não existir, o placeholder é substituído por string vazia. Se
     * o valor resolvido for igual ao original (sem placeholders), retorna o mesmo
     * record (evita alocação desnecessária).
     */
    private Reminder resolverPlaceholders(Reminder r) {
        String name = resolver(r.name());
        String cron = resolver(r.cron());
        String timezone = resolver(r.timezone());
        String message = resolver(r.message());
        String mediaUrl = resolver(r.mediaUrl());

        // Se nada mudou, retorna o mesmo record
        if (Objects.equals(name, r.name())
                && Objects.equals(cron, r.cron())
                && Objects.equals(timezone, r.timezone())
                && Objects.equals(message, r.message())
                && Objects.equals(mediaUrl, r.mediaUrl())) {
            return r;
        }

        return new Reminder(
                r.id(),
                name,
                r.enabled(),
                cron,
                timezone,
                message,
                mediaUrl,
                r.targetChatIds(),
                r.tags(),
                r.version());
    }

    /**
     * Resolve placeholders {@code ${VAR}} em uma string.
     *
     * @return string com placeholders substituídos, ou {@code null} se {@code valor} for nulo
     */
    private String resolver(String valor) {
        if (valor == null) return null;

        Matcher m = PLACEHOLDER_PATTERN.matcher(valor);
        if (!m.find()) return valor;

        StringBuilder sb = new StringBuilder();
        do {
            String expressao = m.group(1); // "VAR" ou "VAR:default"

            String[] partes = expressao.split(":", 2);
            String envName = partes[0];
            String defaultValue = partes.length > 1 ? partes[1] : "";

            String resolved = environment.getProperty(envName, defaultValue);
            if (resolved.isBlank() && defaultValue.isEmpty()) {
                log.warn("⚠️ Placeholder '${{{}}}' não resolvido — substituído por vazio", envName);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(resolved));
        } while (m.find());
        m.appendTail(sb);
        return sb.toString();
    }
}
