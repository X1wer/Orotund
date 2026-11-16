"""A „Legtöbbet játszott” lista kiolvasása adb-vel (csak olvasás, a telefonon nem módosít semmit).

Használat: python -I -X utf8 scrape_counts.py <kimenet.csv>
A lista legyen megnyitva a telefonon, a tetején. Addig görget, amíg 0 lejátszásszámú sorhoz nem ér
(vagy a lista véget nem ér). A kimenet: pontosvesszővel tagolt CSV (UTF-8): count;duration;title;artist
"""
import csv
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ADB = os.path.expandvars(r"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe")
ENV = dict(os.environ, MSYS_NO_PATHCONV="1")
ROW = re.compile(r"^(\d+)\s+(\d{1,2}:\d{2}(?::\d{2})?)$")


def adb(*args):
    return subprocess.run([ADB, *args], capture_output=True, env=ENV).stdout.decode("utf-8", "replace")


def screen_rows():
    xml = ""
    for _ in range(4):
        xml = adb("exec-out", "uiautomator", "dump", "/dev/tty")
        if "</hierarchy>" in xml:
            break
        time.sleep(1)
    xml = xml[: xml.index("</hierarchy>") + len("</hierarchy>")]
    texts = []
    for n in ET.fromstring(xml).iter("node"):
        t = n.get("text") or ""
        if not t:
            continue
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
        # csak a lista sorai: a fejléc és a fülek kívül esnek (x < 170 vagy y < 360)
        if x1 >= 170 and y1 >= 360:
            texts.append((y1, x1, t))
    texts.sort()
    rows = []
    for i, (y, x, t) in enumerate(texts):
        m = ROW.match(t)
        # a cím a számlálósor fölött közvetlenül áll (kb. 54 képpont); félig látszó, cím nélküli sort kihagyunk
        if not m or i == 0 or not (30 < y - texts[i - 1][0] < 80) or ROW.match(texts[i - 1][2]):
            continue
        artist = ""
        if i + 1 < len(texts) and 0 <= texts[i + 1][0] - y < 20 and not ROW.match(texts[i + 1][2]):
            artist = texts[i + 1][2]
        rows.append((int(m.group(1)), m.group(2), texts[i - 1][2], artist))
    return rows


def overlap_merge(collected, page):
    """A lap eleje átfedi a gyűjtött lista végét (a széleken félig látszó sor előadója hiányozhat): csak az
    új sorokat fűzi hozzá, és a hiányzó előadót pótolja. Igaz, ha volt átfedés."""
    key = lambda r: r[:3]
    for k in range(min(len(collected), len(page)), 0, -1):
        if [key(r) for r in collected[-k:]] == [key(r) for r in page[:k]]:
            for j in range(k):
                if not collected[-k + j][3] and page[j][3]:
                    collected[-k + j] = page[j]
            collected.extend(page[k:])
            return True
    return False


def main():
    out = sys.argv[1]
    collected = screen_rows()
    if not collected:
        sys.exit("Nem találtam lista sort – nyisd meg a Legtöbbet játszott listát.")
    stalls = 0
    while collected[-1][0] > 0 and stalls < 3:
        adb("shell", "input", "swipe", "540", "1700", "540", "900", "1500")
        time.sleep(0.8)
        page = screen_rows()
        before = len(collected)
        if not page:
            stalls += 1
            continue
        if not overlap_merge(collected, page):
            print("VÉG:", collected[-3:])
            print("LAP:", page[:3])
            sys.exit(f"Nincs átfedés a lapok között ({before} sornál) – a görgetés túl nagy lépéssel ment.")
        stalls = stalls + 1 if len(collected) == before else 0
        if len(collected) % 100 < 8:
            print(len(collected), "sor, utolsó:", collected[-1][0], flush=True)
    rows = [r for r in collected if r[0] > 0]
    with open(out, "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, delimiter=";")
        w.writerow(["count", "duration", "title", "artist"])
        for count, dur, title, artist in rows:
            w.writerow([count, dur, title, "" if artist in ("<unknown>", "") else artist])
    print("kész:", len(rows), "sor ->", out)


main()
