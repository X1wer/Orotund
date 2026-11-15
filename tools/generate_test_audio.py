"""Saját, jogdíjmentes teszt-hangfájlok készítése ffmpeg-gel.

Szinuszhangokból és generált tesztképekből áll; semmilyen külső zenét nem használ.
Kimenet: testdata/generated/ (nincs a gitben). Futtatás a projekt gyökeréből:

    python tools/generate_test_audio.py

Ha nincs ffmpeg a PATH-on, csak a WAV-fájlok készülnek el (a Python beépített moduljával),
a többi formátum kimarad – ezt a kimenet jelzi.
"""

from __future__ import annotations

import math
import os
import shutil
import struct
import subprocess
import sys
import wave
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "testdata" / "generated"

LONG_TITLE = (
    "Nagyon hosszú cím, amely biztosan nem fér el egy sorban: árvíztűrő tükörfúrógép, "
    "ÁRVÍZTŰRŐ TÜKÖRFÚRÓGÉP – és még mindig tart, hogy a rövidítést is ki lehessen próbálni"
)

# (fájlnév, formátum, hossz mp, frekvencia Hz, címkék vagy None, borító sorszáma vagy None)
TRACKS = [
    ("01 Arvizturo.mp3", "mp3", 300, 440, {
        "title": "Árvíztűrő tükörfúrógép", "artist": "Kovács Éva", "album": "Ékezetes dalok",
        "genre": "Népzene", "track": "1", "date": "2024"}, 1),
    ("02 Hosszu cim.mp3", "mp3", 45, 523, {
        "title": LONG_TITLE, "artist": "Egy Nagyon Hosszú Nevű Előadó és a Zenekara Kft.",
        "album": "Ékezetes dalok", "genre": "Pop", "track": "2", "date": "2024"}, 2),
    ("03 Tavaszi szel.m4a", "m4a", 60, 587, {
        "title": "Tavaszi szél", "artist": "Szabó Ádám", "album": "Évszakok",
        "genre": "Népzene", "track": "1", "date": "2023"}, 3),
    ("04 Csendes ej.flac", "flac", 40, 659, {
        "title": "Csendes éj", "artist": "Kórus", "album": "Évszakok",
        "genre": "Klasszikus", "track": "2", "date": "2023"}, 1),
    ("05 Oszi reggel.ogg", "ogg", 50, 698, {
        "title": "Őszi reggel", "artist": "Szabó Ádám", "album": "Évszakok",
        "genre": "Népzene", "track": "3", "date": "2023"}, None),
    ("06 Címke nélküli felvétel.wav", "wav", 30, 784, None, None),
    ("07 Cimke nelkul.mp3", "mp3", 35, 880, None, None),
    ("08 Rovid hang.mp3", "mp3", 3, 988, {
        "title": "Rövid hang", "artist": "Teszt", "album": "Rövidek"}, None),
    ("10 Ismetles teszt.mp3", "mp3", 12, 330, {
        "title": "Ismétlés teszt (12 mp)", "artist": "Teszt", "album": "Rövidek",
        "track": "1"}, 2),
]

COVERS = {
    1: "testsrc=size=500x500:rate=1",
    2: "smptebars=size=500x500:rate=1",
    3: "color=c=0x1F6F5C:size=500x500:rate=1",
}


def run(cmd: list[str]) -> None:
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(f"ffmpeg hiba:\n{' '.join(cmd)}\n{result.stderr[-2000:]}")


def write_wav_python(path: Path, seconds: int, freq: int) -> None:
    rate = 44100
    with wave.open(str(path), "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(rate)
        frames = bytearray()
        for i in range(rate * seconds):
            v = int(9000 * math.sin(2 * math.pi * freq * i / rate))
            frames += struct.pack("<hh", v, v)
        w.writeframes(bytes(frames))


def make_cover(ffmpeg: str, index: int) -> Path:
    path = OUT / f"_cover{index}.png"
    if not path.exists():
        run([ffmpeg, "-y", "-loglevel", "error", "-f", "lavfi", "-i", COVERS[index],
             "-frames:v", "1", str(path)])
    return path


def make_track(ffmpeg: str, name: str, fmt: str, seconds: int, freq: int,
               tags: dict | None, cover: int | None) -> None:
    out = OUT / name
    cmd = [ffmpeg, "-y", "-loglevel", "error",
           "-f", "lavfi", "-i", f"sine=frequency={freq}:duration={seconds}:sample_rate=44100"]
    cover_path = make_cover(ffmpeg, cover) if cover else None
    if cover_path:
        cmd += ["-i", str(cover_path)]
    cmd += ["-map", "0:a"]
    if cover_path:
        cmd += ["-map", "1:v", "-disposition:v", "attached_pic"]
    codec = {
        "mp3": ["-c:a", "libmp3lame", "-b:a", "192k", "-id3v2_version", "3"],
        "m4a": ["-c:a", "aac", "-b:a", "160k"],
        "flac": ["-c:a", "flac"],
        "ogg": ["-c:a", "libvorbis", "-q:a", "5"],
        "wav": ["-c:a", "pcm_s16le"],
    }[fmt]
    cmd += codec
    if cover_path:
        cmd += ["-c:v", "mjpeg" if fmt == "m4a" else "png"]
    if tags:
        for key, value in tags.items():
            cmd += ["-metadata", f"{key}={value}"]
    else:
        # Teljesen címke nélkül: a cím a fájlnévből jön.
        cmd += ["-map_metadata", "-1"]
        if fmt == "mp3":
            cmd += ["-write_id3v1", "0", "-write_xing", "1"]
    cmd += [str(out)]
    run(cmd)


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    ffmpeg = shutil.which("ffmpeg")
    skipped = []
    for name, fmt, seconds, freq, tags, cover in TRACKS:
        if ffmpeg:
            make_track(ffmpeg, name, fmt, seconds, freq, tags, cover)
        elif fmt == "wav":
            write_wav_python(OUT / name, seconds, freq)
        else:
            skipped.append(name)
            continue
        print(f"kész: {name}")

    # Sérült fájl: véletlen bájtok MP3 kiterjesztéssel.
    broken = OUT / "09 Serult fajl.mp3"
    broken.write_bytes(b"ID3" + os.urandom(48_000))
    print(f"kész: {broken.name} (szándékosan sérült)")

    for cover in OUT.glob("_cover*.png"):
        cover.unlink()
    if skipped:
        print("NEM készült el (nincs ffmpeg):", ", ".join(skipped))
    return 0


if __name__ == "__main__":
    sys.exit(main())
