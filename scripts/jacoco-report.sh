#!/usr/bin/env bash
#
# jacoco-report.sh
#
# Gera o relatório de cobertura JaCoCo e exibe um resumo por pacote.
#
# Uso:
#   ./scripts/jacoco-report.sh           # roda test + jacoco e mostra resumo
#   ./scripts/jacoco-report.sh --html    # abre o HTML no navegador
#   ./scripts/jacoco-report.sh --json    # imprime resumo em JSON
#
# Pré-requisitos:
#   - python3 (para o parser do XML)
#   - ./gradlew funcional
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
JACOCO_XML="${PROJECT_ROOT}/build/reports/jacoco/test/jacocoTestReport.xml"
JACOCO_HTML="${PROJECT_ROOT}/build/reports/jacoco/test/html/index.html"

cd "${PROJECT_ROOT}"

echo "🔨 Rodando testes + JaCoCo..."
./gradlew clean test jacocoTestReport --console=plain

if [[ ! -f "${JACOCO_XML}" ]]; then
  echo "❌ Relatório XML não encontrado em ${JACOCO_XML}"
  exit 1
fi

MODE="${1:-}"

if [[ "${MODE}" == "--html" ]]; then
  echo "🌐 Abrindo relatório HTML: ${JACOCO_HTML}"
  if command -v xdg-open &>/dev/null; then
    xdg-open "${JACOCO_HTML}"
  elif command -v open &>/dev/null; then
    open "${JACOCO_HTML}"
  else
    echo "Abra manualmente: ${JACOCO_HTML}"
  fi
  exit 0
fi

if [[ "${MODE}" == "--json" ]]; then
  python3 - "${JACOCO_XML}" <<'PY'
import json
import sys
import xml.etree.ElementTree as ET

xml_path = sys.argv[1]
root = ET.parse(xml_path).getroot()

result = {"packages": [], "classes": []}

for pkg in root.findall('.//package'):
    pkg_name = pkg.get('name').replace('/', '.')
    missed = covered = 0
    for c in pkg.findall('counter'):
        if c.get('type') == 'LINE':
            missed = int(c.get('missed'))
            covered = int(c.get('covered'))
    total = missed + covered
    pct = round((covered / total * 100), 2) if total > 0 else 0
    result["packages"].append({
        "name": pkg_name,
        "covered": covered,
        "missed": missed,
        "total": total,
        "pct": pct,
    })

for pkg in root.findall('.//package'):
    pkg_name = pkg.get('name').replace('/', '.')
    for cls in pkg.findall('class'):
        cls_name = cls.get('name').split('/')[-1]
        missed = covered = 0
        for c in cls.findall('counter'):
            if c.get('type') == 'LINE':
                missed = int(c.get('missed'))
                covered = int(c.get('covered'))
        total = missed + covered
        pct = round((covered / total * 100), 2) if total > 0 else 0
        result["classes"].append({
            "package": pkg_name,
            "name": cls_name,
            "covered": covered,
            "missed": missed,
            "total": total,
            "pct": pct,
        })

print(json.dumps(result, indent=2, ensure_ascii=False))
PY
  exit 0
fi

# Modo padrão: sumário por pacote (tabela)
echo ""
echo "📊 Resumo de cobertura por pacote"
echo "──────────────────────────────────────────────────────────────────────"
printf "%6s %7s %7s %7s  %s\n" "%" "Cob" "Tot" "Miss" "Pacote"
echo "──────────────────────────────────────────────────────────────────────"

python3 - "${JACOCO_XML}" <<'PY'
import sys
import xml.etree.ElementTree as ET

xml_path = sys.argv[1]
root = ET.parse(xml_path).getroot()

rows = []
for pkg in root.findall('.//package'):
    pkg_name = pkg.get('name').replace('/', '.')
    missed = covered = 0
    for c in pkg.findall('counter'):
        if c.get('type') == 'LINE':
            missed = int(c.get('missed'))
            covered = int(c.get('covered'))
    total = missed + covered
    pct = (covered / total * 100) if total > 0 else 0
    rows.append((pct, covered, total, missed, pkg_name))

rows.sort()
for pct, cov, tot, mis, name in rows:
    print(f"{pct:>5.1f}% {cov:>7} {tot:>7} {mis:>7}  {name}")
PY

echo ""
echo "🌐 Relatório HTML: ${JACOCO_HTML}"
echo "   Dica: ./scripts/jacoco-report.sh --html    (abre no navegador)"
echo "   Dica: ./scripts/jacoco-report.sh --json    (saída JSON)"