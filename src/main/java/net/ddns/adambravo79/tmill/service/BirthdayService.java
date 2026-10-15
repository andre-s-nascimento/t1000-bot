/* (c) 2026 | 15/09/2026 */
package net.ddns.adambravo79.tmill.service;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.constant.BotMessages;
import net.ddns.adambravo79.tmill.model.Birthday;
import net.ddns.adambravo79.tmill.repository.BirthdayRepository;
import net.ddns.adambravo79.tmill.service.feature.FeatureFlagAdminService;
import net.ddns.adambravo79.tmill.telegram.core.GroupAuthorizationService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import net.ddns.adambravo79.tmill.telegram.util.MetricsService;

@Slf4j
@Service
@RequiredArgsConstructor
public class BirthdayService {

    private static final Pattern DATE_PATTERN = Pattern.compile("\\b(\\d{1,2})[/-](\\d{1,2})\\b");

    private final BirthdayRepository repository;
    private final TelegramFacade telegramFacade;
    private final GroupAuthorizationService groupAuthorizationService;
    private final MetricsService metricsService;
    private final FeatureFlagAdminService featureFlags;

    @Value(
            "${birthday.gif-url:https://media2.giphy.com/media/v1.Y2lkPTc5MGI3NjExc2cyem12dm1ldmp0NGR4bHl0NHRqcXBka3pzejN6eHJpeG9nZTliZiZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/11wifmSGQD9CtW/giphy.gif}")
    private String gifUrl;

    /**
     * Registra/atualiza o aniversário de um usuário a partir de uma data em formato dd/MM.
     *
     * @return mensagem de confirmação para enviar ao usuário
     */
    public String registrar(long userId, String userName, String dataTexto) {
        if (dataTexto == null || dataTexto.isBlank()) {
            return BotMessages.ANIVERSARIO_FORMATO_INVALIDO;
        }

        Matcher m = DATE_PATTERN.matcher(dataTexto);
        if (!m.find()) {
            return BotMessages.ANIVERSARIO_FORMATO_INVALIDO;
        }

        int day;
        int month;
        try {
            day = Integer.parseInt(m.group(1));
            month = Integer.parseInt(m.group(2));
        } catch (NumberFormatException e) {
            return BotMessages.ANIVERSARIO_FORMATO_INVALIDO;
        }

        if (!isDataValida(day, month)) {
            return BotMessages.ANIVERSARIO_DATA_INVALIDA;
        }

        repository.upsert(userId, userName, day, month);

        String dataFormatada = String.format("%02d/%02d", day, month);
        log.info(
                "🎂 Aniversário registrado userId={} name={} data={}",
                userId,
                userName,
                dataFormatada);

        return String.format(BotMessages.ANIVERSARIO_REGISTRADO, dataFormatada);
    }

    /** Retorna true se o par dia/mês é uma data válida (considera ano bissexto genérico). */
    boolean isDataValida(int day, int month) {
        if (month < 1 || month > 12) return false;
        if (day < 1) return false;
        // Usa 2024 (bissexto) para permitir 29/02.
        // O valor retornado por LocalDate.of é usado em log.trace para satisfazer java:S2201.
        try {
            LocalDate dataReferencia = LocalDate.of(2024, month, day);
            log.trace("Data de referência válida: {}", dataReferencia);
            return true;
        } catch (DateTimeException e) {
            return false;
        }
    }

    public void enviarParabensDoDia() {
        LocalDate hoje = LocalDate.now(ZoneId.of(BotMessages.BRAZIL_ZONE));
        enviarParabensPara(hoje.getDayOfMonth(), hoje.getMonthValue());
    }

    public int enviarParabensPara(int day, int month) {
        if (!featureFlags.isEnabled("birthday.enabled")) {
            log.info("🎂 Parabéns desabilitados por feature flag — pulando.");
            return 0;
        }

        List<Birthday> aniversariantes = repository.findByDayAndMonth(day, month);

        if (aniversariantes.isEmpty()) {
            log.info("🎂 Nenhum aniversariante em {}/{}", day, month);
            return 0;
        }

        int year = LocalDate.now(ZoneId.of(BotMessages.BRAZIL_ZONE)).getYear();
        log.info(
                "🎂 {} aniversariante(s) em {}/{} (ano {})",
                aniversariantes.size(),
                day,
                month,
                year);

        int enviados = 0;
        for (Birthday b : aniversariantes) {
            if (b.lastSentYear() != null && b.lastSentYear() == year) {
                log.info("⏭️ Parabéns já enviados este ano para userId={} ({})", b.userId(), year);
                continue;
            }
            if (enviarParabensIndividuais(b)) {
                repository.markSent(b.userId(), year);
                enviados++;
                metricsService.success("aniversario_enviado");
            } else {
                metricsService.error("aniversario_enviado");
            }
        }
        return enviados;
    }

    private boolean enviarParabensIndividuais(Birthday b) {
        String mensagem = buildMensagemParabens(buildMencaoHtml(b));

        boolean enviadoPrivado = enviarPrivado(b, mensagem);

        // 🆕 Sub-flag: envia nos grupos apenas se habilitada
        boolean enviadoGrupo = false;
        if (featureFlags.isEnabled("birthday.group-notification.enabled")) {
            enviadoGrupo = enviarGrupos(b, mensagem);
        } else {
            log.debug("📴 Notificação em grupo desabilitada — enviando só no privado.");
        }

        return enviadoPrivado || enviadoGrupo;
    }

    private boolean enviarPrivado(Birthday b, String mensagem) {
        try {
            boolean enviado = telegramFacade.enviarMidia(b.userId(), gifUrl, mensagem);
            if (enviado) {
                log.info(
                        "🎉 Parabéns enviados (privado) para userId={} name={}",
                        b.userId(),
                        b.userName());
            } else {
                log.warn(
                        "⚠️ envio (privado) retornou false para userId={} name={}",
                        b.userId(),
                        b.userName());
            }
            return enviado;
        } catch (HttpClientErrorException.Forbidden e) {
            log.warn(
                    "⚠️ Usuário {} não iniciou o bot no privado (Forbidden). Pulando privado,"
                            + " enviando apenas nos grupos.",
                    b.userId());
            return false;
        } catch (Exception e) {
            log.error("❌ Falha ao enviar parabéns (privado) para userId={}", b.userId(), e);
            return false;
        }
    }

    private boolean enviarGrupos(Birthday b, String mensagem) {
        Set<Long> grupos = groupAuthorizationService.getAllowedGroups();
        if (grupos.isEmpty()) {
            log.info("ℹ️ Nenhum grupo autorizado para enviar parabéns de userId={}", b.userId());
            return false;
        }

        boolean algumEnviado = false;
        for (Long groupId : grupos) {
            if (enviarParaGrupo(groupId, b, mensagem)) {
                algumEnviado = true;
            }
        }
        return algumEnviado;
    }

    private boolean enviarParaGrupo(long groupId, Birthday b, String mensagem) {
        try {
            boolean enviado = telegramFacade.enviarMidia(groupId, gifUrl, mensagem);
            if (enviado) {
                log.info(
                        "🎉 Parabéns enviados (grupo {}) para userId={} name={}",
                        groupId,
                        b.userId(),
                        b.userName());
            } else {
                log.warn("⚠️ envio (grupo {}) retornou false para userId={}", groupId, b.userId());
            }
            return enviado;
        } catch (Exception e) {
            log.error(
                    "❌ Falha ao enviar parabéns no grupo {} para userId={}",
                    groupId,
                    b.userId(),
                    e);
            return false;
        }
    }

    String buildMencaoHtml(Birthday b) {
        String nome = b.userName() == null || b.userName().isBlank() ? "amigo(a)" : b.userName();
        String nomeEscapado = escapeHtml(nome);
        return "<a href=\"tg://user?id=" + b.userId() + "\">" + nomeEscapado + "</a>";
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    String buildMensagemParabens(String nomeMencionado) {
        return """
        🎬 <b>HAPPY BIRTHDAY, %s!</b> 🎂

        <i>"Happy birthday to you... happy birthday to you..."</i>
        — Marilyn Monroe, Madison Square Garden, 19 de maio de 1962 🎤

        Hoje o T-1000 pausa a missão de eliminar o John Connor pra te desejar:
        <b>feliz aniversário!</b> 🥳

        Que seu dia seja digno de um Oscar — com pipoca, bolo e uma boa maratona.
        Lembre-se: <i>"a vida é uma peça de teatro que não permite ensaios"</i> (Charles Chaplin).

        Parabéns, %s! 🎉
        """
                .formatted(nomeMencionado, nomeMencionado);
    }

    public String getGifUrl() {
        return gifUrl;
    }
}
