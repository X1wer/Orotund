# SzabadZene – munkaállapot

Minden munkamenet elején olvasd el, a végén frissítsd. A követelmények: `KOVETELMENYEK.md`.

---

## 1. rész – alapok, lejátszás, a két kiemelt elvárás · KÉSZ (2026-11-15)

### Elkészült
- Projekt: Gradle 9.6.1, AGP 9.4.1, Kotlin 2.4.21, KSP 2.3.13, Compose BOM 2026.09.00,
  Media3 1.11.1, Room 2.8.5, DataStore 1.2.1; minSdk 26, compile/targetSdk 37.
  `applicationId`: `io.github.x1wer.szabadzene` (debug: `.debug` utótaggal).
- Zenetár: MediaStore-beolvasás a háttérben, Room-gyorsítótár, változásfigyelés (ContentObserver),
  verzióhelyes engedélykérés (API 33+: `READ_MEDIA_AUDIO`, alatta `READ_EXTERNAL_STORAGE`),
  megtagadás és „ne kérdezd újra” kezelése, rendszerbeállítások gomb, visszatéréskor újraellenőrzés.
  Számok lista magyar ábécé szerint; hiányzó címnél fájlnév; hiányzó előadónál „Ismeretlen előadó”.
- **ID3-javítás:** a MediaStore az ékezetes magyar ID3-címkéket kínai írásjelekké torzíthatja
  (API 28-on és 37-en is reprodukálva). Gyanús szövegnél az `Id3TextReader` a fájlból olvassa újra.
- Lejátszás: `PlaybackService` (MediaSessionService, egy ExoPlayer), háttérlejátszás,
  értesítés és zárolási képernyő, hangfókusz, kihúzáskor/BT-megszakadáskor szünet,
  hibás/hiányzó fájl átlépése legfeljebb a lista hosszáig, érthető üzenettel.
- **A) Számsor-menü:** közös `TrackRow`, a menüterület ≥ 64 × 64 dp és a sor teljes magassága;
  alsó menüpanel címmel. Menüpontok: Lejátszás következőként, Hozzáadás a várólistához, Fájladatok.
- **B) Ugrógombok:** teljes lejátszó (a csúszka alatt külön sor) és minilejátszó (egy- vagy kétsoros),
  közös `SkipController` (függő célpozíció, 0-nál és a szám végén megáll, a szám végén szünet),
  beállítások 5/10/15/30/60 mp irányonként (DataStore). Ugyanez a logika fut az értesítés
  gombjaiból és a fejhallgató/BT előre–vissza gombjából (`SkipAwarePlayer`).
- Beállítások képernyő: ugrásértékek, verzió.
- Felület magyarul és angolul (`values` = angol alap, `values-hu`).
- Tesztadat-generátor: `tools/generate_test_audio.py` (MP3, M4A, FLAC, OGG, WAV, sérült fájl).

### Tesztek – ténylegesen lefuttatva
- A végleges kódon (`clean` után) futtatva:
- **JVM-egységtesztek: 40/40 sikeres** (`SkipControllerTest` 27, `Id3TextReaderTest` 7, `SmallLogicTest` 6
  – ugrási határesetek, gyors koppintások késleltetett állapotfrissítéssel, ID3-olvasás).
- **Compose UI-tesztek (API 37 emulátor): 14/14 sikeres** (`TrackRowTest` 5, `PlayerLayoutTest` 9 –
  menüterület mérete és kattintás-szétválasztás a közép/sarkok/élek pontjain, 2,0× betű és hosszú cím,
  ugrógombok láthatósága lejátszás/szünet/tekerés után, inaktív állapot, kis álló és fekvő kijelző
  2,0× betűvel görgetés nélkül, minilejátszó egy- és kétsoros elrendezése).
- `lintDebug`: 0 hiba (csak „újabb verzió elérhető” és többes szám-javaslat figyelmeztetések).
- **Kézi ellenőrzés emulátoron (API 37 és API 28), adb-vel és képernyőképekkel:**
  - menü a menüterület szélére koppintva is a helyes számra nyílik, zene nem indul (állapot `NONE`);
  - szünetben 3× +10 mp → +30 mp, 2× −10 mp → −20 mp, 5× −10 mp → 0, szám nem vált, szünet marad;
  - lejátszás közben a szám végére érő előreugrás: szünet a végén, 3 mp múlva is ugyanaz a szám;
    utána Lejátszás → a várólista szerinti következő szám indul;
  - lejátszás közbeni ugrás után tovább szól; 5 mp / 30 mp beállítás pontos, újraindítás után is megmarad;
  - értesítés: ±10 mp gombok (API 28 és 37), szünetben megnyomva szünetben marad;
  - médiabillentyű előre/vissza (fejhallgató/BT szimuláció) a beállított lépéssel ugrik;
  - háttér, képernyőzár: tovább szól; forgatás: 0 új lejátszópéldány, 1 munkamenet, nincs dupla hang;
  - repülőgép-üzemmód (Wi-Fi + adat ki): tovább szól; a végső manifestben nincs `INTERNET`
    és `ACCESS_NETWORK_STATE` sem;
  - sérült fájl és törölt fájl a várólistában: üzenet, átlépés vagy megállás, nincs összeomlás;
  - szimulált bejövő hívás (API 28): csörgéskor szünet, a hívás után magától folytatódik;
  - engedély megtagadása: nincs összeomlás, rendszerbeállítások gomb működik.
- Képernyőképek: `reports/screens/` (nincs gitben).

### Nem tesztelt / valódi készüléken ellenőrizendő
- Valódi vezetékes fejhallgató kihúzása és Bluetooth-kimenet megszakadása (a kód az ExoPlayer
  `handleAudioBecomingNoisy` beállítására épül), valódi BT-gombok.
- Nagyon nagy zenetár (több ezer szám) beolvasási ideje.

### Ismert korlátok, megjegyzések
- MP3-nál az ugrás ~0,1 mp-et eltérhet (keretillesztés); M4A/FLAC-nál ezredmásodpercre pontos.
- A legkisebb kijelzőn (320 × 568 dp) 2,0× betűvel a kétsoros minilejátszó a képernyő nagy részét
  elfoglalja – a lista görgethető, az utolsó elem elérhető.
- A rendszer médiavezérlője (értesítés) az ugrógombokat egyéni műveletként kapja; hogy hol és hány
  gomb látszik, azt az Android verziója dönti el.

### Amit a 2. rész vár / tudnivalók
- **Nyelvválasztás:** a `cmd locale set-app-locales ... hu-HU` NEM illeszkedett a generált
  `locale_config` „hu” bejegyzésére, a „hu” igen → a kézi választásnál `hu` címkét használj
  (`AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("hu"))`).
  Az AppCompat (`autoStoreLocales`) újratelepítés utáni első indításkor egyszer szinkronizál, és felülírja
  a parancssorból beállított nyelvet – tesztelésnél ezt vedd figyelembe.
- (A 2. részben elkészült: állapotmentés, `onPlaybackResumption`, listák, Mappák fül, menük → `ui/Menus.kt`.)

---

## 2. rész – zenetár, listák, várólista, beállítások · KÉSZ (2026-11-15)

### Elkészült
- **Adatbázis 2. verzió** (Room AutoMigration 1→2): `playlists`, `playlist_entries` (CASCADE a listára,
  szándékosan nincs idegen kulcs a `tracks` gyorsítótárra), `favorites`, `queue_items`, `playback_state`.
- **Zenetár:** fülek (Számok, Albumok, Előadók, Műfajok), gyűjtemény-részletező „Lejátszás”/„Keverés”
  gombbal; ékezet- és kisbetű-független keresés címre, előadóra, albumra, fájlnévre (több szó: mindegyiknek
  egyeznie kell); rendezés cím (magyar ábécé), előadó, időtartam, hozzáadás ideje szerint, csökkenő is (mentve).
  Az albumok **név + mappa** szerint csoportosulnak (a MediaStore album-azonosítója az elrontott ID3-kódolás
  miatt ugyanarra az albumra is eltérhet).
- **Listák fül:** Kedvencek felül, saját listák; létrehozás, átnevezés, törlés (megerősítéssel),
  számok / albumok / mappák hozzáadása, eltávolítás a listából (a fájl nem törlődik), **áthúzásos átrendezés**
  (fogantyúval; akadálymentesen „Mozgatás fel/le” egyéni művelettel is).
- **Mappák fül:** mappák listája, mappa elrejtése a zenetárból; visszavétel a Beállításokban; kézi újraolvasás.
- **Várólista-képernyő** (a teljes lejátszóból): lejátszási sorrendben, koppintásra lejátszás, átrendezés
  (keverés közben tiltva, magyarázattal), eltávolítás. A „Lejátszás következőként” keverés közben is közvetlenül
  az aktuális szám után kerül, a „Hozzáadás a várólistához” a kevert sor végére (`QueueLogic`, `SkipAwarePlayer`).
- **Állapotmentés:** várólista, aktuális szám, pozíció, ismétlés, keverés **és a kevert sorrend** mentése
  (eseményekre 1 mp-es összevonással, lejátszás közben 10 mp-enként, leálláskor); induláskor visszaállítás
  automatikus lejátszás nélkül; a közben eltűnt számok kimaradnak (az aktuális helyett a következő jön).
  `onPlaybackResumption` + `MediaButtonReceiver`: leállított appnál a fejhallgató/BT lejátszógombja a mentett helyről folytat.
- **Teljes lejátszó:** kedvenc kapcsoló (szív) és várólista gomb a módok sorában.
- **Számmenü** környezettől függően: Lejátszás következőként, Hozzáadás a várólistához, Hozzáadás listához…,
  kedvencek be/ki, Eltávolítás a várólistából / a listából, Megosztás (Android megosztó), Fájladatok.
  Gyűjteménymenü: Lejátszás, Keverés, Következőként, Várólistához, Listához + saját pontok (mappa elrejtése, átnevezés, törlés).
- **Beállítások:** téma (rendszer/világos/sötét), rendszerszínek (Android 12+), kiemelőszín (5 féle),
  nyelv (Rendszer / Magyar / English – `hu` címke, AppCompat), ugrásértékek, fejhallgató: szünet leválasztáskor
  (alapból be, ki is kapcsolható), folytatás visszacsatlakozáskor (alapból ki; 30 percen belül, csak ha a szünetet
  a leválasztás okozta), zenetár újraolvasása, rövid fájlok elrejtése (ki/10/30/60 mp), elrejtett mappák, súgó, licencek, verzió.
- A rendszersávok ikonjai az app témáját követik (sötét témán világos ikonok).

### Tesztek – ténylegesen lefuttatva (végleges kódon, `clean` után)
- **JVM-egységtesztek: 63/63 sikeres** – új: `LibraryLogicTest` 12 (ékezetfüggetlen keresés mindkét irányban,
  magyar ábécé, rendezések, mappaszűrés almappákkal, minimum hossz, album-/előadó-/mappacsoportok),
  `QueueLogicTest` 11 (visszaállítás hiányzó számokkal, kevert sorrend átszámozása, „következőként” és hozzáfűzés keverésnél).
- **Műszeres tesztek (API 37): 23/23 sikeres** – új: `DatabaseTest` 5 (lista létrehozás, hozzáadás, átrendezés
  mindkét irányba, eltávolítás utáni folytonos sorszámok, átnevezés, törlés kaszkáddal, kedvencek, várólista mentés–betöltés),
  `MigrationTest` 1 (1→2 adatvesztés nélkül), `ReorderableTest` 3 (húzás, akadálymentes mozgatás, tiltott állapot).
- `lintDebug`: 0 hiba.
- **Kézi ellenőrzés emulátoron (API 37):** keresés („arvizturo” → Árvíztűrő; „SZABO ADAM” → Szabó Ádám számai;
  nincs találat üzenet); albumok; lista létrehozása a menüből és 3 szám hozzáadása; húzásos átrendezés, ami
  újraindítás után is megmarad; keverés + „Lejátszás következőként” a szóló szám után; **teljes leállítás és
  újraindítás után ugyanaz a szám, ugyanott (5243 → 5252 ms), szünetben, ugyanazzal a kevert sorrenddel**;
  várólista átrendezése és eltávolítás lejátszás megszakítása nélkül; kedvenc; mappa elrejtése és visszavétele;
  10 mp-es szűrő (11 → 8 szám); sötét téma narancs színnel; angol nyelv a Beállításokból; megosztás;
  médiagombos folytatás leállított appnál.
- Képernyőképek: `reports/screens/p2_*.png` (nincs gitben).

### Nem tesztelt / valódi készüléken ellenőrizendő
- „Folytatás visszacsatlakozáskor”: emulátoron nem lehet valódi fejhallgató-kihúzást és -visszadugást
  előidézni (a rendszer AUDIO_BECOMING_NOISY üzenete védett). A kód: `PlaybackService.deviceCallback`.

### Ismert korlátok, megjegyzések
- Az alsó navigációban a fül neve „Listák” (helytakarékosság); a képernyő címe „Lejátszási listák”.
- Keverés közben a várólista nem rendezhető át (a kevert sorrend számít) – a képernyő ezt kiírja.
- A várólistában ugyanaz a szám többször is szerepelhet; a sor kulcsa az alaplista-index + azonosító.
- A `kotlinx-serialization` az appban 1.8.1-re van emelve (a `room-testing` miatt; lásd `app/build.gradle.kts`).

### Amit a 3. rész vár / tudnivalók
- Hangszínszabályzó: a lejátszó a `PlaybackService`-ben van (`player.audioSessionId`); a beállítás a
  `SettingsRepository`-ba kerüljön. A teljes lejátszó módsorában (`ModeRow`) van hely egy EQ-gombnak.
- Elalvásidőzítő: a szolgáltatásban (a lejátszó mellett), a felület `PlayerConnection`-en keresztül.
- Fájlműveletek (címkeszerkesztés, vágás, törlés, csengőhang): a számmenübe (`ui/Menus.kt`, `MenuHost`) kerülnek;
  törlés után a `LibraryRepository.refresh()` és a várólista/listák konzisztenciája ellenőrizendő.
- A Room-séma jelenleg 2-es; új táblához AutoMigration és a `MigrationTest` bővítése kell.

## 3. rész – hangszínszabályzó, időzítő, widget, fájlműveletek · NINCS ELKEZDVE

## 4. rész – átfogó ellenőrzés, javítás, átadás · NINCS ELKEZDVE
