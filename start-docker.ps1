# start-docker.ps1
# Script para iniciar o Docker Desktop

Write-Host "Iniciando Docker Desktop..." -ForegroundColor Yellow

# Caminhos possíveis do Docker Desktop
$dockerPaths = @(
    "C:\Program Files\Docker\Docker\Docker Desktop.exe",
    "C:\Program Files\Docker\Docker\Docker.exe",
    "$env:USERPROFILE\AppData\Local\Programs\Docker\Docker\Docker Desktop.exe"
)

$found = $false
foreach ($path in $dockerPaths) {
    if (Test-Path $path) {
        Write-Host "Docker encontrado em: $path" -ForegroundColor Green
        Start-Process -FilePath $path -WindowStyle Normal
        $found = $true
        break
    }
}

if (-not $found) {
    Write-Host "Docker Desktop não encontrado!" -ForegroundColor Red
    Write-Host "Instale via: winget install Docker.DockerDesktop" -ForegroundColor Yellow
    exit 1
}

Write-Host "Aguardando o Docker inicializar..." -ForegroundColor Yellow

# Aguarda até 60 segundos para o Docker ficar pronto
for ($i = 1; $i -le 12; $i++) {
    Start-Sleep -Seconds 5
    try {
        $result = docker info 2>&1
        if ($LASTEXITCODE -eq 0) {
            Write-Host "✅ Docker está rodando!" -ForegroundColor Green
            exit 0
        }
    } catch {
        # Ignora erro
    }
    Write-Host "Aguardando... ($i/12)" -ForegroundColor Yellow
}

Write-Host "❌ Docker não iniciou. Tente iniciar manualmente." -ForegroundColor Red
exit 1