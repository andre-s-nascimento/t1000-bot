#!/bin/bash

# Verifica se o nome foi passado
if [ -z "$1" ]; then
    echo "Erro: Forneça um nome. Uso: ./dump_files.sh <nome>"
    exit 1
fi

NOME=$1
DATA=$(date +%d-%m-%Y)
SAIDA="../dump-${NOME}-${DATA}.txt"

# Lista de pastas para excluir (formato para o find)
# O -prune descarta a pasta inteira se ela corresponder ao nome
find . \
    \( -path "./build" -o \
       -path "./.gradle" -o \
       -path "./.git" -o \
       -path "./.vscode" -o \
       -path "./temp*" -o \
       -path "./gradle" -o \
       -path "./bin" -o \
       -path "./node_modules" \) -prune -o \
    -type f \( -name "*.java" -o -name "*.html" -o -name "*.css" -o -name "*.js" -o -name "*.yml" -o -name "*.properties" \) \
    -print | sort | while read -r f; do 
        echo -e "\n\n/**********************************************************"
        echo -e " * ARQUIVO: $f"
        echo -e " **********************************************************/\n"
        cat "$f"
done > "$SAIDA"

echo "Filtro aplicado! Arquivo gerado: $SAIDA"
