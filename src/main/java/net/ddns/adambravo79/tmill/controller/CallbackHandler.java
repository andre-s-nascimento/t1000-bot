package net.ddns.adambravo79.tmill.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.exception.MovieNotFoundException;
import net.ddns.adambravo79.tmill.model.MovieOrchestrationResponse;
import net.ddns.adambravo79.tmill.model.MovieRecord;
import net.ddns.adambravo79.tmill.service.MovieService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;

@Slf4j
@Component
@RequiredArgsConstructor
public class CallbackHandler {

    private final MovieService movieService;
    private final AudioHandler audioHandler;
    private final TelegramFacade telegramFacade;

    public void handleCallbackUpdate(Update update) {
        CallbackQuery callback = update.callbackQuery();
        String data = callback.data();
        long chatId = callback.message().chat().id();

        log.debug("Callback: chatId={}, data={}", chatId, data);

        if (data.startsWith("id:")) {
            handleMovieSelection(callback, chatId);
            return;
        }

        if (data.startsWith("trans_bruto|") || data.startsWith("trans_refinado|")) {
            audioHandler.handleTranscriptionCallback(callback, data);
            return;
        }

        log.warn("Callback desconhecido: {}", data);
        telegramFacade.answerCallbackQuery(callback.id(), "Ação não reconhecida", false);
    }

    private void handleMovieSelection(CallbackQuery callback, long chatId) {
        String data = callback.data();
        long movieId;
        try {
            movieId = Long.parseLong(data.replace("id:", ""));
        } catch (NumberFormatException e) {
            log.error("ID inválido no callback: {}", data);
            telegramFacade.answerCallbackQuery(callback.id(), "ID inválido", true);
            return;
        }

        telegramFacade.answerCallbackQuery(callback.id(), "Buscando filme...", false);

        try {
            MovieOrchestrationResponse resposta = movieService.buscarPorId(movieId);
            String newText = "✅ Filme selecionado: " + resposta.textoFormatado().split("\n")[0];
            // Correção: editarMensagemHtml recebe int, não Integer
            telegramFacade.editarMensagemHtml(chatId, callback.message().messageId(), newText);
            exibirRespostaFilme(chatId, resposta);
        } catch (MovieNotFoundException e) {
            telegramFacade.answerCallbackQuery(callback.id(), "Filme não encontrado", true);
            telegramFacade.editarMensagem(
                    chatId, callback.message().messageId(), "❌ Filme não encontrado.");
        } catch (Exception e) {
            log.error("Erro ao buscar detalhes do filme", e);
            telegramFacade.answerCallbackQuery(callback.id(), "Erro interno", true);
        }
    }

    private void exibirRespostaFilme(long chatId, MovieOrchestrationResponse resposta) {
        String fotoUrl = resposta.urlFoto();
        if (fotoUrl != null
                && !fotoUrl.isBlank()
                && (fotoUrl.startsWith("http://") || fotoUrl.startsWith("https://"))) {
            telegramFacade.enviarFotoHtml(chatId, fotoUrl, resposta.textoFormatado());
        } else {
            telegramFacade.enviarMensagemHtml(
                    chatId, resposta.textoFormatado() + "\n\n_(sem imagem)_");
        }
    }

    /**
     * Cria botões inline para desambiguação de filmes. Usa arrays de InlineKeyboardButton (sem
     * InlineKeyboardRow).
     */
    public InlineKeyboardMarkup criarBotoesDesambiguacao(List<MovieRecord> resultados) {
        List<InlineKeyboardButton[]> rows = new ArrayList<>();
        List<InlineKeyboardButton> currentRow = new ArrayList<>();
        for (int i = 0; i < resultados.size() && i < 10; i++) {
            MovieRecord filme = resultados.get(i);
            String ano =
                    (filme.releaseDate() != null && filme.releaseDate().length() >= 4)
                            ? " (" + filme.releaseDate().substring(0, 4) + ")"
                            : " (S/A)";
            currentRow.add(
                    new InlineKeyboardButton(filme.title() + ano).callbackData("id:" + filme.id()));
            if ((i + 1) % 2 == 0 || (i + 1) == resultados.size()) {
                rows.add(currentRow.toArray(new InlineKeyboardButton[0]));
                currentRow = new ArrayList<>();
            }
        }
        return new InlineKeyboardMarkup(rows.toArray(new InlineKeyboardButton[0][]));
    }
}
