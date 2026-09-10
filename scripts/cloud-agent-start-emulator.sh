#!/usr/bin/env bash
# Per-boot: KVM access + Android emulator readiness. Idempotent. Does not install SDKs.
# Best-effort: logs a clear status and exits 0 so agents can still assembleDebug if the AVD cannot boot.
set -u

export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-/home/ubuntu/android-sdk}"
export ANDROID_HOME="${ANDROID_HOME:-$ANDROID_SDK_ROOT}"
export PATH="${JAVA_HOME}/bin:${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/emulator:${ANDROID_SDK_ROOT}/platform-tools:${PATH}"

AVD_NAME="${SOUNDGROOVE_AVD_NAME:-SoundGroove_ATD34}"
STATUS_FILE="${SOUNDGROOVE_EMU_STATUS:-/tmp/soundgroove-emulator.status}"
LOG_FILE="${SOUNDGROOVE_EMU_LOG:-/tmp/soundgroove-emulator.log}"
BOOT_TIMEOUT_SEC="${SOUNDGROOVE_EMU_BOOT_TIMEOUT_SEC:-300}"
# Nested KVM is often advertised (/dev/kvm) but the guest never runs (qemu ~0% CPU).
# TCG (-accel off) is slow but boots in this Cloud Agent VM.
ACCEL="${SOUNDGROOVE_EMU_ACCEL:-off}"
ADB="${ANDROID_SDK_ROOT}/platform-tools/adb"
EMU="${ANDROID_SDK_ROOT}/emulator/emulator"

write_status() {
  echo "$1" | tee "${STATUS_FILE}"
}

if [[ ! -x "${EMU}" ]]; then
  write_status "failed: emulator binary missing at ${EMU}"
  exit 0
fi

# Nested virt: allow the agent user to use KVM without a new login session.
if [[ -e /dev/kvm ]]; then
  sudo chmod 666 /dev/kvm 2>/dev/null || true
fi

if [[ ! -e /dev/kvm ]]; then
  write_status "warn: /dev/kvm missing — will try software accel"
fi

mkdir -p "${HOME}/.android"
if ! avdmanager list avd 2>/dev/null | grep -q "Name: ${AVD_NAME}"; then
  if avdmanager list avd 2>/dev/null | grep -q "Name: SoundGroove_API34"; then
    AVD_NAME="SoundGroove_API34"
  else
    write_status "failed: AVD ${AVD_NAME} missing — run scripts/cloud-agent-install.sh"
    exit 0
  fi
fi

"${ADB}" start-server >/dev/null 2>&1 || true

device_online() {
  "${ADB}" devices 2>/dev/null | awk 'NR>1 && $2=="device" {print $1; exit}'
}

boot_completed() {
  local serial
  serial="$(device_online)"
  [[ -n "${serial}" ]] || return 1
  [[ "$("${ADB}" -s "${serial}" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]
}

push_test_audio() {
  local serial
  serial="$(device_online)"
  [[ -n "${serial}" ]] || return 0
  local src="/tmp/soundgroove-test-audio"
  mkdir -p "${src}"
  if [[ ! -f "${src}/sg_tone_a.wav" ]]; then
    ffmpeg -y -f lavfi -i "sine=frequency=440:duration=8" -ar 44100 -ac 1 "${src}/sg_tone_a.wav" >/dev/null 2>&1 || return 0
    ffmpeg -y -f lavfi -i "sine=frequency=660:duration=8" -ar 44100 -ac 1 "${src}/sg_tone_b.wav" >/dev/null 2>&1 || true
  fi
  "${ADB}" -s "${serial}" shell mkdir -p /sdcard/Music >/dev/null 2>&1 || true
  "${ADB}" -s "${serial}" push "${src}/sg_tone_a.wav" /sdcard/Music/sg_tone_a.wav >/dev/null 2>&1 || true
  if [[ -f "${src}/sg_tone_b.wav" ]]; then
    "${ADB}" -s "${serial}" push "${src}/sg_tone_b.wav" /sdcard/Music/sg_tone_b.wav >/dev/null 2>&1 || true
  fi
  "${ADB}" -s "${serial}" shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Music/sg_tone_a.wav >/dev/null 2>&1 || true
}

if boot_completed; then
  push_test_audio
  write_status "ready: emulator already booted ($(device_online))"
  exit 0
fi

if [[ -z "$(device_online)" ]]; then
  echo "[cloud-agent-start] launching ${AVD_NAME} accel=${ACCEL}" | tee -a "${LOG_FILE}"
  nohup "${EMU}" -avd "${AVD_NAME}" \
    -no-window -no-audio -no-boot-anim \
    -gpu swiftshader_indirect \
    -accel "${ACCEL}" \
    -memory 1536 -cores 2 \
    -no-snapshot-save -no-snapshot-load \
    -netdelay none -netspeed full \
    >> "${LOG_FILE}" 2>&1 &
  echo $! > /tmp/soundgroove-emulator.pid
fi

elapsed=0
while (( elapsed < BOOT_TIMEOUT_SEC )); do
  if boot_completed; then
    "${ADB}" wait-for-device >/dev/null 2>&1 || true
    push_test_audio
    write_status "ready: boot completed in ${elapsed}s ($(device_online))"
    exit 0
  fi
  sleep 3
  elapsed=$((elapsed + 3))
done

write_status "failed: emulator did not boot in ${BOOT_TIMEOUT_SEC}s — see ${LOG_FILE}"
exit 0
