/* (c) 2026 | 07/11/2026 */
package net.ddns.adambravo79.tmill.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
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

    private final ReminderRepository repository;
    private final ReminderService reminderService;
    private final JsonConfigLoader jsonConfigLoader;

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

            int imported = reminderService.importAll(reminders, false);
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
}
