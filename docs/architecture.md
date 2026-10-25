# Architektúra

```
┌───────────────────────────── Android telefon ─────────────────────────────┐
│                                                                           │
│  ObserverService (AccessibilityService)                                   │
│    ├─ rootInActiveWindow() ──▶ NodeAdapter ──▶ ObservedNode fa            │
│    │                              └─ nodeById: Map<Int, AccessibilityNodeInfo>
│    ├─ TreeSerializer ──▶ kompakt szöveg (pkg, nodes, @id, label, bounds)  │
│    ├─ takeScreenshot() ──▶ HardwareBuffer ──▶ JPEG (≤1280 px, q70)        │
│    └─ Actuator: dispatchGesture / ACTION_SET_TEXT / performGlobalAction   │
│                                                                           │
│  AgentLoop (suspend, a service-ben fut)                                   │
│    observe ─▶ prompt ─▶ ModelClient ─▶ ActionCodec ─▶ act ─▶ settle       │
│      ▲                                              │                     │
│      └──────────── RunStore (meta.json, steps.jsonl, shot-NNN.jpg) ◀──────┘
└───────────────────────────────────────────────────────────────────────────┘
```

## Miért ez a felosztás

**Egy pillanatkép = egy `Observation`.** A fa és a hozzá tartozó `nodeById` map együtt
készül, mert a node-id csak addig érvényes, amíg a képernyő nem változik. A modell
`@12`-t lát, az aktor ugyanabból a map-ből oldja fel — így nincs koordináta-találgatás.

**A fa a primer csatorna, a kép a fallback.** A fa ~1-3k token, determinisztikus, és
tartalmazza a `clickable/editable/scrollable` flaget, amit egy kép nem. A kép csak akkor
megy a modellnek, ha:

- a modell kéri (`{"a":"shot"}`), vagy
- 3 egymást követő akció után sem változott a fa (stall → auto-shot), vagy
- a fa üres/értelmetlen (játék, canvas).

**Settle-detektálás, nem sleep.** Minden akció után a fa-hash-t pollozzuk 120 ms-onként;
akkor tekintjük stabilnak, ha két egymást követő minta azonos (min. ~240 ms). A
`changed` flag megy vissza a modellnek — ez a loop legfontosabb robusztussági jele.

**Egy akció per lépés.** Nem próbálunk több lépést egy válaszban: a settle + changed
visszacsatolás így minden akcióra megvan, és a hiba lokalizálható.

## Rétegek

| réteg | fájl | Android-függő? |
|---|---|---|
| fa-modell | `observe/ObservedNode.kt` | nem |
| szerializáló | `observe/TreeSerializer.kt` | nem |
| settle | `observe/SettleDetector.kt` | nem |
| action DSL | `act/Action.kt`, `act/ActionCodec.kt` | nem (csak `org.json`) |
| a11y adapter | `observe/NodeAdapter.kt` | igen |
| screenshot | `observe/ScreenCapture.kt` | igen |
| aktor | `act/Actuator.kt` | igen |
| modell hívás | `plan/ModelClient.kt` | igen (OkHttp + Base64) |
| loop | `plan/AgentLoop.kt` | igen |
| trace | `trace/RunStore.kt` | igen (Context) |
| UI | `ui/*` | igen |

Az Android-független rétegek szándékosan azok: ez a JVM-unit-teszt alapja
(`app/src/test/`). Ha ott `android.*` import jelenik meg, a tesztelhetőség megszűnik.

## Hurok

```
1. observe()            → Observation (fa + node map + screen)
2. prompt               → system (assets/prompts/system.md) + actor (goal, step, lastAction, changed, fa)
3. ModelClient.complete → 1 JSON akció
4. ActionCodec.parse    → Action | hiba (hiba → a nyers válasz megy a trace-be, stall nő)
5. Actuator.perform     → ok/hiba
6. SettleDetector.wait  → changed?
7. RunStore.step        → trace
```

Kilépés: `done`, `fail`, stop-kérés, lépés-plafon, 6 stall.

## Ismert hiányosságok (szándékosan később)

- Nincs lebegő STOP overlay (`SYSTEM_ALERT_WINDOW`), csak in-app stop.
- Nincs package-denylist (bank/jelszókezelő tiltás) — csak a prompt tiltja.
- Nincs több-mondatos planner/actor szétválasztás (egy modell, egy akció).
- Nincs replay UI (a trace fájlokból viszont rekonstruálható).
- Nincs `ACTION_SET_TEXT` fallback custom mezőkre.
