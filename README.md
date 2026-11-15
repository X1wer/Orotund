# SzabadZene

Offline, natív Android-zenelejátszó a telefonon tárolt zenékhez (Kotlin, Jetpack Compose, Material 3,
AndroidX Media3). Nincs benne reklám, előfizetés, bejelentkezés, analitika vagy nyomkövetés, és **nem kér
internet-engedélyt** (a végső, összefésült manifestben nincs `INTERNET`, sőt `ACCESS_NETWORK_STATE` sem).
Licenc: GPL-3.0.

Két dologra külön figyel:

- **Könnyen eltalálható számmenü:** minden számsor jobb szélén legalább 64 × 64 dp-s, a sor teljes
  magasságát kitöltő menüterület van. A teljes terület megnyitja a menüt, és soha nem indít lejátszást.
- **Állandóan látható ugrógombok:** „−10 mp” / „+10 mp” a teljes lejátszóban (a csúszka alatt) és az alsó
  minilejátszóban is, kis kijelzőn és fekvő tájolásban is. A lépés irányonként állítható (5, 10, 15, 30 vagy 60 mp).

## Funkciók

- Zenetár számok, albumok, előadók, műfajok és mappák szerint; ékezet- és kisbetű-független keresés,
  rendezés (magyar ábécé szerint is).
- Kedvencek és saját lejátszási listák (áthúzással átrendezhetők); szerkeszthető várólista
  („Lejátszás következőként”, hozzáfűzés, átrendezés, eltávolítás).
- Háttérlejátszás, értesítési és zárolási képernyős vezérlés, fejhallgató/Bluetooth gombok, hangfókusz-kezelés.
- A várólista, az aktuális szám, a pozíció, az ismétlés és a véletlen mód újraindítás után visszaáll
  (a zene magától nem indul el).
- Elalvásidőzítő (5–120 perc vagy „az aktuális szám végén”), hangszínszabályzó előbeállításokkal és egyéni
  sávokkal (csak a készüléken ténylegesen elérhető hatásokkal), kezdőképernyős widget.
- Címke- és borítószerkesztés (MP3, M4A, FLAC, OGG Vorbis) visszaolvasással ellenőrzött mentéssel;
  hangkivágás új fájlba (`Music/SzabadZene`, az eredeti érintetlen); csengőhangként beállítás;
  fájltörlés a telefonról külön megerősítéssel (a listából való eltávolítástól független művelet).
- Világos / sötét / rendszer szerinti téma, rendszerszínek (Android 12+) vagy választható kiemelőszín,
  magyar és angol felület (rendszernyelv szerint, vagy kézzel választva).

Részletek, ismert korlátok: [`docs/TESZTOSSZEFOGLALO.md`](docs/TESZTOSSZEFOGLALO.md).
Követelmények: [`docs/KOVETELMENYEK.md`](docs/KOVETELMENYEK.md). Fejlesztési napló: [`docs/ALLAPOT.md`](docs/ALLAPOT.md).

## Rendszerkövetelmény

Android 8.0 (API 26) vagy újabb. A fordításhoz: Android Studio (a projekt a mellékelt JBR-rel,
JDK 17+ fut), Android SDK 37 (compile/target), internetkapcsolat az első Gradle-szinkronhoz.

## Telepítés (kész teszt-APK)

A kész, debug kulccsal aláírt teszt-APK a `dist/` mappában van (verziószámmal, pl.
`dist/SzabadZene-1.0.0-debug.apk`; az ellenőrző összeg a `dist/SHA256SUMS.txt` fájlban). A `dist/` mappa
nincs a git-tárolóban, a `./gradlew assembleDebug` után a fenti módon újra előállítható.

Telepítés számítógépről (USB-hibakeresés bekapcsolva vagy futó emulátor):

```bash
adb install -r dist/SzabadZene-1.0.0-debug.apk
```

Telefonon: másold át az APK-t, nyisd meg, és engedélyezd az ismeretlen forrásból való telepítést.
A debug APK csomagnév-utótagja `.debug` (`io.github.x1wer.szabadzene.debug`), így egy későbbi
kiadási változat mellett is feltelepíthető.

## Megnyitás és fordítás Android Studióban

1. **File → Open**, majd válaszd ki ezt a mappát (`SzabadZene`). Az útvonal lehetőleg csak ASCII
   karaktereket tartalmazzon.
2. Az első Gradle-szinkron letölti a függőségeket. Az SDK helyét a `local.properties` adja meg
   (`sdk.dir=...`); az Android Studio létrehozza, ha hiányzik (a fájl gépfüggő, nincs a tárolóban).
3. Futtatás: válaszd az `app` konfigurációt és egy eszközt (**Run ▸**), vagy a Build menüből
   **Build App Bundle(s) / APK(s) → Build APK(s)**.

Parancssorból (Windows, Git Bash):

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew assembleDebug            # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest        # JVM-egységtesztek
./gradlew lintDebug                # lint
./gradlew connectedDebugAndroidTest   # Compose UI- és fájlműveleti tesztek futó emulátoron/eszközön
```

Műszeres tesztek előtt javasolt visszaállítani a kijelzőt (`adb shell wm size reset`,
`wm density reset`, `settings put system font_scale 1.0`); a futás végén az app eltávolítódik az eszközről.

## Tesztadatok

Saját, generált (jogdíjmentes) hangfájlok készíthetők: `python -I tools/generate_test_audio.py`
(ffmpeg kell; kimenet: `testdata/generated/`, nincs gitben). Feltöltés:

```bash
export MSYS_NO_PATHCONV=1
adb push testdata/generated/. /sdcard/Music/SzabadZeneTeszt/
```

Android 10-től `adb shell content call --uri content://media --method scan_volume --arg external_primary`
indítja a beolvasást; Android 9-en `adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Music/SzabadZeneTeszt/<fájl>`.

## Használat röviden

- **Első indítás:** az app engedélyt kér a hangfájlok olvasásához (Android 13-tól „zene és hang”).
  A zenéket a MediaStore alapján, a háttérben olvassa be; a **Mappák** fülön mappák rejthetők el.
- **Lejátszás:** koppints egy számra. Az alsó minilejátszó törzsére koppintva a teljes lejátszó nyílik.
  A csúszka alatti „−10 mp” / „+10 mp” gombok az aktuális számon belül ugranak; az értéket a
  **Beállítások → Lejátszás** alatt állíthatod irányonként.
- **Számmenü:** a sor jobb szélén lévő három pont (a teljes jobb oldali sáv) nyitja: következőként,
  várólistához, listához adás, kedvenc, megosztás, fájladatok, címkeszerkesztés, kivágás, csengőhang, törlés.
- **Listák:** Kedvencek felül, alatta a saját listák; a sorok a fogantyúval húzhatók.
- **Hangszínszabályzó és időzítő:** a teljes lejátszó módsorából és a fejlécéből érhető el (a Beállításokban is).
- **Widget:** a kezdőképernyő widgetlistájából a „SzabadZene” (4×1, átméretezhető).
- **Fájlműveletek:** Android 11-től a rendszer saját engedélykérő ablaka jelenik meg minden szerkesztésnél
  és törlésnél; Android 10-ig a közös tárhely írási engedélye szükséges. Teljes fájlrendszer-hozzáférést
  az app nem kér.

## Felépítés

A kódszerkezet és a fejlesztői megjegyzések a [`CLAUDE.md`](CLAUDE.md) fájlban vannak
(`data/` – zenetár, adatbázis, fájlszerkesztés; `playback/` – egyetlen ExoPlayer + MediaSession a
`PlaybackService`-ben, közös ugrási logika; `ui/` – Compose-felület).

## Licenc

GPL-3.0 – lásd a [`LICENSE`](LICENSE) fájlt. A külső összetevők licenceit a
[`docs/LICENCEK.md`](docs/LICENCEK.md) sorolja fel (és az app **Beállítások → Licencek** képernyője).
