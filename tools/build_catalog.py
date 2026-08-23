#!/usr/bin/env python3
"""Generate app/src/main/assets/emotions.json from the wheel taxonomy below.

The wheel has six families of 60 degrees each, laid out clockwise from the top.
Every family holds one core emotion plus a middle ring and an outer ring of seven
emotions each, also listed clockwise. Labels and definitions are Spanish content;
everything else is structure.

Definitions are short on purpose: they exist to help pick between siblings of the
same family, not to be exhaustive. They lean on the Diccionario de la lengua
espanola (RAE).

Usage: python3 tools/build_catalog.py
"""

from __future__ import annotations

import json
import pathlib
import sys
import unicodedata

# family -> (core, middle ring clockwise, outer ring clockwise)
WHEEL = {
    "SURPRISE": {
        "core": (
            "sorpresa",
            "Alteración del ánimo por algo imprevisto. Todavía no es buena ni mala: solo rompe lo que esperabas.",
        ),
        "middle": [
            ("conmoción", "Sacudida fuerte del ánimo ante una noticia o un suceso que te deja sin reacción."),
            ("desorientación", "Perder el rumbo: ya no sabes dónde estás parado ni qué sigue."),
            ("deslumbramiento", "Quedar cegado por algo tan brillante o admirable que no ves nada más."),
            ("desconcierto", "No entender lo que pasa. Las cosas no salieron como las tenías previstas."),
            ("incredulidad", "No poder creer lo que ves o lo que oyes, aunque tengas la prueba delante."),
            ("estupefacción", "Pasmo que te deja mudo e inmóvil. Es la sorpresa llevada al extremo."),
            ("expectativa", "Esperar algo que va a pasar, con la atención puesta en ello y el ánimo suspendido."),
        ],
        "outer": [
            ("intriga", "Inquietud curiosa por algo que se te oculta y quieres averiguar."),
            ("asombro", "Admiración grande ante algo que excede lo que creías posible."),
            ("fascinación", "Atracción irresistible: algo te atrapa la atención y no quieres soltarlo."),
            ("curiosidad", "Ganas de saber o averiguar. Es la sorpresa que abre puertas en vez de paralizar."),
            ("admiración", "Reconocer con gusto el valor o el mérito de algo o de alguien."),
            ("extrañeza", "Sensación de rareza ante algo que no encaja con lo habitual."),
            ("entusiasmo", "Exaltación alegre que te impulsa a hacer algo con ganas."),
        ],
    },
    "ANGER": {
        "core": (
            "enojo",
            "Movimiento del ánimo contra algo que consideras injusto, molesto o que te estorba.",
        ),
        "middle": [
            ("frustración", "Malestar por no lograr lo que te habías propuesto o porque algo te lo impide."),
            ("resentimiento", "Enojo guardado por un agravio que no se cerró. Sigue vivo tiempo después."),
            ("indignación", "Enojo ante algo injusto o indigno, más allá de si te afecta a ti."),
            ("impaciencia", "Falta de paciencia: la espera se te vuelve insoportable."),
            ("hostilidad", "Disposición a atacar o a tratar a alguien como enemigo."),
            ("impotencia", "Rabia de no poder hacer nada para cambiar lo que te afecta."),
            ("exasperación", "Enojo llevado al límite por algo que se repite y no cede."),
        ],
        "outer": [
            ("rabia", "Enojo intenso y descontrolado, con ganas de descargarlo."),
            ("furia", "Rabia desatada que arrasa con el juicio."),
            ("ira", "Enojo violento que empuja a la agresión."),
            ("odio", "Aversión profunda y sostenida hacia alguien, con deseo de su mal."),
            ("disgusto", "Molestia o desagrado por algo que te contraría."),
            ("irritación", "Enfado ligero pero persistente, como una piedra en el zapato."),
            ("rencor", "Resentimiento arraigado que se guarda y no se perdona."),
        ],
    },
    "JOY": {
        "core": (
            "alegría",
            "Sentimiento grato que nace de algo bueno y se nota por fuera.",
        ),
        "middle": [
            ("esperanza", "Confianza en que lo que deseas todavía puede llegar a ocurrir."),
            ("optimismo", "Ver el lado favorable de las cosas y esperar que salgan bien."),
            ("conexión", "Sentirte unido a alguien y correspondido en ese vínculo."),
            ("satisfacción", "Gusto por haber cumplido algo o porque se colmó lo que querías."),
            ("vitalidad", "Energía y empuje para hacer y para sostener la actividad."),
            ("inspiración", "Impulso creador: de pronto sabes qué hacer y con qué ánimo."),
            ("ternura", "Cariño suave y delicado hacia alguien a quien quieres cuidar."),
        ],
        "outer": [
            ("paz", "Ausencia de conflicto por dentro y por fuera. Nada te está peleando."),
            ("calma", "Quietud del ánimo: la agitación bajó."),
            ("gratitud", "Reconocimiento y aprecio por un bien recibido."),
            ("gozo", "Alegría honda y sostenida, no un simple buen rato."),
            ("alivio", "Descanso que sigue al fin de una molestia, un dolor o una espera."),
            ("dicha", "Felicidad afortunada: la sensación de que la vida te salió bien."),
            ("serenidad", "Calma firme que se sostiene aunque alrededor haya movimiento."),
        ],
    },
    "FEAR": {
        "core": (
            "miedo",
            "Angustia ante un peligro real o imaginado, presente o por venir.",
        ),
        "middle": [
            ("inquietud", "Desasosiego leve: algo te mueve el ánimo y no te deja estar quieto."),
            ("desesperación", "Pérdida total de la esperanza: el miedo sin salida a la vista."),
            ("desconfianza", "Sospecha de que algo o alguien va a fallarte."),
            ("vulnerabilidad", "Sentirte expuesto y sin defensa ante lo que pueda venir."),
            ("inseguridad", "Falta de confianza en ti o en lo que sostiene la situación."),
            ("aprensión", "Recelo anticipado por algo que va a pasar y temes que salga mal."),
            ("ansiedad", "Agitación por un peligro difuso, muchas veces sin objeto claro."),
        ],
        "outer": [
            ("pánico", "Miedo súbito y arrollador que anula la capacidad de pensar."),
            ("recelo", "Desconfianza sorda que te mantiene en guardia."),
            ("parálisis", "Quedarte inmóvil por miedo, sin poder actuar ni decidir."),
            ("pavor", "Miedo con espanto, que eriza el cuerpo."),
            ("temor", "Miedo moderado y concreto: sabes exactamente qué te preocupa."),
            ("terror", "Miedo extremo que domina el cuerpo entero."),
            ("angustia", "Aflicción apretada en el pecho, con miedo difuso y pena mezclados."),
        ],
    },
    "SADNESS": {
        "core": (
            "tristeza",
            "Pesar del ánimo por una pérdida o por algo que duele.",
        ),
        "middle": [
            ("nostalgia", "Pena dulce por algo o alguien que quedó atrás y extrañas."),
            ("desilusión", "Pérdida de la ilusión que habías puesto en algo o en alguien."),
            ("desamparo", "Sentirte solo y sin protección ni ayuda de nadie."),
            ("desesperanza", "Convicción de que ya no va a mejorar: la esperanza se apagó."),
            ("pesadumbre", "Peso del ánimo por algo que te aflige y no se va."),
            ("melancolía", "Tristeza vaga y sostenida, sin causa que puedas señalar."),
            ("desolación", "Tristeza extrema, con sensación de haberlo perdido todo."),
        ],
        "outer": [
            ("desdicha", "Desgracia: la sensación de que la suerte está en tu contra."),
            ("decepción", "Pesar porque el resultado no correspondió a lo prometido o merecido."),
            ("vacío", "Ausencia de sentido o de contenido: nada te llena ni te importa."),
            ("amargura", "Tristeza con resabio de rencor por lo que la vida te quitó."),
            ("desánimo", "Falta de ánimo para emprender o para continuar."),
            ("apatía", "Indiferencia: nada te mueve, ni lo bueno ni lo malo."),
            ("soledad", "Pena de estar sin compañía o de no sentirte acompañado."),
        ],
    },
    "DISGUST": {
        "core": (
            "asco",
            "Repugnancia ante algo que te resulta intolerable y te hace apartarte.",
        ),
        "middle": [
            ("repugnancia", "Aversión física fuerte: el cuerpo entero se niega."),
            ("saturación", "Hartazgo por exceso: ya no cabe más de lo mismo."),
            ("incomodidad", "Malestar leve: algo no está bien y no te deja acomodarte."),
            ("disconformidad", "No estar de acuerdo con cómo son o cómo se hicieron las cosas."),
            ("desagrado", "Disgusto suave por algo que sencillamente no te gusta."),
            ("repulsión", "Rechazo que te empuja a alejarte de inmediato."),
            ("desprecio", "Tener a alguien o algo por indigno de tu estima o de tu atención."),
        ],
        "outer": [
            ("aversión", "Rechazo arraigado que te hace evitar algo por sistema."),
            ("rechazo", "Negarte a admitir o a aceptar algo."),
            ("fastidio", "Molestia menor pero insistente que te agota la paciencia."),
            ("desdén", "Indiferencia despectiva: ni siquiera merece tu atención."),
            ("repudio", "Rechazo declarado y público de algo que condenas."),
            ("antipatía", "Aversión instintiva hacia alguien, sin motivo que puedas explicar."),
            ("hastío", "Cansancio profundo de algo que se repite hasta el hartazgo."),
        ],
    },
}

FAMILY_ORDER = ["SURPRISE", "ANGER", "JOY", "FEAR", "SADNESS", "DISGUST"]

CORE, MIDDLE, OUTER = 1, 2, 3


def slugify(label: str) -> str:
    """Accent-free, lowercase id: 'desilusión' -> 'desilusion'."""
    decomposed = unicodedata.normalize("NFD", label.strip().lower())
    return "".join(c for c in decomposed if unicodedata.category(c) != "Mn")


def build() -> list[dict]:
    emotions: list[dict] = []
    for family in FAMILY_ORDER:
        rings = WHEEL[family]
        label, definition = rings["core"]
        emotions.append(
            {
                "id": slugify(label),
                "label": label,
                "family": family,
                "level": CORE,
                "index": 0,
                "definition": definition,
            }
        )
        for level, key in ((MIDDLE, "middle"), (OUTER, "outer")):
            ring = rings[key]
            if len(ring) != 7:
                sys.exit(f"{family}/{key} has {len(ring)} entries, expected 7")
            for index, (label, definition) in enumerate(ring):
                emotions.append(
                    {
                        "id": slugify(label),
                        "label": label,
                        "family": family,
                        "level": level,
                        "index": index,
                        "definition": definition,
                    }
                )
    return emotions


def main() -> None:
    root = pathlib.Path(__file__).resolve().parent.parent
    out = root / "app" / "src" / "main" / "assets" / "emotions.json"

    emotions = build()

    if len(emotions) != 90:
        sys.exit(f"expected 90 emotions, built {len(emotions)}")
    ids = [e["id"] for e in emotions]
    duplicates = {i for i in ids if ids.count(i) > 1}
    if duplicates:
        sys.exit(f"duplicate ids: {sorted(duplicates)}")
    too_long = [e["id"] for e in emotions if len(e["definition"]) > 200]
    if too_long:
        sys.exit(f"definitions over 200 chars: {too_long}")

    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(
        json.dumps({"version": 1, "emotions": emotions}, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )

    print(f"wrote {out.relative_to(root)}")
    print(f"  {len(emotions)} emotions across {len(FAMILY_ORDER)} families")
    print(f"  longest definition: {max(len(e['definition']) for e in emotions)} chars")


if __name__ == "__main__":
    main()
