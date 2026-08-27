#!/usr/bin/env python3
"""Build app/src/main/assets/emotions.json from the wheel's CSV.

The wheel has six families of sixty degrees each, laid out clockwise from the top.
Every family holds one core emotion plus a middle ring and an outer ring of seven
emotions each, also listed clockwise. The CSV is the source of truth so the wording
can be edited in a spreadsheet; this script only validates it and reshapes it.

Expected columns (matched ignoring case and accents; the Spanish names the app's own
journal CSV uses are accepted too):

    family      the family's own core emotion: sorpresa, enojo, alegria,
                miedo, tristeza or asco
    ring        1 core, 2 middle ring, 3 outer ring
    position    0..6 clockwise within the ring; always 0 for the core
    emotion     the word shown on the wheel
    definition  one or two lines, meant to tell sibling emotions apart

Emotion names and definitions stay in Spanish: they are the content of the wheel.

Usage:
    python3 tools/build_catalog.py [CSV] [-o JSON]
"""

from __future__ import annotations

import csv
import json
import pathlib
import unicodedata
from collections import Counter

import click

REPO_ROOT = pathlib.Path(__file__).resolve().parent.parent
DEFAULT_SOURCE = REPO_ROOT / "data" / "emotions-wheel.csv"
DEFAULT_OUTPUT = REPO_ROOT / "app" / "src" / "main" / "assets" / "emotions.json"

CORE, MIDDLE, OUTER = 1, 2, 3
RING_NAMES = {CORE: "core", MIDDLE: "middle ring", OUTER: "outer ring"}
SECTORS_PER_RING = 7
MAX_DEFINITION = 200

# Clockwise from twelve o'clock, matching the printed wheel. The CSV names each family
# by its core emotion; these are the constants the Kotlin EmotionFamily enum uses.
FAMILY_IDS = {
    "sorpresa": "SURPRISE",
    "enojo": "ANGER",
    "alegria": "JOY",
    "miedo": "FEAR",
    "tristeza": "SADNESS",
    "asco": "DISGUST",
}
FAMILY_ORDER = list(FAMILY_IDS.values())

COLUMNS = ("family", "ring", "position", "emotion", "definition")

# Someone editing this file next to the journal CSV, whose headers are Spanish, could
# easily write them in Spanish here too. Both are read.
COLUMN_ALIASES = {
    "familia": "family",
    "anillo": "ring",
    "posicion": "position",
    "emocion": "emotion",
    "definicion": "definition",
}


def strip_accents(value: str) -> str:
    decomposed = unicodedata.normalize("NFD", value.strip().lower())
    return "".join(c for c in decomposed if unicodedata.category(c) != "Mn")


def slugify(label: str) -> str:
    """Accent-free, lowercase id: 'desilusión' -> 'desilusion'."""
    return strip_accents(label)


def read_rows(source: pathlib.Path) -> list[dict]:
    """Reads the CSV into normalized rows, naming the offending line on any problem."""
    with source.open(encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        headers = {}
        for name in reader.fieldnames or []:
            key = strip_accents(name)
            headers[COLUMN_ALIASES.get(key, key)] = name
        missing = [column for column in COLUMNS if column not in headers]
        if missing:
            raise click.ClickException(
                f"{source}: missing columns {missing}; found {reader.fieldnames}"
            )

        rows = []
        for line, raw in enumerate(reader, start=2):
            values = {column: (raw.get(headers[column]) or "").strip() for column in COLUMNS}
            if not any(values.values()):
                continue

            family = FAMILY_IDS.get(strip_accents(values["family"]))
            if family is None:
                raise click.ClickException(
                    f"{source}:{line}: unknown family '{values['family']}'; "
                    f"expected one of {sorted(FAMILY_IDS)}"
                )

            try:
                level = int(values["ring"])
                index = int(values["position"])
            except ValueError as error:
                raise click.ClickException(
                    f"{source}:{line}: ring and position must be numbers ({error})"
                ) from error

            if level not in RING_NAMES:
                raise click.ClickException(f"{source}:{line}: ring {level} is not 1, 2 or 3")
            if not values["emotion"]:
                raise click.ClickException(f"{source}:{line}: no emotion")
            if not values["definition"]:
                raise click.ClickException(
                    f"{source}:{line}: '{values['emotion']}' has no definition"
                )

            rows.append(
                {
                    "id": slugify(values["emotion"]),
                    "label": values["emotion"],
                    "family": family,
                    "level": level,
                    "index": index,
                    "definition": values["definition"],
                    "line": line,
                }
            )
        return rows


def validate(rows: list[dict], source: pathlib.Path) -> None:
    """Every family needs one core and two full rings, or the wheel grows a hole."""
    problems: list[str] = []

    for family in FAMILY_ORDER:
        members = [row for row in rows if row["family"] == family]
        cores = sum(1 for row in members if row["level"] == CORE)
        if cores != 1:
            problems.append(f"{family}: {cores} core emotions, expected exactly 1")
        for level in (MIDDLE, OUTER):
            ring = sorted(row["index"] for row in members if row["level"] == level)
            if ring != list(range(SECTORS_PER_RING)):
                problems.append(
                    f"{family}, {RING_NAMES[level]}: positions {ring}, expected 0..6"
                )

    unexpected = {row["family"] for row in rows} - set(FAMILY_ORDER)
    if unexpected:
        problems.append(f"unrecognized families: {sorted(unexpected)}")

    duplicates = [id_ for id_, n in Counter(row["id"] for row in rows).items() if n > 1]
    if duplicates:
        problems.append(f"duplicate ids: {sorted(duplicates)}")

    for row in rows:
        if not row["id"].replace(" ", "").isalpha():
            problems.append(
                f"line {row['line']}: '{row['label']}' yields the id '{row['id']}', "
                f"which is not plain letters"
            )
        if len(row["definition"]) > MAX_DEFINITION:
            problems.append(
                f"line {row['line']}: the definition of '{row['label']}' is "
                f"{len(row['definition'])} characters (limit {MAX_DEFINITION})"
            )

    if problems:
        listing = "\n".join(f"  - {problem}" for problem in problems)
        raise click.ClickException(f"{source} does not describe a valid wheel:\n{listing}")


def order_key(row: dict) -> tuple[int, int, int]:
    return FAMILY_ORDER.index(row["family"]), row["level"], row["index"]


@click.command(context_settings={"help_option_names": ["-h", "--help"]})
@click.argument(
    "source",
    required=False,
    type=click.Path(exists=True, dir_okay=False, path_type=pathlib.Path),
)
@click.option(
    "-o",
    "--output",
    default=DEFAULT_OUTPUT,
    show_default=str(DEFAULT_OUTPUT.relative_to(REPO_ROOT)),
    type=click.Path(dir_okay=False, path_type=pathlib.Path),
    help="Where to write the catalog JSON.",
)
@click.option("--check", is_flag=True, help="Validate the CSV without writing anything.")
def main(source: pathlib.Path | None, output: pathlib.Path, check: bool) -> None:
    """Turn the wheel's CSV into the catalog the app reads.

    SOURCE is the CSV to import; defaults to data/emotions-wheel.csv.
    """
    source = source or DEFAULT_SOURCE
    if not source.exists():
        raise click.ClickException(f"no such CSV: {source}")

    rows = read_rows(source)
    validate(rows, source)
    rows.sort(key=order_key)

    emotions = [
        {key: row[key] for key in ("id", "label", "family", "level", "index", "definition")}
        for row in rows
    ]

    click.echo(f"{source}: {len(emotions)} emotions across {len(FAMILY_ORDER)} families")
    click.echo(
        f"  longest definition: {max(len(e['definition']) for e in emotions)} characters"
    )

    if check:
        click.echo("--check: the CSV is valid, nothing written")
        return

    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        json.dumps({"version": 1, "emotions": emotions}, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    click.echo(f"wrote {output}")


if __name__ == "__main__":
    main()
