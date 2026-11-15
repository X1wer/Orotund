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

## Architektúra

```
io.github.x1wer.szabadzene
├── SzabadZeneApp / AppContainer   – kézi függőségkezelés, egy példányos összetevők
├── MainActivity                    – Compose-gazda; onStart/onStop köti a MediaControllert
├── data/
│   ├── db/        Room (AppDatabase, TrackEntity, TrackDao) – a zenetár gyorsítótára
│   ├── library/   MediaStoreScanner, LibraryRepository, Track, Id3TextReader
│   └── settings/  SettingsRepository (DataStore), SkipSettings
├── playback/
│   ├── PlaybackService   – MediaSessionService: az EGYETLEN ExoPlayer + MediaSession
│   ├── SkipController    – az EGYETLEN közös ugrási logika (tiszta Kotlin, unit-tesztelt)
│   ├── SkipAwarePlayer   – ForwardingPlayer: a rendszer/fejhallgató előre-vissza is ide fut
│   ├── PlayerConnection  – a felület MediaController-kapcsolata, PlaybackUiState-folyam
│   └── PlaybackCommands  – saját munkamenet-parancsok (ugrás) és események (hiba)
└── ui/
    ├── AppRoot           – navigáció (fő képernyő + teljes lejátszó), számmenü-gazda
    ├── components/       – TrackRow, TrackActionsSheet, SkipButton, Artwork, FileInfoDialog
    ├── library/ player/ settings/ theme/
```

- Az ugrás útja: gomb → `PlayerConnection.skip()` → saját munkamenet-parancs →
  `PlaybackService.performSkip()` → `SkipController.skip()` → ExoPlayer. Az értesítés gombjai és
  a fejhallgató előre/vissza gombja (`SkipAwarePlayer.seekBack/Forward`) ugyanide futnak.
- A vezérlő → munkamenet irányban a `MediaItem` URI-ja elveszhet; a szolgáltatás
  `onAddMediaItems`-ben a `requestMetadata.mediaUri`-ból vagy az azonosítóból állítja vissza.
- A MediaStore egyes ID3-címkék kódolását elrontja (magyar ékezeteknél kínai írásjelek);
  ilyenkor a `MediaStoreScanner` az `Id3TextReader`-rel a fájlból olvassa újra a szöveget.
- Kiadási aláírás még nincs. Ha kell: saját, új kulccsal (más app kulcsát ne használd).
