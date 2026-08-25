#!/usr/bin/env python3
"""Convert a hand-kept CSV journal into app/src/main/assets/journal_seed.json.

The source is messy in ways the app has to survive: header names carry trailing
spaces, some rows name several emotions separated by slashes, a few dates have a
doubled slash, and some labels carry stray asterisks. Every repair is reported on
stdout so nothing is silently rewritten, and dates are never moved to a different
year — a row dated 2029 is imported as 2029 and merely flagged.

Emotion names that exist on the wheel become ids; the rest are kept verbatim as a
free-text emotion so no entry is lost.

Expected columns (matched ignoring case, accents and stray spaces):

    fecha    dd/mm/yyyy
    emocion  one or more names, separated by "/" or ","
    evento   what happened

Usage:
    python3 tools/build_seed.py [CSV] [-o JSON]
"""

from __future__ import annotations

import csv
import hashlib
import json
import pathlib
import re
import unicodedata
from datetime import date

import click

REPO_ROOT = pathlib.Path(__file__).resolve().parent.parent
DEFAULT_SOURCE = pathlib.Path.home() / "Downloads" / "Bitácora emociones.csv"
DEFAULT_OUTPUT = REPO_ROOT / "app" / "src" / "main" / "assets" / "journal_seed.json"
DEFAULT_CATALOG = REPO_ROOT / "app" / "src" / "main" / "assets" / "emotions.json"

COLUMNS = ("fecha", "emocion", "evento")

# A journal spanning more than this is more likely to hold a mistyped year than to be
# genuine, so the span is reported. It is never corrected.
SUSPICIOUS_SPAN_DAYS = 365


def strip_accents(value: str) -> str:
    decomposed = unicodedata.normalize("NFD", value.strip().lower())
    return "".join(c for c in decomposed if unicodedata.category(c) != "Mn")


def clean(value: str) -> str:
    """Trim whitespace and the stray asterisks the source uses as side notes."""
    return re.sub(r"\s+", " ", value.replace("*", " ")).strip()


def parse_date(raw: str, line: int, repairs: list[str]) -> date | None:
    text = clean(raw)
    if not text:
        return None

    normalized = re.sub(r"/{2,}", "/", text)
    if normalized != text:
        repairs.append(f"línea {line}: fecha '{text}' -> '{normalized}' (barra doble)")

    parts = normalized.split("/")
    if len(parts) != 3:
        return None
    try:
        day, month, year = (int(part) for part in parts)
        return date(year, month, day)
    except ValueError:
        return None


def parse_emotions(raw: str, catalog: dict[str, str]) -> tuple[list[str], str | None]:
    """Splits a cell like 'impaciencia / hostilidad / desánimo' into wheel ids
    plus whatever could not be matched, kept as free text."""
    ids: list[str] = []
    unmatched: list[str] = []
    for part in (clean(part) for part in re.split(r"[/,]", raw)):
        if not part:
            continue
        slug = strip_accents(part)
        if slug in catalog:
            if slug not in ids:
                ids.append(slug)
        else:
            unmatched.append(part.lower())
    return ids, ", ".join(unmatched) if unmatched else None


def stable_id(entry_date: date, emotions: str, situation: str) -> str:
    """Content-derived id, shaped like a UUID, so re-running never duplicates a row.
    JournalCsv.stableId in the app uses the same recipe."""
    digest = hashlib.sha1(
        f"{entry_date.isoformat()}|{emotions}|{situation}".encode("utf-8")
    ).hexdigest()
    return f"{digest[:8]}-{digest[8:12]}-{digest[12:16]}-{digest[16:20]}-{digest[20:32]}"


def load_catalog(path: pathlib.Path) -> dict[str, str]:
    if not path.exists():
        raise click.ClickException(
            f"no encuentro el catálogo {path}; corre antes tools/build_catalog.py"
        )
    data = json.loads(path.read_text(encoding="utf-8"))
    return {emotion["id"]: emotion["label"] for emotion in data["emotions"]}


def read_entries(
    source: pathlib.Path,
    catalog: dict[str, str],
    repairs: list[str],
    skipped: list[str],
    unmatched_labels: set[str],
) -> list[dict]:
    entries: list[dict] = []
    with source.open(encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        headers = {strip_accents(name): name for name in (reader.fieldnames or [])}
        missing = [column for column in COLUMNS if column not in headers]
        if missing:
            raise click.ClickException(
                f"{source}: faltan columnas {missing}; encontré {reader.fieldnames}"
            )

        for line, raw in enumerate(reader, start=2):
            values = {column: (raw.get(headers[column]) or "").strip() for column in COLUMNS}
            if not any(values.values()):
                continue

            entry_date = parse_date(values["fecha"], line, repairs)
            if entry_date is None:
                skipped.append(f"línea {line}: fecha ilegible '{values['fecha']}'")
                continue

            situation = clean(values["evento"])
            ids, custom = parse_emotions(values["emocion"], catalog)
            if not ids and not custom:
                skipped.append(f"línea {line}: sin emoción")
                continue
            if custom:
                unmatched_labels.update(part.strip() for part in custom.split(","))

            entries.append(
                {
                    "id": stable_id(entry_date, ",".join(ids) + (custom or ""), situation),
                    "date": entry_date.isoformat(),
                    "emotionIds": ids,
                    "customEmotion": custom,
                    "situation": situation,
                }
            )
    return entries


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
    help="Dónde escribir el JSON de precarga.",
)
@click.option(
    "-c",
    "--catalog",
    default=DEFAULT_CATALOG,
    show_default=str(DEFAULT_CATALOG.relative_to(REPO_ROOT)),
    type=click.Path(dir_okay=False, path_type=pathlib.Path),
    help="Catálogo contra el que se resuelven los nombres de emociones.",
)
@click.option(
    "--strict",
    is_flag=True,
    help="Falla si alguna fila no se pudo leer, en vez de omitirla.",
)
@click.option(
    "--check",
    is_flag=True,
    help="Solo lee y reporta; no escribe nada.",
)
def main(
    source: pathlib.Path | None,
    output: pathlib.Path,
    catalog: pathlib.Path,
    strict: bool,
    check: bool,
) -> None:
    """Convierte una bitácora en CSV al JSON que la app precarga al primer arranque.

    SOURCE es el CSV a importar; por omisión ~/Downloads/Bitácora emociones.csv.
    """
    source = source or DEFAULT_SOURCE
    if not source.exists():
        raise click.ClickException(f"no encuentro el CSV: {source}")

    wheel = load_catalog(catalog)
    repairs: list[str] = []
    skipped: list[str] = []
    unmatched_labels: set[str] = set()

    entries = read_entries(source, wheel, repairs, skipped, unmatched_labels)
    if not entries:
        raise click.ClickException(f"{source}: no pude leer ninguna fila")

    entries.sort(key=lambda entry: entry["date"])
    ids = [entry["id"] for entry in entries]
    if len(set(ids)) != len(ids):
        raise click.ClickException("dos filas son idénticas y colisionan en el mismo id")

    first = date.fromisoformat(entries[0]["date"])
    last = date.fromisoformat(entries[-1]["date"])
    multi = sum(1 for e in entries if len(e["emotionIds"]) > 1 or e["customEmotion"])

    click.echo(f"{source}: {len(entries)} registros, {first} .. {last}")
    click.echo(f"  {multi} con más de una emoción o con texto libre")
    if unmatched_labels:
        click.echo(
            f"  fuera de la rueda ({len(unmatched_labels)}): {sorted(unmatched_labels)}"
        )
    for repair in repairs:
        click.echo(f"  reparado: {repair}")
    for problem in skipped:
        click.echo(f"  omitido: {problem}", err=True)

    if (last - first).days > SUSPICIOUS_SPAN_DAYS:
        click.echo(
            f"  aviso: la bitácora abarca {(last - first).days} días. Si no es lo que "
            f"esperabas, revisa los años; este script ya no los corrige solo.",
            err=True,
        )

    if skipped and strict:
        raise click.ClickException(f"--strict: {len(skipped)} filas sin leer")

    if check:
        click.echo("--check: no escribí nada")
        return

    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        json.dumps({"version": 1, "entries": entries}, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    click.echo(f"escrito {output}")


if __name__ == "__main__":
    main()
