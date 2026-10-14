# Teclado Leve PT-BR v1.8

## Novidades

- **Diálogo de atualização responsivo**
  - Largura ~92% da tela (máx. ~420 dp em tablets)
  - Área de novidades com rolagem vertical interna
  - Altura máxima ~50% da tela para os botões **Baixar / GitHub / Depois** ficarem sempre visíveis
- **Conversão de Markdown** no corpo da release (sem bibliotecas extras)
  - `##` / `#` → título em negrito e um pouco maior
  - `**texto**` → negrito
  - `` `código` `` → monoespaçado em azul claro
  - Listas `-` / `*` / `1.` → marcador •
  - Linhas em branco consecutivas são colapsadas
- Mantém **ícones Material (Google)** da 1.7 na toolbar e nas configurações
- Mantém **fala → texto** (Groq Whisper) da 1.6
- `versionCode` 9 / `versionName` 1.8
- minSdk 26 (Android 8.0+)

## Instalação

1. Se a assinatura for diferente da versão anterior (ex.: build do AI Studio vs keystore própria), desinstale a versão antiga antes.
2. Baixe `teclado-pt-br-v1.8.apk` nesta release.
3. Instale e ative o teclado em **Configurações do sistema → Idiomas e entrada → Teclado virtual**.

## Arquivo

- APK completo ~11 MB (mesmo conteúdo otimizado das builds anteriores)
- Assinado com a keystore própria (linha a partir da 1.6)

## Observações técnicas

- Parser de Markdown leve (SpannableStringBuilder), sem dependências novas
- Compatível com Android 8.0+ e aparelhos de baixa RAM
- Verificação de atualização continua consultando a API pública do GitHub (`/releases/latest`)
