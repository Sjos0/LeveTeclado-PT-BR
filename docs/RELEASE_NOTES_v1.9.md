# Teclado Leve PT-BR v1.9

## Resumo

Atualização focada na **tela inicial de configurações** do aplicativo: visual mais limpo, lista reorganizada e ícones oficiais Material Symbols. A base funcional da v1.8 (menu da toolbar, temas, emoji, compartilhar, desfazer e diálogo de atualização) permanece.

> **Status:** esboço de release — código já em `main`; APK 1.9 ainda pendente de build/assinatura.

---

## Novidades

### Tela de configurações (app)

- Visual **Material claro**: fundo `#F8F9FA`, linhas brancas, textos `#202124` / `#5F6368`
- Barra superior com o nome **Teclado Leve** (sem excesso de chrome)
- Tema da Activity claro (`DeviceDefault.Light.NoActionBar`), barra de status clara
- Linhas de menu com **ícone + título + subtítulo + seta** (chevron)
- Campo discreto **Testar o teclado** no topo da lista
- Cartões de ativação (habilitar / definir como padrão) em azul Material, só quando necessário

### Ordem dos itens

1. Idiomas  
2. Preferências  
3. Tema  
4. Correção de texto  
5. Dicionário  
6. Digitação por voz  
7. Privacidade  
8. Verificar atualizações  
9. Sobre  

Itens de layout/símbolos permanecem acessíveis pelo fluxo de preferências / teclado, sem poluir a lista principal.

### Ícones oficiais (Material Symbols Outlined 24px)

Baixados do repositório oficial **google/material-design-icons** (sem paths manuais):

| Item | Símbolo |
|------|---------|
| Idiomas | `language` |
| Preferências | `tune` |
| Tema | `palette` |
| Correção de texto | `spellcheck` |
| Dicionário | `menu_book` |
| Digitação por voz | `mic` |
| Privacidade | `security` |
| Verificar atualizações | `system_update_alt` |
| Sobre | `info` |
| Seta da linha | `chevron_right` |

Cor de preenchimento alinhada à lista: `#5F6368`.

### Mantido da v1.8 (já em uso)

- Menu da toolbar (`view_cozy`): Tema, Emoji, Compartilhar, Desfazer
- Personalização da toolbar (long-press + arraste, ordem salva)
- Diálogo de atualização responsivo com formatação de notas
- Fala → texto (Groq Whisper), autocorreção e preferências existentes

---

## Versionamento previsto

| Campo | Valor |
|-------|--------|
| `versionName` | **1.9** |
| `versionCode` | **10** (incremento sobre 1.8 = 9) |
| minSdk | 26 (Android 8.0+) |

> A v1.8 permanece como build estável em uso (`versionCode` 9). O bump para 1.9 / code 10 deve ser feito no próximo build distribuível.

---

## Commits desta linha (após notas da v1.8)

| Commit | Resumo |
|--------|--------|
| `29ecc07` | Tela de configurações em estilo Material claro |
| `de4d035` | Ícones da lista de configurações (primeira passagem) |
| `012711d` | Substituição pelos XML oficiais Material Symbols Outlined |

*(Commits de build/CI do ambiente de compilação remotos podem aparecer intercalados e não alteram o escopo funcional acima.)*

---

## Instalação (quando o APK 1.9 estiver pronto)

1. Se a assinatura for a mesma da 1.8 em uso, a atualização pode substituir direto.  
2. Se a assinatura for diferente, desinstale a versão antiga antes.  
3. Baixe `teclado-pt-br-v1.9.apk` na release.  
4. Ative o teclado em **Configurações do sistema → Idiomas e entrada → Teclado virtual**, se necessário.

---

## Observações técnicas

- Sem dependências novas; APK continua leve (~11 MB na linha atual)
- Ícones vetoriais oficiais (viewport 960×960 Material Symbols), sem assets proprietários de terceiros
- Preferências e temas continuam em `SharedPreferences` (`MODE_PRIVATE`)
- Verificação de atualização segue consultando a API pública do GitHub (`/releases/latest`)
- Compatível com Android 8.0+ e aparelhos de baixa RAM
- Sem serviços em segundo plano novos

---

## Pendências para fechar a release 1.9

- [ ] Bump `versionCode` 10 / `versionName` 1.9 no Gradle  
- [ ] Build release assinado  
- [ ] Anexar APK na tag GitHub `v1.9`  
- [ ] Validar instalação sobre a 1.8 em uso  
- [ ] Revisar este documento no corpo da release no GitHub  
