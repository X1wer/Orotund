# SzabadZene – projektutasítások

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
│   ├── db/          Room v2: tracks (gyorsítótár), playlists, playlist_entries, favorites,
│   │                queue_items, playback_state; DAO-k: TrackDao, PlaylistDao, FavoriteDao, QueueDao
│   ├── library/     MediaStoreScanner, LibraryRepository (szűrt + teljes folyam), Track,
│   │                Id3TextReader, LibraryLogic (keresés, rendezés, csoportosítás, szűrés – tiszta Kotlin)
│   ├── edit/        TagEditor (JAudioTagger: MP3 ID3v2.4/UTF-8, M4A, FLAC, OGG Vorbis – tiszta JVM),
│   │                LosslessTrim (MP3-keret- és WAV-mintamásolás, tiszta Kotlin), AudioTrimmer (+ M4A
│   │                átcsomagolás, FLAC/OGG → AAC újrakódolás MediaCodec-kel), TrackFileEditor (Android-oldal:
│   │                másolat → írás → visszaolvasás → visszaírás → ellenőrzés; vágás új fájlba; törlés; csengőhang)
│   ├── playlists/   PlaylistRepository (listák, kedvencek; forgetTrack a fájltörlés után)
│   └── settings/    SettingsRepository (DataStore): ugrás, megjelenés, fejhallgató, szűrés, rendezés, hangszínszabályzó
├── playback/
│   ├── PlaybackService   – MediaSessionService: az EGYETLEN ExoPlayer + MediaSession; várólista-mentés
│   │                       és -visszaállítás, onPlaybackResumption, fejhallgató-figyelés
│   ├── SkipController    – az EGYETLEN közös ugrási logika (tiszta Kotlin, unit-tesztelt)
│   ├── SkipAwarePlayer   – ForwardingPlayer: rendszer/fejhallgató előre-vissza + beszúrások figyelése
│   ├── QueueLogic        – visszaállítás és kevert sorrend (tiszta Kotlin, unit-tesztelt); QueueStore
│   ├── PlayerConnection  – a felület MediaController-kapcsolata, PlaybackUiState és várólista-folyam
│   ├── PlaybackCommands  – saját munkamenet-parancsok (ugrás, időzítő, újratöltés) és események (hiba)
│   ├── SleepTimer        – elalvásidőzítő logikája (tiszta Kotlin); a szolgáltatás futtatja, állapota
│   │                       munkamenet-extrákban jut a felülethez (PlayerConnection.sleepTimer)
│   ├── EqualizerLogic / AudioEffectsController – előbeállítások (log-frekvenciás görbe), a hangmunkamenetre
│   │                       kötött Equalizer + BassBoost; az elérhetőséget AudioEffectsRegistry teszi közzé
│   └── PlayerWidget      – kezdőképernyős widget (RemoteViews); a szolgáltatás frissíti, a gombok médiagombok
└── ui/
    ├── AppRoot           – navigáció: fő képernyő 4 füllel (saját ágakkal), teljes lejátszó, várólista
    ├── Menus             – közös szám- és gyűjteménymenü (MenuState, MenuHost), megosztás, listához adás
    ├── components/       – TrackRow, CollectionRow, ReorderableColumn, Dialogs, SkipButton, Artwork, ...
    ├── edit/             – FileActionsHost (engedélyfolyamatok API-szint szerint), TagEditorDialog, TrimDialog
    ├── sound/            – EqualizerScreen, SleepTimerDialog
    ├── library/ playlists/ folders/ queue/ player/ settings/ theme/
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
- Átadási dokumentumok: `README.md`, `docs/TESZTOSSZEFOGLALO.md`, `docs/LICENCEK.md`. Verzió: 1.0.0.
- Kiadási aláírás még nincs. Ha kell: saját, új kulccsal (más app kulcsát ne használd).
