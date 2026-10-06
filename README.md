# Restroom for Android

Public restroom finder: Refuge Restrooms data, PottyPins door codes, OpenStreetMap parks and campgrounds,
on a MapLibre map styled by OpenFreeMap. Rams × Bauhaus: paper, ink, one red, one 8 dp grid, no radii, no shadows.
Sibling of [restroom-ios](https://github.com/kevinpostal/restroom-ios).

- `core/` — all logic in C++17 (parsers, door-code heuristics, 0.01° tile cache with stale-while-revalidate,
  ranking, pin matching), unit-tested on the host with GoogleTest. No Android dependencies.
- `app/` — Kotlin/Compose shell: fetches bytes, reads location, hosts the map, renders what the core publishes.
  JNI surface is `Core.kt` ↔ `app/src/main/cpp/jni.cpp`.

## Build

```
tools/bootstrap-mac.sh      # JDK 17, Android SDK/NDK, emulator image, AVD "restroom" (one time)
make host-test              # C++ core tests on the Mac
make build                  # debug APK with the NDK-built core
make emu install run        # boot the emulator, install, launch
make geo LAT=… LON=…        # teleport the emulator's GPS (send after the app is listening)
make uitest                 # instrumented Compose tests on the booted emulator
```
