# SzabadZene – külső összetevők és licencek

A SzabadZene saját kódja **GPL-3.0** alatt áll (`LICENSE`). Az alábbi összetevők kerülnek a telepíthető
APK-ba (a `releaseRuntimeClasspath` függőségfájából, 2026-11-15); mindegyik GPL-3.0-val kompatibilis.
Ugyanez a lista az appban a **Beállítások → Licencek** képernyőn is látható (magyarul és angolul).

| Összetevő | Verzió | Licenc | Megjegyzés |
|---|---|---|---|
| Kotlin standard könyvtár, kotlinx.coroutines | 2.4.21 / 1.11.0 | Apache-2.0 | JetBrains |
| kotlinx.serialization (core, json) | 1.8.1 | Apache-2.0 | a DataStore/Room tranzitív függősége |
| JetBrains Annotations | 23.0.0 | Apache-2.0 | |
| AndroidX (Core, AppCompat, Activity, Lifecycle, Navigation, Collection, Annotation, …) | lásd `gradle/libs.versions.toml` | Apache-2.0 | Google |
| Jetpack Compose (UI, Foundation, Material 3, Material Icons Extended) | BOM 2026.09.00 | Apache-2.0 | Google |
| AndroidX Media3 (ExoPlayer, Session) | 1.11.1 | Apache-2.0 | Google |
| AndroidX Room | 2.8.5 | Apache-2.0 | Google |
| AndroidX DataStore | 1.2.1 | Apache-2.0 | Google; tranzitív: Okio 3.9.1 (Square, Apache-2.0) |
| Guava (android változat), failureaccess | 33.3.1-android / 1.0.2 | Apache-2.0 | Google |
| JSpecify | 1.0.0 | Apache-2.0 | |
| **JAudioTagger** (`net.jthink:jaudiotagger`) | 3.0.1 | **LGPL** (a projekt LGPL-2.1-ként hivatkozik) | MP3/M4A/FLAC/OGG címkeírás |

Csak fordítási / teszt időben használt (nem kerül az APK-ba): Android Gradle Plugin, KSP, Room-fordító,
JUnit 4 (EPL-1.0), AndroidX Test, Espresso, Compose UI-teszt (Apache-2.0).

## JAudioTagger (LGPL) – feltételek és teendők

- Az LGPL-2.1 a könyvtárat GPL-3.0 licencű programba beépíteni engedi (az LGPL 3. szakasza szerint a
  könyvtár a GPL alá is helyezhető), így az alkalmazás GPL-3.0 alatt terjeszthető.
- Dinamikusan, módosítás nélkül, a Maven Central kiadásból (`net.jthink:jaudiotagger:3.0.1`) használjuk;
  a könyvtár forrásán nem változtattunk. A teljes forráskódú SzabadZene-projekt a cserét (újrafordítás másik
  verzióval) lehetővé teszi – ez az LGPL „újralinkelhetőségi” feltételének is megfelel.
- A Maven-POM csak „LGPL” megjelölést tartalmaz; a pontos verziószámot (2.1 vagy később) a könyvtár
  forrásfejlécei adják. **Nyilvános közzététel előtt** érdemes a kiadáshoz a JAudioTagger licencszövegét
  (https://www.gnu.org/licenses/old-licenses/lgpl-2.1.txt) és a GPL-3.0 szöveget (`LICENSE`) is mellékelni,
  és a közzétett forráskód mellé a „third-party” megjegyzést kitenni. Ezt a szöveget a gép nem töltötte le
  (nincs letöltési engedély), ezért nincs a tárolóban.

## Nem használt dolgok

Nincs reklám-, analitika-, hibajelentő vagy hálózati könyvtár; az `INTERNET` és az `ACCESS_NETWORK_STATE`
engedély sincs az összefésült manifestben (az APK-n `apkanalyzer manifest permissions` ellenőrizte).

## Tesztadatok

A tesztfájlok (`tools/generate_test_audio.py`) saját, szinuszhangból generált, jogdíjmentes anyagok; az
alkalmazás ikonja és minden grafikája saját rajz (nincs harmadik féltől származó kép vagy betűtípus).
