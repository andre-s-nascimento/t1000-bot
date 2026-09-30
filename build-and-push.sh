#!/usr/bin/env bash
set -euo pipefail

# ============================================================================
# build-and-push.sh
# ----------------------------------------------------------------------------
# Builda a imagem Docker localmente e faz push para o Docker Hub.
#
# Uso:
#   ./build-and-push.sh              # build + push
#   ./build-and-push.sh --no-cache   # sem cache de camadas
#   ./build-and-push.sh --no-push    # só build, sem push
#   ./build-and-push.sh --pull       # força pull da imagem base
#
# Requer:
#   - docker logado no Docker Hub (docker login)
#   - Dockerfile e certs/ presentes no repo
# ============================================================================

# Cores
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# ============================================================================
# CONFIGURAÇÕES
# ============================================================================
IMAGE_NAME="andresnascimento/t1000-bot"
LATEST_TAG="latest"
TIMESTAMP_TAG=$(date +%Y%m%d-%H%M%S)

# Plataforma alvo — detecta automaticamente pela arquitetura da máquina.
# Local e OCI são x86_64 → gera linux/amd64.
# Se quiser forçar: DOCKER_PLATFORM=linux/arm64 ./build-and-push.sh
ARCH=$(uname -m)
case "$ARCH" in
    x86_64)  DEFAULT_PLATFORM="linux/amd64" ;;
    aarch64|arm64) DEFAULT_PLATFORM="linux/arm64" ;;
    *)       DEFAULT_PLATFORM="" ;;  # deixa o Docker decidir
esac
PLATFORM="${DOCKER_PLATFORM:-$DEFAULT_PLATFORM}"

# Flags de build (opcionais via CLI)
BUILD_ARGS=()
PUSH=true

for arg in "$@"; do
    case "$arg" in
        --no-cache) BUILD_ARGS+=("--no-cache") ;;
        --no-push)  PUSH=false ;;
        --pull)     BUILD_ARGS+=("--pull") ;;
        *)
            log_error "Argumento desconhecido: $arg"
            echo "Uso: $0 [--no-cache] [--no-push] [--pull]"
            exit 1
            ;;
    esac
done

# ============================================================================
# VALIDAÇÕES
# ============================================================================
validate_environment() {
    if ! command -v docker &>/dev/null; then
        log_error "Docker não está instalado ou não está no PATH"
        exit 1
    fi

    if [ ! -f "Dockerfile" ]; then
        log_error "Dockerfile não encontrado em $(pwd)"
        exit 1
    fi

    if [ ! -f "certs/aiven-truststore.jks" ]; then
        log_warn "certs/aiven-truststore.jks não encontrado no build context."
        log_warn "O Dockerfile atual não copia esse arquivo (é montado em runtime),"
        log_warn "então o build deve prosseguir normalmente."
    fi
}

check_docker_login() {
    if ! docker info 2>/dev/null | grep -q "Username:"; then
        log_warn "Não está logado no Docker Hub."
        log_info "Executando docker login..."
        docker login || { log_error "Falha no login"; exit 1; }
    else
        log_info "✅ Já logado no Docker Hub"
    fi
}

# ============================================================================
# BUILD INFO
# ============================================================================
generate_build_info() {
    log_info "Gerando build-info.properties..."

    local build_date
    build_date=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
    local git_branch
    git_branch=$(git rev-parse --abbrev-ref HEAD)
    local git_commit
    git_commit=$(git rev-parse --short HEAD)

    mkdir -p src/main/resources
    cat > src/main/resources/build-info.properties <<EOF
build.branch=${git_branch}
build.commit=${git_commit}
build.time=${build_date}
EOF

    log_info "✅ build-info.properties: branch=${git_branch}, commit=${git_commit}"
}

extract_app_version() {
    local version
    version=$(grep -E "^version\s*=" build.gradle | sed -E "s/^version\s*=\s*['\"]([^'\"]+)['\"].*/\1/")
    if [ -z "$version" ]; then
        log_warn "Não foi possível extrair a versão do build.gradle"
        version="unknown"
    fi
    echo "$version"
}

# ============================================================================
# BUILD
# ============================================================================
build_image() {
    local release_tag="v$(extract_app_version)"

    log_info "🐳 Buildando imagem:"
    log_info "   - ${IMAGE_NAME}:${LATEST_TAG}"
    log_info "   - ${IMAGE_NAME}:${TIMESTAMP_TAG}"
    log_info "   - ${IMAGE_NAME}:${release_tag}"
    log_info "   Platform: ${PLATFORM:-<padrão do docker>}"

    local platform_args=()
    if [ -n "$PLATFORM" ]; then
        platform_args+=("--platform" "$PLATFORM")
    fi

    docker build \
        "${BUILD_ARGS[@]}" \
        "${platform_args[@]}" \
        --label "org.opencontainers.image.created=$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
        --label "org.opencontainers.image.revision=$(git rev-parse HEAD)" \
        --label "org.opencontainers.image.version=${release_tag}" \
        --label "org.opencontainers.image.source=https://github.com/andre-s-nascimento/t1000-bot" \
        --label "org.opencontainers.image.title=t1000-bot" \
        --label "org.opencontainers.image.url=https://github.com/andre-s-nascimento/t1000-bot" \
        -t "${IMAGE_NAME}:${LATEST_TAG}" \
        -t "${IMAGE_NAME}:${TIMESTAMP_TAG}" \
        -t "${IMAGE_NAME}:${release_tag}" \
        .

    log_info "✅ Imagem construída"
}

# ============================================================================
# PUSH
# ============================================================================
push_image() {
    local release_tag="v$(extract_app_version)"

    log_info "📤 Push da tag ${LATEST_TAG}..."
    docker push "${IMAGE_NAME}:${LATEST_TAG}"

    log_info "📤 Push da tag ${TIMESTAMP_TAG}..."
    docker push "${IMAGE_NAME}:${TIMESTAMP_TAG}"

    log_info "📤 Push da tag ${release_tag}..."
    docker push "${IMAGE_NAME}:${release_tag}"

    log_info "✅ Push concluído"
}

# ============================================================================
# RESUMO FINAL
# ============================================================================
print_summary() {
    local release_tag="v$(extract_app_version)"

    echo ""
    echo "════════════════════════════════════════════════════════════════"
    echo "✅ Build e push concluídos!"
    echo "════════════════════════════════════════════════════════════════"
    echo "📦 Tags disponíveis no Docker Hub:"
    echo "   - ${IMAGE_NAME}:${LATEST_TAG}"
    echo "   - ${IMAGE_NAME}:${TIMESTAMP_TAG}"
    echo "   - ${IMAGE_NAME}:${release_tag}"
    echo ""
    echo "🖥️  No servidor, execute:"
    echo "   ./deploy-oci.sh deploy"
    echo ""
    echo "📊 Tamanho da imagem:"
    docker images "${IMAGE_NAME}:${LATEST_TAG}" \
        --format "table {{.Repository}}:{{.Tag}}\t{{.Size}}\t{{.CreatedAt}}"
    echo "════════════════════════════════════════════════════════════════"
}

# ============================================================================
# MAIN
# ============================================================================
main() {
    echo "========================================="
    echo "🐳 Build and Push - ${IMAGE_NAME}"
    echo "========================================="

    validate_environment
    check_docker_login
    generate_build_info
    build_image

    if [ "$PUSH" = true ]; then
        push_image
    else
        log_warn "Push desabilitado (--no-push)"
    fi

    print_summary
}

main "$@"