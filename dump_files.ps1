#!/usr/bin/env pwsh

<#
.SYNOPSIS
    Gera um dump de todos os arquivos de código-fonte do projeto, excluindo pastas desnecessárias
.DESCRIPTION
    Cria um arquivo .txt com o conteúdo de todos os arquivos .java, .html, .css, .js, .yml e .properties,
    excluindo pastas como build, .gradle, .git, etc.
.PARAMETER Nome
    Nome para identificar o dump (será usado no nome do arquivo)
.EXAMPLE
    .\dump_files.ps1 -Nome "meu-projeto"
.EXAMPLE
    .\dump_files.ps1 "meu-projeto"
#>

param(
    [Parameter(Mandatory=$true, Position=0)]
    [string]$Nome
)

# Verifica se o nome foi passado
if ([string]::IsNullOrEmpty($Nome)) {
    Write-Host "❌ Erro: Forneça um nome. Uso: .\dump_files.ps1 <nome>" -ForegroundColor Red
    exit 1
}

# Configurações
$DATA = Get-Date -Format "dd-MM-yyyy"
$SAIDA = "..\dump-${Nome}-${DATA}.txt"

# Pastas para excluir
$excludeFolders = @(
    "build",
    ".gradle",
    ".git",
    ".vscode",
    "temp*",
    "gradle",
    "bin",
    "node_modules"
)

# Extensões para incluir
$includeExtensions = @(
    "*.java",
    "*.html",
    "*.css",
    "*.js",
    "*.yml",
    "*.properties"
)

# Função para verificar se uma pasta deve ser excluída
function Should-Exclude {
    param([string]$Path)
    
    foreach ($folder in $excludeFolders) {
        # Se for "temp*", usa wildcard
        if ($folder -like "*temp*" -or $folder -like "*\*") {
            if ($Path -match $folder) {
                return $true
            }
        } else {
            # Verifica se o caminho contém a pasta exata
            if ($Path -match "(^|\\|\/)${folder}($|\\|\/)") {
                return $true
            }
        }
    }
    return $false
}

# Função para verificar se um arquivo deve ser incluído
function Should-Include {
    param([string]$FilePath)
    
    foreach ($ext in $includeExtensions) {
        if ($FilePath -like $ext) {
            return $true
        }
    }
    return $false
}

# Coleta todos os arquivos
Write-Host "🔍 Coletando arquivos..." -ForegroundColor Cyan

# Usa Get-ChildItem recursivamente
$files = Get-ChildItem -Recurse -File -ErrorAction SilentlyContinue | Where-Object {
    $relativePath = $_.FullName
    $isExcluded = Should-Exclude -Path $relativePath
    
    if (-not $isExcluded) {
        $include = Should-Include -FilePath $_.Name
        return $include
    }
    return $false
} | Sort-Object FullName

# Verifica se encontrou arquivos
if ($files.Count -eq 0) {
    Write-Host "⚠️ Nenhum arquivo encontrado para processar." -ForegroundColor Yellow
    exit 1
}

Write-Host "📄 Encontrados $($files.Count) arquivos." -ForegroundColor Cyan

# Gera o arquivo de saída
Write-Host "📝 Gerando arquivo: $SAIDA" -ForegroundColor Cyan

# Usa um StringBuilder para performance
$sb = [System.Text.StringBuilder]::new()

foreach ($file in $files) {
    # Obtém o caminho relativo a partir do diretório atual
    $relativePath = $file.FullName
    $currentDir = Get-Location
    $relativePath = $relativePath.Replace($currentDir.Path, ".").Replace("\", "/")
    
    # Adiciona cabeçalho
    [void]$sb.AppendLine("")
    [void]$sb.AppendLine("")
    [void]$sb.AppendLine("/**********************************************************")
    [void]$sb.AppendLine(" * ARQUIVO: $relativePath")
    [void]$sb.AppendLine(" **********************************************************/")
    [void]$sb.AppendLine("")
    
    # Lê o conteúdo do arquivo
    try {
        $content = Get-Content -Path $file.FullName -Raw -ErrorAction Stop
        [void]$sb.AppendLine($content)
    } catch {
        [void]$sb.AppendLine("// ERRO AO LER ARQUIVO: $_")
    }
}

# Escreve o arquivo
$sb.ToString() | Out-File -FilePath $SAIDA -Encoding UTF8

Write-Host "✅ Filtro aplicado! Arquivo gerado: $SAIDA" -ForegroundColor Green