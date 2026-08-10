#!/usr/bin/env pwsh

<#
.SYNOPSIS
    Script para build e push de imagem Docker para o Docker Hub com timestamp e output em tempo real
#>

$ErrorActionPreference = "Continue"

# Força o BuildKit a exibir progresso em texto simples
$env:DOCKER_BUILDKIT = "1"

# ============================================================
# Configurações de Cores e Logging com Timestamp
# ============================================================
function Get-TimeStamp {
    return Get-Date -Format "HH:mm:ss"
}

function Write-Success {
    param([string]$Message)
    $ts = Get-TimeStamp
    Write-Host "$ts-[SUCESSO]" -ForegroundColor Green -NoNewline
    Write-Host " $Message"
}

function Write-ErrorMsg {
    param([string]$Message)
    $ts = Get-TimeStamp
    Write-Host "$ts-[ERRO]" -ForegroundColor Red -NoNewline
    Write-Host " $Message"
}

function Write-Info {
    param([string]$Message)
    $ts = Get-TimeStamp
    Write-Host "$ts-[INFO]" -ForegroundColor Cyan -NoNewline
    Write-Host " $Message"
}

function Write-Warning {
    param([string]$Message)
    $ts = Get-TimeStamp
    Write-Host "$ts-[AVISO]" -ForegroundColor Yellow -NoNewline
    Write-Host " $Message"
}

function Write-Step {
    param([string]$Message)
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Magenta
    Write-Host " $Message" -ForegroundColor Magenta
    Write-Host "========================================" -ForegroundColor Magenta
}

# ============================================================
# Configurações
# ============================================================
$IMAGE_NAME = "andresnascimento/t1000-bot"
$VERSION_TAG = Get-Date -Format "yyyyMMdd-HHmmss"
$LATEST_TAG = "latest"
$MAX_RETRIES = 3
$RETRY_DELAY = 5

# ============================================================
# Funções de Utilidade
# ============================================================
function Test-DockerInstalled {
    try {
        $dockerPath = (Get-Command docker -ErrorAction SilentlyContinue).Source
        if ($dockerPath) {
            return $true
        }
        
        $commonPaths = @(
            "C:\Program Files\Docker\Docker\resources\bin\docker.exe",
            "$env:USERPROFILE\AppData\Local\Programs\Docker\Docker\resources\bin\docker.exe",
            "C:\Program Files\Docker\Docker\docker.exe"
        )
        
        foreach ($path in $commonPaths) {
            if (Test-Path $path) {
                $dockerDir = Split-Path $path
                $env:Path += ";$dockerDir"
                return $true
            }
        }
        return $false
    } catch {
        return $false
    }
}

function Start-DockerDesktop {
    Write-Info "Tentando iniciar o Docker Desktop..."
    
    $dockerPaths = @(
        "C:\Program Files\Docker\Docker\Docker Desktop.exe",
        "C:\Program Files\Docker\Docker\Docker.exe",
        "$env:USERPROFILE\AppData\Local\Programs\Docker\Docker\Docker Desktop.exe"
    )
    
    $started = $false
    foreach ($path in $dockerPaths) {
        if (Test-Path $path) {
            Write-Info "Iniciando Docker Desktop: $path"
            try {
                Start-Process -FilePath $path -WindowStyle Minimized
                $started = $true
                break
            } catch {
                Write-Warning "Falha ao iniciar: $path"
            }
        }
    }
    
    if (-not $started) {
        try {
            $service = Get-Service -Name "com.docker.service" -ErrorAction SilentlyContinue
            if ($service) {
                Write-Info "Iniciando serviço do Docker..."
                Start-Service -Name "com.docker.service"
                $started = $true
            }
        } catch {
            Write-Warning "Não foi possível iniciar o serviço do Docker"
        }
    }
    
    return $started
}

function Test-DockerDaemon {
    param([int]$TimeoutSeconds = 60)
    
    Write-Info "Verificando conexão com o daemon do Docker..."
    
    $startTime = Get-Date
    $attempt = 0
    
    do {
        $attempt++
        try {
            $result = docker version --format '{{.Server.Version}}' 2>&1
            
            if ($LASTEXITCODE -eq 0) {
                Write-Success "Docker daemon conectado! Versão: $result"
                return $true
            }
        } catch {
        }
        
        if ($attempt -lt $MAX_RETRIES) {
            Write-Warning "Tentativa $attempt falhou. Aguardando $RETRY_DELAY segundos..."
            Start-Sleep -Seconds $RETRY_DELAY
        }
        
        $elapsed = (Get-Date) - $startTime
    } while ($attempt -lt $MAX_RETRIES -and $elapsed.TotalSeconds -lt $TimeoutSeconds)
    
    return $false
}

function Wait-ForDocker {
    param([int]$MaxWaitSeconds = 90)
    
    Write-Info "Aguardando o Docker iniciar (máximo $MaxWaitSeconds segundos)..."
    
    $waited = 0
    while ($waited -lt $MaxWaitSeconds) {
        try {
            docker version 2>$null | Out-Null
            if ($LASTEXITCODE -eq 0) {
                Write-Success "Docker está pronto!"
                return $true
            }
        } catch {
        }
        
        if ($waited % 10 -eq 0 -and $waited -gt 0) {
            Write-Info "Aguardando... ($waited/$MaxWaitSeconds segundos)"
        }
        
        Start-Sleep -Seconds 2
        $waited += 2
    }
    
    return $false
}

function Ensure-DockerRunning {
    Write-Step "VERIFICANDO DOCKER"
    
    if (-not (Test-DockerInstalled)) {
        Write-ErrorMsg @"
Docker não encontrado!
"@
        return $false
    }
    Write-Success "Docker instalado"
    
    if (Test-DockerDaemon -TimeoutSeconds 10) {
        Write-Success "Docker já está rodando"
        return $true
    }
    
    Write-Warning "Docker não está rodando. Tentando iniciar..."
    
    if (Start-DockerDesktop) {
        if (Wait-ForDocker -MaxWaitSeconds 90) {
            Write-Success "Docker iniciado com sucesso!"
            return $true
        }
    }
    
    Write-Info "Verificando serviços do Docker..."
    try {
        $services = Get-Service -Name "*docker*" -ErrorAction SilentlyContinue
        foreach ($svc in $services) {
            if ($svc.Status -eq 'Stopped') {
                Write-Info "Iniciando serviço: $($svc.DisplayName)"
                Start-Service -Name $svc.Name -ErrorAction SilentlyContinue
            }
        }
        
        if (Wait-ForDocker -MaxWaitSeconds 30) {
            Write-Success "Docker iniciado via serviço!"
            return $true
        }
    } catch {
        Write-Warning "Erro ao verificar serviços: $_"
    }
    
    return $false
}

function Test-DockerLogin {
    $configPath = Join-Path $env:USERPROFILE ".docker\config.json"
    if (Test-Path $configPath) {
        try {
            $config = Get-Content $configPath -Raw | ConvertFrom-Json
            if ($config.auths -or $config.credsStore -or $config.credHelpers) {
                Write-Success "Credenciais de login encontradas no Docker Config."
                return $true
            }
        } catch {
        }
    }

    try {
        $info = docker info 2>$null
        if ($info -match "Username") {
            Write-Success "Logado no Docker Hub."
            return $true
        }
    } catch {
    }

    return $false
}

function Generate-BuildInfo {
    Write-Step "GERANDO INFORMAÇÕES DE BUILD"
    
    $BUILD_DATE = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    
    $GIT_BRANCH = "unknown"
    $GIT_COMMIT = "unknown"
    
    try {
        $GIT_BRANCH = git rev-parse --abbrev-ref HEAD 2>$null
        if (-not $GIT_BRANCH) { $GIT_BRANCH = "unknown" }
        
        $GIT_COMMIT = git rev-parse --short HEAD 2>$null
        if (-not $GIT_COMMIT) { $GIT_COMMIT = "unknown" }
    } catch {
        Write-Warning "Não foi possível obter informações do Git"
    }
    
    $buildInfoDir = "src/main/resources"
    if (-not (Test-Path $buildInfoDir)) {
        New-Item -ItemType Directory -Path $buildInfoDir -Force | Out-Null
        Write-Info "Diretório criado: $buildInfoDir"
    }
    
    $buildInfoContent = @"
build.branch=${GIT_BRANCH}
build.commit=${GIT_COMMIT}
build.time=${BUILD_DATE}
build.version=${VERSION_TAG}
"@
    
    $buildInfoPath = Join-Path $buildInfoDir "build-info.properties"
    $buildInfoContent | Out-File -FilePath $buildInfoPath -Encoding UTF8
    
    Write-Success "Build info gerado:"
    Write-Info "   Branch: $GIT_BRANCH"
    Write-Info "   Commit: $GIT_COMMIT"
    Write-Info "   Data: $BUILD_DATE"
    Write-Info "   Versão: $VERSION_TAG"
}

function Build-DockerImage {
    Write-Step "CONSTRUINDO IMAGEM DOCKER"
    
    Write-Info "Imagem: ${IMAGE_NAME}"
    Write-Info "Tags: ${LATEST_TAG}, ${VERSION_TAG}"
    
    $nowStr = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
    
    Write-Info "Executando: docker build --progress=plain -t ${IMAGE_NAME}:${LATEST_TAG} -t ${IMAGE_NAME}:${VERSION_TAG} --build-arg BUILD_VERSION=${VERSION_TAG} --build-arg ""BUILD_TIME=$nowStr"" ."
    Write-Host ""
    
    # Chamada nativa sem Start-Process para repassar os argumentos com espaço perfeitamente
    & docker build --progress=plain -t "${IMAGE_NAME}:${LATEST_TAG}" -t "${IMAGE_NAME}:${VERSION_TAG}" --build-arg "BUILD_VERSION=${VERSION_TAG}" --build-arg "BUILD_TIME=$nowStr" .
    
    if ($LASTEXITCODE -ne 0) {
        Write-ErrorMsg "Falha no build da imagem!"
        return $false
    }
    
    Write-Success "Imagem construída com sucesso!"
    return $true
}

function Push-DockerImage {
    param([string]$Tag)
    
    Write-Info "Enviando tag: $Tag"
    
    $maxRetries = 3
    $retryCount = 0
    
    do {
        & docker push "${IMAGE_NAME}:${Tag}"
        
        if ($LASTEXITCODE -eq 0) {
            Write-Success "Push da tag $Tag concluído!"
            return $true
        }
        
        $retryCount++
        if ($retryCount -lt $maxRetries) {
            Write-Warning "Falha no push (tentativa $retryCount/$maxRetries). Tentando novamente em 5 segundos..."
            Start-Sleep -Seconds 5
        }
    } while ($retryCount -lt $maxRetries)
    
    Write-ErrorMsg "Falha ao enviar tag $Tag após $maxRetries tentativas"
    return $false
}

# ============================================================
# SCRIPT PRINCIPAL
# ============================================================
Write-Host ""
Write-Host "╔═══════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║       BUILD AND PUSH DOCKER IMAGE                 ║" -ForegroundColor Cyan
Write-Host "╚═══════════════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

# 1. Verifica Docker
if (-not (Ensure-DockerRunning)) {
    exit 1
}

# 2. Login no Docker Hub
Write-Step "LOGIN NO DOCKER HUB"

if (-not (Test-DockerLogin)) {
    Write-Info "Fazendo login no Docker Hub..."
    docker login
    
    if ($LASTEXITCODE -ne 0) {
        Write-ErrorMsg "Falha no login!"
        exit 1
    }
}

# 3. Gera informações de build
Generate-BuildInfo

# 4. Build da imagem
if (-not (Build-DockerImage)) {
    exit 1
}

# 5. Push das tags
Write-Step "ENVIANDO IMAGENS PARA O DOCKER HUB"

$pushSuccess = $true

if (-not (Push-DockerImage -Tag $VERSION_TAG)) {
    $pushSuccess = $false
}

if (-not (Push-DockerImage -Tag $LATEST_TAG)) {
    $pushSuccess = $false
}

# 6. Conclusão
Write-Step "CONCLUSÃO"

if ($pushSuccess) {
    Write-Host ""
    Write-Host "✅ BUILD E PUSH CONCLUÍDOS COM SUCESSO!" -ForegroundColor Green
    Write-Host ""
    Write-Info "Imagem: $IMAGE_NAME"
    Write-Info "Tags: $LATEST_TAG, $VERSION_TAG"
    Write-Host ""
    Write-Info "No servidor, execute:"
    Write-Host "  docker pull ${IMAGE_NAME}:latest" -ForegroundColor Yellow
    Write-Host "  ./deploy.sh restart" -ForegroundColor Yellow
    Write-Host ""
    exit 0
} else {
    Write-Host ""
    Write-Host "❌ BUILD OU PUSH FALHOU" -ForegroundColor Red
    Write-Host ""
    Write-Info "Verifique os erros acima e tente novamente."
    Write-Host ""
    exit 1
}
