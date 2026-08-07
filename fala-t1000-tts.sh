#!/bin/bash

# ================================================================
# Script: gerar-audio.sh
# Descrição: Chama o endpoint /admin/test-azure-tts para gerar áudio a partir de texto
# Uso: ./gerar-audio.sh --message "Texto para síntese" [--chat-id -100...]
# ================================================================

BASE_URL="http://localhost:8082"
ENDPOINT="/admin/fala-t1000-tts"

# Valores padrão
DEFAULT_CHAT_ID="-1003703557250"

# ----------------------------------------------------------------
# Função de ajuda
# ----------------------------------------------------------------
function show_help() {
    cat <<EOF
Uso: $0 [OPÇÕES]

Obrigatório:
  --message, -m   Texto para sintetizar em áudio (ex: "Olá, este é um teste")

Opcional:
  --chat-id, -c   ID do chat para receber o áudio (padrão: $DEFAULT_CHAT_ID)
  --help, -h      Mostra esta ajuda

Exemplo:
  $0 --message "Olá, este é um teste de áudio" --chat-id -1003703557250
EOF
    exit 0
}

# ----------------------------------------------------------------
# Parse de argumentos
# ----------------------------------------------------------------
MESSAGE=""
CHAT_ID="$DEFAULT_CHAT_ID"

while [[ $# -gt 0 ]]; do
    case $1 in
        --message|-m)
            MESSAGE="$2"
            shift 2
            ;;
        --chat-id|-c)
            CHAT_ID="$2"
            shift 2
            ;;
        --help|-h)
            show_help
            ;;
        *)
            echo "❌ Argumento desconhecido: $1"
            show_help
            ;;
    esac
done

# Verifica se a mensagem foi fornecida
if [[ -z "$MESSAGE" ]]; then
    echo "❌ Erro: Mensagem é obrigatória. Use --message."
    show_help
fi

# ----------------------------------------------------------------
# URL encoding (portável com jq)
# ----------------------------------------------------------------
function urlencode() {
    echo -n "$1" | jq -sRr @uri
}

MESSAGE_ENCODED=$(urlencode "$MESSAGE")
CHAT_ID_ENCODED=$(urlencode "$CHAT_ID")

# Monta a URL final
URL="${BASE_URL}${ENDPOINT}?message=${MESSAGE_ENCODED}&chatId=${CHAT_ID_ENCODED}"

# ----------------------------------------------------------------
# Executa o curl
# ----------------------------------------------------------------
echo "🎤 Gerando áudio para:"
echo "$MESSAGE"
echo
echo "📤 Enviando para chat: $CHAT_ID"
echo

curl -X POST "$URL" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -w "\n✅ Status: %{http_code}\n"