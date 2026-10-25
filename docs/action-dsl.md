# Action DSL

Egy lépés = egy JSON objektum. A modell ezt adja vissza, az `ActionCodec` toleránsan
parse-olja (kódkerítés, próza, whitespace nem gond; az első kiegyensúlyozott `{...}`
objektumot veszi).

## Akciók

| JSON | hatás |
|---|---|
| `{"a":"tap","n":12}` | a 12-es node megkoppintása: `ACTION_CLICK` a legközelebbi clickable ősön, különben gesture a bounds közepére |
| `{"a":"tap","x":540,"y":1200}` | abszolút pixel-koppintás (csak képernyőkép alapján, vision ág) |
| `{"a":"long","x":540,"y":1200,"ms":600}` | hosszú nyomás |
| `{"a":"swipe","x1":540,"y1":1500,"x2":540,"y2":500,"ms":300}` | csúsztatás |
| `{"a":"type","t":"Szia világ"}` | `ACTION_SET_TEXT` a **fókuszált** beviteli mezőre (unicode is megy) |
| `{"a":"type","n":7,"t":"Szia világ"}` | ugyanaz, de a 7-es node-ra (vagy annak editable leszármazottjára) |
| `{"a":"scroll","n":3,"dir":"down"}` | `ACTION_SCROLL_FORWARD` a scrollable ősön; `"up"` = backward |
| `{"a":"key","k":"BACK"}` | `GLOBAL_ACTION_*`: BACK, HOME, RECENTS, NOTIFICATIONS, QUICK_SETTINGS |
| `{"a":"open","q":"Settings"}` | app indítása **címke vagy package** alapján (`q` = label/pakkázs, `pkg` is elfogadott) |
| `{"a":"shot"}` | képernyőkép kérése; a kép a **következő** lépés promptjába kerül |
| `{"a":"wait","ms":600}` | várakozás (max 10 s), settle nélkül |
| `{"a":"done","summary":"..."}` | a feladat kész |
| `{"a":"fail","why":"..."}` | feladás, indoklással |

## Szemantika

- **A node-id (`n`) csak az adott megfigyelésre érvényes.** Új fa = új id-k.
- Ha a node nem clickable, a legközelebbi clickable ős kapja az `ACTION_CLICK`-et
  (max 8 szint). Ha az is elutasítja, gesture-koppintás a bounds közepére.
- `dispatchGesture` aszinkron: `onCompleted`/`onCancelled` dönt. A visszatérési érték
  csak azt jelzi, hogy a gesture elindult.
- `type` először `ACTION_FOCUS`-t ad, utána `ACTION_SET_TEXT`-et. Ha a node nem
  editable, 3 szintig keres editable leszármazottat. `n` nélkül a
  `findFocus(FOCUS_INPUT)`-ot használja, tehát pixel-koppintás után elég
  `{"a":"type","t":"..."}`.
- `wait` nem settle-el — akkor hasznos, ha az app saját animációt futtat és a fa nem
  változik (pl. splash).
- `open` először `getLaunchIntentForPackage`-ot próbál (package névre), aztán a launcher
  activity-k között keres: pontos címke → részleges címke → package részlet. A találatot
  explicit componenttel indítja (`FLAG_ACTIVITY_NEW_TASK`). A láthatósághoz a manifestben
  `<queries>` van a MAIN/LAUNCHER intentre (Android 11 package visibility).

## Parse-szabályok

- A `{"a": ...}` kis/nagybetű-független.
- Ismeretlen `a` → hiba (a loop a nyers választ trace-eli, stall nő).
- `tap` esetén `n` elsőbbséget élvez; `x`/`y` csak `n` hiányában.
- A válasz max ~200 karakter legyen; a `max_tokens` 400.

## Script mód (modell nélkül)

A UI *Script* doboza JSON **tömböt** vár, és sorban végrehajtja, akciónként friss
megfigyeléssel:

```json
[
  {"a":"key","k":"HOME"},
  {"a":"wait","ms":800},
  {"a":"shot"}
]
```

Ez az aktor regressziós tesztje: ha ez nem megy, a modell sem fog tudni semmit.
