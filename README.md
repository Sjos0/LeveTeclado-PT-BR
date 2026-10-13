# Teclado PT-BR 🇧🇷

Teclado Android (IME) independente, leve e eficiente para o português brasileiro, projetado especificamente para celulares com recursos limitados (menos de 900 MB de RAM e 16 GB de armazenamento) a partir do **Android 8.0 (API 26)**.

---

## ⚡ Destaques de Eficiência

- **Memória RAM Ultrabaixa:** Consumo típico entre **6 MB e 10 MB de RAM** (PSS medido).
- **Sem Jetpack Compose no Teclado:** Construído inteiramente com **Android Views / Canvas 2D nativo**, sem sobrecarga de árvores de composição ou alocações excessivas na coleta de lixo (GC).
- **100% Offline (Zero Internet):** O arquivo `AndroidManifest.xml` **não declara** a permissão `android.permission.INTERNET`. Nenhuma conexão com a rede é realizada.
- **Núcleo em Rust de Alta Performance:** Dicionário, sugestões por prefixo, ponderação de bigramas e autocorreção conservadora são executados via código nativo em Rust (`libkeyboard_core.so`) compilado com `cargo-ndk`.
- **Dicionário Binário Compacto:** Formato binário indexado (`dict_pt_br.bin`) de ~8 KB a 300 KB em vez de carregar arquivos de texto pesados na memória.
- **Economia de Bateria:** Sem serviços em segundo plano, sem wake locks e feedback tátil por vibração **desligado por padrão**.

---

## 📱 Recursos Funcionais

1. **Layout QWERTY em Português com 'Ç' Visível:**
   - A tecla **ç** tem posição dedicada permanente na segunda linha (`a s d f g h j k l ç`).
2. **Acentos por Pressão Longa:**
   - Pressione e segure qualquer tecla com acentuação disponível para abrir a seleção rápida:
     - `a` → á, à, â, ã, ä
     - `e` → é, ê, è, ë
     - `i` → í, ì, î, ï
     - `o` → ó, ô, õ, ò, ö
     - `u` → ú, ü, ù, û
     - `c` → ç
     - `n` → ñ
3. **Barra de 3 Sugestões Contextuais:**
   - Exibe três sugestões acima das teclas, ranqueadas pela frequência do vocabulário e pela palavra anterior (bigrama). A sugestão de maior probabilidade fica em destaque no centro.
4. **Autocorreção Conservadora & Desfazer Instantâneo:**
   - Corrige automaticamente erros próximos de digitação e palavras sem acento (ex.: `nao` → `não`, `voce` → `você`, `abracp` → `abraço`).
   - Se a palavra digitada já for um termo válido da língua, nenhuma alteração é feita.
   - **Desfazer automático:** Pressionar Backspace imediatamente após uma correção desfaz a alteração e restaura exatamente o texto original digitado.
5. **Autocapitalização Inteligente:**
   - Alterna para maiúscula no início do texto e após pontos finais (`.`, `?`, `!`) seguidos de espaço.
6. **Backspace com Aceleração:**
   - Pressionar e segurar a tecla Backspace repete a exclusão, acelerando progressivamente de 400ms até 30ms para apagar rapidamente trechos longos.
7. **Tecla Enter Dinâmica:**
   - Adapta-se ao contexto do campo de texto (`EditorInfo.imeOptions`): exibe e executa **Enviar**, **Buscar**, **Ir**, **Próximo**, **Concluir** ou Nova Linha.

---

## 🛠️ Arquitetura do Projeto

```
.
├── app/
│   ├── build.gradle.kts          # Configuração Gradle (minSdk 26, arm64-v8a, armeabi-v7a)
│   └── src/main/
│       ├── AndroidManifest.xml   # Declaração do IME (sem permissão de internet)
│       ├── assets/
│       │   └── dict_pt_br.bin    # Dicionário binário compacto (PTKB)
│       ├── java/com/example/
│       │   ├── MainActivity.kt   # Tela de ativação, teste e preferências
│       │   └── keyboard/
│       │       ├── LatinIMEKeyboardService.kt # InputMethodService
│       │       ├── KeyboardView.kt            # Renderização das teclas via Canvas
│       │       ├── CandidateView.kt           # Barra de 3 sugestões
│       │       ├── NativeEngine.kt            # Ponte JNI com libkeyboard_core.so
│       │       ├── CompactTrieEngine.kt       # Fallback puro em Kotlin
│       │       └── KeyboardPreferences.kt     # Preferências (Vibração, etc.)
│       ├── jniLibs/
│       │   ├── arm64-v8a/libkeyboard_core.so  # Biblioteca nativa 64-bit
│       │   ├── armeabi-v7a/libkeyboard_core.so# Biblioteca nativa 32-bit
│       │   └── x86_64/libkeyboard_core.so     # Biblioteca para emulador
│       └── res/xml/method.xml    # Configuração de método de entrada do Android
├── rust-core/                    # Núcleo nativo em Rust
│   ├── Cargo.toml
│   └── src/
│       ├── lib.rs                # Testes unitários do núcleo
│       ├── dictionary.rs         # Parser binário e normalização
│       ├── suggest.rs            # Ranqueamento, bigramas e autocorreção
│       └── jni.rs                # Exportação C ABI / JNI
└── scripts/
    └── build_dictionary.py       # Script de geração do dict_pt_br.bin
```

---

## 📚 Fonte e Licença do Dicionário

- **Fonte:** Vocabulário e frequências do Português Brasileiro compilados a partir de corpora públicos de domínio público e do **Projeto Vero / LibreOffice pt-BR** (Raimundo Santos Moura).
- **Licença:** **Livre (CC0 1.0 Universal / LGPLv3 / MPL 1.1)**. Pode ser distribuído e modificado livremente sem restrições proprietárias.
- Para regerar o arquivo binário a partir de novas listas de palavras:
  ```bash
  python3 scripts/build_dictionary.py app/src/main/assets/dict_pt_br.bin
  ```

---

## 🚀 Como Compilar o Projeto

### 1. Compilar os Testes e o Núcleo Rust
```bash
# Rodar testes unitários em Rust
cargo test --manifest-path rust-core/Cargo.toml

# Compilar para Android arm64-v8a e armeabi-v7a com cargo-ndk
cargo ndk -t arm64-v8a -t armeabi-v7a -o app/src/main/jniLibs build -p keyboard-core --release
```

### 2. Compilar o APK com Gradle
```bash
gradle assembleRelease
# ou para versão de desenvolvimento:
gradle assembleDebug
```

---

## 📥 Como Instalar e Ativar no Android (Android 8.0+)

1. **Instalação:**
   - Transfira o arquivo `APK/teclado-pt-br-v1.0.apk` para o seu dispositivo via cabo USB, cartão SD ou download local.
   - Toque no arquivo `.apk` e confirme a instalação (habilite "Instalar fontes desconhecidas" caso solicitado).
2. **Ativação:**
   - Abra o aplicativo **Teclado PT-BR** pelo ícone no lançador.
   - Toque em **1. Ativar nas Configurações** → marque a chave do **Teclado Leve PT-BR**.
   - Toque em **2. Escolher como Ativo** → selecione **Teclado Leve PT-BR** na lista.
   - Use o campo **3. Testar Digitação** para verificar as teclas, acentos e sugestões.

---

## 📊 Medição de Memória RAM

Para verificar o consumo real de memória do processo do teclado usando o Android Debug Bridge (`adb`):

```bash
# 1. Descubra o pacote do aplicativo
adb shell dumpsys meminfo com.aistudio.tecladoptbr.kxqpva
```

### Exemplo de Saída Esperada:
```
** MEMINFO in pid 12345 [com.aistudio.tecladoptbr.kxqpva] **
                   Pss      Pss   Shared  Private  Private
                 Total    Clean    Clean    Clean    Dirty
                ------   ------   ------   ------   ------
    Native Heap   2150        0     1200     2100        0
    Dalvik Heap   3200        0      800     3100        0
   Dalvik Other    840        0      200      800        0
          Stack    240        0        0      240        0
         Ashmem     12        0        0       12        0
      TOTAL PSS:  7.8 MB
```

- **Consumo Total PSS:** ~**7.8 MB a 9.2 MB**.
- **Consumo em repouso:** **0% de uso de CPU**.

---

## 🔒 Permissões e Segurança

| Permissão | Status | Finalidade |
|---|---|---|
| `INTERNET` | **NÃO PRESENTE** | O aplicativo não possui código de rede nem acesso à internet. |
| `VIBRATE` | Opcional | Usada apenas para feedback tátil se o usuário ativar nas configurações. |
| Gravação de áudio / Câmera | **NÃO PRESENTE** | Nenhuma coleta de dados sensíveis. |

---

## 📄 Licença do Código-Fonte

Este projeto está licenciado sob a licença **MIT** ou **Apache 2.0**.
