package net.ddns.adambravo79.tmill.controller.kafka;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.dto.AudioProcessedEvent;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;

@Slf4j
@Component
@RequiredArgsConstructor
public class AudioResultConsumer {

    private final TelegramFacade telegramFacade;

    @KafkaListener(topics = "t1000.audio.processed", groupId = "t1000-bot-responses")
    public void handleProcessedAudio(@Payload AudioProcessedEvent event) {
        log.info(
                "📥 [Bot] Recebida resposta do Worker via Kafka. chatId={}, sucesso={}",
                event.chatId(),
                event.sucesso());

        if (event.sucesso()) {
            // Como a transcrição já foi gerada pelo Worker, aqui é o momento exato
            // para enviar os botões de ação (ex: "Gerar Resumo", "Adicionar Tarefa")
            String mensagemSucesso = "✅ *Áudio processado com sucesso!*";
            telegramFacade.enviarMensagemHtml(event.chatId(), mensagemSucesso);

            // TODO: Injetar seu serviço de botões aqui para enviar o InlineKeyboardMarkup
        } else {
            // Tratamento gracioso de erro sem expor stacktrace pro usuário
            String mensagemErro =
                    String.format(
                            "❌ *Ops! Não consegui processar seu áudio.*\n\nMotivo: %s",
                            event.mensagemErro());
            telegramFacade.enviarMensagemHtml(event.chatId(), mensagemErro);
        }
    }
}
