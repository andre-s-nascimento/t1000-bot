param([string]$Nome)

if ([string]::IsNullOrEmpty($Nome)) {
    Write-Host "Uso: .\dump_files.ps1 <nome>" -ForegroundColor Red
    exit 1
}

$DATA = Get-Date -Format "dd-MM-yyyy"
$SAIDA = "C:\dev\projetos\java\dump-${Nome}-${DATA}.txt"

# Usa caminho absoluto
$basePath = "C:\dev\projetos\java\t1000"
Set-Location $basePath

Write-Host "Procurando arquivos em: $basePath" -ForegroundColor Cyan

# Busca arquivos
$files = @()
$files += Get-ChildItem -Path $basePath -Recurse -Filter "*.java" -File
$files += Get-ChildItem -Path $basePath -Recurse -Filter "*.sh" -File
$files += Get-ChildItem -Path $basePath -Recurse -Filter "*.yml" -File
$files += Get-ChildItem -Path $basePath -Recurse -Filter "*.properties" -File
$files += Get-ChildItem -Path $basePath -Recurse -Filter "*.md" -File
$files += Get-ChildItem -Path $basePath -Recurse -Filter "*.json" -File
$files += Get-ChildItem -Path $basePath -Recurse -Filter "*.gradle" -File
$files += Get-ChildItem -Path $basePath -Recurse -Filter "Dockerfile" -File

# Remove duplicatas
$files = $files | Sort-Object FullName -Unique

Write-Host "Encontrados $($files.Count) arquivos." -ForegroundColor Cyan

if ($files.Count -eq 0) {
    Write-Host "Nenhum arquivo encontrado!" -ForegroundColor Red
    exit 1
}

$sb = [System.Text.StringBuilder]::new()

foreach ($file in $files) {
    $relativePath = $file.FullName.Replace($basePath, ".").Replace("\", "/")
    
    [void]$sb.AppendLine("")
    [void]$sb.AppendLine("/**********************************************************")
    [void]$sb.AppendLine(" * ARQUIVO: $relativePath")
    [void]$sb.AppendLine(" **********************************************************/")
    [void]$sb.AppendLine("")
    
    try {
        $content = Get-Content -Path $file.FullName -Raw -ErrorAction Stop
        [void]$sb.AppendLine($content)
    } catch {
        [void]$sb.AppendLine("// ERRO AO LER ARQUIVO: $_")
    }
}

$sb.ToString() | Out-File -FilePath $SAIDA -Encoding UTF8
Write-Host "Arquivo gerado: $SAIDA" -ForegroundColor Green
Write-Host "Tamanho: $((Get-Item $SAIDA).Length) bytes" -ForegroundColor Yellow
