# SzabadZene

Offline zenelejátszó Androidra, a telefonon tárolt zenékhez. Nincs benne reklám, előfizetés,
bejelentkezés vagy nyomkövetés, és nem kér internet-engedélyt.

Két dologra külön figyel:

- **Könnyen eltalálható számmenü:** minden szám mellett legalább 64 × 64 dp-s menüterület, amely
  nem indítja el a zenét.
- **Állandóan látható ugrógombok:** „−10 mp” / „+10 mp” a teljes és az alsó minilejátszóban is.
  A lépés irányonként állítható (5, 10, 15, 30 vagy 60 mp).

További funkciók:

- zenetár számok, albumok, előadók, műfajok és mappák szerint; ékezetfüggetlen keresés, rendezés;
- kedvencek és saját lejátszási listák (áthúzással átrendezhetők);
- szerkeszthető várólista; a „Lejátszás következőként” keverés közben is a soron következő helyre kerül;
- a várólista, a pozíció és a módok újraindítás után is megmaradnak (a zene magától nem indul el);
- világos/sötét téma, rendszerszínek vagy választható kiemelőszín, magyar és angol felület;
- mappák elrejtése, rövid hangfájlok szűrése, megosztás, fájladatok.

> Állapot: fejlesztés alatt (0.2.0). A részletek a `docs/ALLAPOT.md` fájlban.

## Telepítés

A debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Telepítés számítógépről:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Első indításkor az app engedélyt kér a hangfájlok olvasásához. A zenéket a telefon
`Music` (Zene) mappájából olvassa be automatikusan.

## Fordítás

Android Studio-ban: **File → Open**, majd a projekt mappája. Parancssorból (Windows, Git Bash):

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew assembleDebug
```

Tesztek: `./gradlew testDebugUnitTest` (JVM) és `./gradlew connectedDebugAndroidTest` (futó emulátoron).

## Licenc

GPL-3.0 – lásd a `LICENSE` fájlt. A felhasznált külső összetevők (AndroidX, Jetpack Compose,
Media3, Room, DataStore, Kotlin) Apache-2.0 licencűek.
