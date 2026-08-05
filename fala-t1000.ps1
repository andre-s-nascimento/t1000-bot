#!/usr/bin/env pwsh

<#
.SYNOPSIS
    Chama o endpoint /admin/fala-t1000 para enviar mensagens via bot
.DESCRIPTION
    Envia uma mensagem para o chat especificado usando o bot T1000
.PARAMETER Message
    Texto da mensagem (obrigatório)
.PARAMETER ChatId
    ID do chat (padrão: -1003703557250)
.PARAMETER ParseMode
    Modo de parse: HTML ou TEXT (padrão: HTML)
.PARAMETER BaseUrl
    URL base do servidor (padrão: http://localhost:8082)
.EXAMPLE
    .\fala-t1000.ps1 -Message "John Connor bom eh John Connor morto"
.EXAMPLE
    .\fala-t1000.ps1 -Message "Olá" -ChatId -1003703557250 -ParseMode HTML
#>

param(
    [Parameter(Mandatory=$true)]
    [string]$Message,
    
    [string]$ChatId = "-1003703557250",
    
    [ValidateSet("HTML", "TEXT")]
    [string]$ParseMode = "HTML",
    
    [string]$BaseUrl = "http://localhost:8082"
)

# Constantes
$ENDPOINT = "/admin/fala-t1000"

# ----------------------------------------------------------------
# Mostrar ajuda se solicitado
# ----------------------------------------------------------------
if ($Message -eq "--help" -or $Message -eq "-h") {
    @"
Uso: .\fala-t1000.ps1 [OPÇÕES]

Obrigatório:
  -Message, -m   Texto da mensagem (ex: "John Connor bom eh John Connor morto")

Opcional:
  -ChatId, -c    ID do chat (padrão: -1003703557250)
  -ParseMode, -p Modo de parse: HTML (padrão) ou TEXT
  -BaseUrl, -u   URL base do servidor (padrão: http://localhost:8082)
  -Help, -h      Mostra esta ajuda

Exemplo:
  .\fala-t1000.ps1 -Message "John Connor bom eh John Connor morto" -ChatId -1003703557250 -ParseMode HTML
"@
    exit 0
}

# ----------------------------------------------------------------
# Processa a mensagem (interpreta escapes como \n, \t, etc.)
# ----------------------------------------------------------------
# PowerShell já interpreta escapes em strings, mas para manter compatibilidade:
$ProcessedMessage = $Message -replace '\\n', "`n" -replace '\\t', "`t" -replace '\\r', "`r"

# ----------------------------------------------------------------
# URL encoding
# ----------------------------------------------------------------
function UrlEncode {
    param([string]$Text)
    
    # Usa [System.Uri]::EscapeDataString para encoding
    return [System.Uri]::EscapeDataString($Text)
}

$MessageEncoded = UrlEncode -Text $ProcessedMessage
$ChatIdEncoded = UrlEncode -Text $ChatId
$ParseModeEncoded = UrlEncode -Text $ParseMode

# Monta a URL final
$URL = "${BaseUrl}${ENDPOINT}?message=${MessageEncoded}&chatId=${ChatIdEncoded}&parseMode=${ParseModeEncoded}"

# ----------------------------------------------------------------
# Executa a requisição
# ----------------------------------------------------------------
Write-Host "📤 Enviando requisição para:" -ForegroundColor Cyan
Write-Host "$URL" -ForegroundColor Yellow
Write-Host ""

try {
    $response = Invoke-WebRequest -Uri $URL -Method Post -ContentType "application/x-www-form-urlencoded" -ErrorAction Stop
    
    Write-Host "✅ Status: $($response.StatusCode)" -ForegroundColor Green
    Write-Host "📝 Resposta: $($response.Content)" -ForegroundColor Gray
    
} catch {
    Write-Host "❌ Erro: $($_.Exception.Message)" -ForegroundColor Red
    if ($_.Exception.Response) {
        Write-Host "   Status: $([int]$_.Exception.Response.StatusCode)" -ForegroundColor Red
    }
    exit 1
}