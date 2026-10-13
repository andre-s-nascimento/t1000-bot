/* (c) 2026 | 13/10/2026 */
package net.ddns.adambravo79.tmill.service.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AudioConfigServiceTest {

    private JsonConfigLoader loader;
    private AudioConfigService service;

    private static final long FIVE_MB = 5L * 1024 * 1024;

    @BeforeEach
    void setUp() {
        loader = mock(JsonConfigLoader.class);
        service = new AudioConfigService(loader);
    }

    // =========================================================================
    // CARREGAMENTO FELIZ
    // =========================================================================

    @Test
    @DisplayName("init: carrega JSON completo com tts, telegram e podcast")
    void init_jsonCompleto() {
        doReturn(Optional.of(jsonCompleto()))
                .when(loader)
                .loadConfig(eq("audio-config.json"), eq(Map.class), any());

        service.init();

        AudioConfigService.TtsConfig tts = service.getTts();
        assertThat(tts.maxAudioSizeBytes()).isEqualTo(5242880L);
        assertThat(tts.compressBitrate()).isEqualTo("64k");
        assertThat(tts.compressChannels()).isEqualTo(1);
        assertThat(tts.compressSampleRate()).isEqualTo(22050);
        assertThat(tts.compressTimeoutSeconds()).isEqualTo(120);

        AudioConfigService.TelegramAudioConfig tg = service.getTelegram();
        assertThat(tg.audioMaxSizeMb()).isEqualTo(20);
        assertThat(tg.useDocumentWhenOverMb()).isEqualTo(5);

        AudioConfigService.PodcastAudioConfig pod = service.getPodcast();
        assertThat(pod.maxAudioSizeBytes()).isEqualTo(5242880L);
        assertThat(pod.compressTimeoutSeconds()).isEqualTo(180);
    }

    // =========================================================================
    // FALLBACKS
    // =========================================================================

    @Test
    @DisplayName("init: JSON ausente usa defaults")
    void init_jsonAusente_usaDefaults() {
        doReturn(Optional.empty())
                .when(loader)
                .loadConfig(eq("audio-config.json"), eq(Map.class), any());

        service.init();

        assertThat(service.getTts().maxAudioSizeBytes()).isEqualTo(FIVE_MB);
        assertThat(service.getTts().compressBitrate()).isEqualTo("64k");
        assertThat(service.getTelegram().audioMaxSizeMb()).isEqualTo(20);
        assertThat(service.getTelegram().useDocumentWhenOverMb()).isEqualTo(5);
        assertThat(service.getPodcast().maxAudioSizeBytes()).isEqualTo(FIVE_MB);
        assertThat(service.getPodcast().compressTimeoutSeconds()).isEqualTo(180);
    }

    @Test
    @DisplayName("init: JSON existe mas sem chave 'tts' mantém default")
    void init_semChaveTts_mantemDefault() {
        Map<String, Object> json = new HashMap<>();
        json.put("telegram", Map.of("audio-max-size-mb", 50));

        doReturn(Optional.of(json))
                .when(loader)
                .loadConfig(eq("audio-config.json"), eq(Map.class), any());

        service.init();

        // tts permanece default
        assertThat(service.getTts().maxAudioSizeBytes()).isEqualTo(FIVE_MB);
        // telegram foi sobrescrito
        assertThat(service.getTelegram().audioMaxSizeMb()).isEqualTo(50);
    }

    @Test
    @DisplayName("init: valores malformados caem no default campo a campo")
    void init_valoresMalformados() {
        Map<String, Object> tts = new HashMap<>();
        tts.put("max-audio-size-bytes", "not-a-number"); // inválido → default
        tts.put("compress-bitrate", 12345); // Number → toString → "12345"
        tts.put("compress-channels", "abc"); // inválido → default

        Map<String, Object> json = new HashMap<>();
        json.put("tts", tts);

        doReturn(Optional.of(json))
                .when(loader)
                .loadConfig(eq("audio-config.json"), eq(Map.class), any());

        service.init();

        AudioConfigService.TtsConfig cfg = service.getTts();
        assertThat(cfg.maxAudioSizeBytes()).isEqualTo(FIVE_MB); // default
        assertThat(cfg.compressBitrate()).isEqualTo("12345"); // toString do Number
        assertThat(cfg.compressChannels()).isEqualTo(1); // default
        assertThat(cfg.compressSampleRate()).isEqualTo(22050); // default
        assertThat(cfg.compressTimeoutSeconds()).isEqualTo(120); // default
    }

    @Test
    @DisplayName("init: números como String são aceitos")
    void init_stringNumbers() {
        Map<String, Object> podcast = new HashMap<>();
        podcast.put("max-audio-size-bytes", "10485760"); // 10 MB como String
        podcast.put("compress-timeout-seconds", "300");

        Map<String, Object> json = new HashMap<>();
        json.put("podcast", podcast);

        doReturn(Optional.of(json))
                .when(loader)
                .loadConfig(eq("audio-config.json"), eq(Map.class), any());

        service.init();

        assertThat(service.getPodcast().maxAudioSizeBytes()).isEqualTo(10L * 1024 * 1024);
        assertThat(service.getPodcast().compressTimeoutSeconds()).isEqualTo(300);
    }

    // =========================================================================
    // RELOAD
    // =========================================================================

    @Test
    @DisplayName("reload: substitui estado com novo JSON")
    void reload_substitui() {
        doReturn(Optional.of(jsonCompleto()))
                .when(loader)
                .loadConfig(eq("audio-config.json"), eq(Map.class), any());
        service.init();
        assertThat(service.getTts().compressBitrate()).isEqualTo("64k");

        Map<String, Object> tts = new HashMap<>();
        tts.put("compress-bitrate", "96k");
        Map<String, Object> json = new HashMap<>();
        json.put("tts", tts);

        doReturn(Optional.of(json))
                .when(loader)
                .loadConfig(eq("audio-config.json"), eq(Map.class), any());
        service.reload();

        assertThat(service.getTts().compressBitrate()).isEqualTo("96k");
    }

    @Test
    @DisplayName("reload: JSON ausente mantém estado anterior")
    void reload_jsonAusente_mantemEstado() {
        doReturn(Optional.of(jsonCompleto()))
                .when(loader)
                .loadConfig(eq("audio-config.json"), eq(Map.class), any());
        service.init();
        String bitrateAntes = service.getTts().compressBitrate();

        doReturn(Optional.empty())
                .when(loader)
                .loadConfig(eq("audio-config.json"), eq(Map.class), any());
        service.reload();

        assertThat(service.getTts().compressBitrate()).isEqualTo(bitrateAntes);
    }

    @Test
    @DisplayName("init: chama loadConfig uma vez")
    void init_chamaLoadConfig() {
        doReturn(Optional.empty())
                .when(loader)
                .loadConfig(eq("audio-config.json"), eq(Map.class), any());

        service.init();

        verify(loader, times(1)).loadConfig(eq("audio-config.json"), eq(Map.class), any());
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private static Map<String, Object> jsonCompleto() {
        Map<String, Object> tts = new HashMap<>();
        tts.put("max-audio-size-bytes", 5242880);
        tts.put("compress-bitrate", "64k");
        tts.put("compress-channels", 1);
        tts.put("compress-sample-rate", 22050);
        tts.put("compress-timeout-seconds", 120);

        Map<String, Object> telegram = new HashMap<>();
        telegram.put("audio-max-size-mb", 20);
        telegram.put("use-document-when-over-mb", 5);

        Map<String, Object> podcast = new HashMap<>();
        podcast.put("max-audio-size-bytes", 5242880);
        podcast.put("compress-timeout-seconds", 180);

        Map<String, Object> json = new HashMap<>();
        json.put("tts", tts);
        json.put("telegram", telegram);
        json.put("podcast", podcast);
        return json;
    }
}
