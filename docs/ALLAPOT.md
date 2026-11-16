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

## 3. rész – hangszínszabályzó, időzítő, widget, fájlműveletek · KÉSZ (2026-11-15)

### Elkészült
- **Hangszínszabályzó** (`playback/AudioEffectsController`, `EqualizerLogic`, `ui/sound/EqualizerScreen`):
  a lejátszó hangmunkamenetére kötött `Equalizer` és `BassBoost`. Futásidőben ellenőrzi, mi hozható ténylegesen
  létre (`AudioEffect.queryEffects()` + példányosítás); ami nem, az nem jelenik meg működőként (helyette magyarázat).
  8 saját, fordítható előbeállítás (Sík, Több mély, Több magas, Ének, Rock, Pop, Dzsessz, Klasszikus) log-frekvenciás
  görbéből a készülék sávjaira számolva + Egyéni (frekvenciához kötve tárolva, így más sávkiosztáson is értelmes).
  DataStore-ban mentve; számváltáskor és újraindítás után is érvényes. Elérhető a teljes lejátszó módsorából és a Beállításokból.
- **Elalvásidőzítő** (`playback/SleepTimer`, a szolgáltatásban fut): 5–120 perc vagy „az aktuális szám végén”
  (ExoPlayer `pauseAtEndOfMediaItems`). Állapota munkamenet-extrákban jut a felülethez (`PlayerConnection.sleepTimer`);
  a teljes lejátszó fejlécében másodpercre pontos visszaszámlálás („Szám vége” felirat a másik módban), megszakítható;
  a Beállításokban is elérhető.
- **Widget** (`playback/PlayerWidget`, 4×1, átméretezhető): borító, cím, előadó, előző / lejátszás-szünet / következő.
  Az állapotot a lejátszószolgáltatás küldi minden releváns eseménynél (ugyanabból a lejátszóból, mint az app és az
  értesítés), leálláskor szünetet mutat. A gombok a Media3 `MediaButtonReceiver`-nek küldenek médiagombot, így leállított
  appnál a lejátszógomb a mentett várólistával folytat. A törzsre koppintva a teljes lejátszó nyílik.
- **Címke- és borítószerkesztés** (`data/edit/TagEditor`, `TrackFileEditor`, `ui/edit/TagEditorDialog`):
  cím, előadó, album, album előadója, műfaj, év, sorszám, borító (képválasztó, max. 1000 px JPEG) vagy borító törlése.
  Csak MP3, M4A, FLAC és OGG Vorbis fájlnál jelenik meg. Folyamat: másolat a gyorsítótárba → írás a másolatba →
  visszaolvasás → (engedély) → az eredeti felülírása → az eredeti visszaolvasása és összevetése → hiba esetén a mentett
  eredeti visszaírása; csak ellenőrzött siker után jelez sikert. Utána médiatár-újraolvasás, borító-gyorsítótár ürítése,
  és a lejátszóban a szám újratöltése (sorrend, kevert sorrend, pozíció megmarad).
  Könyvtár: **JAudioTagger 3.0.1 (LGPL-2.1, GPL-3.0-val kompatibilis)**; Licencek képernyőn feltüntetve.
- **Hangkivágás** (`data/edit/LosslessTrim`, `AudioTrimmer`, `ui/edit/TrimDialog`): kétvégű csúszka + ±0,5 mp
  finomhangolás, előhallgatás saját ideiglenes lejátszóval (a fő lejátszó szünetel, a várólista nem változik), fájlnév.
  Mindig új fájl a `Music/SzabadZene` mappába; az eredeti érintetlen. Mentés előtt a kimenet hosszát visszamérjük.
- **Csengőhang**: magyarázó ablak → a rendszer „Rendszerbeállítások módosítása” képernyője → visszatéréskor ellenőrzés;
  megtagadáskor érthető üzenet. Beállítás után visszaolvassuk a rendszer csengőhangját.
- **Törlés a telefonról** (külön menüpont, „Eltávolítás a listából”-tól független): saját megerősítés (név, méret,
  „nem vonható vissza”), majd Android 11-től a rendszer törlési kérése, Android 10-ig írási engedély. Törlés után a szám
  kikerül a várólistából (minden előfordulása), minden listából (folytonos sorszámokkal), a kedvencek közül és a zenetárból.
- Számmenü új pontjai: Címkék és borító szerkesztése (csak írható formátumnál), Kivágás, Beállítás csengőhangként,
  Törlés a telefonról. Súgó és licencek kiegészítve. Minden szöveg magyarul és angolul.
- Engedélyek: `WRITE_EXTERNAL_STORAGE` csak `maxSdkVersion=29`, `WRITE_SETTINGS` (rendszerképernyőn kérve).
  Nincs `INTERNET`, nincs teljes fájlrendszer-hozzáférés. Adatbázis-séma változatlan (2), új tábla nem kellett.

### Formátumonként (ellenőrzött állapot)
| Formátum | Címke + borító írása | Vágás |
|---|---|---|
| MP3 | igen – ID3v2.4, UTF-8 (a meglévő v2.2/v2.3 címke v2.4-re alakul; a keretek megmaradnak) | veszteségmentes keretmásolás, ~26 ms-os pontosság; a Xing/Info fejléc kimarad (VBR fájlnál egyes lejátszók a hosszt becsülhetik) |
| M4A (AAC) | igen | veszteségmentes átcsomagolás (AAC-keret ~23 ms) |
| FLAC | igen (PICTURE blokk) | **újrakódolt M4A/AAC 192 kbit/s** (az Android-muxer FLAC-ot nem ír) |
| OGG Vorbis | igen (METADATA_BLOCK_PICTURE) | **újrakódolt M4A/AAC 192 kbit/s** |
| WAV (PCM) | nem kínáljuk fel (a médiatár nem olvassa vissza a WAV-címkéket) | veszteségmentes mintamásolás |
| Opus, ALAC, egyéb | nem kínáljuk fel | AAC-újrakódolás (nem tesztelt) |

### Tesztek – ténylegesen lefuttatva (végleges kódon, `clean` után)
- **JVM-egységtesztek: 95/95 sikeres** (a korábbi 63 + 32 új): `TagEditorTest` 8 (olvasás; írás és visszaolvasás
  MP3/címke nélküli MP3/M4A/FLAC/OGG fájlon ékezetes szöveggel és borítóval; borító törlése, mezők ürítése; az MP3
  hangadata bájtra változatlan; eltérés felismerése; sérült fájl → hiba; formátumfelismerés; MP3: ID3v2.4 + UTF-8),
  `LosslessTrimTest` 7 (MP3 vágás hossza ±60 ms, címke megmarad, az eredeti bájtra azonos; tartomány a fájl végén túl;
  üres szakasz → hiba; WAV bájtpontos mintamásolás; vágási mód választása; várólista-eltávolítás indexei),
  `SleepTimerTest` 8 (visszaszámlálás, egyszeri leállás lejáratkor, késő óra-ütés, megszakítás, újraindítás,
  „szám végén” mód, formázás), `EqualizerLogicTest` 9 (előbeállítások pontjai, log-frekvenciás interpoláció,
  tartományra szorítás, egyéni értékek más sávkiosztáson, egy sáv módosítása, mentési formátum és hibás érték).
- **Műszeres tesztek (Clean_API_37): 27/27 sikeres** (a korábbi 23 + `FileOperationsTest` 4):
  vágás mind az 5 formátumból (MP3×2, M4A, FLAC, OGG, WAV) 1–3 mp-re: a várt mód, az eredeti változatlan, a
  `MediaMetadataRetriever` szerinti és a **teljes dekódolással mért hossz 2 000 ± 400 ms** (lejátszhatóság);
  címkeírás 5 fájlon, visszaolvasva **az Android saját olvasójával** is (cím, előadó magyar ékezetekkel, borító),
  és a fájl ezután is végig dekódolható; törlés-következetesség az adatbázisban (több lista, ismétlődő elemek,
  folytonos sorszámok, kedvencek); **teljes MediaStore-folyamat**: tesztfájl feltöltése → címkementés `Success` →
  a zenetárban az új cím → vágás FLAC-ból M4A-ba `Music/SzabadZene`-be (~2 mp) → listához és kedvencekhez adás →
  törlés → a fájl, a lista- és kedvencbejegyzések és a zenetár-sor is eltűnt.
- `lintDebug`: 0 hiba (14 figyelmeztetés: újabb függőségverzió, többes szám-javaslat, API 31+ widget-attribútumok).
- A teljes sor egyben: `clean testDebugUnitTest lintDebug assembleDebug connectedDebugAndroidTest` – BUILD SUCCESSFUL.
- Közben talált és javított hiba: az Android ID3-olvasója a Latin-1 tartományú ékezetes szöveget (Á, Ö…) UTF-16-ban
  is elrontja → az MP3-címke ID3v2.4 + UTF-8 lett; ezt a műszeres teszt az Android olvasójával ellenőrzi.

### Kézi ellenőrzés emulátoron (Clean_API_37), képernyőképekkel (`reports/screens/p3_*.png`, nincs gitben)
Csak generált tesztfájlok másolatain (`Music/SzabadZeneTeszt3/p3 *`):
- **Teljes lejátszó:** időzítőgomb a fejlécben, hangszínszabályzó gomb a módsorban; az ugrógombok és a 64 dp-s
  menüterület változatlan.
- **Hangszínszabályzó:** az emulátoron elérhető (5 sáv: 60 Hz–14 kHz, ±15 dB, + mélyhang-erősítés). Rock előbeállítás
  bekapcsolva az `audio_flinger` szerint a lejátszó munkamenetén engedélyezett hatás; **számváltás után és teljes
  leállítás + újraindítás után** (új hangmunkamenet) is engedélyezve maradt.
- **Időzítő „szám végén”:** a szám végén (0:40/0:40) megállt, nem lépett a következőre, az időzítő kikapcsolt.
  **Időzítő 5 perc:** „összes ismétlése” mellett is pontosan lejártakor szünetelt; a visszaszámlálás látszott (4:56…).
- **Widget:** felvétel a kezdőképernyőre; cím, előadó, borító; **leállított appnál a lejátszógomb a mentett helyről
  folytatott**; előző/következő/szünet működik; az app minilejátszója és a widget ugyanazt mutatta (cím + állapot).
  (Itt derült ki és javítva lett: a Media3 `MediaButtonReceiver` Android 8 felett csak a „play” gombot fogadja.)
- **Címkeszerkesztés MP3-on (új borítóval):** a rendszer írási kérése megjelent; **„Deny” → „Nincs engedély…”,
  a fájl md5-je változatlan**; „Allow” → mentés; ffprobe a lehúzott fájlon: új cím/előadó/album + beágyazott JPEG,
  hossz változatlan (35,0 mp); a MediaStore és a lista az új címet és borítót mutatta; a várólistában is frissült.
- **Kivágás MP3-ból:** csúszkával 8,6–21,2 mp, előhallgatás a kezdőpontról; kimenet `Music/SzabadZene/p3 Cimkezendo
  (vágott).mp3`, ffprobe: 12,64 mp, hibátlan dekódolás, címkék átkerültek; **az eredeti md5-je változatlan**.
- **Csengőhang:** magyarázat → rendszerképernyő; visszalépés engedély nélkül → „A csengőhang nem változott…”;
  engedélyezés után beállítva (`settings get system ringtone` = a fájl), szimulált bejövő hívásnál (`gsm call`)
  a Telecom ezt játszotta le. Utána az emulátor eredeti csengőhangja visszaállítva.
- **Törlés:** a vágott fájl kedvencekben, egy listában és kétszer a várólistában; megerősítés (név, méret, „nem
  vonható vissza”), majd a rendszer törlési kérése → a fájl és a MediaStore-sor eltűnt; Kedvencek 0, lista 0,
  várólista 17 → 15, zenetár 15. Lista-környezetben „Eltávolítás ebből a listából” és „Törlés a telefonról” külön pont.
- **A most szóló szám címkéjének mentése:** mentés közben a lejátszás nem állt meg és nem indult újra (kb. 8 mp-cel
  a mentés után 0:26-on járt), a lejátszó az új címet kapta, a következő számra hiba nélkül lépett. (Ez egy generált
  tesztfájlon történt; az eredeti tesztfájl utána visszatöltve.)

### Nem tesztelt / valódi készüléken ellenőrizendő
- Android 8–10 engedélyútja (`WRITE_EXTERNAL_STORAGE`, régi tárhelymód) – csak API 37-en futott minden; a kód megvan.
- Hangszínszabályzó nélküli készülék (a „nem támogatott” ág): az emulátoron van hatás, így ez csak kódból ellenőrzött.
- Gyártói hanghatás-felületek (pl. Dolby, Samsung SoundAlive) együttélése a saját hangszínszabályzóval.
- Valódi hívás csengése (csak emulátoros `gsm call`).
- Opus/ALAC vágása (újrakódolási tartalékút), nagyon nagy (több száz MB-os) fájl címkézése/vágása – a teljes fájl
  másolata a gyorsítótárba kerül, kevés szabad helynél a művelet hibával (adatvesztés nélkül) leáll.

### Ismert korlátok, megjegyzések
- A MediaStore másodpercre kerekített módosítási idő + méret alapján dönt az újraolvasásról: ha egy fájlt
  ugyanabban a másodpercben, ugyanakkora méretre írunk, mint az előző beolvasás, nem olvassa újra (a valós
  használatban ritka; a tesztben ezért vár 2 mp-et).
- A widget előző/következő gombja csak futó lejátszónál működik; leállított appnál a lejátszógomb folytat.
- Az elalvásidőzítő nem éli túl a folyamat leállítását (szándékosan: újraindításkor nem indul magától zene).
- A widget neve a kezdőképernyő widgetlistájában a rendszer nyelvén jelenik meg (ezt az indító dönti el).
- Adatbázis-séma változatlan (v2): a törlés-következetességhez nem kellett új tábla.

### Amit a 4. rész vár / tudnivalók
- Átfogó ellenőrzés: Android 8–10 (API 28 emulátor) fájlművelet-útjai, hangszínszabályzó nélküli eset.
- Tesztfájlok: `python -I tools/generate_test_audio.py --fixtures` (gitben: `app/src/sharedTest/assets/audio/`).

---


## 4. rész – átfogó ellenőrzés, javítás, átadás · KÉSZ (2026-11-15) · 1.0.0

Részletes eredmények: `TESZTOSSZEFOGLALO.md`; licencek: `LICENCEK.md`; használat/fordítás: `README.md`.

### Elkészült
- A specifikáció 7. fejezetének mind a 12 pontja végigjárva `Clean_API_37`-en (kis kijelző 320 × 568 dp, fekvő tájolás,
  1,3× és 2,0× betű, repülőgép-mód, engedélymegtagadás, hiányzó/sérült fájl, újraindítás utáni visszaállás);
  az összefésült manifestben nincs `INTERNET` (és `ACCESS_NETWORK_STATE` sem).
- A 3. rész fájlműveletei (címke + borító, vágás, csengőhang, törlés) **Android 9-en** (`Medium_Phone_API_28`) is kipróbálva.
- **Talált és javított hibák:** (1) Android 9-en a „Törlés a telefonról” csak a MediaStore-sort törölte, a fájl a tárhelyen
  maradt → `TrackFileEditor.deleteLegacy` a fájlt is törli, siker csak ha a fájl és a sor is eltűnt; regressziós teszt:
  `FileOperationsTest.deleteRemovesThePhysicalFileAndTheMediaStoreRow` (a javítás nélkül bukik). (2) `TagEditorDialog`:
  rendszersáv-ikonok és a navigációs sáv alá lógó „Mentés” gomb. (3) Alsó navigáció címkéi 2,0× betűnél levágódtak
  (a címke betűmérete 1,3×-nél korlátozva). (4) Licencek képernyő kiegészítve (Okio, kotlinx.serialization, JSpecify, JetBrains Annotations).
- A `FileOperationsTest` teljes MediaStore-folyamata és a címkeolvasás-teszt API 28-on is fut (`createStoreFile`, `cleanUp` segédek).
- Átadási dokumentumok: `README.md` újraírva, `docs/TESZTOSSZEFOGLALO.md`, `docs/LICENCEK.md`.
- Verzió: 1.0.0 (versionCode 4). Teszt-APK: `dist/SzabadZene-1.0.0-debug.apk` (debug kulccsal aláírt debug build,
  SHA-256 a `dist/SHA256SUMS.txt`-ben; a `dist/` nincs gitben). Release-aláírás nincs beállítva.

### Tesztek – ténylegesen lefuttatva (végleges kódon, `clean` után)
- JVM-egységtesztek: **95/95**. Műszeres tesztek: **28/28 `Clean_API_37`-en és 28/28 `Medium_Phone_API_28`-on**
  (a 3. rész 27 tesztje + az új törlési regressziós teszt). `lintDebug`: 0 hiba, 14 figyelmeztetés. `assembleDebug`: sikeres.

### Nyitva / valódi készüléken ellenőrizendő
- Bluetooth, vezetékes fejhallgató kihúzása/visszadugása, valódi BT-gombok, hívás közbeni hangfókusz valódi hívásnál,
  hangszínszabályzó nélküli készülék ága, gyártói hanghatások, nagyon nagy zenetár / nagy fájlok.
- A widgetet a 4. részben nem néztem meg újra (a 3. részben ellenőrizve); Android 8 és 10–12 rendszeren nem futott semmi.
- Nincs automatizált teszt a rendszersáv-színezéshez és az alsó navigáció betűméret-korlátjához (képernyőképpel ellenőrizve).
- Ismert, nem javított apróság: ha az olvasási engedélyt az indítás előtt visszavonják, a mentett várólista számai
  „nem sikerült lejátszani” üzenettel egymás után kihagyódnak (összeomlás nélkül); jobb lenne ilyenkor egyetlen
  „nincs engedély” üzenet – nem mély átalakítás, de nem kritikus.
- Nyilvános közzététel előtt: a JAudioTagger LGPL-szövegének mellékelése (lásd `LICENCEK.md`).

### Tudnivalók a további munkához
- API 28-on nincs `scan_volume`: `adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/...`.
- `adb shell` idézőjelezés: szóközös útvonalnál `adb shell "rm '/sdcard/Music/x y.mp3'"`.
- Műszeres tesztek eszközönként: `ANDROID_SERIAL=emulator-5556 ./gradlew connectedDebugAndroidTest` (a futás után az app eltávolítódik).

---

## 5. rész – lejátszó-bővítések, lejátszásszám, dalszöveg · KÉSZ (2026-11-16) · 1.1.0

A felhasználó egy másik lejátszó képernyőképei alapján kért bővítéseket; a követelmények:
`KOVETELMENYEK.md` 9. fejezet. A két kiemelt elvárás nem gyengült (a minilejátszóban és a teljes
lejátszóban is megmaradt a fő ±N mp-es pár, minden ugrógomb legalább 56 × 56 dp).

### Elkészült
- **Zenetár:** mindig látható keresőmező a Számok fül tetején („Dalok keresése (N)”) a rendezés gombjával;
  alapértelmezett rendezés: a legújabb szám felül (`LibrarySort` alapértéke; mezőváltáskor a természetes irány).
- **Minilejátszó:** borító, cím, előadó, „» következő szám”, várólista-sorszám (#22/48 → várólista);
  vezérlők: −N, előző, lejátszás, következő, +N; vízszintes húzás vált számot. Kétsoros (600 dp alatt), széles
  kijelzőn egysoros. 2,0× betűnél a „következő szám” sor elmarad.
- **Előző gomb:** beállítható küszöb (3/5/10/15/30 mp, alapból 10 mp, korábban rögzített 3 mp):
  `PreviousLogic` + `SkipAwarePlayer.seekToPrevious` – így az értesítés, a fejhallgató és a widget is ezt követi.
- **Teljes lejátszó:** fejléc (várólista sorszámmal, Dal/Dalszöveg), „» következő”, műveletsor (sebesség,
  hangszínszabályzó, kedvenc, listához adás, időzítő, hangerő), több ugrógomb (alapból −30 −10 −5 | +5 +10 +30,
  a fő érték mindig; helyhiánynál előbb a fogaskerék, aztán a további gombok maradnak el), vezérlősor keveréssel
  és ismétléssel. Ismétlés: 4 módú választó, köztük „leállás az aktuális dal végén” (`PlaybackPrefs.stopAfterCurrent`,
  a szolgáltatás `pauseAtEndOfMediaItems`-szel hajtja végre, majd a következő szám elejére áll).
- **Sebesség** (0,5–2,0×, tárolva, a szolgáltatás alkalmazza), **hangerő** (rendszer médiahangereje).
- **Lejátszásszám:** Room v3, `play_stats` tábla (AutoMigration 2→3); `PlayCountTracker` (fél szám, legfeljebb
  4 perc tényleges hallgatás; lejátszásonként egyszer); kézi átírás a számmenüben; Listák fül: Legtöbbet játszott,
  Nemrég játszott (100-100), előzmények törlése / számlálók nullázása; fájltörléskor a statisztika is törlődik.
- **Dalszöveg:** beágyazott dalszöveg olvasása/írása (`TagValues.lyrics`, null = nem nyúl hozzá; a címkeszerkesztő
  így nem írja felül), `LrcParser` (időzített LRC, offset, fejléc-címkék, szavankénti jelölés), Dalszöveg nézet
  kiemeléssel és koppintásra tekeréssel, szerkesztő .lrc/.txt importtal (UTF-8, különben Windows-1250).
- **Beállítások:** ugrógombok a teljes lejátszóban, Előző-küszöb, lejátszási sebesség; súgó két új témával.
- Mellékesen javítva: az `app/build.gradle.kts` egy része Latin-1 kódolású volt → UTF-8.

### Tesztek – ténylegesen lefuttatva (végleges kódon)
- JVM-egységtesztek (`clean` után): **120/120** (új: `PlayerExtrasLogicTest` 9, `PlayCountTrackerTest` 8,
  `LyricsAndStatsTest` 7, `TagEditorTest.lyricsWriteReadAndKeepOtherTags`).
- Műszeres tesztek: **33/33 `Clean_API_37`-en és 33/33 `Medium_Phone_API_28`-on** (új: több ugrógomb és
  `onSkipBy`, ismétlés-választó + Dalszöveg fül a kezelőszervekkel, minilejátszó gombjai/húzás/várólista,
  `play_stats` DAO, migráció 2→3).
- `lintDebug`: 0 hiba, 15 figyelmeztetés (függőségverziók, többes szám-javaslatok, API 31+ widget-attribútumok).
- Kézi ellenőrzés `Clean_API_37`-en (képernyőképek a munkamenet ideiglenes mappájában):
  keresőmező és legújabb-elöl rendezés; minilejátszó (#2/11, » következő, előző/következő); teljes lejátszó
  6 ugrógombbal + fogaskerékkel (411 dp); Előző: 40 mp-nél elejére, másodszorra előző szám; számláló
  (12 mp-es szám kétszer számolt), kézi átírás 2 → 6 és átrendeződés; Nemrég játszott sorrend; ismétlés-választó;
  leállás mód: a 60 mp-es szám végén szünet, a következő szám 0:00-n; sebesség 1.5× (a munkamenet is 1.5-öt
  jelent); dalszöveg hozzáadása FLAC-hoz (rendszer írási engedélye) és időzített kiemelés lejátszás közben;
  Beállítások új sorai; 720×1280, 360 dpi, 2,0× betű: minilejátszó gombjai elférnek.

### Nem tesztelt / valódi készüléken ellenőrizendő
- Android 9-en csak a műszeres tesztek futottak; az új funkciókat kézzel nem próbáltam ott (a dalszöveg-mentés
  ugyanazt az engedélyutat használja, mint a 4. részben Android 9-en kipróbált címkeírás).
- A hangerő-ablak valódi Bluetooth-eszközzel (abszolút hangerő), illetve fix hangerejű készüléken.
- A húzásos számváltás érzékenysége valódi ujjal.

### Ismert korlátok, megjegyzések
- Külön .lrc fájlt az app magától nem olvas (Android 13+ alatt a hangfájl-engedély nem ad hozzáférést a
  szövegfájlokhoz); a szerkesztőben fájlból importálva a zenefájlba kerül.
- A lejátszásszám csak az 1.1.0 telepítése óta gyűlik; korábbi lejátszásokról nincs adat.
- A teljes lejátszóban kb. 400 dp szélesség alatt a fogaskerék elmarad (a beállítás a Beállításokban is elérhető).

---

## 6. rész – méretek, kijelölés, fülváltás húzással, listaimport · KÉSZ (2026-11-16) · 1.2.0

A felhasználó kérései: állítható betű- és szimbólumméret; Lejátszás és Keverés gomb a főoldalon; erősebb
elválasztás a számok között; oldalra húzással váltás az alsó fülek között; több szám kijelölése és együttes
kezelése; szélesebb, „menőbb” oldalsó menügomb; lejátszási listák importálása (pl. egy mappából).
Követelmények: `KOVETELMENYEK.md` 10. fejezet. A két kiemelt elvárás nem gyengült (a menüterület 64 → 72 dp
széles, minimum 64 × 64 dp; az ugrógombok változatlanok).

### Elkészült
- **Betű- és ikonméret** (Beállítások → Megjelenés, 85/100/115/130/150%): `AppearanceSettings.textScalePercent`,
  `iconScalePercent`; `ui/theme/UiScale.kt` (`ProvideUiScale`, `LocalIconScale`). A betűszorzó a rendszerével
  együtt legfeljebb 2,0×; 100%-nál a rendszer sűrűsége érintetlen. Minden ikon az `ui/components/Icon.kt`
  burkolón át jelenik meg (a méretet a `size` paraméter adja, nem a módosító – így hat rá a szorzó); a
  borító helyőrzője szándékosan a Material `Icon`. Az alsó navigáció ikonja legfeljebb 1,3×.
- **Számok fül:** „Lejátszás”/„Keverés” gomb a keresőmező alatt (a látható sorrendben); kijelölés gomb a
  keresőmező mellett. Kijelölő mód (`TrackSelection` a `TrackList`-ben; hosszú nyomással is indul):
  jelölőnégyzet, kiemelt háttér, felső sáv (bezárás, „N kijelölve”, mind/egyik sem, lejátszás, ⋮). A ⋮ a
  gyűjteménymenüt nyitja `selectionActions`-szel (kedvencek, megosztás – `ACTION_SEND_MULTIPLE` –, törlés),
  `onFinished` zárja a kijelölést. Vissza gomb, keresés és fülváltás kilép a kijelölésből.
- **Több fájl törlése:** `MenuState.deleteFiles` (lista; a régi `deleteFile` helyett), egy megerősítés, Android
  11-től egy rendszerablak az összes fájlra, régebben fájlonként `deleteLegacy`; `afterDeleted(list)` egyetlen
  újraolvasással; részleges sikernél „törölve: x / y”.
- **Számsorok:** 1 dp-s `outlineVariant` elválasztó vonal (`Modifier.rowDivider`) a szám- és gyűjteménysorokon;
  közös `RowMenuArea`: 72 dp széles, teljes sormagasságú terület, közepén tónusos kör „•••” ikonnal (az éppen
  szóló számnál `primaryContainer`), körkörös hullám.
- **Fülváltás húzással:** `AppRoot.MainScaffold` → `HorizontalPager` (mind a négy oldal komponálva marad,
  `beyondViewportPageCount = 3`), fülenként saját `NavHost` és `NavController`. A Vissza gombot csak a látható
  fül navigációja kapja (a többi oldal egy néma `OnBackPressedDispatcher`-t lát); a fül gyökerén a Vissza az első
  fülre visz; az aktív fülre koppintva a fül kezdőképernyője jön.
- **Listaimport:** `data/playlists/PlaylistImport.kt` (tiszta Kotlin: `PlaylistFileParser` M3U/M3U8/PLS,
  UTF-8 vagy windows-1250; `PlaylistPaths`; `PlaylistMatcher`; `uniquePlaylistName`) és `PlaylistImporter`
  (SAF: `OpenMultipleDocuments` / `OpenDocumentTree`, mappa almappákkal 3 szintig, max. 300 fájl; a listafájl
  mappája a dokumentumazonosítóból). Felület: `ui/playlists/PlaylistImportUi.kt` (Listák fejléc, összesítő
  ablak). „Mentés új listaként” az album/előadó/műfaj/mappa menüjében (`CollectionMenuRequest.playlistName`).
- Súgó: két új téma (kijelölés, listaimport), a számmenü leírása frissítve; verzió 1.2.0 (versionCode 6).

### Tesztek – ténylegesen lefuttatva (végleges kódon)
- JVM-egységtesztek (`clean` után): **132/132** (új: `PlaylistImportTest` 12 – M3U/PLS olvasás, kódolás,
  útvonalak, dokumentumazonosító → mappa, párosítás abszolút/relatív/idegen útvonallal, kétértelmű fájlnév és
  eltérő hossz elutasítása, cím szerinti tartalék, sorrend és hiányzók, egyedi név, méretszorzók).
- Műszeres tesztek: **39/39 `Clean_API_37`-en és 39/39 `Medium_Phone_API_28`-on** (új: `SelectionAndScaleTest` 3 –
  hosszú nyomás, koppintásos és jelölőnégyzetes kijelölés, a menü kijelölő módban is csak menü; 72 dp-s
  menüterület 1,5× ikonnal és 2,0× betűvel 320 dp-n; ikon- és betűszorzó; `MainNavigationTest` 3 – húzás a
  fülek között, Vissza előbb a fülön belül, majd az első fülre; a fül megtartja a képernyőjét).
- `lintDebug`: 0 hiba, 15 figyelmeztetés (ugyanazok, mint az 1.1.0-ban).
- Kézi ellenőrzés `Clean_API_37`-en: új számsorok és gombok; kijelölés gombbal, 3 szám → kedvencekhez (a sáv
  bezárult, „3 szám a kedvencek közé került”); oldalra húzás a Listák fülre; mappaimport (Music/Listak:
  Buli 3/4, Edzes 2/2 almappából, .txt kihagyva) és fájlimport (windows-1250 kódolású Regi.m3u 1/1); az
  importált lista tartalma és sorrendje; 150%-os betű és ikon a zenetárban és a teljes lejátszóban (az
  ugrógombok elférnek; a „Lejátszás” felirat kettétörését javítottam); két ideiglenes másolat törlése
  kijelöléssel, egy rendszerablakkal (a fájlok eltűntek); album mentése új listaként.
- Kézi ellenőrzés `Medium_Phone_API_28`-on: hosszú nyomásos kijelölés, Vissza, húzás a Listák fülre; import a
  Letöltésekből (2/4 – az egyik bejegyzés szándékosan hibás hosszát az app helyesen elvetette).

### Nem tesztelt / valódi készüléken ellenőrizendő
- A fülek közötti húzás és a minilejátszó húzása valódi ujjal (egymás mellett nem ütköznek: a minilejátszó
  a lapozón kívül van).
- Több fájl törlése és megosztása Android 9-en kézzel (a törlési út ugyanaz, mint az egyes törlésé).
- Nagyon nagy (több ezer számos) zenetár és lista importálásának sebessége.

### Ismert korlátok, megjegyzések
- A kijelölés csak a Számok fülön van (album, lista, keresési találatok nézetben nincs).
- Az import a hálózati (http) bejegyzéseket kihagyja; .wpl/.xspf formátumot nem olvas. Azonos nevű listánál
  „Név (2)” jön létre (újraimportáláskor is). Csak fájlnév szerint egyező bejegyzést csak akkor fogad el, ha
  egyértelmű, és ha a listafájl megadja a hosszt, az is egyezik (±3 mp).
- A betűméret-szorzó a rendszer betűméretével együtt legfeljebb 2,0×; 2,0×-es rendszerbetűnél nincs hatása.

---

## 7. rész – zenetár fejléc és fülek, Lejátszás/Keverés viselkedés, widget, listaexport · KÉSZ (2026-11-16) · 1.3.0

A felhasználó kérései: a „Zenetár” fejléc tűnjön el lefelé görgetéskor, és csak a lista tetején jöjjön vissza;
beállítható legyen, mely belső fülek látszanak (egyetlen fülnél tűnjön el a fülsáv); a Lejátszás és a Keverés
csak ikon legyen a keresősáv mellett, és beállítható legyen, mit tegyenek, ha már szól zene (a mostani szám ne
ugorjon el, utána érvényesüljön az új szabály); a widget új elrendezése (balra a borító, jobbra felül a gombok,
a két szélen ±N mp, alattuk a cím, alatta kisebben a következő szám); listaexport kiválasztható listákkal.
Követelmények: `KOVETELMENYEK.md` 11. fejezet. A két kiemelt elvárás nem változott.

### Elkészült
- **Fejléc:** `LibraryScreen` – `TopAppBarDefaults.exitUntilCollapsedScrollBehavior()`; a `nestedScroll` csak
  akkor kapcsolódik, amikor a fejléc látszik (keresésben és kijelölésben nem, különben a rejtett fejléc
  elnyelné a görgetést); fülváltáskor visszaáll. A fülsáv a helyén marad.
- **Fülek:** `LibrarySection` (TRACKS, ALBUMS, ARTISTS, GENRES) és `LibraryLayout` a beállításokban
  (`library_sections`, kanonikus sorrend, legalább egy); `ui/library/LibraryTabsDialog.kt` (az utolsó bejelölt
  fül nem kapcsolható ki). Megnyitható a fejléc ⋮ menüjéből és a Beállítások → Zenetár részből. Egy fülnél
  nincs `PrimaryScrollableTabRow`. A betöltésig (egy képkocka) nem rajzol fület.
- **Lejátszás/Keverés:** `PlayShuffleIconButtons` (48 dp-s kitöltött / tónusos ikongomb) a keresőmező mellett;
  a kijelölés és a rendezés gombja a fejlécbe került. Közös művelet: `rememberPlayAll()` →
  `PlayerConnection.playAll(tracks, shuffle, keepCurrent)`; az album- és listanézet gombjai is ezt használják.
  A Lejátszás most kikapcsolja a keverést (`playInOrder`).
- **„A mostani szám végigszól” mód:** `PlaybackPrefs.playButtons` (`PlayButtonMode.RESTART` alapból /
  `AFTER_CURRENT`). Ha szól zene, a `PLAY_KEEP_CURRENT` munkamenet-parancs megy a szolgáltatásnak
  (`PlaybackService.playKeepingCurrent`): a most szóló elem marad, előtte/utána lecserélődik a lista
  (`QueueLogic.keepCurrentPlan`), keverésnél a kevert sorrend a mostani számmal kezdődik. Visszajelzés:
  `LocalSnackbarHost` („A mostani szám után keverve/sorrendben folytatódik”).
- **Widget:** új `widget_player.xml` (76 dp-s borító, gombsor: −N mp, előző, lejátszás, következő, +N mp; cím;
  „Következő: …”); `PlayerWidget.Snapshot` + `nextTitle`, `backSeconds`, `forwardSeconds`; az ugrás
  `KEYCODE_MEDIA_REWIND/FAST_FORWARD` → `sessionPlayer.seekBack/seekForward` (a közös ugrási logika). A
  szolgáltatás az ugrás-beállítás, az ismétlés és a keverés változásakor is frissíti. Alapméret 4 × 1
  (`targetCell` 4 × 1; a `minHeight` szándékosan 40 dp, mert Android 12 előtt a launcher ebből számolja a sorokat).
- **Export:** `data/playlists/PlaylistExport.kt` (tiszta Kotlin: `StorageLocation`, `PlaylistFileWriter` –
  fájlnév, egyedi név, relatív útvonal, M3U8) és `PlaylistExporter` (SAF: `OpenDocumentTree`, `createDocument`,
  felülírásnál „wt”); `ui/playlists/PlaylistExportUi.kt` (Listák fejléc, kiválasztó ablak, összesítő).
- Súgó és szövegek frissítve (kijelölés, import/export, widget, számmenü); verzió 1.3.0 (versionCode 7).

### Tesztek – ténylegesen lefuttatva (végleges kódon)
- JVM-egységtesztek (`clean` után, majd a végleges kódon újra): **142/142** (új: `PlaylistExportTest` 10 – tárhely és
  mappa a dokumentumazonosítóból, relatív útvonal, helyes/egyedi fájlnév, M3U8 tartalom, export → import
  visszaolvasás ugyanazokra a számokra, a fülek beállításának szabályai, „a mostani szám végigszól” terv
  sorrendben, listán kívüli számmal és keverve).
- Műszeres tesztek: **44/44 `Clean_API_37`-en és 44/44 `Medium_Phone_API_28`-on** (új: `LibraryLayoutTest` 5 – a
  fejléc eltűnik és visszajön, egy fülnél nincs fülsáv és az utolsó fül nem kapcsolható ki, az ikonok a keresőmező
  mellett, „a mostani szám végigszól” mód a valódi lejátszóval, az export kiválasztó ablaka). A teszt friss
  telepítésen egy valódi hibát is kimutatott (lásd lent), javítva.
- `lintDebug`: 0 hiba, 15 figyelmeztetés (a widget szándékos eltéréseit `tools:ignore` jelöli).
- Kézi ellenőrzés `Clean_API_37`-en (51 számmal): fejléc eltűnése/visszatérése, részleges visszagörgetésnél
  rejtve marad; fülek ablaka (fejléc ⋮ és Beállítások), egy fülnél nincs fülsáv; „a mostani szám végigszól”:
  Keverés → a szóló szám marad (#5/51 → #1/51), üzenet; Lejátszás → #5/51, a pozíció folyamatosan nő; widget
  4 × 1: borító, cím, „Következő: Tavaszi szél”, lejátszás, +10/−10 mp (123,9 → 134,2 → 124,7 s); import
  (3/4, 2/2, 1/1); export a Music/Export mappába relatív útvonalakkal; második export „(2)” fájlokkal;
  felülírással a meglévő fájl tartalma cserélődik (2091 → 117 bájt, maradék nélkül).
- Kézi ellenőrzés `Medium_Phone_API_28`-on: a widget 4 × 1-ként kerül ki, a tartalma egy sorba fér.
- **Javított hiba:** ha a folyamat engedély nélkül indult (widget, médiagomb), és az engedélyt később a
  rendszerbeállításokban adták meg, a Zenetár végtelenül „beolvasott”. Most a `LibraryScreen` is indítja a
  beolvasást, ha engedélyt lát (`LibraryRepository.start()` egyszeri, többször hívható).

### Nem tesztelt / valódi készüléken ellenőrizendő
- A fejléc eltűnése és a widget valódi telefonon, más launcherrel (pl. Samsung One UI: a cellaméret eltér, a
  widget 4 × 1-es alapmérete ott más lehet).
- Export SD-kártyára és a Letöltések-szolgáltatóba (ott teljes útvonal kerül a fájlba); nagyon nagy listák.
- Az „a mostani szám végigszól” mód több ezer számos zenetárral (a parancs az összes azonosítót átküldi).

### Ismert korlátok, megjegyzések
- A „Felülírás” jelölő a munkameneten belül megjegyzi az utolsó állapotát.
- Az export kiválasztó ablaka a listák állapotát a megnyitáskor olvassa; az automatikus listák (Legtöbbet /
  Nemrég játszott) a pillanatnyi tartalmukkal kerülnek a fájlba.
- A gyűjteménymenük „Lejátszás/Keverés” pontja (⋮) továbbra is azonnal indít (a beállítás a gombokra vonatkozik).

---

## 8. rész – lejátszásszámok importálása másik lejátszóból · KÉSZ (2026-11-16) · 1.4.0

Egy másik lejátszóból (amely nem exportál, és az adatbázisa zárolt) a lejátszásszámokat és a listákat a
képernyőről olvastuk ki USB-n, adb-vel – csak olvasva (`tools/scrape_play_counts.py`, `tools/scrape_playlists.py`).
A kiolvasott adatok (CSV, .m3u8) személyesek, a projekten kívül maradnak.

### Elkészült
- `data/stats/PlayCountImport.kt` (tiszta Kotlin): `PlayCountCsv` (elválasztó-felismerés, fejléc vagy alapsorrend,
  idézőjelek, BOM, hossz), `PlayCountMatcher` (cím/fájlnév, előadó, hossz), `PlayCountMerge` (csak növel).
  `PlayCountImporter` (fájl → párosítás → `setPlayCount`), `ui/settings/PlayCountImportUi.kt`, új sor a
  Beállítások → Zenetár részben. A listák a meglévő .m3u8-importtal jönnek át (cím szerinti párosítás).

### Tesztek – ténylegesen lefuttatva
- JVM 151/151 (új: `PlayCountImportTest` 9), műszeres 45/45 `Clean_API_37`-en (új: `PlayCountImportInstrumentedTest`),
  lint 0 hiba. Valódi telefonon: a kiolvasók több mint ezer soros listán, a párosítás 99%-os volt.

### Tanulságok
- Telefont vezérlő szkriptből egyszerre csak egy fusson (két példány egymást zavarja), `run_in_background`-dal.
- A `monkey` indítóparancs bekapcsolhatja az automatikus forgatást: appot `am start`-tal indíts.

### 1.4.1
- A „Legtöbbet játszott” listának nincs korlátja; a „Hozzáadás a kedvencekhez” minden gyűjtemény menüjében megvan.
- Tesztek: JVM 152/152, műszeres 45/45, lint 0 hiba.

---

## 9. rész – listák rendezése, új ikon, új név · KÉSZ (2026-11-16) · 1.5.0

- **Listák rendezése** (Listák fül fejléc, rendezés gomb): név / létrehozás ideje / számok száma, csökkenő, és
  „Különleges listák mindig elöl” (alapból be). Tiszta logika: `data/playlists/PlaylistOrdering.kt`; a
  `PlaylistSummary` a létrehozás idejét is hozza (`created_at`, séma nem változott). Beállítás: `playlist_sort`,
  `playlist_sort_descending`, `playlist_special_first`.
- **Új ikon:** sötét mélyzöld sugaras háttér (`drawable/ic_launcher_background.xml`), rügyzöld hangjegy levélzászlóval
  és két hanghullámmal (`ic_launcher_foreground.xml`), egyszínű változat a témázott ikonokhoz
  (`ic_launcher_monochrome.xml`). A rajz a biztonságos 66 dp-s körön belül van (kerek maszkkal ellenőrizve).
- **Új név:** magyarul **Zengő**, minden más nyelven **Orotund** (`app_name`, widget, súgó). A vágott fájlok mappája
  `Music/Orotund`. A csomagnév (`io.github.x1wer.szabadzene`) és a forráskód-csomag változatlan, hogy a telepített
  app adatai megmaradjanak.
- Tesztek: JVM 155/155 (új: `PlaylistOrderingTest` 3), műszeres 45/45 `Clean_API_37`-en, lint 0 hiba.
- Verzió 1.5.0 (versionCode 10). GitHub: `X1wer/Orotund`.

### Aláírt kiadás – 1.5.0 (2026-11-16)
- Új, saját Orotund-kulcs (PKCS12, RSA 4096, alias `orotund`; a tanúsítvány SHA-256-ja a README-ben). A kulcstár és
  a DPAPI-val védett jelszófájl a felhasználói profil kulcsmappájában van, a kiadási szkriptek a projekten kívül
  (`outputs/Orotund-release`): a jelszó csak az apksigner folyamat környezetében él.
- A kiadási build (R8) eddig nem fordult: a JAudioTagger AWT/ImageIO hivatkozásaira `-dontwarn`, a könyvtárra
  `-keep` (reflexió). Kézzel ellenőrizve az aláírt APK `Clean_API_37`-en: indítás, zenetár, lejátszás,
  címkeolvasás és -írás (rendszer-jóváhagyással), hiba nélkül.
- A GPL-3.0 és az LGPL-2.1 teljes szövege az APK-ban, a Licencek képernyőről megnyitható.
