package net.ddns.adambravo79.tmill.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AzureTtsClient {

    private final RestClient restClient;
    private final String region;

    public AzureTtsClient(
            @Value("${azure.speech.key}") String subscriptionKey,
            @Value("${azure.speech.region}") String region) {
        this.region = region;
        this.restClient =
                RestClient.builder()
                        .baseUrl("https://" + region + ".tts.speech.microsoft.com")
                        .defaultHeader("Ocp-Apim-Subscription-Key", subscriptionKey)
                        .defaultHeader("Content-Type", "application/ssml+xml")
                        .defaultHeader(
                                "X-Microsoft-OutputFormat", "audio-16khz-128kbitrate-mono-mp3")
                        .defaultHeader("User-Agent", "TmillBot")
                        .build();
        log.info("✅ Azure TTS Client (REST) inicializado com região: {}", region);
    }

    /**
     * Sintetiza um texto completo, dividindo em partes de até 5000 caracteres (limite da API) e
     * concatenando os áudios MP3 resultantes.
     */
    public byte[] synthesizeFullText(String fullText) {
        if (fullText == null || fullText.isBlank()) {
            log.warn("Texto vazio para síntese.");
            return new byte[0];
        }

        List<String> parts = splitTextIntoParts(fullText, 5000);
        List<byte[]> audioParts = new ArrayList<>();

        for (int i = 0; i < parts.size(); i++) {
            log.info(
                    "Sintetizando parte {}/{} ({} caracteres)",
                    i + 1,
                    parts.size(),
                    parts.get(i).length());
            byte[] audio = synthesizeSinglePart(parts.get(i));
            if (audio != null && audio.length > 0) {
                audioParts.add(audio);
            }
        }

        if (audioParts.isEmpty()) {
            log.error("Nenhum áudio sintetizado.");
            return new byte[0];
        }

        if (audioParts.size() == 1) {
            return audioParts.get(0);
        }

        return concatenateMp3s(audioParts);
    }

    private byte[] synthesizeSinglePart(String text) {
        String ssml =
                "<speak version=\"1.0\" xmlns=\"http://www.w3.org/2001/10/synthesis\""
                        + " xml:lang=\"pt-BR\"><voice name=\"pt-BR-AntonioNeural\">"
                        + text
                        + "</voice></speak>";

        try {
            return restClient
                    .post()
                    .uri("/cognitiveservices/v1")
                    .body(ssml)
                    .retrieve()
                    .body(byte[].class);
        } catch (Exception e) {
            log.error("Erro ao sintetizar parte", e);
            return new byte[0];
        }
    }

    // ----- Métodos auxiliares (split e concatenação) -----

    private List<String> splitTextIntoParts(String text, int maxLength) {
        List<String> parts = new ArrayList<>();
        if (text.length() <= maxLength) {
            parts.add(text);
            return parts;
        }
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxLength, text.length());
            if (end < text.length()) {
                int lastBreak = findLastSentenceBreak(text, start, end);
                if (lastBreak > start) {
                    end = lastBreak + 1;
                }
            }
            parts.add(text.substring(start, end).trim());
            start = end;
        }
        return parts;
    }

    private int findLastSentenceBreak(String text, int start, int end) {
        for (int i = end - 1; i >= start; i--) {
            char c = text.charAt(i);
            if (c == '.' || c == '!' || c == '?' || c == '\n') {
                return i;
            }
        }
        return -1;
    }

    private byte[] concatenateMp3s(List<byte[]> audioParts) {
        try {
            List<Path> tempFiles = new ArrayList<>();
            for (int i = 0; i < audioParts.size(); i++) {
                Path temp = Files.createTempFile("azure_tts_" + i + "_", ".mp3");
                Files.write(temp, audioParts.get(i));
                tempFiles.add(temp);
            }

            Path output = Files.createTempFile("azure_tts_concat_", ".mp3");
            boolean success = concatWithFfmpeg(tempFiles, output);
            byte[] result = success ? Files.readAllBytes(output) : new byte[0];

            for (Path p : tempFiles) Files.deleteIfExists(p);
            Files.deleteIfExists(output);

            return result;
        } catch (Exception e) {
            log.error("Erro ao concatenar áudios", e);
            return new byte[0];
        }
    }

    private boolean concatWithFfmpeg(List<Path> inputs, Path output) {
        try {
            Path fileList = Files.createTempFile("filelist_", ".txt");
            List<String> lines =
                    inputs.stream().map(p -> "file '" + p.toAbsolutePath() + "'").toList();
            Files.write(fileList, lines);

            ProcessBuilder pb =
                    new ProcessBuilder(
                            "ffmpeg",
                            "-f",
                            "concat",
                            "-safe",
                            "0",
                            "-i",
                            fileList.toAbsolutePath().toString(),
                            "-c",
                            "copy",
                            output.toAbsolutePath().toString());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();
            Files.deleteIfExists(fileList);
            return exitCode == 0;
        } catch (Exception e) {
            log.error("FFmpeg falhou", e);
            return false;
        }
    }
}
