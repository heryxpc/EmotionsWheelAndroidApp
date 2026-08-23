#!/usr/bin/env python3
"""Convert the hand-kept CSV journal into app/src/main/assets/journal_seed.json.

The source CSV is messy in ways the app has to survive: header names carry
trailing spaces, some rows name several emotions separated by slashes, a few
dates have a doubled slash or a year that does not fit the sequence, and some
labels carry stray asterisks. Every repair is reported on stdout so nothing is
silently rewritten.

Emotion names that exist on the wheel become ids; the rest are kept verbatim as
a free-text emotion so no entry is lost.

Usage: python3 tools/build_seed.py [path/to/Bitacora emociones.csv]
"""

from __future__ import annotations

import csv
import hashlib
import json
import pathlib
import re
import sys
import unicodedata
from datetime import date

# The journal covers August 2026. Years outside this range are typos in the source.
EXPECTED_YEAR = 2026
DEFAULT_CSV = pathlib.Path.home() / "Downloads" / "Bitácora emociones.csv"


def slugify(label: str) -> str:
    decomposed = unicodedata.normalize("NFD", label.strip().lower())
    return "".join(c for c in decomposed if unicodedata.category(c) != "Mn")


def clean(value: str) -> str:
    """Trim whitespace and the stray asterisks the source uses as side notes."""
    return re.sub(r"\s+", " ", value.replace("*", " ")).strip()


def parse_date(raw: str, repairs: list[str]) -> date:
    text = clean(raw)
    normalized = re.sub(r"/{2,}", "/", text)
    if normalized != text:
        repairs.append(f"fecha '{text}' -> '{normalized}' (barra doble)")

    day, month, year = (int(part) for part in normalized.split("/"))
    if year != EXPECTED_YEAR:
        repairs.append(f"fecha '{normalized}' -> año {EXPECTED_YEAR} (fuera de la secuencia)")
        year = EXPECTED_YEAR
    return date(year, month, day)


def parse_emotions(raw: str, catalog: dict[str, str]) -> tuple[list[str], str | None]:
    """Split a cell like 'impaciencia / hostilidad / desánimo' into wheel ids
    plus whatever could not be matched, kept as free text."""
    parts = [clean(part) for part in re.split(r"[/,]", raw)]
    ids: list[str] = []
    unmatched: list[str] = []
    for part in parts:
        if not part:
            continue
        slug = slugify(part)
        if slug in catalog:
            if slug not in ids:
                ids.append(slug)
        else:
            unmatched.append(part.lower())
    return ids, ", ".join(unmatched) if unmatched else None


def stable_id(entry_date: date, emotions: str, situation: str) -> str:
    """Deterministic id so re-running the script never duplicates an entry."""
    digest = hashlib.sha1(
        f"{entry_date.isoformat()}|{emotions}|{situation}".encode("utf-8")
    ).hexdigest()
    return f"{digest[:8]}-{digest[8:12]}-{digest[12:16]}-{digest[16:20]}-{digest[20:32]}"


def main() -> None:
    root = pathlib.Path(__file__).resolve().parent.parent
    source = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_CSV
    if not source.exists():
        sys.exit(f"CSV not found: {source}")

    catalog_path = root / "app" / "src" / "main" / "assets" / "emotions.json"
    if not catalog_path.exists():
        sys.exit("emotions.json missing — run tools/build_catalog.py first")
    catalog = {
        emotion["id"]: emotion["label"]
        for emotion in json.loads(catalog_path.read_text(encoding="utf-8"))["emotions"]
    }

    repairs: list[str] = []
    unmatched_labels: set[str] = set()
    entries: list[dict] = []

    with source.open(encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle)
        # Header names in the source carry trailing spaces; map them by position.
        fields = [name.strip() for name in (reader.fieldnames or [])]
        if fields[:3] != ["fecha", "emoción", "evento"]:
            sys.exit(f"unexpected CSV header: {reader.fieldnames}")
        raw_fields = reader.fieldnames or []

        for row_number, row in enumerate(reader, start=2):
            raw_date = (row.get(raw_fields[0]) or "").strip()
            raw_emotion = (row.get(raw_fields[1]) or "").strip()
            situation = clean(row.get(raw_fields[2]) or "")
            if not raw_date and not raw_emotion and not situation:
                continue

            entry_date = parse_date(raw_date, repairs)
            ids, custom = parse_emotions(raw_emotion, catalog)
            if custom:
                unmatched_labels.update(part.strip() for part in custom.split(","))
            if not ids and not custom:
                sys.exit(f"row {row_number}: no emotion at all")

            entries.append(
                {
                    "id": stable_id(entry_date, ",".join(ids) + (custom or ""), situation),
                    "date": entry_date.isoformat(),
                    "emotionIds": ids,
                    "customEmotion": custom,
                    "situation": situation,
                }
            )

    entries.sort(key=lambda entry: entry["date"])

    ids_seen = [entry["id"] for entry in entries]
    if len(set(ids_seen)) != len(ids_seen):
        sys.exit("stable ids collided — two rows are byte-identical")

    out = root / "app" / "src" / "main" / "assets" / "journal_seed.json"
    out.write_text(
        json.dumps({"version": 1, "entries": entries}, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )

    multi = sum(1 for entry in entries if len(entry["emotionIds"]) > 1 or entry["customEmotion"])
    print(f"wrote {out.relative_to(root)}")
    print(f"  {len(entries)} entries, {entries[0]['date']} .. {entries[-1]['date']}")
    print(f"  {multi} entries with more than one emotion or free text")
    print(f"  emociones fuera de la ruleta ({len(unmatched_labels)}): {sorted(unmatched_labels)}")
    print(f"  correcciones aplicadas ({len(repairs)}):")
    for repair in repairs:
        print(f"    - {repair}")


if __name__ == "__main__":
    main()
