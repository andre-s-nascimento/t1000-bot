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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.constant.BotMessages;
import net.ddns.adambravo79.tmill.model.Birthday;
import net.ddns.adambravo79.tmill.repository.BirthdayRepository;
import net.ddns.adambravo79.tmill.telegram.core.GroupAuthorizationService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;

@Slf4j
@Service
@RequiredArgsConstructor
public class BirthdayService {

    private static final Pattern DATE_PATTERN = Pattern.compile("\\b(\\d{1,2})[/-](\\d{1,2})\\b");

    private final BirthdayRepository repository;
    private final TelegramFacade telegramFacade;
    private final GroupAuthorizationService groupAuthorizationService;

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
        // Usa 2024 (bissexto) para permitir 29/02
        try {
            LocalDate.of(2024, month, day);
            return true;
        } catch (DateTimeException e) {
            return false;
        }
    }

    /**
     * Dispara mensagens de parabéns para todos os aniversariantes de hoje. Executado pelo scheduler
     * às 00:01 (BRT).
     */
    public void enviarParabensDoDia() {
        LocalDate hoje = LocalDate.now(ZoneId.of(BotMessages.BRAZIL_ZONE));
        enviarParabensPara(hoje.getDayOfMonth(), hoje.getMonthValue());
    }

    /**
     * Variante testável: envia parabéns para aniversariantes de um dia/mês específico. Ignora
     * usuários que já receberam parabéns no ano corrente.
     *
     * <p>Envia primeiro no privado; se falhar com Forbidden (403), apenas loga e segue. Em seguida,
     * envia em todos os grupos autorizados (menção por ID).
     *
     * @return número de usuários que receberam pelo menos uma mensagem (privado ou grupo)
     */
    public int enviarParabensPara(int day, int month) {
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
            }
        }
        return enviados;
    }

    /**
     * Envia a mensagem de parabéns no privado e nos grupos. Retorna true se pelo menos um envio
     * ocorreu (privado OU algum grupo).
     */
    private boolean enviarParabensIndividuais(Birthday b) {
        String mensagem = buildMensagemParabens(buildMencaoHtml(b));

        // 1. Privado primeiro
        boolean enviadoPrivado = enviarPrivado(b, mensagem);

        // 2. Grupos depois
        boolean enviadoGrupo = enviarGrupos(b, mensagem);

        return enviadoPrivado || enviadoGrupo;
    }

    /**
     * Envia a mensagem no privado do usuário. Se o usuário não iniciou o bot (Forbidden 403), loga
     * warn e retorna false — o envio nos grupos continua normalmente.
     */
    private boolean enviarPrivado(Birthday b, String mensagem) {
        try {
            telegramFacade.enviarMidia(b.userId(), gifUrl, mensagem);
            log.info(
                    "🎉 Parabéns enviados (privado) para userId={} name={}",
                    b.userId(),
                    b.userName());
            return true;
        } catch (org.springframework.web.client.HttpClientErrorException.Forbidden e) {
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

    /**
     * Envia a mensagem de parabéns em todos os grupos autorizados. Continua mesmo se algum grupo
     * falhar.
     */
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
            telegramFacade.enviarMidia(groupId, gifUrl, mensagem);
            log.info(
                    "🎉 Parabéns enviados (grupo {}) para userId={} name={}",
                    groupId,
                    b.userId(),
                    b.userName());
            return true;
        } catch (Exception e) {
            log.error(
                    "❌ Falha ao enviar parabéns no grupo {} para userId={}",
                    groupId,
                    b.userId(),
                    e);
            return false;
        }
    }

    /**
     * Constrói a menção HTML por ID do Telegram — funciona mesmo se o usuário não tiver username
     * público. O Telegram renderiza como o nome em azul, clicável.
     */
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

    /**
     * Monta a mensagem de parabéns com temática cinematográfica (Marilyn Monroe / Happy Birthday Mr.
     * President).
     */
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

    /** Exposto para uso em admin/diagnóstico. */
    public String getGifUrl() {
        return gifUrl;
    }
}
