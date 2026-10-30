#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# CI helper. A zero sdkmanager status alone is not proof that its packages exist.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
: "${ANDROID_HOME:?ANDROID_HOME must name the Android SDK root}"
SDK="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
LOG="${R54_SDK_LOG:-sdk-setup.log}"
ATTEMPTS="${R54_SDK_ATTEMPTS:-3}"
DELAY="${R54_SDK_RETRY_DELAY:-5}"
[[ "$ATTEMPTS" =~ ^[1-3]$ && "$DELAY" =~ ^([0-9]|[12][0-9]|30)$ ]] || {
  echo 'SDK retry settings must be 1..3 attempts and 0..30 seconds' >&2
  exit 2
}
: > "$LOG"
printf 'SDK root: %s\n' "$ANDROID_HOME" >> "$LOG"

fail() {
  printf 'error: %s\n' "$*" >> "$LOG"
  python3 tools/ci_annotations.py "$LOG" sdk-install
  exit 1
}
[[ -x "$SDK" ]] || fail "sdkmanager is missing or not executable: $SDK"

platform_ready() {
  [[ -f "$ANDROID_HOME/platforms/android-37.0/android.jar" && \
     -x "$ANDROID_HOME/build-tools/36.0.0/aapt2" && \
     -x "$ANDROID_HOME/platform-tools/adb" ]]
}
emulator_ready() {
  local executable="$ANDROID_HOME/emulator/emulator" result output line
  if [[ ! -x "$executable" ]]; then
    printf 'error: emulator executable is missing or not executable: %s\n' "$executable" >> "$LOG"
    return 1
  fi
  if output=$("$executable" -version 2>&1); then
    printf '%s\n' "$output" >> "$LOG"
    return 0
  else
    result=$?
    printf 'error: emulator -version exited %s\n' "$result" >> "$LOG"
    # Prefix each line so the bounded CI annotation preserves the actual loader message.
    while IFS= read -r line; do printf 'error: emulator output: %s\n' "$line" >> "$LOG"; done <<< "$output"
    # ldd is diagnostic only. Its absence or failure must not hide the emulator failure.
    if command -v ldd >/dev/null 2>&1; then ldd "$executable" >> "$LOG" 2>&1 || true; fi
    return 1
  fi
}
image_ready() {
  [[ -f "$ANDROID_HOME/system-images/android-34/google_apis/x86_64/system.img" ]]
}

install_and_check() {
  local label="$1" probe="$2"; shift 2
  local attempt sdk_status
  for ((attempt=1; attempt<=ATTEMPTS; attempt++)); do
    printf '\n%s attempt %s/%s\n' "$label" "$attempt" "$ATTEMPTS" >> "$LOG"
    if "$SDK" "$@" >> "$LOG" 2>&1; then sdk_status=0; else sdk_status=$?; fi
    printf 'sdkmanager exit: %s\n' "$sdk_status" >> "$LOG"
    if ((sdk_status == 0)) && "$probe"; then
      printf '%s packages passed existence/executability checks\n' "$label" >> "$LOG"
      echo "$label SDK components passed local installation checks."
      return 0
    fi
    printf 'error: %s installation or file/version probe failed on attempt %s\n' "$label" "$attempt" >> "$LOG"
    if ((attempt < ATTEMPTS)); then sleep "$DELAY"; fi
  done
  fail "$label components were not verified after $ATTEMPTS attempts; see the setup log"
}

install_and_check 'Android build' platform_ready \
  'platforms;android-37.0' 'build-tools;36.0.0' 'platform-tools'
install_and_check 'Emulator' emulator_ready 'emulator'
install_and_check 'API 34 system image' image_ready 'system-images;android-34;google_apis;x86_64'
echo 'SDK components installed and checked; emulator boot and device tests remain separate gates.'
