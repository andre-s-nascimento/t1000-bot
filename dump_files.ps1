param([string]$Nome)

$DATA = Get-Date -Format "dd-MM-yyyy"
$SAIDA = "..\dump-${Nome}-${DATA}.txt"

$files = Get-ChildItem -Recurse -Include "*.java","*.sh","*.yml","*.properties","*.md","*.json","*.gradle" -File

$sb = [System.Text.StringBuilder]::new()

foreach ($file in $files) {
    $relativePath = $file.FullName.Replace((Get-Location).Path, ".").Replace("\", "/")
    
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
