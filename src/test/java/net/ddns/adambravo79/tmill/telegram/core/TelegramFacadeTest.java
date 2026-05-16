/* (c) 2026 | 15/05/2026 */
package net.ddns.adambravo79.tmill.telegram.core;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import net.ddns.adambravo79.tmill.telegram.exception.TelegramExceptionHandler;
import net.ddns.adambravo79.tmill.telegram.exception.TelegramFileException;
import net.ddns.adambravo79.tmill.telegram.util.MetricsService;
import net.ddns.adambravo79.tmill.telegram.util.RetryPolicy;

class TelegramFacadeTest {

    // =========================
    // MetricsService
    // =========================
    @Test
    void deveRegistrarSucessoEErro() {
        MetricsService metrics = new MetricsService();

        metrics.success("audio");
        metrics.error("audio");

        assertThat(metrics.getSuccess("audio")).isEqualTo(1);
        assertThat(metrics.getError("audio")).isEqualTo(1);
    }

    // =========================
    // RetryPolicy
    // =========================
    @Test
    void deveExecutarComSucessoSemRetry() throws Exception {
        RetryPolicy policy = new RetryPolicy();
        String result = policy.execute(() -> "ok");
        assertThat(result).isEqualTo("ok");
    }

    @Test
    void deveFalharAposRetries() {
        RetryPolicy policy = new RetryPolicy();
        assertThatThrownBy(
                        () ->
                                policy.execute(
                                        () -> {
                                            throw new IOException("timeout");
                                        }))
                .isInstanceOf(IOException.class);
    }

    // =========================
    // TelegramExceptionHandler
    // =========================
    @Test
    void deveMapearErroDeArquivo() throws Exception {
        TelegramExceptionHandler handler = new TelegramExceptionHandler();
        TelegramSender sender = mock(TelegramSender.class);

        handler.handle(new TelegramFileException("erro", new RuntimeException()), 1L, sender);

        verify(sender).enviar(1L, "⚠️ Não consegui baixar o áudio.");
    }

    @Test
    void deveMapearErroGenerico() throws Exception {
        TelegramExceptionHandler handler = new TelegramExceptionHandler();
        TelegramSender sender = mock(TelegramSender.class);

        handler.handle(new Exception("falha qualquer"), 1L, sender);

        verify(sender).enviar(eq(1L), contains("⚠️"));
    }

    // =========================
    // TelegramSafeExecutor
    // =========================
    @Test
    void deveExecutarAcaoComSucesso() {
        TelegramSafeExecutor executor = new TelegramSafeExecutor();
        TelegramSender fallback = mock(TelegramSender.class);

        executor.run(1L, fallback, () -> {}); // não lança exceção
        verifyNoInteractions(fallback);
    }

    @Test
    void deveExecutarFallbackEmErroTelegram() throws Exception {
        TelegramSafeExecutor executor = new TelegramSafeExecutor();
        TelegramSender fallback = mock(TelegramSender.class);

        executor.run(
                1L,
                fallback,
                () -> {
                    throw new TelegramApiException("fail");
                });
        verify(fallback).enviar(eq(1L), contains("⚠️ Erro ao enviar mensagem"));
    }

    @Test
    void deveExecutarFallbackEmErroGenerico() throws Exception {
        TelegramSafeExecutor executor = new TelegramSafeExecutor();
        TelegramSender fallback = mock(TelegramSender.class);

        executor.run(
                1L,
                fallback,
                () -> {
                    throw new RuntimeException("boom");
                });
        verify(fallback).enviar(eq(1L), contains("⚠️ Erro inesperado"));
    }

    // =========================
    // TelegramFacade (novos métodos)
    // =========================
    @Test
    void deveEnviarMensagem() throws Exception {
        TelegramClient client = mock(TelegramClient.class);
        TelegramSafeExecutor executor = new TelegramSafeExecutor();
        TelegramFacade facade = new TelegramFacade(client, executor);

        facade.enviarMensagem(1L, "teste");

        verify(client).execute(any(SendMessage.class));
    }

    @Test
    void deveEnviarFoto() throws Exception {
        TelegramClient client = mock(TelegramClient.class);
        TelegramSafeExecutor executor = new TelegramSafeExecutor();
        TelegramFacade facade = new TelegramFacade(client, executor);

        facade.enviarFoto(1L, "http://url", "legenda");

        verify(client).execute(any(SendPhoto.class));
    }

    @Test
    void deveEnviarComBotoes() throws Exception {
        TelegramClient client = mock(TelegramClient.class);
        TelegramSafeExecutor executor = new TelegramSafeExecutor();
        TelegramFacade facade = new TelegramFacade(client, executor);

        facade.enviarComBotoes(1L, "texto", null);

        verify(client).execute(any(SendMessage.class));
    }

    @Test
    void deveObterArquivoViaGetFile() throws Exception {
        TelegramClient client = mock(TelegramClient.class);
        TelegramSafeExecutor executor = new TelegramSafeExecutor();
        TelegramFacade facade = new TelegramFacade(client, executor);

        GetFile getFile = new GetFile("fileId");
        org.telegram.telegrambots.meta.api.objects.File tgFile =
                new org.telegram.telegrambots.meta.api.objects.File();
        tgFile.setFileId("fileId");

        when(client.execute(getFile)).thenReturn(tgFile);

        org.telegram.telegrambots.meta.api.objects.File result = facade.getFile(getFile);

        assertThat(result.getFileId()).isEqualTo("fileId");
        verify(client).execute(getFile);
    }

    @Test
    void deveBaixarArquivo() throws Exception {
        TelegramClient client = mock(TelegramClient.class);
        TelegramSafeExecutor executor = new TelegramSafeExecutor();
        TelegramFacade facade = new TelegramFacade(client, executor);

        org.telegram.telegrambots.meta.api.objects.File tgFile =
                new org.telegram.telegrambots.meta.api.objects.File();
        tgFile.setFileId("fileId");

        java.io.File fakeFile = new java.io.File("teste.txt");
        when(client.downloadFile(tgFile)).thenReturn(fakeFile);

        java.io.File result = facade.downloadFile(tgFile);

        assertThat(result).hasName("teste.txt");
        verify(client).downloadFile(tgFile);
    }

    // NOVO: teste para editar mensagem
    @Test
    void deveEditarMensagem() throws Exception {
        TelegramClient client = mock(TelegramClient.class);
        TelegramSafeExecutor executor = new TelegramSafeExecutor();
        TelegramFacade facade = new TelegramFacade(client, executor);

        facade.editarMensagem(123L, 456, "novo texto");

        verify(client).execute(any(EditMessageText.class));
    }

    // NOVO: teste para answerCallbackQuery (não valida fallback, apenas que a chamada ocorre)
    @Test
    void deveAnswerCallbackQuery() throws Exception {
        TelegramClient client = mock(TelegramClient.class);
        TelegramSafeExecutor executor = new TelegramSafeExecutor();
        TelegramFacade facade = new TelegramFacade(client, executor);

        facade.answerCallbackQuery("cb123", "processando", false);

        verify(client)
                .execute(any(org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery.class));
    }

    // =========================
    // TelegramExceptionHandler (Refatorado)
    // =========================
    @ParameterizedTest(name = "Erro {0} deve retornar mensagem contendo: {2}")
    @MethodSource("proverCenariosDeErro")
    void deveMapearErrosEspecificos(String nomeErro, Exception excecao, String mensagemEsperada)
            throws Exception {
        TelegramExceptionHandler handler = new TelegramExceptionHandler();
        TelegramSender sender = mock(TelegramSender.class);

        handler.handle(excecao, 1L, sender);

        verify(sender).enviar(eq(1L), contains(mensagemEsperada));
    }

    private static Stream<Arguments> proverCenariosDeErro() {
        return Stream.of(
                Arguments.of("Unauthorized", new Exception("401 Unauthorized"), "Token inválido"),
                Arguments.of(
                        "ChatNotFound",
                        new Exception("400 chat not found"),
                        "Não consegui encontrar este chat"),
                Arguments.of("Timeout", new Exception("timeout"), "servidor demorou a responder"),
                Arguments.of(
                        "Arquivo Grande",
                        new Exception("file is too big"),
                        "arquivo enviado é muito grande"),
                Arguments.of(
                        "Tipo Inválido",
                        new Exception("wrong file type"),
                        "Formato de arquivo não suportado"),
                Arguments.of(
                        "Arquivo Geral",
                        new TelegramFileException("erro", new RuntimeException()),
                        "Não consegui baixar o áudio"));
    }
}
