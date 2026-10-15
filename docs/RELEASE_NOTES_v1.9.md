# Teclado Leve PT-BR v1.9

## Resumo

Atualização da **tela de configurações** e dos **ícones** (lista do app e toolbar do teclado), com correções de legibilidade nos diálogos. A base funcional da v1.8 permanece (menu da toolbar, temas, emoji, compartilhar, desfazer e diálogo de atualização).

> **Status:** código em `main`. Versionamento no Gradle ainda em 1.8 / code 9 até o próximo build distribuível (AI Studio). APK 1.9 pendente.

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
| `versionName` / `versionCode` no Gradle | Ainda **1.8** / **9** (bump **1.9** / **10** no build do AI Studio) |
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

## Instalação (quando o APK 1.9 estiver pronto)

1. Com a mesma assinatura da 1.8, a atualização pode substituir direto.  
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
- [ ] Bump `versionCode` 10 / `versionName` 1.9 no Gradle (AI Studio / build de release)  
- [ ] Build release assinado e APK na tag `v1.9`  
- [ ] Validar instalação sobre a 1.8 em uso  
