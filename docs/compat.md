# Kompatibilitás

Cél: **Android 11 (API 30) – Android 15 (API 35)**, egy kódúton.

| funkció | API 30–32 | API 33 | API 34–35 |
|---|---|---|---|
| `AccessibilityService.takeScreenshot()` | ✅ (ezért `minSdk 30`) | ✅ | ✅ |
| `canTakeScreenshot` a service XML-ben | ✅ | ✅ | ✅ |
| `dispatchGesture` | ✅ | ✅ | ✅ |
| `ACTION_SET_TEXT` | ✅ | ✅ | ✅ |
| `POST_NOTIFICATIONS` | nincs | kérés kell (még nem használjuk) | ugyanaz |
| foreground service type | nem használunk FGS-t | ugyanaz | ugyanaz |

## Miért nincs MediaProjection

- Session-önkénti consent dialóg, ami megszakítja a loopot.
- API 34+: `mediaProjection` FGS típus + token életciklus (stop után nem használható újra).
- A `takeScreenshot` ugyanazt tudja, ugyanazzal az engedéllyel, ami az agentnek
  amúgy is kell — és `FLAG_SECURE`-nél pontos hibakódot ad fekete kép helyett.

Ára: API 30 alatti Android nem támogatott (Android 10 és régebbi kimarad).

## Pixel 2 (walleye) — teszteszköz

- Android 11, API 30, stock, zárt bootloader, nem rootolt.
- `takeScreenshot` működik; throttle ~350 ms (`ScreenCapture.MIN_INTERVAL_MS`).
- Az a11y engedély adb-ból bekapcsolható (lásd `AGENTS.md`).
- USB vagy WiFi ADB; a wireless debugging port reboot után **véletlen** (nmap-pal keresd).

## Napi telefon (Android 14/15) — élesítés

- **Restricted settings**: side-loadolt APK-nál az akadálymentesítési engedély
  letiltható. Beállítások → Appok → droidremote → ⋮ → *Allow restricted settings*,
  utána engedélyezhető a service.
- A rendszer figyelmeztet, hogy az app mindent lát — ez így van, ez a lényeg.
- `targetSdk 35` → edge-to-edge; a Compose UI `safeDrawingPadding()`-gel kezeli.
- OEM akkumulátor-optimalizáló (Samsung/Xiaomi) megölheti a service-t; Pixel-en nincs gond.
- A napi telefonon a `FLAG_SECURE` appok (bank, jelszókezelő) használhatatlanok — ez
  nem bug, hanem a platform korlátja.

## Amit a rendszer nem enged

- a11y service programozott engedélyezése (szándékos; adb-ból viszont megy);
- billentyű-injektálás root nélkül (`INJECT_EVENTS` signature permission);
- `FLAG_SECURE` felület olvasása;
- a saját app elrejtése a user elől (a service-nek látszania kell a beállításokban).
