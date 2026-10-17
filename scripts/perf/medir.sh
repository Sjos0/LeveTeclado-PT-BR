#!/usr/bin/env bash
# Mede RAM e armazenamento do Termclado em um emulador Android (execução no GitHub Actions).
# Uso: scripts/perf/medir.sh <pasta_de_saida> <caminho_do_apk_debug>
set -euo pipefail

OUT="${1:-perf-results}"
APK="${2:-app/build/outputs/apk/debug/app-debug.apk}"
PKG="com.aistudio.tecladoptbr.kxqpva"
IME="$PKG/com.example.keyboard.LatinIMEKeyboardService"
mkdir -p "$OUT"
RESUMO="$OUT/resumo.txt"
: > "$RESUMO"

log() { echo "$*" | tee -a "$RESUMO"; }

adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 3; done
adb shell input keyevent 82 >/dev/null 2>&1 || true

log "== Instalação =="
adb install -r "$APK" | tr -d '\r' | tee -a "$RESUMO"

log "== Ativa o Termclado como teclado do sistema =="
adb shell ime enable "$IME" | tr -d '\r' | tee -a "$RESUMO"
adb shell ime set "$IME" | tr -d '\r' | tee -a "$RESUMO"

log "== Abre o campo de teste (teclado visível) =="
adb shell am start -W -n "$PKG/com.example.DevTypingActivity" | tr -d '\r' | tee -a "$RESUMO"
sleep 6

snapshot() {
  local nome="$1"
  adb shell dumpsys meminfo "$PKG" | tr -d '\r' > "$OUT/$nome-meminfo.txt"
  local pss
  pss=$(grep -E "TOTAL PSS|TOTAL:" "$OUT/$nome-meminfo.txt" | head -1 | grep -oE "[0-9]+" | head -1 || echo "0")
  log "RAM ($nome): ${pss:-0} KB de PSS total"
}

snapshot "teclado-visivel"

log "== Armazenamento =="
apk_path=$(adb shell pm path "$PKG" | tr -d '\r' | sed 's/^package://' | head -1)
apk_kb=$(adb shell stat -c %s "$apk_path" | tr -d '\r')
log "APK instalado: $((apk_kb / 1024)) KB ($apk_path)"
dados=$(adb shell run-as "$PKG" du -sk . 2>/dev/null | tr -d '\r' | awk '{print $1}' || true)
log "Dados privados do app: ${dados:-indisponível} KB"

log "== Estado da janela (confirma que o teclado está ativo) =="
adb shell dumpsys input_method | tr -d '\r' | grep -E "mCurMethodId|mInputShown|mIsInteractive" | head -5 | tee -a "$RESUMO" || true

adb shell am force-stop "$PKG" || true
log "Concluído. Resultados em $OUT/"
