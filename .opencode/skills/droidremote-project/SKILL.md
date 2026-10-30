---
name: droidremote-project
description: droidremote — AI agent, ami a telefonon belül vezérel egy Android készüléket (AccessibilityService fa + takeScreenshot + dispatchGesture, root és board nélkül). Használd amikor a /var/home/gma/git/Android/droidremote repóban dolgozol, Android accessibility-alapú automatizálást/agentet építesz, a Pixel 2-n adb-ből verifikálsz, vagy a takeScreenshot / dispatchGesture / ACTION_SET_TEXT / settle-detektálás kerül szóba. Tartalmazza a mért csapdákat (a11y engedély elveszik reinstall után, IME kitakarja a gombokat, brew JDK 27 vs AGP, Gradle 9 wrapper-generálás).
---

# droidremote

Android app, ami **a saját `AccessibilityService`-én** keresztül olvassa a képernyőt és
injektál inputot; a döntést OpenAI-kompatibilis modell hozza. Nincs root, nincs
MediaProjection, nincs ESP32. Az ötlet az [ESP32 iPhone Agent](https://github.com/onehumanbeing/esp32-iphone-agent)
(MIT) Android-adaptációja — a kód nem lett átvéve.

Repo: `/var/home/gma/git/Android/droidremote` (origin GitHub + gitea `gma/droidremote`).
Csomag: `hu.gmz.droidremote`. `minSdk 30`, `targetSdk 35`, Kotlin + Compose.

## Build

```bash
cd /var/home/gma/git/Android/droidremote
export JAVA_HOME=/home/linuxbrew/.linuxbrew/opt/openjdk@21   # KÖTELEZŐ, lásd csapdák
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

- SDK: `/home/linuxbrew/.linuxbrew/share/android-commandlinetools` (brew
  `android-commandlinetools`), `local.properties`-ben (gitignore).
- Wrapper: Gradle 8.11.1, AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.10.01.
- APK: `app/build/outputs/apk/debug/app-debug.apk` (~25 MB, debug).

## Eszköz-verifikáció (Pixel 2, Android 11 / API 30)

```bash
adb connect 192.168.1.148:5555                     # ha USB-ről adb tcpip 5555 be volt kapcsolva
nmap -p 30000-60000 --open -T4 192.168.1.148       # reboot után a wireless debug port VÉLETLEN
adb connect 192.168.1.148:<port>
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n hu.gmz.droidremote/.ui.MainActivity
```

### a11y engedély adb-ból (a UI-t nem kell nyomkodni)

```bash
adb shell settings put secure enabled_accessibility_services \
  hu.gmz.droidremote/hu.gmz.droidremote.service.ObserverService
adb shell settings put secure accessibility_enabled 1
adb shell dumpsys accessibility | grep -E 'Bound services|Enabled services'
```

Jó jel: `capabilities=161` (= retrieve window content + perform gestures + take screenshot).
**Sorrend számít:** app indítás után állítsd be (force-stop → put → start sorrendnél
üres maradt az `Enabled services`).

### Debug export (UI nélküli ellenőrzés)

Az app a `filesDir/debug/` alá ír: `observe.txt` (utolsó fa), `observe-meta.json`,
`last.jpg` (utolsó kép), `log.txt` (napló), `last-action.txt`. Debug build → `run-as`:

```bash
adb shell run-as hu.gmz.droidremote cat files/debug/log.txt
adb shell run-as hu.gmz.droidremote cat files/debug/observe.txt
adb exec-out run-as hu.gmz.droidremote cat files/debug/last.jpg > /tmp/last.jpg
adb shell run-as hu.gmz.droidremote ls files/runs   # agent futások trace-e
```

### UI koppintás adb-ből (Compose is)

```bash
tools/ui-tap.sh "Frissítés"        # uiautomator dump → szöveg → bounds közepe → input tap
```

## Architektúra (fájlok)

| réteg | fájl | Android-függő |
|---|---|---|
| fa-modell | `observe/ObservedNode.kt` | nem |
| szerializáló | `observe/TreeSerializer.kt` | nem |
| settle | `observe/SettleDetector.kt` | nem |
| DSL + parser | `act/Action.kt`, `act/ActionCodec.kt` | nem |
| a11y adapter | `observe/NodeAdapter.kt` | igen |
| kép | `observe/ScreenCapture.kt` | igen |
| aktor | `act/Actuator.kt` | igen |
| modell | `plan/ModelClient.kt`, `plan/AgentLoop.kt` | igen |
| trace | `trace/RunStore.kt`, `trace/DebugExport.kt` | igen |
| UI | `ui/MainActivity.kt`, `ui/AgentViewModel.kt` | igen |

A promptok **egyetlen** igazságforrása: `app/src/main/assets/prompts/{system,actor}.md`.
A fa szövegformátuma: `pkg=… window="…" nodes=N screen=WxH` + `@id Class "label" (l,t-r,b) flags #viewId`,
flag: `c` clickable, `l` longClickable, `e` editable, `s` scrollable, `k` checkable, `K` checked,
`f` focusable, `o` selected, `d` disabled.

Akció DSL: `tap(n|x,y)`, `long`, `swipe`, `type(n?,t)`, `scroll(n,dir)`, `key(BACK|HOME|RECENTS|NOTIFICATIONS|QUICK_SETTINGS)`,
`shot`, `wait`, `done`, `fail`. `type` `n` nélkül a fókuszált mezőt írja (`findFocus(FOCUS_INPUT)`).

## Mért tények (Pixel 2, 2026-10-25)

- observe: saját app 29 node / 1399 byte; launcher 16 node / 939 byte.
- `takeScreenshot`: 720×1280 JPEG (maxLongEdge 1280, q70), 62–145 KB, ~350 ms throttle.
- gesture tap / ACTION_CLICK fallback / `performGlobalAction(HOME)` / `ACTION_SET_TEXT`: működik.
- Ha a node stale (layout elmozdult), az `ACTION_CLICK` false → gesture a bounds közepére.

## Csapdák

- **Reinstall / force-stop után az a11y engedély elveszhet** → újra `settings put`.
- **Az IME kitakarja az alsó gombokat.** Adb-s UI-vezérlésnél a mező fókuszálása után
  `adb shell input keyevent 4`, különben a koppintás a billentyűzetre megy.
- **brew `gradle` (9.8.2) behúzta az openjdk 27-et és átlinkelte a `java`-t** → AGP 8.7.3
  nem megy vele, és a Gradle 9 sem kompatibilis az AGP-vel. Kell a wrapper + JDK 21.
- **Gradle 9 wrapper-generálás**: üres `settings.gradle.kts` kell a temp könyvtárba, különben
  „does not contain a Gradle build".
- **`AccessibilityNodeInfo` lehet stale**: minden akció előtt friss `observe()` (a loop ezt
  teszi); a UI-ban a `lastObservation` használatos.
- **`FLAG_SECURE`** → `takeScreenshot` `-5` (secure window), nem hiba.
- **A cleartext HTTP alapból tiltott** (targetSdk 28+). Ezért van
  `res/xml/network_security_config.xml`; enélkül a `http://127.0.0.1:…` / LAN endpoint
  `CLEARTEXT communication … not permitted` hibát ad.
- **`input text` vs `ACTION_SET_TEXT`**: az előbbi ASCII-only és IME-függő, az utóbbi unicode.

## Ami még nincs (M3–M6)

Agent loop élő endpointtal (van kód, nincs bekötött kulcs), replay UI + ZIP export,
package-denylist, lebegő STOP overlay, költség-plafon, Keystore, `ACTION_SET_TEXT` fallback
custom mezőkre, landscape.

## Munkamódszer

1. `./gradlew :app:testDebugUnitTest` — a tiszta rétegek eszköz nélkül.
2. `assembleDebug` + `adb install -r`.
3. a11y be (fent), app indítás.
4. **Megfigyelés → Frissítés** (fa), **Képernyőkép** (takeScreenshot), **Script** (aktor
   modell nélkül: `[{"a":"open","q":"com.android.settings"},{"a":"wait","ms":1500},{"a":"shot"}]`).
5. Csak ezután agent loop. Verifikáció a `files/debug/*` fájlokból, nem a UI-t nézve.

### A teljes loop verifikálása modell nélkül (bevált)

Nem kell valódi LLM: fake endpoint + `adb reverse`.

```bash
python3 tools/fake-endpoint.py 8099 &        # fix {"a":"done","summary":"fake ok"}
adb reverse tcp:8099 tcp:8099
# prefs: base_url=http://127.0.0.1:8099/v1, model=fake, api_key=""
# adb install -r → prefs írás run-as-szal → am start → a11y be → Agent fül → Indítás
```

Elvárt log: `indulás: HOME …` → `→ modell (1. lépés)` → `── KÉSZ: fake ok`.
A `/tmp/opencode/fake-requests.jsonl` tartalmazza a tényleges promptot (system + fa),
a `files/runs/<id>/meta.json` a `status=done, steps=1`-et.

**Force-stop után az a11y service „crashed" állapotba kerülhet** (`Crashed services:{…}`),
és a settings-tel nem lehet újraéleszteni — `adb install -r` (PACKAGE_REPLACED) tisztítja.
