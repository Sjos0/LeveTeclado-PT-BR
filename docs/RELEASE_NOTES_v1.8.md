# Teclado Leve PT-BR v1.8

## Novidades

### Menu do teclado (ícone view_cozy)

- Ícone esquerdo da toolbar trocado para **Material Symbols Outlined `view_cozy`**
- Toque abre painel no lugar das teclas; o ícone vira **X** para fechar
- Ações reais no painel:
  - **Tema** — alterna 3 paletas (Grafite e Cobalto, OLED Preto, Ardósia Azul) e aplica no teclado e na toolbar; preferência salva
  - **Emoji** — painel leve com categorias; toque insere o caractere no campo
  - **Compartilhar** — `ACTION_SEND` com texto selecionado ou texto ao redor do cursor
  - **Desfazer** — pilha de operações (emoji/colar) + fallback da última auto-correção
- **Personalizar toolbar**: long-press (~450 ms) + arraste nos ícones da barra; ordem persistida em SharedPreferences
- **Fora do escopo** (não implementados): GIF, Redimensionar, Uma mão só, Adesivo, Flutuante

### Diálogo de atualização

- Largura ~92% da tela (máx. ~420 dp em tablets)
- Área de novidades com rolagem vertical interna
- Altura máxima ~50% da tela para os botões **Baixar / GitHub / Depois** ficarem sempre visíveis
- **Conversão de Markdown** no corpo da release (sem bibliotecas extras)
  - `##` / `#` → título em negrito e um pouco maior
  - `**texto**` → negrito
  - `` `código` `` → monoespaçado em azul claro
  - Listas `-` / `*` / `1.` → marcador •
  - Linhas em branco consecutivas são colapsadas

### Estabilidade (commits intermediários nesta linha 1.8)

- Correção de **autocorreção**, **voz (Groq Whisper)** e regras de **backup** do teclado
- Persistência da chave Groq e fluxo fala→texto mantidos da 1.6+
- Ícones Material (Google) na toolbar e configurações (da 1.7+)

### Versionamento

- `versionCode` **9** / `versionName` **1.8**
- minSdk 26 (Android 8.0+)

## Commits incluídos nesta linha (após v1.7)

| Commit | Resumo |
|--------|--------|
| `1f3c2a0` | Diálogo de atualização responsivo com Markdown |
| `d39e365` | Refino do diálogo e notas da v1.8 |
| `87d467f` | Ícone MENU → Material `view_cozy` |
| `5b8581c` | Correção autocorreção, voz e backup |
| `21b5b0d` | Menu completo: Tema, Emoji, Compartilhar, Desfazer + reorder toolbar |

## Instalação

1. Se a assinatura for diferente da versão anterior (ex.: build do AI Studio vs keystore própria), desinstale a versão antiga antes.
2. Baixe `teclado-pt-br-v1.8.apk` nesta release.
3. Instale e ative o teclado em **Configurações do sistema → Idiomas e entrada → Teclado virtual**.

## Arquivo

- APK completo ~11 MB (mesmo conteúdo otimizado das builds anteriores)
- Assinado com a keystore própria (linha a partir da 1.6)

## Observações técnicas

- Parser de Markdown leve (`SpannableStringBuilder`), sem dependências novas
- Temas e ordem da toolbar em `KeyboardPreferences` (SharedPreferences MODE_PRIVATE)
- Painéis `MorePanelView` e `EmojiPanelView` no lugar do `KeyboardView` (FrameLayout)
- Compatível com Android 8.0+ e aparelhos de baixa RAM
- Verificação de atualização continua consultando a API pública do GitHub (`/releases/latest`)
- Sem serviços em segundo plano novos; sem bibliotecas pesadas
