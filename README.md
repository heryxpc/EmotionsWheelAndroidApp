# Ruleta de Emociones

App Android para nombrar lo que sientes y llevar una bitácora emocional.

Una rueda interactiva de 90 emociones —6 familias, 3 anillos— donde al tocar cualquier
palabra aparece su definición corta. De ahí se pasa a registrar la fecha, la emoción y
la situación que la provocó, y a consultar la bitácora completa.

La taxonomía y los colores son propiedad de Casa [Therapévo](https://www.casatherapevo.com/), diseñados como material de apoyo; las definiciones se apoyan en el Diccionario de la lengua española (RAE).

## Por qué una rueda

La estructura del centro viene de la **Teoría de las Emociones Básicas de Paul Ekman**,
que sostiene que existe un conjunto reducido de emociones universales, reconocibles por
su expresión facial en cualquier cultura. Las seis familias del núcleo —alegría tristeza, enojo, miedo, asco y sorpresa— son ese conjunto.

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

La ruleta no reproduce literalmente la rueda de Plutchik, quien trabaja con ocho emociones primarias —incluye *confianza* y *anticipación*— y ordena sus
anillos por intensidad decreciente hacia afuera. Aquí las familias son las seis de Ekman
y los dos anillos exteriores son matices, no una escala: en la familia *enojo*, *furia*
está en el anillo exterior y es más intensa que *enojo*, no menos.

## Gestos de la ruleta

| Gesto | Qué hace |
|---|---|
| Tocar | Elige la emoción y muestra su definición |
| Arrastrar con un dedo | Gira la rueda (en reposo) o la desplaza (acercada) |
| Pellizcar con dos dedos | Acerca hasta 4x y desplaza; el anillo medio se lee más claro, al tener palabras más largas |
| Botón ⤢ | Vuelve la rueda a su posición y tamaño original |

El zoom se aplica al dibujar, no sobre un mapa de bits ya trazado, así que las letras se
vuelven a componer al tamaño nuevo y siguen nítidas hasta el máximo 4x.

## Requisitos desarrollo

- Android Studio 2026.1 o posterior (incluido el JDK)
- SDK de Android 37, `minSdk` 26
- Python 3.14 o superior (para los scripts de catalogo y emociones iniciales)


## Correr la app

Abre la carpeta en Android Studio, deja que sincronice Gradle y presiona **Run**.

Desde la terminal:

```bash
export JAVA_HOME="<PATH_ANDROID_STUDIO>/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Pruebas

```bash
./gradlew :app:testDebugUnitTest
```

## Estructura

```
app/src/main/
├── assets/emotions.json        # las 90 emociones y sus definiciones, se genera con `emotions-wheel.csv`y `build_catalog.py`
├── assets/journal_seed.json    # bitácora precargada creada con `journal-sample.csv`y `build_seed.csv`
├── res/values/strings.xml      # todos los textos de UI
└── java/com/emotionwheel/app/
    ├── data/                   # Room, catálogo, CSV, respaldo en la nube
    └── ui/                     # ruleta, registro, bitácora, ajustes
data/
├── emotions-wheel.csv          # las 90 emociones y definiciones editables
└── journal-sample.csv          # bitácora de ejemplo
tools/
├── build_catalog.py            # emotions-wheel.csv -> emotions.json
├── build_seed.py               # bitácora en CSV (e.g. journal-sample.csv) -> journal_seed.json
└── requirements.txt
```

El contenido —emociones, definiciones, textos de UI— se encuentra en español. Se puede modificar actualizando `app/src/main/assets` y `app/src/main/res/values/strings.xml`.

## Datos

**Almacenamiento local (Room).** Toda la bitácora vive en SQLite en el teléfono.
Funciona sin internet y sin cuentas.

**Precarga.** Al primer arranque se cargan los registros de
`app/src/main/assets/journal_seed.json`. El que viene en el repositorio se genera desde
`data/journal-sample.csv` y es **ficticio**: sirve para ver la app con contenido y para
documentar el formato.

> ⚠️ **Tu bitácora es privada.** `tools/build_seed.py` escribe por omisión sobre
> `journal_seed.json`, que sí está versionado. No subas este archivo como cambio en el repositorio. Usa `-o` para cambiar el archivo de salida:
>
> ```bash
> .venv/bin/python tools/build_seed.py mi-bitacora.csv -o /ruta/fuera/del/repo.json
> ```
>
> Revisa `git status` antes de confirmar. Una vez subido, sacarlo exige reescribir la historia.

**CSV.** La bitácora se exporta y se importa en el formato
`fecha,emoción,evento`, Importar compara por contenido (fecha + emociones + situación), no por id, así que reimportar un
archivo que exportaste desde la app no duplica nada.

**Respaldo en la nube (opcional).** Ver [docs/firebase-setup.md](docs/firebase-setup.md).
Sin `google-services.json` el proyecto compila y corre igual, con el respaldo apagado.

## Regenerar los datos

Las dos fuentes son CSV, para poder editarlas en una hoja de cálculo. Los scripts que
las convierten usan [click](https://click.palletsprojects.com/), así que necesitan un
entorno virtual:

```bash
python3 -m venv .venv
.venv/bin/pip install -r tools/requirements.txt
```

**Catálogo de la rueda** — `data/emotions-wheel.csv`, con columnas
`family,ring,position,emotion,definition`. La familia se nombra por su emoción del centro
(*sorpresa*, *enojo*, *alegría*, *miedo*, *tristeza*, *asco*); el anillo es 1 para el
núcleo, 2 para el medio y 3 para el exterior; la posición va de 0 a 6 en sentido horario.

```bash
.venv/bin/python tools/build_catalog.py                  # usa data/emotions-wheel.csv
.venv/bin/python tools/build_catalog.py otra-rueda.csv
.venv/bin/python tools/build_catalog.py --check          # solo valida
```

Valida que cada familia tenga un núcleo y dos anillos completos, que no haya ids
repetidos y que ninguna definición pase de 200 caracteres. Si algo falla, dice en qué
línea.

**Bitácora de precarga** — cualquier CSV con columnas `fecha,emoción,evento`.

```bash
.venv/bin/python tools/build_seed.py "ruta/a/BitacoraEmociones.csv"
.venv/bin/python tools/build_seed.py bitacora.csv --check
.venv/bin/python tools/build_seed.py bitacora.csv --strict   # falla si omite filas
```

Repara barras dobles en las fechas, separa las filas con varias emociones y guarda como
texto libre lo que la rueda no nombra. **No corrige años**: una fila fechada en 2029 se
importa en 2029, igual que hace el importador de la app; si el rango de fechas resulta
sospechosamente amplio, lo avisa para que revises el CSV.

Los dos aceptan `-h` para ver todas las opciones.
