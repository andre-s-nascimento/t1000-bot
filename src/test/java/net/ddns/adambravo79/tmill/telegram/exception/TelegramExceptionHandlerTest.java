/* (c) 2026 | 15/05/2026 */
package net.ddns.adambravo79.tmill.telegram.exception;

import static org.mockito.Mockito.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import net.ddns.adambravo79.tmill.telegram.core.TelegramSender;

class TelegramExceptionHandlerTest {

    @ParameterizedTest
    @CsvSource({
        "401 Unauthorized, Token inválido",
        "400 chat not found, ❌ Não consegui encontrar este chat",
        "file is too big, 📂 O arquivo enviado é muito grande",
        "wrong file type, 🛑 Formato de arquivo não suportado"
    })
    void deveTratarErrosEspecificos(String mensagemErro, String mensagemEsperada) throws Exception {
        TelegramExceptionHandler handler = new TelegramExceptionHandler();
        TelegramSender sender = mock(TelegramSender.class);

        handler.handle(new Exception(mensagemErro), 123L, sender);

        verify(sender).enviar(eq(123L), contains(mensagemEsperada));
    }
}
