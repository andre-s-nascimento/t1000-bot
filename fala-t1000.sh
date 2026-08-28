#!/bin/bash

# ================================================================
# Script: fala-t1000.zsh
# Descrição: Chama o endpoint /admin/fala-t1000 para enviar mensagens via bot
# Uso: ./fala-t1000.zsh --message "John Connor bom eh John Connor morto" [--chat-id -100...] [--parse-mode HTML]
# ================================================================

BASE_URL="http://localhost:8082"
ENDPOINT="/admin/fala-t1000"

# Valores padrão (opcionais)
DEFAULT_CHAT_ID="-1003703557250"
DEFAULT_PARSE_MODE="HTML"

# ----------------------------------------------------------------
# Função de ajuda
# ----------------------------------------------------------------
function show_help() {
    cat <<EOF
Uso: $0 [OPÇÕES]

Obrigatório:
  --message, -m   Texto da mensagem (ex: "John Connor bom eh John Connor morto")

Opcional:
  --chat-id, -c   ID do chat (padrão: $DEFAULT_CHAT_ID)
  --parse-mode, -p Modo de parse: HTML (padrão) ou TEXT
  --help, -h      Mostra esta ajuda

Exemplo:
  $0 --message "John Connor bom eh John Connor morto" --chat-id -1003703557250 --parse-mode HTML
EOF
    exit 0
}

# ----------------------------------------------------------------
# Parse de argumentos
# ----------------------------------------------------------------
MESSAGE=""
CHAT_ID="$DEFAULT_CHAT_ID"
PARSE_MODE="$DEFAULT_PARSE_MODE"

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
        --parse-mode|-p)
            PARSE_MODE="$2"
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

# 🔥 Interpreta escapes como \n, \t, etc.
MESSAGE=$(echo -e "$MESSAGE")

# ----------------------------------------------------------------
# URL encoding (função portável)
# ----------------------------------------------------------------
function urlencode() {
    echo -n "$1" | jq -sRr @uri
}

MESSAGE_ENCODED=$(urlencode "$MESSAGE")
CHAT_ID_ENCODED=$(urlencode "$CHAT_ID")
PARSE_MODE_ENCODED=$(urlencode "$PARSE_MODE")

# Monta a URL final
URL="${BASE_URL}${ENDPOINT}?message=${MESSAGE_ENCODED}&chatId=${CHAT_ID_ENCODED}&parseMode=${PARSE_MODE_ENCODED}"

# ----------------------------------------------------------------
# Executa o curl
# ----------------------------------------------------------------
echo "📤 Enviando requisição para:"
echo "$URL"
echo

curl -X POST "$URL" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -w "\n✅ Status: %{http_code}\n"
