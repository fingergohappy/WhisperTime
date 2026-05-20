#!/usr/bin/env bash

if [ -n "${ZSH_VERSION:-}" ]; then
  case "${ZSH_EVAL_CONTEXT:-}" in
    *:file|*:file:*)
      echo "[bootstrap-android-lsp] run with: bash scripts/bootstrap-android-lsp.sh; do not source this script" >&2
      return 1 2>/dev/null || exit 1
      ;;
  esac
fi

if [ -z "${BASH_VERSION:-}" ]; then
  echo "[bootstrap-android-lsp] run with bash: bash scripts/bootstrap-android-lsp.sh" >&2
  return 1 2>/dev/null || exit 1
fi

if [ "${BASH_SOURCE[0]}" != "$0" ]; then
  echo "[bootstrap-android-lsp] run with: bash scripts/bootstrap-android-lsp.sh; do not source this script" >&2
  return 1
fi

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"

KLS_VERSION="${KLS_VERSION:-1.3.13}"
LEMMINX_VERSION="${LEMMINX_VERSION:-0.31.0}"
TAPLO_VERSION="${TAPLO_VERSION:-0.10.0}"

LSP_ROOT="$PROJECT_ROOT/.lsp"
BIN_DIR="$LSP_ROOT/bin"
DOWNLOAD_DIR="$LSP_ROOT/downloads"
KLS_DIR="$LSP_ROOT/kotlin-language-server/$KLS_VERSION"
LEMMINX_DIR="$LSP_ROOT/lemminx/$LEMMINX_VERSION"
TAPLO_DIR="$LSP_ROOT/taplo/$TAPLO_VERSION"

require_command() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "[bootstrap-android-lsp] missing command: $cmd" >&2
    exit 1
  fi
}

download_file() {
  local url="$1"
  local output="$2"
  curl -fL --retry 3 --retry-delay 1 "$url" -o "$output"
}

write_exec_wrapper() {
  local name="$1"
  local target="$2"

  cat >"$BIN_DIR/$name" <<EOF
#!/usr/bin/env sh
exec "$target" "\$@"
EOF
  chmod +x "$BIN_DIR/$name"
}

ensure_project_root() {
  if [ ! -f "$PROJECT_ROOT/settings.gradle.kts" ] || [ ! -f "$PROJECT_ROOT/app/build.gradle.kts" ]; then
    echo "[bootstrap-android-lsp] this script must live under the WhisperTime project root" >&2
    exit 1
  fi
}

install_kotlin_language_server() {
  local archive="$DOWNLOAD_DIR/kotlin-language-server-$KLS_VERSION-server.zip"
  local url="https://github.com/fwcd/kotlin-language-server/releases/download/$KLS_VERSION/server.zip"

  if [ ! -x "$KLS_DIR/bin/kotlin-language-server" ]; then
    local tmp_dir="$KLS_DIR.tmp"
    rm -rf "$tmp_dir"
    mkdir -p "$tmp_dir" "$(dirname "$KLS_DIR")"

    if [ ! -f "$archive" ]; then
      echo "[bootstrap-android-lsp] downloading kotlin-language-server $KLS_VERSION"
      download_file "$url" "$archive"
    fi

    echo "[bootstrap-android-lsp] extracting kotlin-language-server to $KLS_DIR"
    unzip -q "$archive" -d "$tmp_dir"

    local bin_path
    bin_path="$(find "$tmp_dir" -type f -path '*/bin/kotlin-language-server' | head -n 1)"
    if [ -z "$bin_path" ]; then
      echo "[bootstrap-android-lsp] kotlin-language-server archive layout is not recognized" >&2
      exit 1
    fi

    local extracted_root
    extracted_root="$(cd -- "$(dirname -- "$bin_path")/.." && pwd)"

    rm -rf "$KLS_DIR"
    mv "$extracted_root" "$KLS_DIR"
    rm -rf "$tmp_dir"
    chmod +x "$KLS_DIR/bin/kotlin-language-server"
  else
    echo "[bootstrap-android-lsp] existing kotlin-language-server: $KLS_DIR"
  fi

  write_exec_wrapper "kotlin-language-server" "$KLS_DIR/bin/kotlin-language-server"
}

install_lemminx() {
  local jar="$LEMMINX_DIR/org.eclipse.lemminx-$LEMMINX_VERSION-uber.jar"
  local url="https://repo.eclipse.org/content/groups/releases/org/eclipse/lemminx/org.eclipse.lemminx/$LEMMINX_VERSION/org.eclipse.lemminx-$LEMMINX_VERSION-uber.jar"

  mkdir -p "$LEMMINX_DIR"

  if [ ! -f "$jar" ]; then
    echo "[bootstrap-android-lsp] downloading LemMinX $LEMMINX_VERSION"
    download_file "$url" "$jar"
  else
    echo "[bootstrap-android-lsp] existing LemMinX: $jar"
  fi

  cat >"$BIN_DIR/lemminx" <<EOF
#!/usr/bin/env sh
exec java -jar "$jar" "\$@"
EOF
  chmod +x "$BIN_DIR/lemminx"
}

taplo_asset_name() {
  local os_name
  local arch_name
  os_name="$(uname -s)"
  arch_name="$(uname -m)"

  case "$os_name:$arch_name" in
    Darwin:arm64) echo "taplo-darwin-aarch64.gz" ;;
    Darwin:x86_64) echo "taplo-darwin-x86_64.gz" ;;
    Linux:aarch64) echo "taplo-linux-aarch64.gz" ;;
    Linux:arm64) echo "taplo-linux-aarch64.gz" ;;
    Linux:x86_64) echo "taplo-linux-x86_64.gz" ;;
    *)
      echo "[bootstrap-android-lsp] unsupported platform for taplo: $os_name $arch_name" >&2
      exit 1
      ;;
  esac
}

install_taplo() {
  local asset
  asset="$(taplo_asset_name)"

  local archive="$DOWNLOAD_DIR/$asset"
  local url="https://github.com/tamasfe/taplo/releases/download/$TAPLO_VERSION/$asset"
  local taplo_bin="$TAPLO_DIR/taplo"

  mkdir -p "$TAPLO_DIR"

  if [ ! -x "$taplo_bin" ]; then
    if [ ! -f "$archive" ]; then
      echo "[bootstrap-android-lsp] downloading taplo $TAPLO_VERSION"
      download_file "$url" "$archive"
    fi

    echo "[bootstrap-android-lsp] extracting taplo to $TAPLO_DIR"
    gzip -dc "$archive" >"$taplo_bin"
    chmod +x "$taplo_bin"
  else
    echo "[bootstrap-android-lsp] existing taplo: $taplo_bin"
  fi

  write_exec_wrapper "taplo" "$taplo_bin"
}

ensure_project_root
require_command curl
require_command unzip
require_command gzip
require_command java

mkdir -p "$BIN_DIR" "$DOWNLOAD_DIR"

install_kotlin_language_server
install_lemminx
install_taplo

echo "[bootstrap-android-lsp] done"
echo "[bootstrap-android-lsp] kotlin-language-server: $BIN_DIR/kotlin-language-server"
echo "[bootstrap-android-lsp] lemminx: $BIN_DIR/lemminx"
echo "[bootstrap-android-lsp] taplo: $BIN_DIR/taplo"
