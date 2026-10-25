# droidremote

AI agent, ami **a telefonon belül** vezérel egy Android készüléket: a képernyőt az
akadálymentesítési fa alapján olvassa, az inputot `dispatchGesture`-sel és
`ACTION_SET_TEXT`-tel hajtja végre, a döntést egy OpenAI-kompatibilis modell hozza.
Nincs számítógép, nincs AirPlay, nincs USB HID, nincs root.

A koncepció az [ESP32 iPhone Agent](https://github.com/onehumanbeing/esp32-iphone-agent)
projektből jön (Henry Yuan, MIT); ez a projekt az Androidra adaptált, board nélküli változat.

## Miért más, mint az eredeti

| | eredeti (iPhone) | droidremote (Android) |
|---|---|---|
| szem | ESP32 AirPlay-vevő, H.264 passthrough, BLE-n át | `AccessibilityService` fa + `takeScreenshot()` |
| kéz | USB HID egér + AssistiveTouch kurzor | `dispatchGesture`, `ACTION_SET_TEXT`, global actions |
| koordináta | relatív kurzor, wiggle + kalibráció | node-id, pontos bounds |
| agy | app a telefonon | app a telefonon |

A node-id alapú cselekvés miatt nincs kurzorkalibráció és nincs félrement koppintás;
a fa-szerializált képernyő pedig ~1-3k token, tehát text-only modell is elég a legtöbb
apphoz (kép csak akkor kell, ha a fa üres: játék, canvas, egyes WebView-k).

## Követelmények

- Android 11+ (API 30) — a `takeScreenshot` miatt. A `MediaProjection` út szándékosan nincs implementálva.
- Debug APK, `adb install`-lal. Nem Play Store-ra készült.
- OpenAI-kompatibilis chat endpoint, ami tud JSON-t adni (vision opcionális).

## Építés és telepítés

```bash
export JAVA_HOME=/home/linuxbrew/.linuxbrew/opt/openjdk@21   # a brew gradle JDK 27-tel jön, az AGP nem szereti
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Az SDK útja `local.properties`-ben van (gitignore-olva):
`sdk.dir=/home/linuxbrew/.linuxbrew/share/android-commandlinetools`.

## Első használat

1. Telepítés után nyisd meg: **Beállítás** fül → *Engedélyezés* → kapcsold be a
   `droidremote agent` akadálymentesítési szolgáltatást.
2. **Megfigyelés** fül → *Frissítés*: meg kell jelennie az aktuális képernyő fájának.
   *Képernyőkép* gomb a `takeScreenshot`-ot teszteli.
3. A *Script* dobozban modell nélkül futtathatsz akciókat (ez az aktor regressziós tesztje):
   ```json
   [{"a":"key","k":"HOME"},{"a":"wait","ms":800},{"a":"shot"}]
   ```
4. **Beállítás**: base URL (`https://ollama.com/v1`), API kulcs, modell, lépés-plafon.
   A *Modellek* gomb listázza az endpoint kínálatát.
5. **Agent** fül: írd be a feladatot, *Indítás*. A napló lépésenként mutatja, mit tett.

## Korlátok

- `FLAG_SECURE` képernyők (bank, jelszókezelő, DRM) nem olvashatók: az agent `fail`-lel leáll.
- Az `ACTION_SET_TEXT` néhány custom mezőn nem működik; ilyenkor a képernyő-billentyűzet
  vision-nel való nyomkodása lenne a fallback (még nincs implementálva).
- A jelszó/PIN/2FA beírása szándékosan tiltott a promptban.
- API kulcs: app-private SharedPreferences, **nincs** Keystore-ba zárva.
- Landcape, split-screen és többablakos mód még nincs tesztelve.

## Biztonság

Ez a legmagasabb bizalmi szintű engedélykombináció a telefonon. Ezért:

- semmi nem indul magától, minden futás explicit *Indítás*;
- minden futás trace-elődik (`filesDir/runs/<id>/`: `meta.json`, `steps.jsonl`, `shot-NNN.jpg`);
- van lépés-plafon és stall-abort (6 változatlan lépés után leáll);
- a STOP a napló fülön érhető el (lebegő overlay még nincs).

Csak saját eszközt és saját fiókot automatizálj, és tartsd szem előtt a futást.

## Felépítés

```
app/src/main/java/hu/gmz/droidremote/
├── observe/   ObservedNode, NodeAdapter (a11y fa), TreeSerializer, ScreenCapture, SettleDetector
├── act/       Action DSL, ActionCodec (tolerant JSON), Actuator (gesture + SET_TEXT + global)
├── plan/      AgentConfig, PromptBuilder, ModelClient (OpenAI-kompatibilis), AgentLoop
├── trace/     RunStore (futás + lépés + screenshot)
├── service/   ObserverService (AccessibilityService, itt fut minden)
└── ui/        Compose UI: Beállítás / Megfigyelés / Agent
app/src/main/assets/prompts/   system.md, actor.md (a prompt egyetlen igazságforrása)
```

Részletek: [docs/architecture.md](docs/architecture.md), [docs/action-dsl.md](docs/action-dsl.md),
[docs/compat.md](docs/compat.md), [docs/safety.md](docs/safety.md).

## Licenc

MIT. Az ötlet és a prompt-szerkezet az ESP32 iPhone Agent (Henry Yuan) projektből származik,
ami szintén MIT — a kód nem lett átvéve.
