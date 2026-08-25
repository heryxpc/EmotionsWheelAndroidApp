#!/usr/bin/env python3
"""Build app/src/main/assets/emotions.json from the wheel's CSV.

The wheel has six families of sixty degrees each, laid out clockwise from the top.
Every family holds one core emotion plus a middle ring and an outer ring of seven
emotions each, also listed clockwise. The CSV is the source of truth so the wording
can be edited in a spreadsheet; this script only validates it and reshapes it.

Expected columns (matched ignoring case and accents; the Spanish names the app's
own journal CSV uses are accepted too):

    family      the family's own core emotion: sorpresa, enojo, alegria,
                miedo, tristeza or asco
    ring        1 core, 2 middle ring, 3 outer ring
    position    0..6 clockwise within the ring; always 0 for the core
    emotion     the word shown on the wheel
    definition  one or two lines, meant to tell sibling emotions apart

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
    """Reads the CSV into normalized rows, reporting every problem it can see."""
    with source.open(encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        headers = {}
        for name in reader.fieldnames or []:
            key = strip_accents(name)
            headers[COLUMN_ALIASES.get(key, key)] = name
        missing = [column for column in COLUMNS if column not in headers]
        if missing:
            raise click.ClickException(
                f"{source}: faltan columnas {missing}; encontré {reader.fieldnames}"
            )

        rows = []
        for line, raw in enumerate(reader, start=2):
            values = {column: (raw.get(headers[column]) or "").strip() for column in COLUMNS}
            if not any(values.values()):
                continue

            family_key = strip_accents(values["family"])
            family = FAMILY_IDS.get(family_key)
            if family is None:
                raise click.ClickException(
                    f"{source}:{line}: familia desconocida '{values['familia']}'; "
                    f"esperaba una de {sorted(FAMILY_IDS)}"
                )

            try:
                level = int(values["ring"])
                index = int(values["position"])
            except ValueError as error:
                raise click.ClickException(
                    f"{source}:{line}: ring y position deben ser números ({error})"
                ) from error

            if level not in (CORE, MIDDLE, OUTER):
                raise click.ClickException(f"{source}:{line}: ring {level} fuera de 1..3")
            if not values["emotion"]:
                raise click.ClickException(f"{source}:{line}: falta la emoción")
            if not values["definition"]:
                raise click.ClickException(
                    f"{source}:{line}: '{values['emocion']}' no tiene definición"
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
        by_level = Counter(row["level"] for row in members)
        if by_level[CORE] != 1:
            problems.append(f"{family}: {by_level[CORE]} emociones en el núcleo, esperaba 1")
        for level, name in ((MIDDLE, "anillo medio"), (OUTER, "anillo exterior")):
            ring = sorted(row["index"] for row in members if row["level"] == level)
            if ring != list(range(SECTORS_PER_RING)):
                problems.append(f"{family}, {name}: posiciones {ring}, esperaba 0..6")

    unexpected = {row["family"] for row in rows} - set(FAMILY_ORDER)
    if unexpected:
        problems.append(f"familias no reconocidas: {sorted(unexpected)}")

    duplicates = [id_ for id_, n in Counter(row["id"] for row in rows).items() if n > 1]
    if duplicates:
        problems.append(f"ids repetidos: {sorted(duplicates)}")

    for row in rows:
        if not row["id"].replace(" ", "").isalpha():
            problems.append(f"línea {row['line']}: '{row['label']}' produce el id '{row['id']}'")
        if len(row["definition"]) > MAX_DEFINITION:
            problems.append(
                f"línea {row['line']}: la definición de '{row['label']}' tiene "
                f"{len(row['definition'])} caracteres (máximo {MAX_DEFINITION})"
            )

    if problems:
        listing = "\n".join(f"  - {problem}" for problem in problems)
        raise click.ClickException(f"{source} no describe una rueda válida:\n{listing}")


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
    help="Dónde escribir el JSON del catálogo.",
)
@click.option(
    "--check",
    is_flag=True,
    help="Solo valida el CSV; no escribe nada.",
)
def main(source: pathlib.Path | None, output: pathlib.Path, check: bool) -> None:
    """Convierte el CSV de la rueda en el catálogo que lee la app.

    SOURCE es el CSV a importar; por omisión data/emotions-wheel.csv.
    """
    source = source or DEFAULT_SOURCE
    if not source.exists():
        raise click.ClickException(f"no encuentro el CSV: {source}")

    rows = read_rows(source)
    validate(rows, source)
    rows.sort(key=order_key)

    emotions = [
        {key: row[key] for key in ("id", "label", "family", "level", "index", "definition")}
        for row in rows
    ]

    click.echo(f"{source}: {len(emotions)} emociones en {len(FAMILY_ORDER)} familias")
    click.echo(f"  definición más larga: {max(len(e['definition']) for e in emotions)} caracteres")

    if check:
        click.echo("--check: el CSV es válido, no escribí nada")
        return

    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        json.dumps({"version": 1, "emotions": emotions}, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    click.echo(f"escrito {output}")


if __name__ == "__main__":
    main()
