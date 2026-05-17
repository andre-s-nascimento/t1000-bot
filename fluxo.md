# 🔁 Fluxo de Releases e Hotfixes (com GitHub Actions)

## 📤 Release padrão

1. Crie uma branch `release/vX.Y.Z` a partir da `develop` (pode fazer via Git ou GitHub).
2. Faça push da branch.
3. O GitHub Actions criará um PR para `main` automaticamente.
4. Aprove e faça o merge do PR.
5. Após o merge, uma tag `vX.Y.Z` será criada automaticamente.
6. A tag dispara o workflow `Release` existente, que gera a Release no GitHub.
7. (Opcional) Um PR de `main` → `develop` será criado para sincronização.

## 🧯 Hotfix

1. Crie uma branch `hotfix/descricao` a partir da `main`.
2. (Opcional) Coloque a versão no nome da branch: `hotfix/v1.2.4` para disparar bump automático.
3. Faça push da branch.
4. O GitHub Actions criará um PR para `main`.
5. Aprove e faça o merge.
6. Tag e release geradas automaticamente.

## 📦 Workflows utilizados

- `release-pr.yml` – cria PR de release
- `hotfix-pr.yml` – cria PR de hotfix
- `tag-on-merge.yml` – cria tag após merge na main
- `sync-develop.yml` – cria PR de sincronização main → develop
- `Release.yml` (existente) – gera a release a partir da tag

## 🔑 Segredos

- `GH_PAT` – token com permissões `repo`, `workflow`

## ✅ Vantagens

- PRs automáticos, mas merges manuais (segurança)
- Tags automáticas
- Releases automáticas
- Sincronização de branches automática
