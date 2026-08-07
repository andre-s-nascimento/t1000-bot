#!/usr/bin/env pwsh

<#
.SYNOPSIS
    Script para build e push de imagem Docker para o Docker Hub
#>

$ErrorActionPreference = "Stop"

# Cores
$GREEN = "`e[0;32m"
$RED = "`e[0;31m"
$YELLOW = "`e[0;33m"
$NC = "`e[0m"

$IMAGE_NAME = "andresnascimento/t1000-bot"
$VERSION_TAG = Get-Date -Format "yyyyMMdd-HHmmss"
$LATEST_TAG = "latest"

function Log-Info {
    param([string]$Message)
    Write-Host "$GREEN[INFO]$NC $Message"
}

function Log-Error {
    param([string]$Message)
    Write-Host "$RED[ERRO]$NC $Message"
    exit 1
}

function Log-Warning {
    param([string]$Message)
    Write-Host "$YELLOW[AVISO]$NC $Message"
}

# ============================================================
# 0. Verificar se o Docker está disponível
# ============================================================
Log-Info "Verificando instalação do Docker..."

# Tenta encontrar o Docker no PATH
$dockerPath = (Get-Command docker -ErrorAction SilentlyContinue).Source

if (-not $dockerPath) {
    Log-Warning "Docker não encontrado no PATH!"
    Log-Warning "Verificando locais comuns de instalação..."
    
    $possiblePaths = @(
        "C:\Program Files\Docker\Docker\resources\bin\docker.exe",
        "C:\Program Files\Docker\Docker\resources\bin\docker",
        "$env:USERPROFILE\AppData\Local\Programs\Docker\Docker\resources\bin\docker.exe"
    )
    
    foreach ($path in $possiblePaths) {
        if (Test-Path $path) {
            $dockerPath = $path
            Log-Info "Docker encontrado em: $dockerPath"
            # Adiciona ao PATH para esta sessão
            $dockerDir = Split-Path $dockerPath
            $env:Path += ";$dockerDir"
            break
        }
    }
    
    if (-not $dockerPath) {
        Log-Error @"
Docker não encontrado!

Para instalar o Docker Desktop no Windows:

1. Via Winget (recomendado):
   winget install Docker.DockerDesktop

2. Baixar manualmente:
   https://www.docker.com/products/docker-desktop/

3. Via Chocolatey:
   choco install docker-desktop

Após a instalação, reinicie o PowerShell e execute este script novamente.
"@
    }
}

# Verifica se o Docker está rodando
Log-Info "Verificando se o Docker Desktop está rodando..."
try {
    docker info 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Log-Warning "Docker Desktop não está rodando!"
        Log-Info "Iniciando Docker Desktop..."
        Start-Process "C:\Program Files\Docker\Docker\Docker Desktop.exe" -ErrorAction SilentlyContinue
        Log-Info "Aguardando o Docker iniciar (30 segundos)..."
        Start-Sleep -Seconds 30
        
        # Verifica novamente
        docker info 2>$null | Out-Null
        if ($LASTEXITCODE -ne 0) {
            Log-Error "Não foi possível iniciar o Docker. Inicie manualmente e tente novamente."
        }
    }
} catch {
    Log-Warning "Não foi possível verificar o status do Docker."
}

Log-Info "✅ Docker está pronto para uso!"

# ============================================================
# 1. Login no Docker Hub
# ============================================================
Log-Info "Fazendo login no Docker Hub (use suas credenciais)..."

# Verifica se já está logado
$loggedIn = docker info 2>$null | Select-String "Username"
if (-not $loggedIn) {
    docker login
    if ($LASTEXITCODE -ne 0) {
        Log-Error "Falha no login. Execute 'docker login' manualmente."
    }
} else {
    Log-Info "✅ Já está logado no Docker Hub."
}

# ============================================================
# 2. Gerar arquivo de build info
# ============================================================
$BUILD_DATE = Get-Date -Format "yyyy-MM-dd HH:mm:ss"

# Verifica se está em um repositório Git
try {
    $GIT_BRANCH = git rev-parse --abbrev-ref HEAD 2>$null
    $GIT_COMMIT = git rev-parse --short HEAD 2>$null
} catch {
    $GIT_BRANCH = "unknown"
    $GIT_COMMIT = "unknown"
}

# Criar diretório se não existir
$buildInfoDir = "src/main/resources"
if (-not (Test-Path $buildInfoDir)) {
    New-Item -ItemType Directory -Path $buildInfoDir -Force | Out-Null
}

# Criar arquivo de propriedades
$buildInfoContent = @"
build.branch=${GIT_BRANCH}
build.commit=${GIT_COMMIT}
build.time=${BUILD_DATE}
"@

$buildInfoPath = Join-Path $buildInfoDir "build-info.properties"
$buildInfoContent | Out-File -FilePath $buildInfoPath -Encoding UTF8

Log-Info "Build info gerado: branch=${GIT_BRANCH}, commit=${GIT_COMMIT}, data=${BUILD_DATE}"

# ============================================================
# 3. Build da imagem Docker
# ============================================================
Log-Info "Construindo imagem: ${IMAGE_NAME}:${LATEST_TAG}"

docker build -t "${IMAGE_NAME}:${LATEST_TAG}" -t "${IMAGE_NAME}:${VERSION_TAG}" .

if ($LASTEXITCODE -ne 0) {
    Log-Error "Falha no build da imagem Docker!"
}

# ============================================================
# 4. Push das tags
# ============================================================
Log-Info "Enviando tag ${VERSION_TAG}..."
docker push "${IMAGE_NAME}:${VERSION_TAG}"

if ($LASTEXITCODE -ne 0) {
    Log-Error "Falha no push da tag ${VERSION_TAG}!"
}

Log-Info "Enviando tag latest..."
docker push "${IMAGE_NAME}:${LATEST_TAG}"

if ($LASTEXITCODE -ne 0) {
    Log-Error "Falha no push da tag latest!"
}

# ============================================================
# 5. Conclusão
# ============================================================
Log-Info "✅ Build e push concluídos!"
Log-Info "No servidor, execute: docker pull ${IMAGE_NAME}:latest && ./deploy.sh restart"

exit 0