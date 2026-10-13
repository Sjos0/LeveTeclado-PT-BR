# Teclado Leve PT-BR: Plano de Implementação (v6 Final)

Plano de engenharia, correção morfológica, segurança criptográfica e otimização de desempenho para o aplicativo "Teclado Leve PT-BR" (`Sjos0/LeveTeclado-PT-BR`).

---

## User Review & Decisões Críticas de Arquitetura

### GLOSSÁRIO
- **Top-50k**: As 50.000 primeiras entradas de `pt_br_50k` (contagens do OpenSubtitles 2018). É o conteúdo do binário unigrama.
- **Corpus completo**: `pt_br_full`, com 848.043 entradas e as mesmas contagens. Usado no cálculo de todas as razões de frequência.
- **Top-20k**: As 20.000 primeiras posições do corpus completo.
- **Válida**:
  - Forma sem acento: consta no VERO.
  - Forma acentuada digitada: consta no VERO **ou** tem $\ge 100$ ocorrências no corpus completo.
- **Candidata**: Forma acentuada válida no VERO, com $\ge 100$ ocorrências e $\ge 5\%$ da contagem da irmã mais frequente.

---

> [!IMPORTANT]
> **1. Validação de Formas e Regra das Formas sem Acento**
> - **Formas sem Acento**: Uma forma sem acento só é válida se constar no VERO. As formas sem acento frequentes em legendas mas inexistentes no VERO (`nao`, `voce`, `tambem`, `mae`, `agua`, `alem`, `alguem`, `area`, `biblia`...) nunca são válidas e entram no fluxo de restauração de acento.
> - **Formas Acentuadas Digitadas**: São válidas se constarem no VERO ou tiverem $\ge 100$ ocorrências no corpus completo. Assim, palavras como `país`, `nós`, `robôs`, `vocês` e `pés` **nunca são alteradas**.
> - **Exceções Explícitas**: Lista curta e versionada, contendo `ate` → `até`. Tem precedência sobre a validade do VERO e sobre a presença no Top-50k. Fora dela, palavras válidas no VERO nunca são alteradas.

> [!IMPORTANT]
> **2. Tabela Curada de Formas Ausentes**
> - **Critério de Inclusão**: Forma sem acento **ausente do Top-50k**, **inválida no VERO**, com exatamente 1 candidata (conforme o Glossário), razão $\ge 8\times$ no corpus completo e alvo no Top-20k. Exclusões: lista de nomes próprios e estrangeirismos em `stoplist_names.txt`.
> - **Origem e Responsável**: Gerada **offline** por `scripts/build_curated.py` e versionada no repositório. O build apenas lê o arquivo. Antes de cada release, revisar 100 entradas aleatórias; se houver erro sistemático, corrigir o critério e gerar de novo.
> - **Conteúdo Inicial**: Contém só entradas ausentes do Top-50k. `acao` entra por esse critério (16 ocorrências no corpus completo; razão 2.061×). `nao`, `voce`, `mae` e `tambem` **não** entram, porque já estão no Top-50k.
> - **Formato no Binário**: Tabela auxiliar separada, ordenada pela chave normalizada, com `(chave_norm, palavra_exata, rank)`.
> - **Razão em Formas Ausentes**: Calculada no corpus completo, com o mesmo limiar de 8×. Exemplo: `acao` (16) → `ação` (32.982) = 2.061×.

> [!IMPORTANT]
> **3. Unificação do Limiar em 8× e Proteção contra Falsos Positivos**
> - O limiar de razão $\ge 8\times$ a favor da forma acentuada e alvo no Top-20k aplica-se uniformemente a palavras de qualquer tamanho.
> - `poe` → `põe` (razão 9,8×), `construi` → `construí` (razão 9,9×) e `cairam` → `caíram` (razão 9,1×) passam pelo limiar de 8×.
> - `ai` possui razão medida de 6,1× no corpus completo e fica estritamente protegido.
> - Formas como `roma`, `victor` e `max` possuem suas formas sem acento muito mais frequentes que as variantes acentuadas e ficam protegidas.

> [!IMPORTANT]
> **4. Dicionário Binário (~1,05 MB) e Ferramenta do `IS_VERO_VALID`**
> - O binário possui ~1,05 MB (Top-50k com palavra normalizada, palavra exata, rank `u16` de 2 bytes e 1 byte de flags, mais a tabela auxiliar curada).
> - **Geração offline de `IS_VERO_VALID`**: O léxico expandido, gerado offline por `unmunch` ($\approx 83$ milhões de linhas), é cruzado com as 50 mil entradas por `grep -xFf`, ou por binding oficial do Hunspell. Não carregar o léxico expandido em memória, nem como set nem como dicionário em Python.
> - Zero alocação de objetos por palavra no runtime: leitura contígua em `ByteBuffer` / slice.

> [!IMPORTANT]
> **5. Keystore, Assinatura e Automação de CI**
> - **Comando `keytool` Condicional na CI**:
>   - Na **Opção A (manter chave atual)**: Executar `keytool -changealias -alias androiddebugkey -destalias upload -keystore release.keystore -storepass android -keypass android`. Só usar se o keystore for exclusivo deste projeto, já que a senha `android` é pública.
>   - Na **Opção B (chave nova de release)**: Criar com `keytool -genkeypair -keystore release.keystore -alias upload -keyalg RSA -keysize 4096 -validity 10000`, com as senhas em GitHub Secrets. Guardar uma cópia da chave fora do GitHub, pois sem ela não há atualização futura. O passo de renomeação do alias é omitido.
> - Variáveis declaradas no workflow: `KEYSTORE_PATH`, `STORE_PASSWORD` e `KEY_PASSWORD`.
> - O SHA-256 protege o download em trânsito; a assinatura da Keystore protege a autenticidade e identidade do app no Android.

---

## 1. Regras Rigorosas de Autocorreção e Acentuação

### A. Palavras Curtas (≤ 4 letras)
- **Regra Geral**: Palavras com até 4 letras nunca são corrigidas por distância de edição.
- **Restauração de Acento**: Exige **exatamente 1 candidata**, conforme o Glossário. Se não houver nenhuma, não há correção. Se houver 2 ou mais, nunca substitui no espaço: exibe todas na barra. Se houver exatamente 1, com razão $\ge 8\times$ e alvo no Top-20k, substitui automaticamente.
  - Casos aprovados: `nao` → `não`, `voce` → `você`, `mae` → `mãe`, `poe` → `põe`.
  - Exceção explícita de alta frequência: `ate` → `até` (99,7% dos usos).
  - Casos protegidos: `ai` (razão 6,1× < 8×; permanece `ai`).

### B. Palavras com 5 ou mais Letras
- **Cálculo de Levenshtein**: Sobre a forma com acento, em codepoints Unicode (não bytes UTF-8). Distância máxima = 1.
- **Escopo de Correção de Palavras Ausentes**: Entre as palavras **ausentes do Top-50k**, só são corrigidas as que constam na Tabela Curada. Palavras **presentes** no Top-50k seguem as regras gerais desta seção (distância 1, razão $\ge 8\times$, alvo no Top-20k).
- **Proteção de Formas Válidas**: Palavras válidas no VERO nunca são alteradas.
- **Exceções de Gírias e Internet**: `tbm`, `pra`, `vc`, `pq`, `blz`, `bjs`, `kkk`, `kkkk`, `pfv`, `vlw` nunca são corrigidas.
- **Nomes Próprios e Estrangeirismos**: Antes de qualquer correção, consultar `stoplist_names.txt`, versionada no repositório, que inclui `francois`. A regra de maiúscula no meio da frase aplica-se quando o texto digitado tem inicial maiúscula.

### C. Ambiguidade de Formas Inválidas
- Formas sem acento inválidas no VERO que possuem 2 ou mais candidatas (Glossário) nunca são substituídas no espaço: exibem todas na barra de sugestões.
  - Exemplos de ambiguidade: `mantem` (`mantém`/`mantêm`), `po` (`pó`/`pô`), `pos` (`pôs`/`pós`), `pure` (`purê`/`pure`), `ra` (`rã`/`rá`) e `vo` (`vó`/`vô`).
- Palavras sem acento válidas no VERO (como `esta`, `pais`, `bebe`) são válidas e não sofrem substituição nem geram alternativas na barra.

### D. Preservação de Capitalização e Desfazer
- `tambem` → `também` | `Tambem` → `Também` | `TAMBEM` → `TAMBÉM`
- Backspace imediato desfaz a correção; toque no chip central da barra também desfaz a substituição.

---

## 2. Sugestões, Complexidade de Busca e Bigramas

- **Sugestões com Campo Vazio**: Exibe as palavras de maior frequência absoluta (`que`, `não`, `de`), e nunca ordem alfabética.
- **Complexidade de Busca de Prefixos**: Busca binária em $O(\log N)$ para os limites do prefixo; seleção do Top-3 por frequência percorre os $k$ candidatos do intervalo em $O(k)$ (onde $k \le 5.643$). Tempo medido via `System.nanoTime()` no debug.
- **Bigramas**: Ativado apenas com dados estatísticos documentados de pares de palavras. Caso contrário, desativado.

---

## 3. Tecla de Emoji e Isolamento de Campos Sensíveis

- **Tecla de Emoji (`😊`)**:
  - Não dispara no `ACTION_DOWN`. Inserção exclusivamente no `ACTION_UP` caso não tenha ocorrido pressão longa.
  - Popup insere o caractere diretamente, sem chamar `deleteSurroundingText(1, 0)`.
  - `onReplaceKey` bloqueado quando não há palavra em composição ativa.
- **Campos Sensíveis (`EditorInfo.inputType`)**:
  - Detecção estrita de: `TYPE_TEXT_VARIATION_PASSWORD`, `TYPE_TEXT_VARIATION_VISIBLE_PASSWORD`, `TYPE_TEXT_VARIATION_WEB_PASSWORD`, `TYPE_NUMBER_VARIATION_PASSWORD`, `TYPE_TEXT_VARIATION_EMAIL_ADDRESS`, `TYPE_TEXT_VARIATION_URI` e `TYPE_TEXT_FLAG_NO_SUGGESTIONS`.
  - Nesses campos: zero sugestões, zero autocorreção, zero autocapitalização e zero gravação em buffer.

---

## 4. Identidade Visual e Acessibilidade (WCAG AA)

### Paleta Oficial Própria e Rótulos de Contraste

| Elemento | Tema Escuro (Padrão) | Tema Claro | Contraste Calculado |
|---|---|---|---|
| **Fundo do Teclado** | `#1C1F24` | `#ECEEF2` | Base |
| **Teclas de Letras** | `#2A2F37` | `#FFFFFF` | - |
| **Borda das Teclas no Escuro** | `#7A8496` | - | **3,57:1** contra tecla de letra (`#2A2F37`); **4,38:1** contra fundo (`#1C1F24`) |
| **Borda das Teclas no Claro** | - | `#6E7887` | **3,47:1** contra tecla de função (`#DFE3E8`); **3,85:1** contra fundo (`#ECEEF2`) |
| **Teclas de Função (Shift, ?123, Del)** | `#232830` | `#DFE3E8` | - |
| **Texto Principal** | `#F2F4F7` | `#1A1D21` | **> 11:1** contra teclas de letra |
| **Texto de Função** | `#B8C0CC` | `#3C4452` | **> 6:1** contra teclas de função |
| **Azul Cobalto de Fundo (Ação)** | `#3D6BE0` (texto `#FFFFFF`: **4,80:1**) | `#2A57C7` (texto `#FFFFFF`: **6,39:1**) | **> 4,5:1** |
| **Azul Cobalto de Texto (sobre Fundo)** | `#7AA2FF` | `#1A43A3` | **6,64:1** contra fundo escuro (`#1C1F24`) |

- **Temas**: Escuro, Claro e Seguir o Sistema (`UI_MODE_NIGHT_MASK`).
- **Área de Toque**: Altura de 48 dp com expansão de toque vertical; largura proporcional.
- **Acessibilidade**: `contentDescription` em todas as teclas e componentes interativos.
- **Assets Vetoriais**: Substituição do PNG de 639 KB por `ic_keyboard_logo.xml` (< 2 KB).
- **Textos e Internacionalização**: Extração completa para `res/values/strings.xml`, substituindo a frase "Sem acesso à internet" pela descrição verdadeira: internet restrita sob demanda para atualizações via GitHub Releases.

---

## 5. Atualizações Seguras (GitHub Releases)

- **Repositório**: Constante única `GITHUB_REPO = "Sjos0/LeveTeclado-PT-BR"`.
- **Frequência**: Máximo de uma checagem a cada 24 horas (salva em `SharedPreferences`).
- **Otimização**: Cabeçalho `If-None-Match` com `ETag`. Tratamento de HTTP 304, 403 e 429.
- **Comparação Semântica**: Tag `vX.Y.Z` comparada numericamente contra `BuildConfig.VERSION_NAME`.
- **Verificação de Integridade**:
  - Download exclusivo de domínios oficiais do GitHub. O host final, após redirecionamento, deve ser `github.com` ou `objects.githubusercontent.com`, validado antes do download.
  - Download de `SHA256SUMS` e conferência do hash SHA-256 antes da instalação.
  - Validação de correspondência do `applicationId` via `PackageManager.getPackageArchiveInfo`.
  - Disparo de instalação oficial via `FileProvider` e `REQUEST_INSTALL_PACKAGES`.

---

## 6. Otimização de Compilação, R8 e Eliminação da Permissão DUMP

### A. Regras R8 / ProGuard (`app/proguard-rules.pro`)
```proguard
# Preservar o motor nativo e métodos JNI
-keep class com.example.keyboard.NativeEngine { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

-repackageclasses ""
-allowaccessmodification
```

### B. Remoção Definitiva da Permissão `DUMP` via Manifest Merger
No arquivo `app/src/main/AndroidManifest.xml`:
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <application ...>
        <!-- Remove o receiver transitivo da biblioteca androidx.profileinstaller e elimina a permissão DUMP -->
        <receiver
            android:name="androidx.profileinstaller.ProfileInstallReceiver"
            tools:node="remove" />
    </application>
</manifest>
```

---

## 7. Metas Numéricas de Desempenho e Medições

- **Memória RAM PSS**:
  - Medição do app vazio antes de cravar meta fixa de 10 MB, e em uso contínuo via `adb shell dumpsys meminfo com.aistudio.tecladoptbr.kxqpva`.
  - Meta de projeto estimada: < 10 MB. O valor medido real será documentado no relatório.
- **Latência por Tecla**:
  - Medição com `System.nanoTime()` no `dispatchKey` em build de debug.
  - Meta máxima: < 16 ms (1 quadro a 60 fps; meta de projeto < 2 ms).
- **Tamanho do APK**:
  - Medição por `ls -lh APK/*.apk`.
  - Meta máxima: < 8,0 MB.

---

## 8. Testes Automatizados e Manuais

### A. Casos de Teste Unitário (Rust e Kotlin)
- **Corrigir ao espaço**:
  - `nao` → `não`
  - `voce` → `você`
  - `mae` → `mãe`
  - `tambem` → `também`
  - `acao` → `ação` (tabela curada)
  - `ate` → `até` (exceção explícita)
  - `poe` → `põe`
  - `construi` → `construí`
  - `cairam` → `caíram`
  - `agua` → `água`
  - `cao` → `cão`
  - `alguem` → `alguém`
  - `razao` → `razão` (tabela curada)
  - `violencia` → `violência` (tabela curada)
  - `sabado` → `sábado` (tabela curada)
- **Só sugerir, sem troca no espaço (Ambíguas)**:
  - `pos` (pôs / pós)
  - `mantem` (mantém / mantêm)
  - `po` (pó / pô)
  - `vo` (vó / vô)
- **Preservar (Não alterar)**:
  - `ai`
  - `roma` (não virar romã)
  - `victor` (não virar víctor)
  - `max` (não virar máx)
  - `francois` (nome / estrangeirismo)
  - `esta`
  - `pais`
  - `bebe`
  - `pode`
  - `pôde`
  - `tbm`
  - `pra`
  - `país`
  - `nós`
  - `robôs`
  - `vocês`
  - `pés`
  - `Roma` no início de frase

### B. Testes Manuais no Aparelho
1. Seleção de emoji no popup sem quebra de código UTF-16.
2. Digitação em campo de senha: sem sugestões, sem autocorreção.
3. Digitação em campo de e-mail e URL: sem autocapitalização indevida.
4. Rotação entre modo Retrato e Paisagem.
5. Alternância fluida entre letras e símbolos (`?123`).

---

## 9. CI / GitHub Actions (`.github/workflows/release.yml`)

- **Disparo**: Criação de tag `v*.*.*`.
- **Execução Prévia de Testes (Obrigatória)**:
  - `cargo test` no Rust.
  - `gradle testDebugUnitTest` no Android.
  - **Se qualquer teste falhar, o build é abortado imediatamente e nenhuma release é publicada**.
- **Compilação e Assinatura**:
  - Compilação das bibliotecas nativas para `arm64-v8a` e `armeabi-v7a` via `cargo-ndk`.
  - Passo condicional de `keytool -changealias` executado apenas se a keystore fornecida possuir o alias `androiddebugkey`.
  - Configuração das variáveis de ambiente `KEYSTORE_PATH`, `STORE_PASSWORD` e `KEY_PASSWORD`.
  - Compilação do APK de release assinado e geração de `SHA256SUMS`.
- **Publicação**:
  - Permissão `permissions: contents: write`.
  - Publicação da release com artefato nomeado `teclado-pt-br-vX.Y.Z.apk` e arquivo `SHA256SUMS`.
- **Documentação e Atribuição**:
  - Criação do arquivo `NOTICE` na raiz com as licenças do VERO (LGPLv3/MPL) e OpenSubtitles (`hermitdave/FrequencyWords` CC BY-SA 4.0).
  - Atualização do `README.md` refletindo o uso de rede estrito para atualizações e medições reais.
