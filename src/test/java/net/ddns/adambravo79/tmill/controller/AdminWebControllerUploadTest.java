package net.ddns.adambravo79.tmill.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import lombok.SneakyThrows;
import net.ddns.adambravo79.tmill.service.AutoResponseService;
import net.ddns.adambravo79.tmill.service.EasterEggService;
import net.ddns.adambravo79.tmill.service.SalaVipService;
import net.ddns.adambravo79.tmill.service.config.SalaVipConfigService;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AdminWebControllerUploadTest {

    @Mock Environment environment;
    @Mock SalaVipService salaVipService;
    @Mock SalaVipConfigService salaVipConfigService;
    @Mock AutoResponseService autoResponseService;
    @Mock EasterEggService easterEggService;

    @InjectMocks AdminWebController controller;

    // O ObjectMapper real é usado pelo controller para validar o JSON
    private final ObjectMapper objectMapper = new ObjectMapper();

    RedirectAttributes redirectAttrs;

    @TempDir Path tempDir;

    @BeforeEach
    void setUp() {
        // O @InjectMocks cuidou de criar o controller passando os mocks acima.
        // Como o ObjectMapper do controller é `final`, precisamos setar via Reflection:
        org.springframework.test.util.ReflectionTestUtils.setField(
                controller, "objectMapper", objectMapper);

        redirectAttrs = new RedirectAttributesModelMap();
    }

    @Test
    void uploadJsonComArquivoVazioDeveRetornarErro() {
        MockMultipartFile file = new MockMultipartFile("file", new byte[0]);
        String result = controller.uploadJson(file, "salavip", redirectAttrs);

        assertThat(result).isEqualTo("redirect:/admin-web");
        assertThat(redirectAttrs.getFlashAttributes()).containsKey("error");
    }

    @Test
    void uploadJsonComAlvoInvalidoDeveRetornarErro() {
        MockMultipartFile file = new MockMultipartFile("file", "{}".getBytes());
        String result = controller.uploadJson(file, "../../etc/passwd", redirectAttrs);

        assertThat(result).isEqualTo("redirect:/admin-web");
        assertThat(redirectAttrs.getFlashAttributes()).containsKey("error");
        // Garante que não tentou salvar nem recarregar nada
        verifyNoInteractions(salaVipService);
    }

    @Test
    @SneakyThrows
    void uploadJsonValidoDeveSalvarERecarregar() {
        // Simula a property apontando para o diretório temporário
        Path destino = tempDir.resolve("sala-vip.json");
        when(environment.getProperty("salavip.data.file")).thenReturn("file:" + destino);

        MockMultipartFile file = new MockMultipartFile("file", "{\"filmes\":[]}".getBytes());

        String result = controller.uploadJson(file, "salavip", redirectAttrs);

        assertThat(result).isEqualTo("redirect:/admin-web");
        assertThat(redirectAttrs.getFlashAttributes()).containsKey("success");
        assertThat(Files.exists(destino)).isTrue();
        verify(salaVipService, times(1)).sincronizarLista();
    }
}
