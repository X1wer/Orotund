# SzabadZene 1.0.0 – tesztösszefoglaló

Dátum: 2026-11-15 · Verzió: 1.0.0 (versionCode 4), debug build, debug kulccsal aláírva ·
APK: `dist/SzabadZene-1.0.0-debug.apk` (SHA-256: `dist/SHA256SUMS.txt`).

Ami itt „megfelelt”, azt tényleg lefuttattam; ami nem futott le, azt külön, indoklással sorolom fel.
Tesztkörnyezet: két Android-emulátor – `Clean_API_37` (API 37, fő teszteszköz) és
`Medium_Phone_API_28` (Android 9, régi tárhelymód). Valódi telefonon **nem** futott semmi.
A tesztfájlok mind saját, generált hangfájlok (`tools/generate_test_audio.py`): ékezetes és nagyon hosszú
cím, címke/borító nélküli fájl, sérült fájl, a minimumszűrő alatti rövid fájl; MP3, M4A, FLAC, OGG, WAV.

## 1. Automatizált tesztek (végleges kódon, `clean` után)

| Készlet | Eredmény |
|---|---|
| JVM-egységtesztek (`testDebugUnitTest`) | **95/95** sikeres |
| Műszeres tesztek, `Clean_API_37` (`connectedDebugAndroidTest`) | **28/28** sikeres, 0 kihagyott |
| Műszeres tesztek, `Medium_Phone_API_28` | **28/28** sikeres, 0 kihagyott |
| `lintDebug` | 0 hiba, 14 figyelmeztetés (újabb függőségverzió, többes szám-javaslat, API 31+ widget-attribútum) |
| `assembleDebug` | sikeres |

A 28 műszeres teszt: számsor-menü és kattintás-szétválasztás (`TrackRowTest`), ugrógombok és elrendezések
kis/fekvő kijelzőn 2,0× betűvel (`PlayerLayoutTest`), adatbázis és migráció (`DatabaseTest`, `MigrationTest`),
átrendezés (`ReorderableTest`), fájlműveletek (`FileOperationsTest`: vágás 5 formátumból, címkeírás az Android
saját olvasójával visszaellenőrizve, teljes MediaStore-folyamat, törlés). Az egységtesztek az ugrási
határhelyzeteket, a gyors egymás utáni ugrásokat, a várólista/kevert sorrend megőrzését, az időzítőt,
a hangszínszabályzó-logikát, a zenetár-logikát és a címke/vágás-logikát fedik.

**Az Android 9-es futás újdonsága (4. rész):** a `FileOperationsTest` mostantól API 28-on is fut (korábban
a teljes folyamat „Android 10+” feltétel miatt kimaradt). Itt találtunk egy hibát (törlés után a fájl a
tárhelyen maradt) – lásd lent; hozzá regressziós teszt készült (`deleteRemovesThePhysicalFileAndTheMediaStoreRow`),
amely a javítás nélküli kódon valóban elbukik (ellenőrizve), a javítottal sikeres mindkét emulátoron.
Az API 28-as emulátor AAC-dekódere az 1 csatornás M4A-mintafájlból az írástól függetlenül csak ~3,0 mp-et ad
(írás előtt is ugyanennyit), ezért ott a „címkeírás után is lejátszható” teszt az írás előtti/utáni dekódolt
hosszt hasonlítja össze (a fájl hangja nem változott), az abszolút 4,0 mp-es ellenőrzés csak API 29-től fut.

## 2. Kézi ellenőrzés emulátoron – a specifikáció 7. fejezete

`API 37` = `Clean_API_37`, `API 28` = `Medium_Phone_API_28`. Eszköz: adb + képernyőkép + UI-hierarchia; képernyőképek:
`reports/screens/p4_*.png` (nincs gitben).

| # | Ellenőrzés | Eredmény |
|---|---|---|
| 1 | Menüterület közepe, két sarka, az ikon melletti rész: 5 koppintás | API 37: mind az 5 a helyes szám menüjét nyitotta; a lejátszási állapot (állapot, pozíció, frissítési időbélyeg) változatlan, zene nem indult |
| 2 | Menüterület ≥ 64 × 64 dp hosszú címnél és 2,0× betűnél | API 37: a mért terület 1080×2400 / 420 dpi-n 168 × 168 px = 64 × 64 dp; 320 × 568 dp-s kijelzőn 2,0× betűvel 144 × 145 px (360 dpi) = 64 × 64,4 dp. A hosszú cím 2 sorban, „…”-tel rövidül. (Automatizáltan: `TrackRowTest`, `PlayerLayoutTest`.) |
| 3 | Ugrógombok azonnal látszanak, szünetben és tekerés után is; minilejátszóban is | API 37: látszanak a minilejátszóban és a teljes lejátszóban lejátszás/szünet közben, és az app újraindítása után is; angol felületen „−10 s / +10 s” (API 28) |
| 4 | Kis kijelző (`wm size 720x1136`, `wm density 360` = 320 × 568 dp), 2,0× betű, fekvő tájolás | API 37: álló: kétsoros minilejátszó, a teljes lejátszóban az ugrógombok a csúszka alatt, a vezérlősor külön; **fekvő, 568 × 320 dp, 2,0×: az ugrógombok és a vezérlők látszanak, a módsor alja (hangszínszabályzó, ismétlés) görgetéssel érhető el** (ismert szélsőséges eset); 1,3× betű normál kijelzőn rendben |
| 5 | Alapérték 10 mp; irányonként eltérő érték; újraindítás után | API 37: visszaugrás 5 mp + előreugrás 30 mp beállítva → feliratok és akadálymentes címkék azonnal frissültek; az app teljes leállítása és újraindítása után is megmaradt |
| 6 | Gyors ismételt ugrás, a szám elején és végén | API 37 (5/30 mp-es beállítással): 3 gyors „+30” szünetben = +90 mp (2669 → 92854 ms, az eltérés az MP3-keretillesztés); 2 × −5 = −10 mp; 30 gyors −5 → pontosan 0, a szám nem váltott; az előreugrás a szám végén megállt (11799/12000 ms), a szám nem váltott |
| 7 | Szünetben az ugrás nem indít lejátszást; végigjátszás a várólista szerint | API 37: szünetben maradt minden ugrás után; a szám végéről a lejátszás gomb a következő számot indította. Ismétlés/kevert mód: lásd 9. pont; a normál végigjátszás/ismétlés logikát az egységtesztek is fedik |
| 8 | Háttér, képernyőzár, forgatás | API 37: HOME, `KEYCODE_SLEEP`, felébresztés, visszatérés, két forgatás: a lejátszás végig szólt, **1 aktív hangsáv** (nincs dupla hang), 1 médiamunkamenet |
| 9 | Újraindítás után visszaállás; engedélymegtagadás; hiányzó és sérült fájl | API 37: kevert + összes ismétlése mód bekapcsolása, szünetben 63,7 mp-nél `force-stop` + újraindítás → ugyanaz a szám, ugyanott (63,9 mp), szünetben, a módok megmaradtak. Engedély visszavonása → magyarázó képernyő; „Engedély megadása” → rendszerablak; kétszeri „Don’t allow” után „Rendszerbeállítások megnyitása”, **nincs összeomlás**. Sérült fájl: átlépés érthető üzenettel. Törölt fájl a várólistában (`FileNotFoundException`): átlépés, nincs összeomlás |
| 10 | Repülőgép-üzemmód; manifest; kimenetek lejátszhatósága | API 37: repülőgép-üzemmód + Wi-Fi/adat ki: lejátszás, fülváltás, következő szám működött. **Összefésült manifest (az APK-n `apkanalyzer`): nincs `INTERNET`, nincs `ACCESS_NETWORK_STATE`.** Címkeírás és vágás kimenete visszaolvasva és dekódolva: lásd 1. és 3. rész |
| 11 | Értesítés, elalvásidőzítő, widget | Értesítés (API 37): `category=transport`, nyilvános (zárolási képernyőn látszik), 5 művelet. Időzítő (API 37): 5 perc → visszaszámlálás a fejlécben és az időzítő-ablakban, kikapcsolható. **Widget: ebben a körben nem ellenőriztem újra** (az emulátor kezdőképernyőjére nem tettem fel); a 3. részben ellenőrizve volt (lásd `ALLAPOT.md`) |
| 12 | Fejhallgató / Bluetooth / hangfókusz | A környezet lehetőségei szerint: médiabillentyűk (play/pause/next) emulátoron működtek; a többi csak valódi készüléken – lásd lent |

### Android 9 (`Medium_Phone_API_28`) – a 3. rész fájlműveletei, régi tárhelymód

| Művelet | Eredmény |
|---|---|
| Címke + borító mentése (MP3, `WRITE_EXTERNAL_STORAGE`) | Sikeres: „A címkék elmentve és ellenőrizve”; a lehúzott fájlban az új cím/előadó/album, a hang érintetlen; a zenetár és a lista az új címet mutatta. Borító eltávolítása is sikeres (a fájlból valóban eltűnt a kép). Az API 28-on az olvasási engedély megadása a tárhely-csoportot, így az írást is megadja – külön írási kérés nem jelent meg |
| Kivágás FLAC-ból | Sikeres: `Music/SzabadZene/04 Csendes ej (vágott).m4a`, 14,2 mp, hibátlanul dekódolható (AAC), az eredeti md5-je változatlan |
| Csengőhang | Engedély nélkül: „A csengőhang nem változott…” üzenet; az engedély megadása után („Rendszerbeállítások módosítása”) beállítva, a rendszer visszaolvasott értéke a fájlra mutatott |
| Törlés a telefonról | **Hibát találtunk és javítottunk** (lásd lent): a MediaStore-sor törlődött, a fájl a tárhelyen maradt |
| Teljes automatizált folyamat (`FileOperationsTest`, mentés → vágás → törlés) | Sikeres |

## 3. A 4. részben talált és javított hibák

1. **Android 9: a „Törlés a telefonról” nem törölte a fájlt** – csak a MediaStore-sort; a fájl a tárhelyen maradt, és a következő beolvasáskor visszatért volna. Javítás: a fájlt az app is törli, és csak a fájl **és** a sor eltűnése után jelez sikert (`TrackFileEditor.deleteLegacy`). Regressziós teszt: `FileOperationsTest.deleteRemovesThePhysicalFileAndTheMediaStoreRow` (a javítás nélkül elbukik, API 28-on és 37-en is sikeres).
2. **Címkeszerkesztő ablak (Android 9):** a rendszersáv ikonjai világos háttéren is világosak (alig látszottak), és a „Mentés” gomb a navigációs sáv alá lógott. Javítás: az ablak saját rendszersáv-színezése és navigációs sáv-térköz (`TagEditorDialog`). Képernyőképpel ellenőrizve API 28-on; automatizált teszt nincs hozzá (rendszerablak-beállítás).
3. **Alsó navigáció 2,0× betűnél, 320 dp-n:** a címkék szó közepén levágódtak („Zenet”, „Mapp”). Javítás: a címke betűméretének felső korlátja 1,3×, a végén „…”. Képernyőképpel ellenőrizve; automatizált teszt nincs.
4. **Licencek képernyő:** hiányzott az Okio, a kotlinx.serialization, a JSpecify és a JetBrains Annotations; pótolva (`docs/LICENCEK.md` is).
5. **Tesztek:** a `FileOperationsTest` teljes folyamata és a címkeolvasás-teszt API 28-on is fut (korábban kimaradt vagy az emulátor AAC-dekódere miatt hibás lett volna).

## 4. Csak valódi készüléken ellenőrizhető esetek (nem teszteltem)

- **Vezetékes fejhallgató kihúzása** és **Bluetooth-kimenet megszakadása** → szünet (`handleAudioBecomingNoisy`); a „folytatás visszacsatlakozáskor” beállítás (`PlaybackService.deviceCallback`). Az emulátor nem tud valódi kihúzást előidézni.
- **Valódi BT/fejhallgató gombok** (előre/vissza → ugrás a beállított másodperccel); az emulátoron csak médiabillentyű-szimuláció futott.
- **Hívás közbeni hangfókusz** valódi hívásnál (az emulátoros `gsm call` szimuláció a 1. részben működött: csörgéskor szünet, a hívás után folytatás).
- **Hangszínszabályzó nélküli készülék:** mindkét emulátoron van `Equalizer`/`BassBoost`, így a „nem támogatott” ág csak kódból ellenőrzött.
- **Gyártói hanghatások** (Dolby, Samsung SoundAlive, stb.) együttélése a saját hangszínszabályzóval.
- **Valódi csengés** csengőhangként (emulátoron a rendszer visszaolvasása és szimulált hívás igazolta).
- **Teljesítmény:** több ezer számos zenetár beolvasási ideje és görgetése; több száz MB-os fájl címkézése/vágása (a művelet a teljes fájl másolatát a gyorsítótárba teszi, kevés szabad helynél adatvesztés nélkül hibával leáll).
- **Gyártói rendszerfelületek:** az értesítési/zárolási médiavezérlő kinézete és a rendszer által megjelenített egyéni ugrógombok eszközönként eltérhetnek.
- **Android 11+ fájlonkénti engedélyablakok** valódi gyártói rendszereken (API 37-en működtek).
- Android 8 (API 26) és 10–12 rendszerek: nem futottak (csak API 28 és 37).

## 5. Ismert korlátok

- **MP3-ugrás** ~0,1 mp-et eltérhet (keretillesztés); M4A/FLAC-nál ezredmásodpercre pontos.
- **Legkisebb kijelző + 2,0× betű:** a kétsoros minilejátszó a képernyő nagy részét elfoglalja (a lista görgethető, az utolsó elem elérhető); fekvő, 568 × 320 dp-n a teljes lejátszó módsorának alja görgetéssel érhető el; az alsó navigáció „Beállítások” címkéje „…”-tal rövidül.
- **Elvett engedély + mentett várólista:** ha a hangfájl-olvasási engedélyt az app indulása előtt visszavonják, a mentett várólista számai „nem sikerült lejátszani” üzenettel egymás után kihagyódnak (összeomlás nélkül, a lista hosszáig); az engedély megadása után az app rendben működik.
- **Keverés közben** a várólista nem rendezhető át (a képernyő ezt kiírja).
- **MediaStore újraolvasás:** ha egy fájl ugyanabban a másodpercben, ugyanakkora méretre íródik át, a rendszer nem olvassa újra (valós használatban ritka).
- **Widget:** az előző/következő gomb csak futó lejátszónál működik (leállított appnál a lejátszógomb a mentett várólistával folytat); a widget neve a rendszer nyelvén jelenik meg.
- **Elalvásidőzítő** nem éli túl a folyamat leállítását (szándékosan: újraindításkor nem indul magától zene).
- **Debug build**: debug kulccsal aláírt, nem optimalizált APK; release-aláírás nincs beállítva (ha kell, saját, új kulccsal készüljön).

## 6. Formátumtámogatás

| Formátum | Lejátszás | Címke + borító írása | Vágás |
|---|---|---|---|
| MP3 | igen (generált fájlokkal ellenőrizve) | igen – ID3v2.4, UTF-8 (a v2.2/v2.3 címke v2.4-re alakul) | veszteségmentes keretmásolás (~26 ms); a Xing/Info fejléc kimarad |
| M4A (AAC) | igen | igen | veszteségmentes átcsomagolás (~23 ms) |
| FLAC | igen | igen (PICTURE blokk) | újrakódolt M4A/AAC 192 kbit/s (az Android-muxer FLAC-ot nem ír) |
| OGG Vorbis | igen | igen (METADATA_BLOCK_PICTURE) | újrakódolt M4A/AAC 192 kbit/s |
| WAV (PCM) | igen | nem kínáljuk fel (a médiatár nem olvassa vissza a WAV-címkéket) | veszteségmentes mintamásolás |
| Opus, ALAC, egyéb | az eszköz/ExoPlayer képességei szerint, **nem tesztelt** | nem kínáljuk fel | AAC-újrakódolás, **nem tesztelt** |

A lejátszható formátumokat az ExoPlayer és az eszköz kodekjei döntik el; csak a fenti, generált fájlokkal
ellenőrzött formátumokat állítjuk támogatottnak.

## 7. Külső összetevők licencei

Lásd [`LICENCEK.md`](LICENCEK.md) (és az app **Beállítások → Licencek** képernyőjét). Az app GPL-3.0; a
JAudioTagger LGPL, GPL-3.0-val kompatibilis; a többi Apache-2.0.
