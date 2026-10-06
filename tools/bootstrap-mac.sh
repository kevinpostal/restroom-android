#!/bin/sh
# One-time Android toolchain for macOS (arm64). Idempotent. No sudo: JDK via the Homebrew formula,
# command-line tools straight from Google, everything else via sdkmanager into ~/Library/Android/sdk.
set -eu
brew list openjdk@17 >/dev/null 2>&1 || brew install openjdk@17
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export ANDROID_HOME="$HOME/Library/Android/sdk"
if [ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  tmp=$(mktemp -d)
  curl -sL -o "$tmp/clt.zip" https://dl.google.com/android/repository/commandlinetools-mac-11076708_latest.zip
  unzip -qo "$tmp/clt.zip" -d "$tmp"
  rm -rf "$ANDROID_HOME/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
fi
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
yes | sdkmanager --licenses >/dev/null 2>&1 || true
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0" "ndk;27.2.12479018" "cmake;3.22.1" \
  "emulator" "system-images;android-35;google_apis;arm64-v8a"
emulator -list-avds | grep -qx restroom || \
  echo no | avdmanager create avd -n restroom -k "system-images;android-35;google_apis;arm64-v8a" -d pixel_6
echo "sdk.dir=$ANDROID_HOME" > "$(dirname "$0")/../local.properties"
echo "ready: JAVA_HOME=$JAVA_HOME ANDROID_HOME=$ANDROID_HOME"
