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


## 4. rész – átfogó ellenőrzés, javítás, átadás · NINCS ELKEZDVE
