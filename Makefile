# Restroom for Android. Host-side C++ tests run with the Mac toolchain; everything else needs the
# Android SDK (tools/bootstrap-mac.sh installs it). Override LAT/LON for `make geo`.

export JAVA_HOME ?= /opt/homebrew/opt/openjdk@17
export ANDROID_HOME ?= $(HOME)/Library/Android/sdk
ADB := $(ANDROID_HOME)/platform-tools/adb
EMULATOR := $(ANDROID_HOME)/emulator/emulator
AVD ?= restroom
APK := app/build/outputs/apk/debug/app-debug.apk
PKG := com.kevinpostal.restroom
LAT ?= 40.7581859
LON ?= -73.9895087
SHOT ?= /tmp/android.png

.PHONY: host-test build emu install run geo shot uitest clean

## C++ core unit tests on the Mac (GoogleTest, no Android needed).
host-test:
	cmake -S core -B build-host -G Ninja -DRESTROOM_BUILD_TESTS=ON -DCMAKE_BUILD_TYPE=Debug
	cmake --build build-host
	ctest --test-dir build-host --output-on-failure

## Debug APK with the NDK-built core.
build:
	./gradlew assembleDebug

## Boot the emulator and wait for Android to finish starting.
emu:
	$(EMULATOR) -avd $(AVD) -gpu host -no-snapshot -no-boot-anim >/tmp/emu.log 2>&1 &
	$(ADB) wait-for-device
	@until [ "$$($(ADB) shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done
	@echo "emulator booted"

install:
	$(ADB) install -r $(APK)
	$(ADB) shell pm grant $(PKG) android.permission.ACCESS_FINE_LOCATION || true

run:
	$(ADB) shell am start -n $(PKG)/.MainActivity

## Teleport the emulator's GPS (adb takes longitude first).
geo:
	$(ADB) emu geo fix $(LON) $(LAT)

shot:
	$(ADB) exec-out screencap -p > $(SHOT)
	@echo "wrote $(SHOT)"

## Instrumented Compose tests on the booted emulator.
uitest:
	./gradlew connectedDebugAndroidTest

clean:
	rm -rf build-host app/build app/.cxx .gradle build
