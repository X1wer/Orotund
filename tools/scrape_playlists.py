"""A „Music player” (com.media.music.mp3.musicplayer) lejátszási listáinak kiolvasása adb-vel, a képernyőről.

Csak olvas: megnyitja a listákat és görget, a telefonon semmit nem módosít. A „Lejátszási lista” fül legyen
megnyitva. Listánként egy .m3u8 fájl készül a kimeneti mappába (#EXTINF hosszal és címmel); a SzabadZene
importja a cím alapján párosítja a számokat (a fájl helye a régi appban nem látszik).

Használat: python -I -X utf8 scrape_playlists.py <kimeneti mappa> [--serial SOROZATSZÁM]
"""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ADB = os.path.expandvars(r"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe")
ENV = dict(os.environ, MSYS_NO_PATHCONV="1")
SERIAL = []
SONG_ROW = re.compile(r"^(?:\d+\s+)?(\d{1,2}:\d{2}(?::\d{2})?)$")
LIST_ROW = re.compile(r"^(\d+)\s+dal(?:ok)?\s*\[", re.IGNORECASE)
TILE_COUNT = re.compile(r"^\((\d+)\)$")
SMART_SKIP = {"Nemrég hozzáadott", "Legtöbbet játszott", "Nemrég játszott"}
FORBIDDEN = '\\/:*?"<>|'


def adb(*args):
    return subprocess.run([ADB, *SERIAL, *args], capture_output=True, env=ENV).stdout.decode("utf-8", "replace")


def nodes():
    xml = ""
    for _ in range(5):
        xml = adb("exec-out", "uiautomator", "dump", "/dev/tty")
        if "</hierarchy>" in xml:
            break
        time.sleep(1)
    xml = xml[: xml.index("</hierarchy>") + len("</hierarchy>")]
    out = []
    for n in ET.fromstring(xml).iter("node"):
        t = n.get("text") or ""
        if t:
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
            out.append((y1, x1, t, (x1 + x2) // 2, (y1 + y2) // 2))
    out.sort()
    return out


STEP = [800]


def swipe_up():
    adb("shell", "input", "swipe", "540", "1700", "540", str(1700 - STEP[0]), "1500")
    time.sleep(0.8)


def to_top():
    for _ in range(15):
        adb("shell", "input", "swipe", "540", "600", "540", "2000", "150")
    time.sleep(1)


# --- A listák nevei ---

def list_entries(texts):
    """A listák fül látható elemei: (név, darabszám, x, y) – a csempék (Kedvencek…) és a sorok."""
    found = []
    for i, (y, x, t, cx, cy) in enumerate(texts):
        m = LIST_ROW.match(t) or TILE_COUNT.match(t)
        if not m or y < 360:
            continue
        # a név a darabszám fölött, ugyanabban az oszlopban (a csempéknél nem a közvetlen előző elem)
        above = [n for n in texts[:i] if abs(n[1] - x) < 10 and 0 < y - n[0] < 160]
        if above:
            name = above[-1]
            found.append((name[2], int(m.group(1)), name[3], name[4]))
    return found


def collect_lists():
    to_top()
    names = []
    stalls = 0
    while stalls < 2:
        before = len(names)
        for name, count, _, _ in list_entries(nodes()):
            if (name, count) not in names:
                names.append((name, count))
        stalls = stalls + 1 if len(names) == before else 0
        swipe_up()
    return [(n, c) for n, c in names if n not in SMART_SKIP]


def open_list(name, count):
    to_top()
    for _ in range(40):
        for n, c, cx, cy in list_entries(nodes()):
            if n == name and c == count:
                adb("shell", "input", "tap", str(cx), str(cy))
                time.sleep(2)
                return True
        swipe_up()
    return False


# --- Egy lista számai ---

def song_rows(texts):
    rows = []
    for i, (y, x, t, _, _) in enumerate(texts):
        m = SONG_ROW.match(t)
        if not m or i == 0 or x < 170 or y < 360:
            continue
        title = texts[i - 1]
        if not (30 < y - title[0] < 80) or SONG_ROW.match(title[2]) or title[1] < 170:
            continue
        artist = ""
        if i + 1 < len(texts) and 0 <= texts[i + 1][0] - y < 20 and not SONG_ROW.match(texts[i + 1][2]):
            artist = texts[i + 1][2]
        rows.append((m.group(1), title[2], "" if artist == "<unknown>" else artist))
    return rows


def merge(collected, page):
    key = lambda r: r[:2]
    for k in range(min(len(collected), len(page)), 0, -1):
        if [key(r) for r in collected[-k:]] == [key(r) for r in page[:k]]:
            collected.extend(page[k:])
            return True
    return False


def scrape_songs(expected):
    collected = song_rows(nodes())
    stalls = 0
    while len(collected) < expected and stalls < 3:
        swipe_up()
        page = song_rows(nodes())
        before = len(collected)
        if not merge(collected, page):
            return collected, "nincs átfedés"
        stalls = stalls + 1 if len(collected) == before else 0
    return collected, None


def seconds(duration):
    parts = [int(p) for p in duration.split(":")]
    return parts[0] * 60 + parts[1] if len(parts) == 2 else parts[0] * 3600 + parts[1] * 60 + parts[2]


def file_name(name, used):
    base = "".join("_" if c in FORBIDDEN or ord(c) < 32 else c for c in name).strip().strip(".") or "Lista"
    candidate, n = base, 2
    while candidate.lower() in used:
        candidate, n = f"{base} ({n})", n + 1
    used.add(candidate.lower())
    return candidate + ".m3u8"


def main():
    out = sys.argv[1]
    if "--serial" in sys.argv:
        SERIAL.extend(["-s", sys.argv[sys.argv.index("--serial") + 1]])
    os.makedirs(out, exist_ok=True)
    if "--step" in sys.argv:
        STEP[0] = int(sys.argv[sys.argv.index("--step") + 1])
    lists = collect_lists()
    if "--only" in sys.argv:
        only = set(sys.argv[sys.argv.index("--only") + 1].split("|"))
        lists = [(n, c) for n, c in lists if n in only]
    print(f"{len(lists)} lista:", ", ".join(f"{n} ({c})" for n, c in lists), flush=True)
    used = set()
    for name, count in lists:
        if not open_list(name, count):
            print(f"HIBA: nem találom: {name}", flush=True)
            continue
        songs, problem = scrape_songs(count)
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(1.5)
        status = "rendben" if len(songs) == count and not problem else f"ELTÉRÉS ({problem or 'darabszám'})"
        print(f"{name}: {len(songs)}/{count} {status}", flush=True)
        lines = ["#EXTM3U", f"#PLAYLIST:{name}"]
        for duration, title, artist in songs:
            lines.append(f"#EXTINF:{seconds(duration)},{title}")
            lines.append(title)
        with open(os.path.join(out, file_name(name, used)), "w", encoding="utf-8", newline="\n") as f:
            f.write("\n".join(lines) + "\n")


main()
