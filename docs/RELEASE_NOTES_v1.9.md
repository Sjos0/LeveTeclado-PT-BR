# Teclado Leve PT-BR v1.9

## Resumo

Atualização da **tela de configurações** e dos **ícones** (lista do app e toolbar do teclado), com correções de legibilidade nos diálogos. A base funcional da v1.8 permanece (menu da toolbar, temas, emoji, compartilhar, desfazer e diálogo de atualização).

> **Status:** versão 1.9 (código 10) publicada em GitHub Releases, com APK assinado e bibliotecas nativas recompiladas pelo CI.

---

## Novidades

### Tela de configurações (app)

- Visual Material claro: fundo `#F8F9FA`, linhas brancas, textos `#202124` / `#5F6368`
- Barra superior com o nome **Teclado Leve**
- Tema da Activity claro (`DeviceDefault.Light.NoActionBar`)
- Linhas com ícone + título + subtítulo + seta (chevron)
- Campo **Testar o teclado** e cartões de ativação (quando necessário)

### Ordem dos itens

1. Idiomas  
2. Preferências  
3. Tema  
4. Correção de texto  
5. **Símbolos e Layout** (restaurado)  
6. Dicionário  
7. Digitação por voz  
8. Privacidade  
9. Verificar atualizações  
10. Sobre  

### Correções de legibilidade (diálogos)

Com o tema claro, textos que ainda usavam cores de tema escuro (`#F4F4F5`, `#94A3B8`, etc.) ficavam ilegíveis. Ajustado para:

- Texto principal: `#202124`
- Texto secundário: `#5F6368`
- Campo da chave Groq: fundo `#F1F3F4`, texto escuro
- Destaque de código nas notas de atualização: `#1967D2`

Afeta: Preferências, Correção de texto, Digitação por voz e diálogo de nova versão.

### Ícones oficiais (Material Symbols Outlined 24px)

Baixados de **google/material-design-icons** (sem paths manuais).

**Lista de configurações** (`#5F6368`):

| Item | Símbolo |
|------|---------|
| Idiomas | `language` |
| Preferências | `tune` |
| Tema | `palette` |
| Correção de texto | `spellcheck` |
| Símbolos e Layout | `keyboard` |
| Dicionário | `menu_book` |
| Digitação por voz | `mic` |
| Privacidade | `security` |
| Verificar atualizações | `system_update_alt` |
| Sobre | `info` |
| Seta da linha | `chevron_right` |

**Toolbar do teclado** (`#B0BEC5`):

| Ação | Símbolo |
|------|---------|
| Menu | `view_cozy` |
| Cursor / texto | `text_fields` |
| Área de transferência | `content_paste` |
| Traduzir | `translate` |
| Configurações | `settings` |
| Microfone | `mic` |
| Fechar menu | `close` |


### Logo do aplicativo

- `artifacts/assets/app_logo.png` (fonte) e `app_logo_512.png`
- Ícones launcher (`mipmap-*` + `ic_keyboard_logo`) atualizados com a nova marca

### Correções de IME (microfone e backspace)

- **Overlay de gravação**: ao gravar por voz, as teclas não usam mais `View.GONE` (isso encolhia a janela do IME e o painel de gravação sumia). Agora a altura é fixada, o teclado fica `INVISIBLE` e o overlay cobre a área com botão **■** para enviar e **Cancelar**.
- **Backspace com campo vazio**: removido o fallback `KEYCODE_DEL`, que em vários apps fecha o teclado ao apagar o último caractere. Apagar usa só `InputConnection`; com o campo vazio o IME permanece aberto.

### Mantido da v1.8

- Menu da toolbar (`view_cozy`): Tema, Emoji, Compartilhar, Desfazer
- Personalização da toolbar (long-press + arraste)
- Diálogo de atualização responsivo com formatação de notas
- Fala → texto (Groq Whisper), autocorreção e preferências

---

## Versionamento

| Campo | Situação |
|-------|----------|
| Código em `main` | Mudanças da 1.9 aplicadas |
| `versionName` / `versionCode` no Gradle | **1.9** / **10** |
| minSdk | 26 (Android 8.0+) |

---

## Commits desta linha

| Commit | Resumo |
|--------|--------|
| `29ecc07` | Tela de configurações Material claro |
| `de4d035` / `012711d` | Ícones da lista (Material Symbols oficiais) |
| `14a4a87` | Ícones da toolbar oficiais |
| *(este)* | Diálogos legíveis + Símbolos e Layout restaurado + notas |

---

## Instalação

1. Esta versão tem assinatura nova: a atualização sobre a 1.8 não funciona. Desinstale a 1.8 antes de instalar a 1.9. As preferências do teclado e a chave Groq são perdidas, e o teclado precisa ser ativado de novo nas configurações.  
2. Se a assinatura for diferente, desinstale a versão antiga antes.  
3. Baixe `teclado-pt-br-v1.9.apk` na release.  
4. Ative o teclado em **Configurações do sistema → Idiomas e entrada → Teclado virtual**, se necessário.

---

## Observações técnicas

- Sem dependências novas; linha de tamanho de APK ~11 MB
- Ícones vetoriais oficiais (viewport 960×960)
- Preferências em `SharedPreferences` (`MODE_PRIVATE`)
- Verificação de atualização via API pública do GitHub
- Android 8.0+; aparelhos de baixa RAM
- Sem serviços em segundo plano novos

---

## Pendências para fechar a release 1.9

- [x] Legibilidade dos diálogos no tema claro  
- [x] Item **Símbolos e Layout** acessível no menu  
- [x] Ícones oficiais (configurações + toolbar)  
- [x] Bump `versionCode` 10 / `versionName` 1.9 no Gradle  
- [x] Build release assinado e APK na tag `v1.9` (feito pelo CI)  
- [ ] Validar instalação sobre a 1.8 em uso  
