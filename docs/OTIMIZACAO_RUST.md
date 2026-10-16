# Otimização do núcleo Rust (rust-core/)

Branch: `otimizacao-rust`

## O que mudou

- **`rust-core/src/suggest.rs`**
  - Sem prefixo e sem contexto, as 3 palavras mais frequentes saem de uma passada só (`top_unigrams`), sem clonar nem ordenar o dicionário inteiro. Empates continuam na ordem do dicionário, como antes.
  - Com prefixo, os candidatos guardam `&str` do dicionário e só o resultado final é clonado.
  - A forma minúscula de cada candidato é calculada uma vez por candidato, não duas.
  - `is_plausible_typo` descarta por diferença de tamanho antes de alocar vetores de caracteres.
  - Em `get_auto_correction`, a checagem de nomes (que aloca) roda só para candidatos que já passaram no filtro de digitação. O resultado é o mesmo, pois as duas condições só descartam candidatos.
- **`rust-core/src/jni.rs`**: os quatro `lock().unwrap()` viraram `lock_dict()`, que tolera mutex envenenado. Antes, um pânico em outra thread derrubava o processo do teclado.
- **`rust-core/src/lib.rs`**: dois testes novos (top-3 sem contexto e ordem em empates).

Assinaturas JNI, `Cargo.toml`, `Cargo.lock`, `dictionary.rs`, o código Kotlin, `jniLibs/` e a versão do app não foram alterados.

## Verificação feita

- Suíte Rust: 7 → 9 testes, todos passando.
- Teste de equivalência: comparei o `suggest.rs` novo com o original do Git.
  - 103.052 chamadas com o dicionário real (`dict_pt_br.bin`), entre prefixos, palavras anteriores e variações de digitação: zero divergências.
  - 30.000 dicionários aleatórios com muitos empates de frequência: zero divergências.
- `cargo build --release` no host: sem avisos novos.

## O que ainda não foi verificado

- Compilação para Android (aarch64, armv7, x86_64). Só foi compilado para o host. Quem compila para o APK deve rodar o build completo.
- Ganho real de memória no aparelho. A mudança reduz alocações por tecla, mas o dicionário atual tem só 8,7 KB, então a economia de RAM residente é pequena. Medir antes e depois (ver abaixo).

## Próximos passos (para quem compila)

1. **Recompilar as `.so`** a partir de `rust-core/` com cargo-ndk, para `arm64-v8a`, `armeabi-v7a` e `x86_64`, e substituir as de `app/src/main/jniLibs/`. A `.so` atual é antiga: o próprio `NativeEngine.kt` diz que ela não contém as regras atuais. Obs.: o `Cargo.lock` está na versão 4, que exige um cargo recente (o cargo 1.75 do Ubuntu não o lê).
2. **Autocorreção no Rust:** depois da recompilação, trocar `NativeEngine.getAutoCorrection` para usar `nativeGetAutoCorrection`, mantendo o motor Kotlin como fallback se a chamada falhar. Só então dá para tirar o `CompactTrieEngine` da inicialização, eliminando o dicionário carregado duas vezes.
3. **Medir:** `adb shell dumpsys meminfo <applicationId>` com o teclado aberto e digitando, antes e depois.
4. **Dicionário grande (quando escolhido):** o formato atual guarda uma `String` por palavra. Com um vocabulário grande, convém um blob com offsets, lido por slices, para não ocupar dezenas de MB.

A versão (`versionName` / `versionCode`) continua 1.8 / 9. O bump fica para o build de release.
