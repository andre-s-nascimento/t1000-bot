package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EasterEggServiceTest {

    @Mock private ResourceLoader resourceLoader;
    @Mock private Resource resource;

    private EasterEggService service;

    private static final String JSON_VALIDO =
            """
      {
        "123": "Easter egg do filme 123",
        "456": "Easter egg do filme 456"
      }
      """;

    @BeforeEach
    void setUp() {
        service = new EasterEggService(resourceLoader);
        ReflectionTestUtils.setField(
                service, "easterEggFileLocation", "classpath:easter-eggs.json");
    }

    private void carregarComSucesso() throws Exception {
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream())
                .thenReturn(new ByteArrayInputStream(JSON_VALIDO.getBytes(StandardCharsets.UTF_8)));
        service.loadEasterEggs();
    }

    @Test
    void deveCarregarEasterEggsComSucesso() throws Exception {
        carregarComSucesso();
        Optional<String> egg = service.getEasterEgg(123);
        assertThat(egg).isPresent().contains("Easter egg do filme 123");
        Optional<String> egg2 = service.getEasterEgg(456);
        assertThat(egg2).isPresent().contains("Easter egg do filme 456");
    }

    @Test
    void deveRetornarEmptyParaIdInexistente() throws Exception {
        carregarComSucesso();
        Optional<String> egg = service.getEasterEgg(999);
        assertThat(egg).isEmpty();
    }

    @Test
    void deveIgnorarArquivoInexistente() throws Exception {
        EasterEggService emptyService = new EasterEggService(resourceLoader);
        ReflectionTestUtils.setField(
                emptyService, "easterEggFileLocation", "classpath:easter-eggs.json");

        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(false);

        emptyService.loadEasterEggs();
        assertThat(emptyService.getEasterEgg(123)).isEmpty();
    }

    @Test
    void deveTratarArquivoVazio() throws Exception {
        EasterEggService emptyService = new EasterEggService(resourceLoader);
        ReflectionTestUtils.setField(
                emptyService, "easterEggFileLocation", "classpath:easter-eggs.json");

        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));

        emptyService.loadEasterEggs();
        assertThat(emptyService.getEasterEgg(123)).isEmpty();
    }

    @Test
    void deveTratarErroAoLerArquivo() throws Exception {
        EasterEggService errorService = new EasterEggService(resourceLoader);
        ReflectionTestUtils.setField(
                errorService, "easterEggFileLocation", "classpath:easter-eggs.json");

        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        // Lança IOException, que é capturada no catch específico
        when(resource.getInputStream()).thenThrow(new IOException("Erro de leitura"));

        errorService.loadEasterEggs();
        assertThat(errorService.getEasterEgg(123)).isEmpty();
    }

    @Test
    void reload_deveRecarregarDados() throws Exception {
        carregarComSucesso();
        assertThat(service.getEasterEgg(123)).isPresent();

        String novoJson = """
        {
          "789": "Novo easter egg"
        }
        """;
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream())
                .thenReturn(new ByteArrayInputStream(novoJson.getBytes(StandardCharsets.UTF_8)));

        service.reload();
        assertThat(service.getEasterEgg(123)).isEmpty();
        assertThat(service.getEasterEgg(789)).isPresent().contains("Novo easter egg");
    }
}
