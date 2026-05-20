#!/usr/bin/env sh
# Usage:
#   1. Run from the project root: bash scripts/bootstrap-android-lsp.sh
#   2. Then source this file: source env-lsp.sh
#   3. Start nvim from the same shell

path_prepend() {
  case ":$PATH:" in
    *":$1:"*) ;;
    *) export PATH="$1:$PATH" ;;
  esac
}

fail_env_lsp() {
  echo "[env-lsp] $*" >&2
  return 1 2>/dev/null || exit 1
}

PROJECT_ROOT="$(pwd -P)"
LSP_ROOT="$PROJECT_ROOT/.lsp"
LSP_BIN_DIR="$LSP_ROOT/bin"
LOCAL_PROPERTIES="$PROJECT_ROOT/local.properties"

if [ ! -f "$PROJECT_ROOT/settings.gradle.kts" ] || [ ! -f "$PROJECT_ROOT/app/build.gradle.kts" ]; then
  fail_env_lsp "run from the WhisperTime project root: source env-lsp.sh"
fi

if command -v asdf >/dev/null 2>&1; then
  ASDF_JAVA_HOME="$(cd "$PROJECT_ROOT" && asdf where java 2>/dev/null)" || ASDF_JAVA_HOME=""
  if [ -n "$ASDF_JAVA_HOME" ] && [ -x "$ASDF_JAVA_HOME/bin/java" ]; then
    export JAVA_HOME="$ASDF_JAVA_HOME"
    path_prepend "$JAVA_HOME/bin"
  fi
fi

if [ -f "$LOCAL_PROPERTIES" ]; then
  ANDROID_SDK_DIR="$(awk -F= '$1 == "sdk.dir" { print $2; exit }' "$LOCAL_PROPERTIES")"
  if [ -n "$ANDROID_SDK_DIR" ] && [ -d "$ANDROID_SDK_DIR" ]; then
    export ANDROID_HOME="$ANDROID_SDK_DIR"
    export ANDROID_SDK_ROOT="$ANDROID_SDK_DIR"
    path_prepend "$ANDROID_HOME/platform-tools"
    path_prepend "$ANDROID_HOME/cmdline-tools/latest/bin"
  fi
fi

if ! command -v java >/dev/null 2>&1; then
  fail_env_lsp "java is required for kotlin-language-server and LemMinX"
fi

if [ ! -x "$LSP_BIN_DIR/kotlin-language-server" ]; then
  fail_env_lsp "missing project kotlin-language-server; run: bash scripts/bootstrap-android-lsp.sh"
fi

if [ ! -x "$LSP_BIN_DIR/lemminx" ]; then
  fail_env_lsp "missing project LemMinX; run: bash scripts/bootstrap-android-lsp.sh"
fi

if [ ! -x "$LSP_BIN_DIR/taplo" ]; then
  fail_env_lsp "missing project taplo; run: bash scripts/bootstrap-android-lsp.sh"
fi

path_prepend "$LSP_BIN_DIR"

export WHISPERTIME_LSP_ROOT="$LSP_ROOT"
export WHISPERTIME_KOTLIN_LANGUAGE_SERVER="$LSP_BIN_DIR/kotlin-language-server"
export WHISPERTIME_LEMMINX="$LSP_BIN_DIR/lemminx"
export WHISPERTIME_TAPLO="$LSP_BIN_DIR/taplo"
