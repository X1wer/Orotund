# Orotund / Zengő (korábban SzabadZene) – projektutasítások

Offline, natív Android-zenelejátszó (Kotlin, Jetpack Compose, Material 3, Media3). A teljes
követelménylista: `docs/KOVETELMENYEK.md`. A munka állapota részenként: `docs/ALLAPOT.md` –
**minden munkamenet elején olvasd el, a végén frissítsd.**

## Közös szabályok

1. **A két kiemelt elvárást semmilyen okból nem szabad gyengíteni:** a számsorok legalább
   64 × 64 dp-s menüterülete (`ui/components/TrackRow.kt`), és az állandóan látható
   másodperces ugrógombok a teljes és a minilejátszóban (`ui/player/`).
2. Csak ténylegesen lefuttatott tesztet szabad sikeresnek jelölni.
3. Commit előtt a fordításnak, az egységteszteknek és a lintnek (`lintDebug`) hibátlanul le kell futnia.
4. `git push` és bármilyen közzététel csak a felhasználó kifejezett kérésére.
5. A felhasználóval magyarul kommunikálj. Minden felületi szöveg kerüljön a `values/strings.xml`
   (angol, alapértelmezett) és a `values-hu/strings.xml` (magyar) fájlba is.
6. Csak a szükséges Android-engedélyeket kérd; `INTERNET` engedély nem lehet a végső manifestben.
7. Más alkalmazás projektjéhez (CardDuel, YTDLP Studio stb.) ne nyúlj.

## Fordítás és tesztek (Git Bash)

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew assembleDebug                 # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest             # JVM-egységtesztek
./gradlew lintDebug                     # lint (hiba esetén megáll)
./gradlew connectedDebugAndroidTest     # Compose UI-tesztek futó emulátoron
```

- A `connectedDebugAndroidTest` a futás végén **eltávolítja** az appot az emulátorról
  (az engedélyek és a beállítások elvesznek) – utána telepítsd újra.
- UI-tesztek előtt állítsd vissza a kijelzőt: `adb shell wm size reset`, `wm density reset`,
  `settings put system font_scale 1.0`.
- Git Bashben az eszközoldali útvonalakhoz kell: `export MSYS_NO_PATHCONV=1`.
- Tesztfájlok: `python -I tools/generate_test_audio.py` (ffmpeg kell), kimenet:
  `testdata/generated/` (nincs gitben), feltöltés: `adb push ... /sdcard/Music/SzabadZeneTeszt/`,
  majd `adb shell content call --uri content://media --method scan_volume --arg external_primary`.
- Emulátorok: `Clean_API_37` (fő teszteszköz), `Medium_Phone_API_28` (régi Android).
- Az app nyelve parancssorból: `adb shell cmd locale set-app-locales <csomag> --user 0 --locales hu`
  (nem `hu-HU`); újratelepítés utáni első indítás után add ki, különben az AppCompat felülírja.

## Architektúra

```
io.github.x1wer.szabadzene
├── SzabadZeneApp / AppContainer   – kézi függőségkezelés, egy példányos összetevők
├── MainActivity                    – Compose-gazda, téma a beállításokból; onStart/onStop köti a MediaControllert
├── data/
│   ├── db/          Room v3: tracks (gyorsítótár), playlists, playlist_entries, favorites,
│   │                queue_items, playback_state, play_stats; DAO-k: TrackDao, PlaylistDao, FavoriteDao,
│   │                QueueDao, PlayStatsDao (séma: app/schemas, AutoMigration 1→2→3, MigrationTest)
│   ├── library/     MediaStoreScanner, LibraryRepository (szűrt + teljes folyam), Track,
│   │                Id3TextReader, LibraryLogic (keresés, rendezés, csoportosítás, szűrés – tiszta Kotlin)
│   ├── edit/        TagEditor (JAudioTagger: MP3 ID3v2.4/UTF-8, M4A, FLAC, OGG Vorbis – tiszta JVM),
│   │                LosslessTrim (MP3-keret- és WAV-mintamásolás, tiszta Kotlin), AudioTrimmer (+ M4A
│   │                átcsomagolás, FLAC/OGG → AAC újrakódolás MediaCodec-kel), TrackFileEditor (Android-oldal:
│   │                másolat → írás → visszaolvasás → visszaírás → ellenőrzés; vágás új fájlba; törlés; csengőhang)
│   ├── playlists/   PlaylistRepository (listák, kedvencek; forgetTrack a fájltörlés után, a statisztikát is törli),
│   │                PlaylistImport (M3U/PLS olvasás és párosítás – tiszta Kotlin), PlaylistImporter (SAF-os import),
│   │                PlaylistExport (M3U8 írás, relatív útvonal – tiszta Kotlin), PlaylistExporter (SAF-os export)
│   ├── stats/       PlayStatsRepository + SmartLists (Legtöbbet / Nemrég játszott – tiszta Kotlin),
│   │                PlayCountImport (CSV → párosítás → csak növelő összevonás), PlayCountImporter
│   ├── lyrics/      LrcParser (időzített és sima dalszöveg – tiszta Kotlin); olvasás/mentés: TrackFileEditor
│   └── settings/    SettingsRepository (DataStore): ugrás (+ további gombok), lejátszás (Előző-küszöb, sebesség,
│                    leállás a szám végén, Lejátszás/Keverés gomb viselkedése), megjelenés (+ betű- és ikonméret),
│                    zenetár fülei (LibraryLayout), fejhallgató, szűrés, rendezés, EQ
├── playback/
│   ├── PlaybackService   – MediaSessionService: az EGYETLEN ExoPlayer + MediaSession; várólista-mentés
│   │                       és -visszaállítás, onPlaybackResumption, fejhallgató-figyelés
│   ├── SkipController    – az EGYETLEN közös ugrási logika (tiszta Kotlin, unit-tesztelt)
│   ├── SkipAwarePlayer   – ForwardingPlayer: rendszer/fejhallgató előre-vissza + beszúrások figyelése;
│   │                       az „Előző” gomb (minden vezérlőé) itt kapja a beállítható küszöböt (PreviousLogic)
│   ├── PlayCountTracker  – mikor számít meghallgatottnak egy szám (tiszta Kotlin); a szolgáltatás hajtja
│   ├── QueueLogic        – visszaállítás, kevert sorrend, „a mostani szám végigszól” terv (tiszta Kotlin,
│   │                       unit-tesztelt); QueueStore
│   ├── PlayerConnection  – a felület MediaController-kapcsolata, PlaybackUiState és várólista-folyam
│   ├── PlaybackCommands  – saját munkamenet-parancsok (ugrás, időzítő, újratöltés) és események (hiba)
│   ├── SleepTimer        – elalvásidőzítő logikája (tiszta Kotlin); a szolgáltatás futtatja, állapota
│   │                       munkamenet-extrákban jut a felülethez (PlayerConnection.sleepTimer)
│   ├── EqualizerLogic / AudioEffectsController – előbeállítások (log-frekvenciás görbe), a hangmunkamenetre
│   │                       kötött Equalizer + BassBoost; az elérhetőséget AudioEffectsRegistry teszi közzé
│   └── PlayerWidget      – kezdőképernyős widget (RemoteViews): borító, ±N mp, előző/lejátszás/következő, cím
│                           és a következő szám; a szolgáltatás frissíti, a gombok médiagombok (az ugrás REWIND /
│                           FAST_FORWARD kóddal a közös ugrási logikán megy át)
└── ui/
    ├── AppRoot           – navigáció: fő képernyő 4 füllel (HorizontalPager, fülenként saját NavHost; a Vissza
    │                       gombot csak a látható fül kapja), teljes lejátszó, várólista
    ├── Menus             – közös szám- és gyűjteménymenü (MenuState, MenuHost), megosztás, listához adás
    ├── components/       – TrackRow (+ RowMenuArea, rowDivider), CollectionRow, Icon (méretszorzós ikon),
    │                       ReorderableColumn, Dialogs, SkipButton, Artwork, ...
    ├── edit/             – FileActionsHost (engedélyfolyamatok API-szint szerint), TagEditorDialog, TrimDialog
    ├── sound/            – EqualizerScreen, SleepTimerDialog
    ├── lyrics/           – LyricsPane (Dalszöveg nézet), LyricsEditorDialog
    ├── player/           – FullPlayer, MiniPlayer, PlayerControls (ugrósor, vezérlősor, műveletsor),
    │                       PlayerDialogs (ismétlési mód, sebesség, hangerő, ugrógombok, lejátszásszám)
    ├── library/ playlists/ folders/ queue/ settings/ theme/ (UiScale: betű- és ikonméret)
```

- Az ugrás útja: gomb → `PlayerConnection.skip()` → saját munkamenet-parancs →
  `PlaybackService.performSkip()` → `SkipController.skip()` → ExoPlayer. Az értesítés gombjai és
  a fejhallgató előre/vissza gombja (`SkipAwarePlayer.seekBack/Forward`) ugyanide futnak.
- A vezérlő → munkamenet irányban a `MediaItem` URI-ja elveszhet; a szolgáltatás
  `onAddMediaItems`-ben a `requestMetadata.mediaUri`-ból vagy az azonosítóból állítja vissza.
- A MediaStore egyes ID3-címkék kódolását elrontja (magyar ékezeteknél kínai írásjelek);
  ilyenkor a `MediaStoreScanner` az `Id3TextReader`-rel a fájlból olvassa újra a szöveget.
- Fájlmódosítás engedélye: Android 11-től `MediaStore.createWriteRequest`/`createDeleteRequest`
  (rendszerablak), Android 10-ig `WRITE_EXTERNAL_STORAGE` (maxSdk 29, `requestLegacyExternalStorage`).
  Új fájl (vágás) Android 10-től engedély nélkül, `Music/SzabadZene`-be.
- MP3-címkét mindig ID3v2.4 + UTF-8 kódolással írunk: az Android ID3-olvasója a Latin-1-et és a
  Latin-1 tartományú UTF-16 szöveget is elrontja (Á, Ö → kínai írásjelek).
- A MediaStore a másodpercre kerekített módosítási idő + méret alapján dönti el, kell-e újraolvasni:
  azonos másodpercben, azonos méretre írt fájlt nem olvas újra (tesztben várni kell).
- Tesztfájlok a JVM- és műszeres tesztekhez: `app/src/sharedTest/assets/audio/`
  (`python -I tools/generate_test_audio.py --fixtures`).
- API 28-on a MediaStore-törlés a fájlt nem törli: `TrackFileEditor.deleteLegacy` a fájlt magától törli (regressziós teszt: `FileOperationsTest`).
- Átadási dokumentumok: `README.md`, `docs/TESZTOSSZEFOGLALO.md`, `docs/LICENCEK.md`. Verzió: 1.6.0.
- A zenetár Lejátszás/Keverés gombja (`rememberPlayAll` → `PlayerConnection.playAll`): „a mostani szám
  végigszól” módban, ha szól zene, a `PLAY_KEEP_CURRENT` munkamenet-parancs megy a szolgáltatásnak, amely a
  most szóló elem körül cseréli le a várólistát (az elemet nem cseréli, így a lejátszás nem szakad meg).
- Rövid visszajelzés a képernyőkből: `LocalSnackbarHost` (AppRoot adja).
- A zenetár fejléce `exitUntilCollapsedScrollBehavior`-ral tűnik el; a `nestedScroll` csak akkor kapcsolódik,
  amikor a fejléc látszik (keresésben és kijelölésben nem), különben a rejtett fejléc elnyelné a görgetést.
- Műszeres tesztben a listákon középen húzz (a lista alja a navigációs sáv mögé fut), és számíts rá, hogy a
  fő képernyő lapozója a többi fület is komponálva tartja (ugyanaz a címke több csomópontnál is lehet).
- A zenetár beolvasását a `LibraryRepository.start()` indítja (egyszeri, többször hívható): az `Application`
  és a `LibraryScreen` is hívja, ha van engedély – a folyamat indulhat engedély nélkül is (widget, médiagomb).
- Ikont mindig az `ui/components/Icon` burkolóval tegyél ki, a méretet a `size` paraméterben add meg (a
  módosítóban megadott méretre nem hat a felhasználó ikonméret-szorzója).
- A Beállítások lista a navigációs sáv mögött is fut: műszeres tesztben az alsó sorokat a saját kattintási
  műveletükkel (`performSemanticsAction(SemanticsActions.OnClick)`) nyisd meg, ne érintéssel.
- A „leállás az aktuális szám végén” mód nem a lejátszó ismétlési módja: a `PlaybackPrefs.stopAfterCurrent`
  beállítás tárolja, a szolgáltatás `pauseAtEndOfMediaItems`-szel hajtja végre, a felület (`AppRoot`) egy
  negyedik ismétlési módként mutatja.
- Parancssori (heredoc) Python-szkriptben a dupla visszaperjel a futtatáson átmenve egyszeresre változhat,
  így a kódba szánt sortörés-jelből valódi sortörés lesz: ilyen szerkesztéshez fájlba írt szkriptet vagy az
  Edit eszközt használd. Minden szövegfájl UTF-8 (az `app/build.gradle.kts` egy része korábban Latin-1 volt,
  ezt az 1.1.0-ban javítottuk) – ékezetes szöveget mindig UTF-8-ként írj.
- Kiadási aláírás még nincs. Ha kell: saját, új kulccsal (más app kulcsát ne használd).
- Ha a gépre telefon is csatlakoztatva van, a `connectedDebugAndroidTest` MINDEN eszközön futna: állítsd be az
  `ANDROID_SERIAL` változót az emulátorra (pl. `emulator-5554`), a valódi telefonra ne települjön teszt.
- A fájlíró eszköz a `\uFEFF` escape-et valódi BOM-karakterré alakíthatja (lint `ByteOrderMark` hiba): ilyen
  escape-et tartalmazó forrásnál ellenőrizd (`grep -c $'\xef\xbb\xbf' fájl`).
- Telefont vezérlő (adb-s) szkriptet mindig egy példányban, `run_in_background`-dal indíts, ne `&`-tel, és indítás
  előtt ellenőrizd, hogy nem fut-e már egy (`Get-CimInstance Win32_Process` … `scrape_`). Két példány egymást zavarja.
