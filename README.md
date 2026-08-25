# Ruleta de Emociones

App Android para nombrar lo que sientes y llevar una bitácora emocional.

Una rueda interactiva de 90 emociones —6 familias, 3 anillos— donde al tocar cualquier
palabra aparece su definición corta. De ahí se pasa a registrar la fecha, la emoción y
la situación que la provocó, y a consultar la bitácora completa.

La taxonomía y los colores vienen de la rueda de emociones de Casa Therapévo; las
definiciones se apoyan en el Diccionario de la lengua española (RAE).

## Por qué una rueda

La estructura del centro viene de la **Teoría de las Emociones Básicas de Paul Ekman**,
que sostiene que existe un conjunto reducido de emociones universales, reconocibles por
su expresión facial en cualquier cultura. Las seis familias del núcleo —alegría,
tristeza, enojo, miedo, asco y sorpresa— son justamente ese conjunto.

La forma circular viene de la **Rueda de las Emociones de Robert Plutchik**, que dispone
las emociones en un círculo donde unas pocas primarias se despliegan hacia afuera en
estados afectivos más complejos: por variación de intensidad (de *irritación* a *ira* a
*furia*) o por combinación de dos primarias en lo que Plutchik llamó *díadas*. Dos de
ellas están en esta misma rueda: **asco + enojo = desprecio**, y **anticipación +
alegría = optimismo**.

El objetivo práctico es dar vocabulario. "Me siento mal" no distingue entre *desilusión*,
*impotencia* y *saturación*, y las tres piden respuestas distintas. Nombrar con precisión
lo que se siente —granularidad emocional— es lo que vuelve útil una bitácora: al releerla
aparecen patrones que un vocabulario grueso esconde.

Conviene aclarar que esta rueda no reproduce literalmente ninguno de los dos modelos.
Plutchik trabaja con ocho primarias —incluye *confianza* y *anticipación*— y ordena sus
anillos por intensidad decreciente hacia afuera. Aquí las familias son las seis de Ekman
y los dos anillos exteriores son matices, no una escala: en la familia *enojo*, *furia*
está en el anillo exterior y es más intensa que *enojo*, no menos.

## Gestos de la ruleta

| Gesto | Qué hace |
|---|---|
| Tocar | Elige la emoción y muestra su definición |
| Arrastrar con un dedo | Gira la rueda (en reposo) o la desplaza (acercada) |
| Pellizcar con dos dedos | Acerca hasta 4x y desplaza; el anillo medio es el que más lo agradece, porque tiene las palabras más largas |
| Botón ⤢ | Vuelve la rueda a su posición y tamaño original |

El zoom se aplica al dibujar, no sobre un mapa de bits ya trazado, así que las letras se
vuelven a componer al tamaño nuevo y siguen nítidas hasta el máximo.

## Requisitos

- Android Studio 2026.1 o posterior (trae el JDK que necesita el proyecto)
- SDK de Android 37, `minSdk` 26

## Correr la app

Abre la carpeta en Android Studio, deja que sincronice Gradle y presiona **Run**.

Desde la terminal:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Pruebas

```bash
./gradlew :app:testDebugUnitTest
```

22 pruebas que cubren la estructura del catálogo (6 familias × 15 emociones, ids
únicos), la aritmética polar con la que se detecta el toque en la rueda, y el lector de
CSV contra los casos raros del archivo original.

## Estructura

```
app/src/main/
├── assets/emotions.json        # las 90 emociones y sus definiciones
├── assets/journal_seed.json    # bitácora previa, precargada al primer arranque
├── res/values/strings.xml      # todos los textos de UI
└── java/com/emotionwheel/app/
    ├── data/                   # Room, catálogo, CSV, respaldo en la nube
    └── ui/                     # ruleta, registro, bitácora, ajustes
tools/
├── build_catalog.py            # genera emotions.json
└── build_seed.py               # convierte el CSV original a journal_seed.json
```

El código está en inglés; el contenido —emociones, definiciones, textos de UI— en
español.

## Datos

**Almacenamiento local (Room).** Toda la bitácora vive en SQLite en el teléfono.
Funciona sin internet y sin cuentas.

**Precarga.** Al primer arranque se cargan los 52 registros del CSV que llevabas a
mano. `tools/build_seed.py` los normaliza: corrige fechas con doble barra y años fuera
de la secuencia, separa las filas que nombran varias emociones, y guarda como texto
libre las cuatro que no están en la rueda (*vergüenza*, *pena*, *lástima*,
*indiferencia*).

**CSV.** La bitácora se exporta y se importa en el formato original
`fecha,emoción,evento`, así que abre en la misma hoja de cálculo de siempre. Importar
compara por contenido (fecha + emociones + situación), no por id, así que reimportar un
archivo que exportaste desde la app no duplica nada.

**Respaldo en la nube (opcional).** Ver [docs/firebase-setup.md](docs/firebase-setup.md).
Sin `google-services.json` el proyecto compila y corre igual, con el respaldo apagado.

## Regenerar los datos

```bash
python3 tools/build_catalog.py                       # emotions.json
python3 tools/build_seed.py "ruta/al/Bitácora emociones.csv"   # journal_seed.json
```
