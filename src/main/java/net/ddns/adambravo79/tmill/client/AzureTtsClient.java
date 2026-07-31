package net.ddns.adambravo79.tmill.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AzureTtsClient {

    private final RestClient restClient;
    private final Path tempDir;

    public AzureTtsClient(
            @Value("${azure.speech.key}") String subscriptionKey,
            @Value("${azure.speech.region}") String region,
            @Value("${app.temp.dir:./temp}") String tempDirPath)
            throws Exception {

        this.tempDir = Paths.get(tempDirPath).toAbsolutePath().normalize();
        Files.createDirectories(this.tempDir);
        log.info("📁 Diretório temporário configurado: {}", this.tempDir);

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(60000);
        this.restClient =
                RestClient.builder()
                        .baseUrl("https://" + region + ".tts.speech.microsoft.com")
                        .requestFactory(factory)
                        .defaultHeader("Ocp-Apim-Subscription-Key", subscriptionKey)
                        .defaultHeader("Content-Type", "application/ssml+xml")
                        .defaultHeader(
                                "X-Microsoft-OutputFormat", "audio-16khz-128kbitrate-mono-mp3")
                        .defaultHeader("User-Agent", "TmillBot")
                        .build();
        log.info("✅ Azure TTS Client (REST) inicializado com região: {}", region);
    }

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

        log.debug(
                "SSML enviado (primeiros 500 chars): {}",
                ssml.length() > 500 ? ssml.substring(0, 500) + "..." : ssml);

        // 🔥 Só salva se a propriedade debug.tts.save-ssml for true (desativado por padrão)
        if (Boolean.parseBoolean(System.getProperty("ajuste.debug.tts.save-ssml", "false"))) {
            try {
                Path ssmlFile = Files.createTempFile(tempDir, "ssml_", ".xml");
                Files.writeString(ssmlFile, ssml, java.nio.charset.StandardCharsets.UTF_8);
                log.info("📄 SSML salvo em: {}", ssmlFile);
            } catch (Exception e) {
                log.warn("Não foi possível salvar SSML", e);
            }
        }

        try {
            byte[] bodyBytes = ssml.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return restClient
                    .post()
                    .uri("/cognitiveservices/v1")
                    .body(bodyBytes)
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
        log.info("Concatenando {} partes de áudio.", audioParts.size());
        try {
            List<Path> tempFiles = new ArrayList<>();
            for (int i = 0; i < audioParts.size(); i++) {
                Path temp = Files.createTempFile(tempDir, "azure_tts_" + i + "_", ".mp3");
                Files.write(temp, audioParts.get(i));
                tempFiles.add(temp);
                log.debug("Arquivo temporário criado: {}", temp);
            }

            Path output = Files.createTempFile(tempDir, "azure_tts_concat_", ".mp3");
            log.info("Arquivo de saída: {}", output);
            boolean success = concatWithFfmpeg(tempFiles, output);
            if (!success) {
                log.error("Falha na concatenação com FFmpeg. Tentando retornar o primeiro áudio.");
                if (!audioParts.isEmpty()) {
                    return audioParts.get(0);
                }
                return new byte[0];
            }

            byte[] result = Files.readAllBytes(output);
            log.info("Concatenação bem-sucedida: {} bytes.", result.length);

            for (Path p : tempFiles) Files.deleteIfExists(p);
            Files.deleteIfExists(output);
            return result;
        } catch (Exception e) {
            log.error("Erro ao concatenar áudios", e);
            if (!audioParts.isEmpty()) {
                log.warn("Usando fallback: retornando o primeiro áudio.");
                return audioParts.get(0);
            }
            return new byte[0];
        }
    }

    private boolean concatWithFfmpeg(List<Path> inputs, Path output) {
        Path fileList = null;
        Process process = null;
        try {
            if (!isFfmpegAvailable()) {
                return false;
            }

            fileList = createFileList(inputs);
            process = executeFfmpeg(fileList, output);
            if (process == null) {
                return false;
            }

            // Aguarda a conclusão
            boolean finished = process.waitFor(120, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.error("❌ FFmpeg timeout após 120 segundos.");
                return false;
            }

            int exitCode = process.exitValue();
            log.info("✅ FFmpeg finalizado com código {}", exitCode);
            return exitCode == 0;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("❌ Thread interrompida durante execução do FFmpeg", e);
            return false;
        } catch (Exception e) {
            log.error("❌ FFmpeg falhou", e);
            return false;
        } finally {
            cleanup(fileList, process);
        }
    }

    /** Verifica se o FFmpeg está disponível no sistema. */
    private boolean isFfmpegAvailable() {
        try {
            ProcessBuilder checkPb = new ProcessBuilder("/usr/bin/ffmpeg", "-version");
            Process checkProcess = checkPb.start();
            boolean finished = checkProcess.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                log.error("FFmpeg não está disponível no sistema (timeout).");
                return false;
            }
            log.info("✅ FFmpeg disponível.");
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupção ao verificar FFmpeg", e);
            return false;
        } catch (Exception e) {
            log.error("FFmpeg não está disponível: {}", e.getMessage());
            return false;
        }
    }

    /** Cria o arquivo de lista para concatenação. */
    private Path createFileList(List<Path> inputs) throws Exception {
        Path fileList = Files.createTempFile(tempDir, "filelist_", ".txt");
        List<String> lines =
                inputs.stream()
                        .map(
                                p ->
                                        "file '"
                                                + p.toAbsolutePath()
                                                        .toString()
                                                        .replace("'", "'\\''")
                                                + "'")
                        .toList();
        Files.write(fileList, lines);
        log.info("📋 Arquivo de lista criado: {}", fileList);
        return fileList;
    }

    /** Executa o FFmpeg para concatenar os áudios. */
    private Process executeFfmpeg(Path fileList, Path output) throws Exception {
        ProcessBuilder pb =
                new ProcessBuilder(
                        "/usr/bin/ffmpeg",
                        "-y",
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

        log.info("⚙️ Executando FFmpeg: {}", String.join(" ", pb.command()));
        Process process = pb.start();

        // Consome a saída em thread separada
        Thread outputReader = new Thread(() -> consumeOutput(process));
        outputReader.setDaemon(true);
        outputReader.start();

        return process;
    }

    /** Consome a saída do processo para evitar bloqueio. */
    private void consumeOutput(Process process) {
        try (var reader =
                new java.io.BufferedReader(
                        new java.io.InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.debug("FFmpeg: {}", line);
            }
        } catch (Exception e) {
            // Ignora erro ao fechar o reader
        }
    }

    /** Limpeza de recursos. */
    private void cleanup(Path fileList, Process process) {
        if (fileList != null) {
            try {
                Files.deleteIfExists(fileList);
            } catch (Exception e) {
                log.warn("Não foi possível deletar arquivo de lista: {}", fileList);
            }
        }
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
        }
    }
}
