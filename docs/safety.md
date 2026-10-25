# Biztonság

Ez az app a legmagasabb bizalmi szintű engedélykombinációval fut a telefonon: látja a
képernyőt és képes koppintani bárhol. Ezért a tervezés konzervatív.

## Amit a rendszer végez

- **Nincs auto-start.** Semmi nem indul bootkor, semmi nem indul értesítésből. Minden
  futás explicit *Indítás* a UI-ból.
- **Lépés-plafon** (alapból 40, állítható 1–200).
- **Stall-abort**: 3 változatlan lépés után képernyőkép, 6 után leállás.
- **Trace mindenről**: `filesDir/runs/<id>/meta.json` + `steps.jsonl` + `shot-NNN.jpg`.
  Utólag rekonstruálható, mit látott és mit tett az agent.
- **Stop** a napló fülön; a futó loop a következő lépés előtt áll le.

## Amit a prompt tilt

- jelszó, PIN, payment adat, 2FA kód beírása → `{"a":"fail"}`;
- secure (fekete) képernyő operálása → `{"a":"fail"}`;
- ugyanazon akció ismétlése változatlan képernyőn;
- „felfedezés" a cél helyett.

A prompt nem biztonsági határ, csak első védvonal. A valódi határ a lépés-plafon,
a trace és az, hogy a user ott van.

## Amit még meg kell építeni (M5)

1. **Package-denylist** akció előtt: ha az aktív ablak package-e tiltott (bank,
   jelszókezelő, 2FA app), az akció nem fut le. A prompt-tiltás kevés.
2. **Lebegő STOP overlay** (`SYSTEM_ALERT_WINDOW`), hogy ne kelljen az apphoz visszatérni.
3. **Költség-plafon** (token/óra) a modell hívás előtt.
4. **Secure-window detektálás** a loop szintjén: ha a fa üres ÉS a screenshot `-5`,
   azonnali abort indoklással.
5. **API kulcs** Android Keystore-ba (jelenleg app-private SharedPreferences).

## Adat

- A képernyőképek és a fa a `filesDir` alatt maradnak (app-private, nem backupolt:
  `allowBackup=false`).
- A modell endpoint **külső**: a fa/kép elhagyja a telefont. Ez tudatos döntés; helyi
  modell esetén (Ollama a LAN-on) nincs kimenő adat.
- A cleartext HTTP engedélyezett (`res/xml/network_security_config.xml`), hogy a LAN-os és
  `adb reverse`-szal bekötött endpointok működjenek. Ez **titkosítatlan** csatorna: éles
  API kulcsot és érzékeny képernyőt csak HTTPS-en keresztül küldj.
- Az API kulcs nincs a repóban, nincs logban.

## Etika / ToS

Csak saját eszközt és saját fiókot automatizálj. A legtöbb szolgáltatás ToS-a tiltja az
automatizált interakciót; ez a projekt eszköz, nem engedély.
