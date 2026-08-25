#!/usr/bin/env python3
"""Convert a hand-kept CSV journal into app/src/main/assets/journal_seed.json.

The source is messy in ways the app has to survive: header names carry trailing
spaces, some rows name several emotions separated by slashes, a few dates have a
doubled slash, and some labels carry stray asterisks. Every repair is reported on
stdout so nothing is silently rewritten, and dates are never moved to a different
year — a row dated 2029 is imported as 2029 and merely flagged.

Emotion names that exist on the wheel become ids; the rest are kept verbatim as a
free-text emotion so no entry is lost.

Expected columns (matched ignoring case, accents and stray spaces; these are the
Spanish headers the app itself reads and writes):

    fecha    dd/mm/yyyy
    emoción  one or more names, separated by "/" or ","
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

COLUMNS = ("date", "emotion", "event")

# The journal file is the user's own, and its headers are Spanish. English ones are
# accepted as well so an exported-and-renamed file still works.
COLUMN_ALIASES = {"fecha": "date", "emocion": "emotion", "evento": "event"}

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
        repairs.append(f"line {line}: date '{text}' -> '{normalized}' (doubled slash)")

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
            f"no catalog at {path}; run tools/build_catalog.py first"
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
        headers = {}
        for name in reader.fieldnames or []:
            key = strip_accents(name)
            headers[COLUMN_ALIASES.get(key, key)] = name
        missing = [column for column in COLUMNS if column not in headers]
        if missing:
            raise click.ClickException(
                f"{source}: missing columns {missing}; found {reader.fieldnames}"
            )

        for line, raw in enumerate(reader, start=2):
            values = {column: (raw.get(headers[column]) or "").strip() for column in COLUMNS}
            if not any(values.values()):
                continue

            entry_date = parse_date(values["date"], line, repairs)
            if entry_date is None:
                skipped.append(f"line {line}: unreadable date '{values['date']}'")
                continue

            situation = clean(values["event"])
            ids, custom = parse_emotions(values["emotion"], catalog)
            if not ids and not custom:
                skipped.append(f"line {line}: no emotion")
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
    help="Where to write the seed JSON.",
)
@click.option(
    "-c",
    "--catalog",
    default=DEFAULT_CATALOG,
    show_default=str(DEFAULT_CATALOG.relative_to(REPO_ROOT)),
    type=click.Path(dir_okay=False, path_type=pathlib.Path),
    help="Catalog the emotion names are resolved against.",
)
@click.option("--strict", is_flag=True, help="Fail on unreadable rows instead of skipping them.")
@click.option("--check", is_flag=True, help="Read and report without writing anything.")
def main(
    source: pathlib.Path | None,
    output: pathlib.Path,
    catalog: pathlib.Path,
    strict: bool,
    check: bool,
) -> None:
    """Turn a CSV journal into the seed the app loads on first run.

    SOURCE is the CSV to import; defaults to ~/Downloads/Bitácora emociones.csv.
    """
    source = source or DEFAULT_SOURCE
    if not source.exists():
        raise click.ClickException(f"no such CSV: {source}")

    wheel = load_catalog(catalog)
    repairs: list[str] = []
    skipped: list[str] = []
    unmatched_labels: set[str] = set()

    entries = read_entries(source, wheel, repairs, skipped, unmatched_labels)
    if not entries:
        raise click.ClickException(f"{source}: could not read a single row")

    entries.sort(key=lambda entry: entry["date"])
    ids = [entry["id"] for entry in entries]
    if len(set(ids)) != len(ids):
        raise click.ClickException("two rows are identical and collide on the same id")

    first = date.fromisoformat(entries[0]["date"])
    last = date.fromisoformat(entries[-1]["date"])
    multi = sum(1 for e in entries if len(e["emotionIds"]) > 1 or e["customEmotion"])

    click.echo(f"{source}: {len(entries)} entries, {first} .. {last}")
    click.echo(f"  {multi} with more than one emotion or with free text")
    if unmatched_labels:
        click.echo(f"  not on the wheel ({len(unmatched_labels)}): {sorted(unmatched_labels)}")
    for repair in repairs:
        click.echo(f"  repaired: {repair}")
    for problem in skipped:
        click.echo(f"  skipped: {problem}", err=True)

    if (last - first).days > SUSPICIOUS_SPAN_DAYS:
        click.echo(
            f"  warning: the journal spans {(last - first).days} days. If that is not "
            f"what you expected, check the years; this script no longer fixes them.",
            err=True,
        )

    if skipped and strict:
        raise click.ClickException(f"--strict: {len(skipped)} rows went unread")

    if check:
        click.echo("--check: nothing written")
        return

    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        json.dumps({"version": 1, "entries": entries}, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    click.echo(f"wrote {output}")


if __name__ == "__main__":
    main()
