# Teclado PT-BR: Digitação Instantânea & Verificador de Atualizações

Implementação do registro instantâneo de toques no teclado (`ACTION_DOWN`), incremento de versão (`versionCode 2`, `v1.1`) para reconhecimento de atualizações pelo instalador do Android, e novo mecanismo de varredura de versões via GitHub Releases.

## User Review & Critical Decisions

> [!IMPORTANT]
> **Decisões Confirmadas**:
> 1. **Momento do Registro da Tecla**: A letra será disparada **instantaneamente ao tocar a tecla** (`ACTION_DOWN`), garantindo tempo de resposta imediato (zero latência percebida).
> 2. **Verificação de Atualizações**: A varredura de versões consultará as **Releases do repositório no GitHub**.

> [!NOTE]
> **Permissão de Rede vs. Privacidade**:
> O app original foi desenhado sem permissão de `INTERNET`. Para permitir que o botão "Verificar atualizações" faça a checagem direta da API de Releases do GitHub, a permissão `<uses-permission android:name="android.permission.INTERNET" />` será declarada exclusivamente para a tela de configurações (`MainActivity`), mantendo o serviço do teclado (`LatinIMEKeyboardService`) estritamente local e sem qualquer comunicação com a nuvem. Também adicionaremos a opção de abrir diretamente o GitHub no navegador via Intent seguro como fallback.

---

## 1. Overview & Core Concept

- **O que faz**:
  - Elimina 100% da latência ao digitar: cada tecla pressionada é enviada de forma imediata à aplicação no exato instante do toque no vidro.
  - Adiciona na tela de Configurações um botão dedicado **"Verificar atualizações"** que consulta a versão mais recente publicada no GitHub.
  - Atualiza o `versionCode` (de 1 para 2) e `versionName` (de 1.0 para 1.1) para que o instalador do Android reconheça os novos APKs como atualizações legítimas sobre a versão anterior instalada.
- **Público-alvo**: Usuários que digitam em alta velocidade no celular e desejam manter o teclado atualizado sem precisar monitorar manualmente o GitHub.

---

## 2. User Experience & Visual Design

### Fluxo do Teclado (Latência Zero)
1. **Toque na Tecla (`ACTION_DOWN`)**:
   - A letra é inserida no campo no instante `0ms` (resposta imediata).
   - O feedback tátil (caso ativado) é disparado em thread separada.
2. **Pressão Longa (Acentos)**:
   - Se o usuário mantiver o dedo pressionado por mais de 300ms, o popup de acentos se abre. Ao escolher um acento (ex.: `á`, `ã`), o caractere previamente inserido é substituído pelo acentuado de forma transparente.
3. **Multi-Touch Contínuo**:
   - Suporte completo a deslizamento e sobreposição rápida de dedos (roll-over), sem engasgos ou perda de caracteres.

### Fluxo do Verificador de Atualizações
1. Na tela **Configurações do Teclado** (seguindo o estilo do Gboard):
   - Novo item: 🔄 **Verificar atualizações** (Subtítulo: *"Versão instalada: v1.1 • Toque para verificar"*).
2. Ao tocar no botão:
   - Exibe indicador de carregamento: *"Consultando GitHub Releases..."*.
   - Se for a versão mais recente: Diálogo de sucesso *"Você está usando a versão mais recente (v1.1)!"*.
   - Se houver nova versão: Diálogo com as notas de lançamento e botão *"Baixar Atualização (APK)"*, direcionando o download diretamente para o dispositivo.

---

## 3. Key Product Decisions & Trade-Offs

- **Disparo no `ACTION_DOWN` vs. `ACTION_UP`**:
  - *Abordagem Escolhida*: Disparo no `ACTION_DOWN`.
  - *Motivo*: Atende diretamente ao requisito de "clicar e já aparecer". A digitação fica extremamente responsiva e ágil.
- **Incremento de `versionCode`**:
  - *Abordagem Escolhida*: Alterar `versionCode = 2` e `versionName = "1.1"` no `app/build.gradle.kts`.
  - *Motivo*: O gerenciador de pacotes do Android (`PackageManager`) rejeita reinstalações com o mesmo `versionCode` ou não as trata como upgrade. Com `versionCode 2`, a atualização substitui a versão 1 mantendo configurações e histórico intactos.
- **Consulta via GitHub API**:
  - *Abordagem Escolhida*: Requisição leve via `HttpURLConnection` em background (`Dispatchers.IO`) com timeout curto (5s).
  - *Motivo*: Não adiciona bibliotecas pesadas de rede (preserva a meta de RAM < 10 MB).

---

## 4. Technical Architecture & Data Strategy

```
┌─────────────────────────────────────────────────────────────┐
│                      MainActivity                           │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ Item: "Verificar atualizações"                        │  │
│  └──────────────────────────┬────────────────────────────┘  │
└─────────────────────────────┼───────────────────────────────┘
                              │
                    Coroutines (IO Thread)
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                   UpdateCheckerService                      │
│  - Endpoint: api.github.com/repos/.../releases/latest       │
│  - Compara: release.tag_name vs BuildConfig.VERSION_NAME    │
│  - Retorna: UpdateStatus (UpToDate | NewVersionAvailable)   │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                 KeyboardView (Canvas 2D)                    │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ MotionEvent.ACTION_DOWN / ACTION_POINTER_DOWN         │  │
│  │  -> dispatchKey(key) INSTANTANEAMENTE (0ms)           │  │
│  └──────────────────────────┬────────────────────────────┘  │
└─────────────────────────────┼───────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                 LatinIMEKeyboardService                     │
│  - currentInputConnection.commitText(char, 1) imediato      │
│  - updateCandidates() em lote em bucket indexado O(1)       │
└─────────────────────────────────────────────────────────────┘
```

---

## 5. Arquivos e Tarefas de Execução

1. **`app/build.gradle.kts`**:
   - Atualizar `versionCode = 2` e `versionName = "1.1"`.
2. **`app/src/main/AndroidManifest.xml`**:
   - Incluir permissão de `INTERNET` para a checagem de atualizações.
3. **`app/src/main/java/com/example/keyboard/KeyboardView.kt`**:
   - Implementar despacho instantâneo no `ACTION_DOWN` com substituição graciosa em caso de seleção de acentos long-press.
4. **`app/src/main/java/com/example/UpdateChecker.kt`**:
   - Criar utilitário leve de checagem da API do GitHub Releases com parsing direto em `JSONObject` (sem dependências externas).
5. **`app/src/main/java/com/example/MainActivity.kt`**:
   - Adicionar o item interativo de "Verificar atualizações" com indicador de status e diálogo de download.
6. **Compilação e Entrega do APK**:
   - Gerar `APK/teclado-pt-br-v1.1.apk`.
