#!/bin/bash
set -e

# Cores
GREEN='\033[0;32m'
NC='\033[0m'

IMAGE_NAME="andresnascimento/t1000-bot"
VERSION_TAG=$(date +%Y%m%d-%H%M%S)
LATEST_TAG="latest"

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }

# 1. Login no Docker Hub (opcional, se já não estiver logado)
log_info "Fazendo login no Docker Hub (use suas credenciais)..."
docker login || { log_info "Login manual necessário. Execute 'docker login' depois."; exit 1; }

# Gerar arquivo de build info
BUILD_DATE=$(date +"%Y-%m-%d %H:%M:%S")
GIT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
GIT_COMMIT=$(git rev-parse --short HEAD)
mkdir -p src/main/resources
cat > src/main/resources/build-info.properties <<EOF
build.branch=${GIT_BRANCH}
build.commit=${GIT_COMMIT}
build.time=${BUILD_DATE}
EOF

log_info "Build info gerado: branch=${GIT_BRANCH}, commit=${GIT_COMMIT}, data=${BUILD_DATE}"

# 2. Build da imagem
log_info "Construindo imagem: ${IMAGE_NAME}:${LATEST_TAG}"
docker build -t "${IMAGE_NAME}:${LATEST_TAG}" -t "${IMAGE_NAME}:${VERSION_TAG}" .

# 3. Push das tags
log_info "Enviando tag ${VERSION_TAG}..."
docker push "${IMAGE_NAME}:${VERSION_TAG}"
log_info "Enviando tag latest..."
docker push "${IMAGE_NAME}:${LATEST_TAG}"

log_info "✅ Build e push concluídos!"
log_info "No servidor, execute: docker pull ${IMAGE_NAME}:latest && ./deploy.sh restart"