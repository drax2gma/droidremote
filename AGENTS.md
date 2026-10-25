# AGENTS.md — droidremote

Android app, ami a saját accessibility service-én keresztül olvassa a képernyőt és
injektál inputot. **Nincs root, nincs MediaProjection, nincs ESP32.**

## Build

```bash
export JAVA_HOME=/home/linuxbrew/.linuxbrew/opt/openjdk@21
./gradlew :app:assembleDebug          # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest      # JVM teszt, eszköz nélkül
```

- **JDK 21 kell**, nem 27. A brew `gradle` (9.8.2) behúzta az openjdk 27-et és átlinkelte
  a `java`-t; az AGP 8.7.3 + Gradle 8.11.1 kombó ezzel nem megy. Ezért van a wrapper
  (8.11.1) és a fenti `JAVA_HOME`.
- SDK: `/home/linuxbrew/.linuxbrew/share/android-commandlinetools` (brew
  `android-commandlinetools`, platform 35 + build-tools 35). `local.properties`-ben,
  gitignore-olva.
- `compileSdk 35`, `targetSdk 35`, **`minSdk 30`** — a `takeScreenshot` API 30-as, ezért
  nincs MediaProjection-ág.

## Eszköz (Pixel 2, Android 11 / API 30)

A telefon `192.168.1.148`, de a **wireless debugging port véletlen** (reboot után új):

```bash
adb connect 192.168.1.148:5555                    # ha USB-ről adb tcpip 5555 volt bekapcsolva
nmap -p 30000-60000 --open -T4 192.168.1.148      # reboot után: az egyetlen nyitott port a debug port
adb connect 192.168.1.148:<port>
adb -s 192.168.1.148:<port> install -r app/build/outputs/apk/debug/app-debug.apk
```

### a11y engedély automatizálása (fejlesztéshez)

Az engedélyt a UI-ból kell megadni, de **adb-ból bekapcsolható** (shell-nek van
WRITE_SECURE_SETTINGS):

```bash
adb shell settings put secure enabled_accessibility_services \
  hu.gmz.droidremote/hu.gmz.droidremote.service.ObserverService
adb shell settings put secure accessibility_enabled 1
adb shell dumpsys accessibility | grep -A3 droidremote   # ellenőrzés
```

Kikapcsolás: `settings put secure enabled_accessibility_services null` (vagy üres string).

### Trace kiolvasása (verifikáció UI nélkül)

Debug build → `run-as` működik:

```bash
adb shell run-as hu.gmz.droidremote ls files/runs
adb shell run-as hu.gmz.droidremote cat files/runs/<id>/steps.jsonl
adb exec-out run-as hu.gmz.droidremote cat files/runs/<id>/shot-001.jpg > /tmp/shot.jpg
```

## Verifikációs sorrend

1. `./gradlew :app:testDebugUnitTest` — a tiszta rétegek (fa-szerializáló, action codec,
   settle detektor) eszköz nélkül.
2. `assembleDebug` + `adb install -r`.
3. a11y be az adb-s trükkel (fent).
4. Indítsd el az appot, **Megfigyelés → Frissítés**: a fa és a node-szám jelenjen meg.
5. **Képernyőkép**: `takeScreenshot` működik-e (API 30, throttle 350 ms).
6. **Script** mód: `[{"a":"key","k":"HOME"},{"a":"wait","ms":800}]` — modell nélkül
   verifikálja az aktort. A trace-ből ellenőrizhető.
7. Csak ezután agent loop (modell endpoint kell).

## Csapdák

- **Reinstall / force-stop után az a11y engedély elveszhet** (`Enabled services:{}`). Ilyenkor
  a `settings put secure enabled_accessibility_services` újra kell. Ellenőrzés:
  `adb shell dumpsys accessibility | grep -E 'Bound services|Enabled services'`.
  Jó jel: `capabilities=161` (= retrieve content + perform gestures + take screenshot).
- **Az IME kitakarja az alsó gombokat.** Ha a UI-t adb-ből vezérled, a mező fókuszálása
  után `input keyevent 4`-gyel zárd be a billentyűzetet, különben a koppintás a billentyűzetre megy.
- **A node-id csak az adott megfigyelésre érvényes.** Minden akció után új fa kell;
  egy régi node-ra hívott `performAction` false-t ad (a gesture-fallback ilyenkor lefut).
- **`takeScreenshot` throttle**: `ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT` ~350 ms alatt.
  `FLAG_SECURE` esetén `-5` (secure window) — ez nem hiba, hanem korlát.
- **`dispatchGesture` aszinkron**: a callback számít, nem a visszatérési érték. A user
  érintése `onCancelled`-t ad → az akció "eldobva", nem szabad ugyanazt ismételni.
- **`ACTION_SET_TEXT` nem univerzális**: custom mező elutasíthatja. Ilyenkor nem szabad
  a képernyő-billentyűzetet vision-nel nyomkodni, amíg nincs rá külön implementáció.
- **A11y fa lehet üres** (Unity/játék/canvas): ilyenkor `{"a":"shot"}` és pixel-koordináta.
- **`AccessibilityNodeInfo` ne legyen hosszú életű** referencia: az `Observation` egy
  pillanatkép; a UI-ban a `lastObservation`-t használjuk, de akció előtt frissítünk.
- **Az app nem indíthatja magától a loopot** — ez szándékos biztonsági döntés.

## Kódkonvenciók

- A `observe/` fa-modellje és a `TreeSerializer`, valamint az `act/ActionCodec` **Android-
  független** — ezt ne rontsd el, mert ez a JVM-tesztelhetőség alapja.
- A promptok egyetlen igazságforrása: `app/src/main/assets/prompts/*.md`. Ne duplikáld
  Kotlin stringbe.
- A trace formátuma (`runs/<id>/meta.json`, `steps.jsonl`) stabil: külső elemző (agent)
  olvassa.
- Secret soha nem kerül a repóba. Az API kulcs futásidőben, SharedPreferences-ben.
