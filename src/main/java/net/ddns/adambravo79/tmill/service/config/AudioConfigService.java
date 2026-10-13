/* (c) 2026 | 12/10/2026 */
package net.ddns.adambravo79.tmill.service.config;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Configurações de áudio (limites, parâmetros de compressão, formatos).
 *
 * <p>Carrega de {@code audio-config.json}, com fallback seguro quando o arquivo
 * está ausente ou inválido. Recarregável em runtime via {@link #reload()}.
 */
@Slf4j
@Service
public class AudioConfigService {

    /** Valores padrão usados quando o JSON não carrega. */
    private static final TtsConfig DEFAULT_TTS =
            new TtsConfig(5L * 1024 * 1024, "64k", 1, 22050, 120);

    private static final TelegramAudioConfig DEFAULT_TELEGRAM = new TelegramAudioConfig(20, 5);
    private static final PodcastAudioConfig DEFAULT_PODCAST =
            new PodcastAudioConfig(5L * 1024 * 1024, 180);

    private final JsonConfigLoader jsonConfigLoader;

    @Value("${audio.config.file:classpath:audio-config.json}")
    private String audioConfigFile;

    private final AtomicReference<TtsConfig> ttsRef = new AtomicReference<>(DEFAULT_TTS);
    private final AtomicReference<TelegramAudioConfig> telegramRef =
            new AtomicReference<>(DEFAULT_TELEGRAM);
    private final AtomicReference<PodcastAudioConfig> podcastRef =
            new AtomicReference<>(DEFAULT_PODCAST);

    public AudioConfigService(JsonConfigLoader jsonConfigLoader) {
        this.jsonConfigLoader = jsonConfigLoader;
    }

    @PostConstruct
    public void init() {
        reload();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void reload() {
        try {
            var loaded =
                    jsonConfigLoader.loadConfig(
                            "audio-config.json",
                            Map.class,
                            audioConfigFile != null && !audioConfigFile.isBlank()
                                    ? audioConfigFile
                                    : null);

            if (loaded.isEmpty()) {
                log.warn("⚠️ audio-config.json não encontrado — usando defaults.");
                return;
            }

            applyTts(loaded.get().get("tts"));
            applyTelegram(loaded.get().get("telegram"));
            applyPodcast(loaded.get().get("podcast"));

            log.info(
                    "✅ AudioConfig carregado: tts={}, telegram={}, podcast={}",
                    ttsRef.get(),
                    telegramRef.get(),
                    podcastRef.get());
        } catch (Exception e) {
            log.error("❌ Falha ao carregar audio-config.json — mantendo anteriores", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void applyTts(Object node) {
        if (!(node instanceof Map<?, ?> m)) return;
        long maxBytes = getLong(m, "max-audio-size-bytes", DEFAULT_TTS.maxAudioSizeBytes());
        String bitrate = getString(m, "compress-bitrate", DEFAULT_TTS.compressBitrate());
        int channels = getInt(m, "compress-channels", DEFAULT_TTS.compressChannels());
        int sampleRate = getInt(m, "compress-sample-rate", DEFAULT_TTS.compressSampleRate());
        int timeout = getInt(m, "compress-timeout-seconds", DEFAULT_TTS.compressTimeoutSeconds());
        ttsRef.set(new TtsConfig(maxBytes, bitrate, channels, sampleRate, timeout));
    }

    @SuppressWarnings("unchecked")
    private void applyTelegram(Object node) {
        if (!(node instanceof Map<?, ?> m)) return;
        int maxMb = getInt(m, "audio-max-size-mb", DEFAULT_TELEGRAM.audioMaxSizeMb());
        int docOver =
                getInt(m, "use-document-when-over-mb", DEFAULT_TELEGRAM.useDocumentWhenOverMb());
        telegramRef.set(new TelegramAudioConfig(maxMb, docOver));
    }

    @SuppressWarnings("unchecked")
    private void applyPodcast(Object node) {
        if (!(node instanceof Map<?, ?> m)) return;
        long maxBytes = getLong(m, "max-audio-size-bytes", DEFAULT_PODCAST.maxAudioSizeBytes());
        int timeout =
                getInt(m, "compress-timeout-seconds", DEFAULT_PODCAST.compressTimeoutSeconds());
        podcastRef.set(new PodcastAudioConfig(maxBytes, timeout));
    }

    private static long getLong(Map<?, ?> m, String key, long fallback) {
        Object v = m.get(key);
        if (v instanceof Number n) return n.longValue();
        if (v != null) {
            try {
                return Long.parseLong(v.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static int getInt(Map<?, ?> m, String key, int fallback) {
        Object v = m.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v != null) {
            try {
                return Integer.parseInt(v.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static String getString(Map<?, ?> m, String key, String fallback) {
        Object v = m.get(key);
        return v != null ? v.toString() : fallback;
    }

    public TtsConfig getTts() {
        return ttsRef.get();
    }

    public TelegramAudioConfig getTelegram() {
        return telegramRef.get();
    }

    public PodcastAudioConfig getPodcast() {
        return podcastRef.get();
    }

    public record TtsConfig(
            long maxAudioSizeBytes,
            String compressBitrate,
            int compressChannels,
            int compressSampleRate,
            int compressTimeoutSeconds) {}

    public record TelegramAudioConfig(int audioMaxSizeMb, int useDocumentWhenOverMb) {}

    public record PodcastAudioConfig(long maxAudioSizeBytes, int compressTimeoutSeconds) {}
}
