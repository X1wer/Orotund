# SzabadZene – követelményspecifikáció

Verzió: 2 (2026-11-15) – a `Zenelejatszo_Claude_prompt.txt` pontosított változata.

Ez a dokumentum az egyetlen hiteles követelménylista. A munka négy részben készül (lásd `SzabadZene_promptok.md`), és minden rész ezt követi. Az 1. rész bemásolja a projektbe `docs/KOVETELMENYEK.md` néven; onnantól a projektbeli másolat az irányadó.

---

## 0. Cél

Teljes, működőképes, natív Android-zenelejátszó készül helyi (offline) zenefájlokhoz. Nem terv és nem felületi prototípus: meg kell valósítani, le kell fordítani és tesztelni kell.

Referencia: https://play.google.com/store/apps/details?id=com.media.music.mp3.musicplayer

- A referencia csak a funkciókör és a használati minta megértésére szolgál (Play Áruház-adatlap). A megjelenés szabadon újratervezhető.
- A referenciaappot ne töltsd le és ne telepítsd. Ne másold a kódját, nevét, ikonját vagy grafikáját: saját kód, név és grafika készüljön.
- Ne állítsd, hogy kipróbáltad a referenciát, ha csak az adatlapját láttad.
- Ahol ez a dokumentum és a referencia eltér, ez a dokumentum az irányadó.

---

## 1. A két kiemelt elvárás

Ez a két pont a felhasználó legfontosabb kérése. Esztétikai, elrendezési vagy kényelmi okból nem gyengíthető.

### A) Könnyen eltalálható hárompontos menü minden számsorban

Az eredeti alkalmazásban a sor jobb oldali menügombjának túl keskeny az érintési területe. A javítás valódi, nagyobb kattintható terület – nem csak nagyobb ikon.

**Közös számsor-komponens**
- Egyetlen újrahasználható számsor-komponens készüljön, és ezt használja minden számlista: Számok, keresési találatok, album-, előadó-, műfaj- és mappaszámlisták, lejátszási listák, Kedvencek, várólista.
- Felépítés: balra borítókép (hiányzó borítónál egységes helyettesítő), középen cím és előadó, jobbra rögzített méretű menüterület.
- A szöveges rész alkalmazkodik: a hosszú cím legfeljebb két sorban jelenik meg, utána „…”-tel rövidül, és soha nem nyomja össze a menüterületet.
- Az éppen szóló szám sora visszafogott, jól felismerhető jelölést kap (pl. kiemelőszínű cím vagy kis lejátszásjelző).

**Menüterület**
- Legalább 64 dp széles és legalább 64 dp magas, és kitölti a sor teljes magasságát. A sor minimális magassága ezért 64 dp; nagyított betűméretnél a sor nőhet, a menüterület soha nem lehet kisebb 64 × 64 dp-nél.
- A teljes menüterület megnyitja a menüt – az ikon körüli üres rész is.
- A három pont jól látható, középre igazított; az ikon 24–28 dp.
- A menüterület és a sor lejátszást indító része nem fedi egymást. A menü megnyitása nem indít lejátszást, nem vált számot, nem módosítja a várólistát, és nem jelöli ki a sort.
- Lenyomáskor a teljes menüterületen látható visszajelzés jelenik meg (pl. ripple).
- Akadálymentes címke: „Szám műveletei: [számcím]”, angolul „Track actions: [title]”.

**Menüpanel**
- A menü alsó panelként (bottom sheet) nyílik; a tetején a szám címe és előadója látszik.
- A menüpontok legalább 56 dp magasak és jól olvashatók.
- Csak működő menüpont jelenhet meg. Egy később elkészülő funkció menüpontja addig rejtve marad.

### B) Állandóan látható másodperces vissza- és előreugrás

Az ugrógombok megjelenéséhez ne kelljen a csúszkát megérinteni, tekerést kezdeni, menüt nyitni vagy hosszan nyomni semmit.

**Teljes lejátszó**
- Közvetlenül az időcsúszka alatt, külön sorban két állandó gomb: „−10 mp” és „+10 mp” (angolul „−10 s” és „+10 s”). A felirat mindig az aktuális beállítást mutatja.
- Ezek az aktuális számon belüli ugrást jelentik. Az előző/következő szám gombjai külön sorban, eltérő ikonnal, jól megkülönböztethetően szerepelnek.
- Érintési terület gombonként legalább 56 × 56 dp, jól olvasható másodpercértékkel.
- Akadálymentes címke: „Visszaugrás 10 másodperccel” / „Előreugrás 10 másodperccel” (angolul „Rewind 10 seconds” / „Forward 10 seconds”), mindig az aktuális értékkel.

**Minilejátszó**
- Az appon belüli alsó minilejátszóban is közvetlenül elérhető a két ugrógomb. Minimális tartalom: borító, cím és előadó, lejátszás/szünet, vissza- és előreugrás. A következő szám gombja opcionális, ha elfér.
- Ha egy sorban nem fér el minden gomb legalább 56 dp-s érintési területtel, kétsoros elrendezés kell (felül a szám adatai, alul a gombok).
- A minilejátszó törzsére koppintva a teljes lejátszó nyílik meg; a gombokra koppintás csak a gomb műveletét végzi.

**Láthatóság és elrendezés**
- Az ugrógombok lejátszás és szünet közben, tekerés előtt, alatt és után is láthatók; időzítő nem rejti el őket.
- Sem a teljes lejátszóban, sem a minilejátszóban nem kerülhetnek túlcsorduló menübe, és nem kerülhetnek egymásra – kis kijelzőn és fekvő tájolásban sem. Helyhiánynál először a borítókép mérete és a díszítő térközök csökkennek.
- Ha nincs betöltött szám, a tartalom nem tekerhető, vagy a hossza ismeretlen, a gombok láthatók, de érthetően inaktívak (vizuálisan és az akadálymentes leírásban is).

**Ugrásértékek**
- A beállításokban a visszaugrás és az előreugrás mértéke külön választható: 5, 10, 15, 30 vagy 60 másodperc. Alapérték mindkét irányban 10 másodperc.
- A beállítás azonnal frissíti a gombfeliratokat, és újraindítás után is megmarad.

**Ugrási szabályok**
- Egyetlen közös ugrási művelet létezik; ezt használja a teljes lejátszó, a minilejátszó és minden más appon belüli ugrógomb.
- A gyors, ismételt koppintások összeadódnak: az új célpozíció a még függő (a lejátszó által még vissza nem igazolt) célpozícióból számolódik, ha van ilyen, különben az aktuális pozícióból. Három gyors „+10 mp” összesen 30 másodperc ugrás. A lejátszó késleltetett állapotfrissítése miatt egyetlen koppintás sem veszhet el.
- Visszaugrásnál a célpozíció legalább 0. A szám elején a visszaugrás nem vált az előző számra.
- Ha az előreugrás célpozíciója eléri vagy meghaladja a szám hosszát: a pozíció a szám végére kerül, a lejátszás szünetel, és nem vált a következő számra. Ezután a lejátszás gomb a normál szabályok szerint folytatja (a várólista és az ismétlési mód szerint). Ez az egyetlen eset, amikor az ugrás megváltoztatja a lejátszás/szünet állapotot.
- Minden más esetben a szüneteltetett szám szüneteltetve marad, a lejátszott szám pedig tovább szól.
- A normál végigjátszás továbbra is a várólista és az ismétlési mód szerint működik.

**Rendszerfelületek**
- Az állandó láthatóság az alkalmazás saját lejátszófelületeire vonatkozik. Az értesítésben és a zárolási képernyőn a Media3 által támogatott médiavezérlőket kell használni (az ugrógombokat egyéni parancsként, ahol a rendszer megjeleníti őket); ott nem kell garantált gombelrendezést ígérni.

---

## 2. Alapbeállítások és keretek

Választott alapértékek (nem az eredeti alkalmazásról szóló állítások):

- **Név:** munkanév SzabadZene. A megjelenített név egyetlen helyen (szövegerőforrásban) legyen átírható. Az alkalmazásazonosító (applicationId) legyen egyedi, és ne ütközzön a gépen vagy az emulátoron lévő más alkalmazásokkal (javaslat: `io.github.x1wer.szabadzene`).
- **Nyelv:** magyar felület teljes angol fordítással. Alapból a rendszernyelvet követi; kézzel is választható: Rendszer / Magyar / English.
- **Offline:** nincs reklám, előfizetés, bejelentkezés, analitika, nyomkövetés vagy hibajelentő szolgáltatás. A végső (összefésült) manifestben ne legyen `INTERNET` engedély.
- **Téma:** világos, sötét és rendszerhez igazodó. Kiemelőszín: Android 12-től dinamikus (rendszerszín), emellett néhány előre megadott szín.
- **Natív** Android-alkalmazás, nem WebView.
- **Nem kell:** streaming, online zenekereső, online borítókereső, letöltő.
- A hétköznapi tervezési és technikai döntéseket önállóan kell meghozni; a két kiemelt elvárást esztétikai okból nem lehet gyengíteni.
- **Licenc:** csak nyílt forráskódú, GPL-3.0-val kompatibilis külső összetevők használhatók, hogy az app később nyilvánosan közzétehető legyen.

---

## 3. Zenetár és lejátszási funkciók

**Zenetár**
- Helyi hangfájlok felismerése MediaStore-ból (szükség esetén rendszerfájlválasztóval / Storage Access Frameworkkel); böngészés számok, albumok, előadók, műfajok és mappák szerint.
- Formátumok: MP3, AAC/M4A, FLAC, OGG és WAV – a lejátszómotor és az eszköz tényleges képességei szerint. A korlátokat dokumentálni kell.
- A beolvasás a háttérben történik, az eredmény tartós helyi adatbázisban gyorsítótárazva; képernyőváltáskor nem olvasódik újra a teljes könyvtár.
- Megjelenítés: borító, cím, előadó, időtartam; hiányzó címnél a fájlnév.
- Mappák kizárása, kézi újraolvasás, opcionális minimum időtartam-szűrő a rövid hangfájlok elrejtésére (ki / 10 / 30 / 60 mp).

**Keresés és rendezés**
- Keresés címre, előadóra, albumra és fájlnévre. A keresés kis- és nagybetűtől, valamint ékezettől független: pl. „arvizturo” megtalálja az „Árvíztűrő” című számot, és fordítva.
- Rendezés cím, előadó, időtartam és hozzáadás ideje szerint, növekvő és csökkenő sorrendben. A szöveges rendezés a magyar ábécét követi.

**Listák és várólista**
- Kedvencek; saját lejátszási listák létrehozása, átnevezése, törlése, a számok sorrendjének módosítása.
- Szám, album vagy mappa tartalmának hozzáadása lejátszási listához és a várólistához.
- Szerkeszthető várólista: „Lejátszás következőként”, hozzáfűzés, átrendezés, eltávolítás.
- A véletlen (kevert) sorrend nem változik meg attól, hogy a felhasználó képernyőt vált vagy elforgatja a telefont.

**Lejátszás**
- Lejátszás/szünet, előző/következő szám, csúszkás tekerés, ismétlés (ki / egy szám / összes), véletlen sorrend.
- Háttérlejátszás, vezérlés az értesítésből és a zárolási képernyőről, fejhallgató- és Bluetooth-gombok támogatása.
- Elalvásidőzítő: a beállított idő után vagy az aktuális szám végén leállítja a zenét; a hátralévő idő látható, az időzítő megszakítható.

**További funkciók**
- Megosztás az Android megosztási felületével; fájladatok megtekintése (útvonal, méret, formátum, időtartam, bitráta és mintavételi frekvencia, ha elérhető).
- Hangszínszabályzó előbeállításokkal és egyéni értékekkel, a készülék által ténylegesen támogatott hanghatásokkal. Nem támogatott hatás nem jelenhet meg működőként.
- Címke- és borítószerkesztés a ténylegesen írható formátumokban.
- Egyszerű hangkivágás kezdő- és végponttal, előhallgatással, mindig új fájlba mentve (alapból a `Music/SzabadZene` mappába). Ha az adott formátumnál veszteségmentes vágás nem megoldható, újrakódolt M4A/AAC kimenet is elfogadható – ezt dokumentálni kell.
- Csengőhangként beállítás a rendszer által előírt engedéllyel.
- Kezdőképernyős widget alapvető lejátszásvezérléssel (aktuális szám, lejátszás/szünet, előző/következő), az alkalmazással mindig egyező állapottal.

**Adatbiztonság**
- A lejátszási listából való eltávolítás és a fájl törlése a telefonról két külön művelet.
- Fájltörléshez egyértelmű megerősítés és az Android által előírt engedély szükséges.
- Címkeszerkesztésnél és vágásnál nem lehet csendes adatvesztés; a vágás alapból új fájlt hoz létre, az eredeti érintetlen marad.
- Ha a címkéket nem sikerült ténylegesen a fájlba írni (visszaolvasással ellenőrizve), nem szabad sikeres módosítást jelezni.

---

## 4. A felület felépítése

**Főképernyő**
- Alsó navigáció: Zenetár, Lejátszási listák, Mappák, Beállítások.
- A Zenetáron belül fülek: Számok, Albumok, Előadók, Műfajok.
- A Lejátszási listák fül tetején rögzítetten a Kedvencek, alatta a saját listák.
- Felül keresés és rendezés.
- Az alsó minilejátszó betöltött szám esetén minden főképernyőn elérhető, és nem takarja el a lista utolsó elemét.

**Teljes lejátszó** (fentről lefelé)
- Vissza/minimalizálás és további műveletek (pl. várólista, hangszínszabályzó, elalvásidőzítő, a szám menüje).
- Borító, cím, előadó, album; kedvenc kapcsoló.
- Jól tekerhető időcsúszka, aktuális idő és teljes hossz.
- Közvetlenül alatta, külön sorban az állandó ugrógombok.
- Külön vezérlősor: előző szám, nagy lejátszás/szünet gomb, következő szám.
- Elérhető ismétlés, véletlen sorrend, várólista és hangszínszabályzó.
- A borító nem foglalhatja el a kezelőszervek helyét.

**Beállítások**
- Téma és kiemelőszín, nyelv, ugrásértékek, mappaszűrés és minimum időtartam, újraolvasás, fejhallgató/Bluetooth viselkedés, elalvásidőzítő.
- Rövid súgó a fontos funkciókról, verzióadatok és a külső összetevők licencei.
- A hétköznapi használat során nem jelenhetnek meg fejlesztői részletek.

**Általános**
- Használható álló és fekvő tájolásban, kis kijelzőn (kb. 320 dp szélesség) és nagyított betűmérettel (2,0×-ig).
- Hosszú címek, hiányzó borítók és üres listák sem törhetik szét az elrendezést; üres listánál rövid magyarázó szöveg jelenik meg.

---

## 5. Lejátszási viselkedés

- A lejátszás folytatódik az app háttérbe helyezése és a képernyő lezárása után.
- A felület megnyitása, forgatása és a képernyőváltás nem indít új lejátszót, és nem okoz dupla hangot.
- A rendszer médiavezérlői és az app mindig ugyanazt az állapotot mutatják.
- Megfelelő hangfókusz-kezelés hívások és más alkalmazások hangja esetén.
- Fejhallgató kihúzásakor vagy a Bluetooth-hangkimenet megszakadásakor a zene alapból szünetel. Visszacsatlakozáskor nem indul el magától, kivéve, ha a felhasználó ezt külön bekapcsolta (alapból kikapcsolt beállítás).
- A várólista, az aktuális szám, a pozíció, az ismétlési és a véletlen mód mentésre kerül; újraindítás után visszaáll, de a zene nem kezd el váratlanul szólni.
- Tekerés közben az időkijelzés a felhasználó által választott pozíciót követi; a háttérből érkező állapotfrissítés nem rántja vissza a csúszkát.
- Hiányzó vagy hibás fájlnál az app nem fagy le és nem kerül végtelen újrapróbálkozásba; érthetően jelzi a problémát, és továbblép vagy megáll.

---

## 6. Technikai irány

- Javasolt alap: Kotlin, Jetpack Compose, Material 3, AndroidX Media3 (ExoPlayer), tartós helyi adatbázis (pl. Room) és DataStore.
- A lejátszó és a MediaSession egy háttérlejátszást támogató MediaSessionService-ben él; a felület MediaControlleren keresztül kapcsolódik hozzá.
- Egyetlen hiteles lejátszási állapot és egyetlen közös ugrási művelet van; ezt használja a teljes és a minilejátszó is.
- Zenetár: MediaStore, szükség esetén rendszerfájlválasztó / Storage Access Framework; a tartós URI-engedélyek és azok elvesztésének kezelése.
- Csak a szükséges engedélyek; verzióhelyes kezelés (médiahozzáférés, előtérszolgáltatás, fájlmódosítás). Teljes fájlrendszer-hozzáférést nem szabad kérni.
- Az aktuális hivatalos Android-dokumentáció és a telepített fejlesztőkörnyezet alapján egymással kompatibilis, stabil függőségek, rögzített verziókkal és Gradle wrapperrel.
- Minimum Android 8 (API 26); a compile/target SDK az aktuális, telepített stabil eszközlánchoz igazodik.
- Átlátható architektúra: a felület, a zenetár, a tartós adatok és a lejátszás felelőssége külön.
- Hanghatásról, formátumról vagy hardveres funkcióról csak ellenőrzés után szabad állítani, hogy támogatott.
- A felhasznált összetevők licenceit dokumentálni kell.
- **Projekt helye:** `C:\Users\Xiwer\Documents\Claude\outputs\SzabadZene` – csak ASCII karakterekből álló útvonal (ékezetes útvonalnál az Android-build Windowson figyelmeztet vagy elhasal). Más alkalmazás projektjét nem szabad módosítani vagy felülírni.
- Helyi git tároló; távoli tárolóba feltöltés (push) és közzététel csak kifejezett kérésre.

Hivatalos kiinduló dokumentáció:
- https://developer.android.com/develop/ui/compose/accessibility/api-defaults
- https://developer.android.com/media/media3/session/background-playback

---

## 7. Ellenőrzés (elfogadási feltételek)

**Tesztkörnyezet**
- A gépen van Android Studio és emulátor: ezt kell használni a telepítéshez és a teszteléshez.
- Tesztadatnak saját, jogdíjmentes, generált hangfájlok kellenek (pl. szinuszhang vagy csend, különböző hosszakkal), köztük: ékezetes és nagyon hosszú cím, hiányzó címke, hiányzó borító, sérült fájl, a minimumszűrő alatti rövid fájl – annyi formátumban, amennyit a gépen lévő eszközök elő tudnak állítani. Új eszközt letölteni csak a felhasználó engedélyével szabad; ami így nem tesztelhető, azt „nem tesztelt”-ként kell jelölni.
- Kis kijelző: pl. `adb shell wm size` / `wm density` segítségével kb. 320 × 568 dp-re állítva. Betűméret: 1,3× és 2,0×.

**Konkrét ellenőrzések**
1. A menüterület közepére, a széleihez közel és az ikon mellé koppintva is a helyes szám menüje nyílik meg; közben nem indul zene, és nem változik a várólista.
2. Hosszú címnél és 2,0× betűméretnél sem csökken a menüterület 64 × 64 dp alá.
3. Az ugrógombok azonnal látszanak, szünetben és tekerés után is megmaradnak – a minilejátszóban is.
4. Kis kijelzőn és fekvő tájolásban a kiemelt gombok nem kerülnek menübe és nem kerülnek egymásra.
5. A 10 másodperces alapérték és az irányonként eltérő beállítás ténylegesen működik, újraindítás után is.
6. Gyors ismételt ugrásnál minden koppintás számít; a szám elején és végén nincs nem kívánt számváltás.
7. Szünetben az ugrás nem indít lejátszást; a normál végigjátszás helyesen követi a várólistát és az ismétlési módot.
8. Háttérbe helyezés, képernyőzár és forgatás közben nincs indokolatlan megszakítás vagy dupla hang.
9. A listák, a kedvencek, a sorrendek és a pozíció újraindítás után visszaállnak; engedélymegtagadás és hiányzó fájl nem okoz összeomlást.
10. Az alkalmazás hálózat nélkül (repülőgép-üzemmódban) is teljesen működik. A címkeszerkesztés és a vágás kimenete valós, visszaolvasható és lejátszható.
11. A widget, az értesítési vezérlés és az elalvásidőzítő működik.
12. A fejhallgató/Bluetooth és a hangfókusz működését a környezet lehetőségei szerint kell vizsgálni; a csak valódi készüléken ellenőrizhető eseteket külön kell jelölni.

**Automatizált tesztek**
- Célzott automatizált tesztek kellenek legalább: az ugrási határhelyzetekre, a gyors egymás utáni ugrásokra, a menü és a számsor kattintásának elkülönítésére, valamint a várólista és a lejátszási állapot megőrzésére.
- A futtatható ellenőrzéseket le kell futtatni; a felületet emulátorban vizuálisan (képernyőképekkel) is meg kell nézni.
- Csak tervezett vagy le nem futtatott teszt nem jelölhető sikeresnek.

---

## 8. Átadás

A munka végén:
- A teljes, újrafordítható Android Studio-projekt.
- Ténylegesen elkészített és telepíthető teszt-APK, a build típusának megjelölésével (debug kulccsal aláírt debug build elegendő).
- Rövid README a megnyitásról, fordításról, telepítésről és használatról.
- Rövid tesztösszefoglaló a ténylegesen ellenőrzött funkciókkal és a fennmaradó korlátokkal.
- A projekt és az APK pontos elérési útja.

Ha a környezet nem enged fordítani vagy emulátort futtatni, pontosan meg kell nevezni az akadályt; ilyenkor nem szabad kész APK-t vagy sikeres tesztelést állítani. Az elvégezhető munkát ettől még be kell fejezni.

**A legfontosabb eredmény:** kényelmesen megnyomható számonkénti menü, állandóan látható másodperces ugrógombok, megbízható offline és háttérlejátszás – ezek az elkészült alkalmazásban valóban működjenek.
