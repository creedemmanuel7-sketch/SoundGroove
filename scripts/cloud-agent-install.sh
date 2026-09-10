#!/usr/bin/env bash
# Idempotent Cloud Agent bootstrap for SoundGroove (Android + Flutter + website).
# Safe to rerun. Does not start servers or run tests.
set -euo pipefail

JAVA_HOME_DEFAULT="/usr/lib/jvm/java-21-openjdk-amd64"
if [[ ! -d "${JAVA_HOME_DEFAULT}" ]]; then
  JAVA_HOME_DEFAULT="/usr/lib/jvm/java-21-openjdk-amd64"
fi
if [[ ! -x "${JAVA_HOME_DEFAULT}/bin/java" ]]; then
  # Fallback: resolve from javac/java on PATH.
  if command -v javac >/dev/null 2>&1; then
    JAVA_HOME_DEFAULT="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
  elif command -v java >/dev/null 2>&1; then
    JAVA_HOME_DEFAULT="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"
  fi
fi

export JAVA_HOME="${JAVA_HOME:-$JAVA_HOME_DEFAULT}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-/home/ubuntu/android-sdk}"
export ANDROID_HOME="${ANDROID_HOME:-$ANDROID_SDK_ROOT}"
FLUTTER_HOME="${FLUTTER_HOME:-/home/ubuntu/flutter}"
CMDLINE_TOOLS_ZIP_URL="${CMDLINE_TOOLS_ZIP_URL:-https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip}"

export PATH="${JAVA_HOME}/bin:${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/emulator:${ANDROID_SDK_ROOT}/platform-tools:${FLUTTER_HOME}/bin:${PATH}"

echo "[cloud-agent-install] JAVA_HOME=${JAVA_HOME}"
"${JAVA_HOME}/bin/java" -version

persist_env() {
  local snippet
  snippet="$(cat <<EOF
export JAVA_HOME="${JAVA_HOME}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT}"
export ANDROID_HOME="${ANDROID_SDK_ROOT}"
export PATH="\${JAVA_HOME}/bin:\${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:\${ANDROID_SDK_ROOT}/emulator:\${ANDROID_SDK_ROOT}/platform-tools:${FLUTTER_HOME}/bin:\${PATH}"
EOF
)"
  mkdir -p "${HOME}"
  local marker="# SoundGroove Cloud Agent toolchain"
  local profile="${HOME}/.bashrc"
  touch "${profile}"
  if ! grep -q "${marker}" "${profile}" 2>/dev/null; then
    {
      echo ""
      echo "${marker}"
      echo "${snippet}"
    } >> "${profile}"
  fi
  local profile2="${HOME}/.profile"
  touch "${profile2}"
  if ! grep -q "${marker}" "${profile2}" 2>/dev/null; then
    {
      echo ""
      echo "${marker}"
      echo "${snippet}"
    } >> "${profile2}"
  fi
}

install_android_sdk() {
  mkdir -p "${ANDROID_SDK_ROOT}"
  local sdkmanager="${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin/sdkmanager"
  if [[ ! -x "${sdkmanager}" ]]; then
    echo "[cloud-agent-install] Installing Android cmdline-tools"
    local tmp
    tmp="$(mktemp -d)"
    curl -fsSL -o "${tmp}/cmdline-tools.zip" "${CMDLINE_TOOLS_ZIP_URL}"
    unzip -q -o "${tmp}/cmdline-tools.zip" -d "${tmp}"
    mkdir -p "${ANDROID_SDK_ROOT}/cmdline-tools/latest"
    # Zip root is either cmdline-tools/ or cmdline-tools/bin depending on vintage.
    if [[ -d "${tmp}/cmdline-tools/bin" ]]; then
      cp -a "${tmp}/cmdline-tools/." "${ANDROID_SDK_ROOT}/cmdline-tools/latest/"
    elif [[ -d "${tmp}/cmdline-tools/latest" ]]; then
      cp -a "${tmp}/cmdline-tools/latest/." "${ANDROID_SDK_ROOT}/cmdline-tools/latest/"
    else
      cp -a "${tmp}/." "${ANDROID_SDK_ROOT}/cmdline-tools/latest/"
    fi
    rm -rf "${tmp}"
  fi
  sdkmanager="${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin/sdkmanager"
  yes | "${sdkmanager}" --sdk_root="${ANDROID_SDK_ROOT}" --licenses >/dev/null || true
  "${sdkmanager}" --sdk_root="${ANDROID_SDK_ROOT}" --install \
    "platform-tools" \
    "platforms;android-36" \
    "platforms;android-34" \
    "build-tools;36.0.0" \
    "build-tools;35.0.0" \
    "emulator" \
    "system-images;android-34;google_apis;x86_64"
  create_avd_if_needed
}

create_avd_if_needed() {
  local avdmanager="${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin/avdmanager"
  local avd_name="${SOUNDGROOVE_AVD_NAME:-SoundGroove_API34}"
  if [[ ! -x "${avdmanager}" ]]; then
    echo "[cloud-agent-install] skip AVD (avdmanager missing)"
    return 0
  fi
  if "${avdmanager}" list avd 2>/dev/null | grep -q "Name: ${avd_name}"; then
    echo "[cloud-agent-install] AVD ${avd_name} already exists"
    return 0
  fi
  echo "[cloud-agent-install] Creating AVD ${avd_name}"
  echo "no" | "${avdmanager}" create avd \
    --name "${avd_name}" \
    --package "system-images;android-34;google_apis;x86_64" \
    --device "pixel_6" \
    --force
  local config="${HOME}/.android/avd/${avd_name}.avd/config.ini"
  if [[ -f "${config}" ]]; then
    {
      echo "hw.keyboard=yes"
      echo "hw.ramSize=2048"
      echo "hw.gpu.enabled=yes"
      echo "hw.gpu.mode=swiftshader_indirect"
    } >> "${config}"
  fi
}

install_flutter() {
  if [[ ! -x "${FLUTTER_HOME}/bin/flutter" ]]; then
    echo "[cloud-agent-install] Installing Flutter SDK (stable)"
    if [[ -d "${FLUTTER_HOME}/.git" ]]; then
      git -C "${FLUTTER_HOME}" fetch --depth 1 origin stable
      git -C "${FLUTTER_HOME}" checkout -B stable FETCH_HEAD
    else
      rm -rf "${FLUTTER_HOME}"
      git clone --depth 1 -b stable https://github.com/flutter/flutter.git "${FLUTTER_HOME}"
    fi
  fi
  "${FLUTTER_HOME}/bin/flutter" config --no-analytics >/dev/null
  "${FLUTTER_HOME}/bin/flutter" precache --android
  "${FLUTTER_HOME}/bin/flutter" --version
}

write_local_properties() {
  local root="${REPO_ROOT:-$(pwd)}"
  if [[ ! -f "${root}/settings.gradle.kts" && -f "/workspace/settings.gradle.kts" ]]; then
    root="/workspace"
  fi
  local file="${root}/local.properties"
  cat > "${file}" <<EOF
sdk.dir=${ANDROID_SDK_ROOT}
flutter.sdk=${FLUTTER_HOME}
EOF
  echo "[cloud-agent-install] Wrote ${file}"
}

install_website() {
  local root="${REPO_ROOT:-$(pwd)}"
  if [[ ! -d "${root}/website" && -d "/workspace/website" ]]; then
    root="/workspace"
  fi
  if [[ -f "${root}/website/package-lock.json" ]]; then
    echo "[cloud-agent-install] npm ci (website)"
    (cd "${root}/website" && npm ci)
  elif [[ -f "${root}/website/package.json" ]]; then
    echo "[cloud-agent-install] npm install (website, no lockfile)"
    (cd "${root}/website" && npm install)
  fi
}

gradle_wrapper_deps() {
  local root="${REPO_ROOT:-$(pwd)}"
  if [[ ! -x "${root}/gradlew" && -x "/workspace/gradlew" ]]; then
    root="/workspace"
  fi
  if [[ -f "${root}/gradlew" ]]; then
    chmod +x "${root}/gradlew"
    echo "[cloud-agent-install] Gradle wrapper + help (download dist)"
    (cd "${root}" && ./gradlew --version)
    # `help` configure le projet : ne pas masquer un échec (pas de ||).
    (cd "${root}" && ./gradlew help)
  fi
}

flutter_pub_get_if_module() {
  local root="${REPO_ROOT:-$(pwd)}"
  if [[ ! -f "${root}/flutter_queue/pubspec.yaml" && -f "/workspace/flutter_queue/pubspec.yaml" ]]; then
    root="/workspace"
  fi
  if [[ -f "${root}/flutter_queue/pubspec.yaml" && -x "${FLUTTER_HOME}/bin/flutter" ]]; then
    echo "[cloud-agent-install] flutter pub get (flutter_queue)"
    (cd "${root}/flutter_queue" && "${FLUTTER_HOME}/bin/flutter" pub get)
  else
    echo "[cloud-agent-install] skip flutter pub get (module absent)"
  fi
}

persist_env
install_android_sdk
install_flutter
write_local_properties
install_website
flutter_pub_get_if_module
gradle_wrapper_deps

echo "[cloud-agent-install] done"
java -version
sdkmanager --list_installed 2>/dev/null | head -n 40 || "${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin/sdkmanager" --sdk_root="${ANDROID_SDK_ROOT}" --list_installed | head -n 40
flutter --version | head -n 4
echo "sdk.dir=$(grep sdk.dir "${REPO_ROOT:-$(pwd)}/local.properties" 2>/dev/null || grep sdk.dir /workspace/local.properties)"
