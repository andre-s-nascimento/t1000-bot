#!/usr/bin/env pwsh

<#
.SYNOPSIS
    Chama o endpoint /admin/fala-t1000-tts para gerar áudio a partir de texto
.DESCRIPTION
    Envia um texto para sintetização de áudio e envia o áudio para o chat especificado
.PARAMETER Message
    Texto para sintetizar em áudio (obrigatório)
.PARAMETER ChatId
    ID do chat para receber o áudio (padrão: -1003703557250)
.PARAMETER BaseUrl
    URL base do servidor (padrão: http://localhost:8082)
.EXAMPLE
    .\fala-t1000-tts.ps1 -Message "Olá, este é um teste de áudio"
.EXAMPLE
    .\fala-t1000-tts.ps1 -Message "Olá" -ChatId -1003703557250
#>

param(
    [Parameter(Mandatory=$true)]
    [string]$Message,
    
    [string]$ChatId = "-1003703557250",
    
    [string]$BaseUrl = "http://localhost:8082"
)

# Constantes
$ENDPOINT = "/admin/fala-t1000-tts"

# ----------------------------------------------------------------
# Mostrar ajuda se solicitado
# ----------------------------------------------------------------
if ($Message -eq "--help" -or $Message -eq "-h") {
    @"
Uso: .\fala-t1000-tts.ps1 [OPÇÕES]

Obrigatório:
  -Message, -m   Texto para sintetizar em áudio (ex: "Olá, este é um teste")

Opcional:
  -ChatId, -c    ID do chat para receber o áudio (padrão: -1003703557250)
  -BaseUrl, -u   URL base do servidor (padrão: http://localhost:8082)
  -Help, -h      Mostra esta ajuda

Exemplo:
  .\fala-t1000-tts.ps1 -Message "Olá, este é um teste de áudio" -ChatId -1003703557250
"@
    exit 0
}

# ----------------------------------------------------------------
# URL encoding
# ----------------------------------------------------------------
function UrlEncode {
    param([string]$Text)
    return [System.Uri]::EscapeDataString($Text)
}

$MessageEncoded = UrlEncode -Text $Message
$ChatIdEncoded = UrlEncode -Text $ChatId

# Monta a URL final
$URL = "${BaseUrl}${ENDPOINT}?message=${MessageEncoded}&chatId=${ChatIdEncoded}"

# ----------------------------------------------------------------
# Executa a requisição
# ----------------------------------------------------------------
Write-Host "Gerando audio para:" -ForegroundColor Cyan
Write-Host "$Message" -ForegroundColor Yellow
Write-Host ""
Write-Host "Enviando para chat: $ChatId" -ForegroundColor Cyan
Write-Host ""

try {
    $response = Invoke-WebRequest -Uri $URL -Method Post -ContentType "application/x-www-form-urlencoded" -ErrorAction Stop -UseBasicParsing
    
    Write-Host "Status: $($response.StatusCode)" -ForegroundColor Green
    
    $contentType = $response.Headers["Content-Type"]
    if ($contentType -and $contentType.Contains("audio")) {
        Write-Host "Audio recebido! Tamanho: $($response.Content.Length) bytes" -ForegroundColor Cyan
        
        $outputFile = "audio-$(Get-Date -Format 'yyyyMMdd-HHmmss').mp3"
        [System.IO.File]::WriteAllBytes($outputFile, $response.Content)
        Write-Host "Audio salvo em: $outputFile" -ForegroundColor Green
    } else {
        Write-Host "Resposta: $($response.Content)" -ForegroundColor Gray
    }
} catch {
    Write-Host "Erro: $($_.Exception.Message)" -ForegroundColor Red
    if ($_.Exception.Response) {
        Write-Host "Status: $([int]$_.Exception.Response.StatusCode)" -ForegroundColor Red
    }
    exit 1
}