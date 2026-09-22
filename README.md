# Vida

Organização pessoal 100% offline para Android: tarefas, agenda, finanças, notas, compras, metas e hábitos — tudo salvo no seu aparelho, sem internet.

## Módulos
- **Início** — visão geral: foco do dia, compromissos, progresso de metas e hábitos
- **Agenda / Tarefas** — listas com toque para concluir e segurar para excluir
- **Finanças** — saldo do mês, receitas, despesas, orçamento mensal e movimentações recentes
- **Notas, Compras, Metas, Hábitos** — registros simples com conclusão
- **Configurações** — tema claro/escuro, exportar dados (JSON) e sobre o app
- **Assistente** — interface pronta para futura integração de IA, sem comprometer o modo offline

## Recursos
- Dados em `SharedPreferences` (JSON versionado) — prontos para migrar a Room
- Finanças consideram apenas o mês corrente
- Orçamento mensal com alerta visual quando estourado
- Exportação dos dados via share (JSON)

## Build
O CI (`.github/workflows/android.yml`) compila o APK a cada push em `main` e publica como artefato `vida-debug-apk`.

Requisitos locais: Android SDK 35, JDK 17.
```
gradle assembleDebug
```
