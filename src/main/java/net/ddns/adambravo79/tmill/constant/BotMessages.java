/**********************************************************
 * ARQUIVO: ./src/main/java/net/ddns/adambravo79/tmill/constant/BotMessages.java
 **********************************************************/

package net.ddns.adambravo79.tmill.constant;

/**
 * Constantes de mensagens, valores e formatos reutilizados no projeto. Centraliza strings
 * duplicadas para facilitar manutenção, consistencia e i18n futura.
 *
 * <p>Organizacao por categoria:
 *
 * <ul>
 *   <li>TMDB / Streaming — mensagens relacionadas a filmes e series
 *   <li>Copa do Mundo — mensagens do servico de jogos
 *   <li>Audio / Transcricao — mensagens de processamento de audio
 *   <li>Bot / Comandos — respostas do bot a comandos e interacoes
 *   <li>Erros — mensagens de falha
 *   <li>Formatos — padroes de data/hora
 *   <li>Configuracao — valores de configuracao padrao
 *   <li>Timezone — identificadores de fuso horario
 *   <li>Emojis — emojis reutilizados
 * </ul>
 */
public final class BotMessages {

    private BotMessages() {}

    // =========================================================================
    // TMDB / STREAMING
    // =========================================================================

    public static final String INDISPONIVEL = "Indisponivel no momento";
    public static final String N_A = "N/A";
    public static final String TBA = "TBA";

    // =========================================================================
    // COPA DO MUNDO
    // =========================================================================

    public static final String WORLD_CUP_DISABLED = "⛔ Servico de Copa desativado.";
    public static final String WORLD_CUP_NOT_AVAILABLE = "Servico de Copa nao disponivel";
    public static final String WORLD_CUP_FINISHED =
            "🏆 A Copa de 2026 ja acabou. Aguarde a proxima!";
    public static final String WORLD_CUP_NO_MATCHES_TODAY = "📭 Nenhum jogo programado para hoje.";
    public static final String WORLD_CUP_NO_MATCHES_DATE = "📭 Nenhum jogo encontrado para ";

    // =========================================================================
    // AUDIO / TRANSCRICAO
    // =========================================================================

    public static final String TRANSCRIPTION_DISABLED = "🔇 Transcricao desativada.";
    public static final String AUDIO_TOO_LARGE =
            "📂 O arquivo de audio excede %d MB. Envie um arquivo menor.";
    public static final String FALHA_TRANSCRICAO = "Falha na transcricao";
    public static final String ERRO_PROCESSAR_AUDIO_CALLBACK =
            "❌ Erro ao processar audio: "; // ← NOVO

    // =========================================================================
    // BOT / COMANDOS
    // =========================================================================

    public static final String COMANDO_NAO_RECONHECIDO =
            "❓ Comando nao reconhecido. Use /start para ajuda.";
    public static final String IDEIA_REGISTRADA = "✅ Ideia registrada! Obrigado pela contribuicao.";
    public static final String IDEIA_VAZIA = "❓ A ideia nao pode ficar vazia.";
    public static final String IDEIA_DIGITE_APOS_COMANDO =
            "❓ Digite a ideia apos o comando. Ex: `T1000 anotar ideia: fazer cafe`";
    public static final String BUSCA_TERM_CURTO = "🔍 O termo deve ter pelo menos 3 caracteres.";
    public static final String BUSCA_TERM_LONGO = "🔍 O termo e muito longo (max. 100 caracteres).";
    public static final String FILME_NAO_ENCONTRADO = "Filme nao encontrado";
    public static final String DATA_INVALIDA =
            "❓ Formato de data invalido. Use 'hoje', 'ontem', DD/MM.";
    public static final String DATA_INVALIDA_WORLDCUP =
            "❓ Data invalida. Use 'ontem', 'hoje' ou AAAA-MM-DD.";
    // =========================================================================
    // ERROS
    // =========================================================================

    public static final String RESPOSTA_INVALIDA = "Resposta invalida";
    public static final String FALHA_BUSCAR_DETALHES_FILME = "Falha ao buscar detalhes do filme";
    public static final String FALHA_BUSCAR_DETALHES_SERIE = "Falha ao buscar detalhes da serie";
    public static final String ERRO_PROCESSAR_AUDIO =
            "❌ Erro ao processar o audio. Tente novamente.";
    public static final String ERRO_GENERICO = "⚠️ Ocorreu um erro inesperado.";
    public static final String ERRO_LIMPAR_DADOS = "❌ Erro ao limpar dados: ";
    public static final String ERRO_LIMPAR_RELEASES = "❌ Erro ao limpar tabela: ";
    public static final String TOKEN_EXPIRADO = "Pedido expirado. Envie o audio novamente.";
    public static final String USUARIO_PRECISA_INICIAR_BOT =
            "⚠️ Usuario precisa iniciar conversa com o bot no privado para receber transcricoes.";
    public static final String CHAT_ID_INVALIDO = "Chat ID invalido: {}";

    // =========================================================================
    // FORMATOS DE DATA/HORA
    // =========================================================================

    public static final String FMT_HH_MM = "HH:mm";
    public static final String FMT_HH_MM_SS = "HH:mm:ss";
    public static final String FMT_YYYY_MM_DD = "yyyy-MM-dd";
    public static final String FMT_DD_MM = "dd/MM";
    public static final String FMT_DD_MM_YYYY_HH_MM = "dd/MM/yyyy HH:mm";
    public static final String FMT_DD_MM_YYYY = "dd/MM/yyyy";
    public static final String FMT_DD_MM_YYYY_HYPHEN = "dd-MM-yyyy";

    // =========================================================================
    // CONFIGURACAO — @Value defaults
    // =========================================================================

    public static final String DEFAULT_WORLDCUP_ENABLED = "${worldcup.enabled:false}";
    public static final String DEFAULT_BOT_ALLOWED_CHATS = "${bot.allowed-chats:}";
    public static final String DEFAULT_GROQ_CONNECT_TIMEOUT = "${groq.connect-timeout:5s}";
    public static final String DEFAULT_GROQ_READ_TIMEOUT = "${groq.read-timeout:30s}";
    public static final String DEFAULT_TELEGRAM_BOT_TOKEN = "${telegram.bot.token}";
    public static final String DEFAULT_TELEGRAM_MSG_LIMIT = "${telegram.message.limit:4000}";

    // =========================================================================
    // TIMEZONE
    // =========================================================================

    public static final String BRAZIL_ZONE = "America/Sao_Paulo";

    // =========================================================================
    // EMOJIS
    // =========================================================================

    public static final String GLOBE_EMOJI = "🌐";
}
