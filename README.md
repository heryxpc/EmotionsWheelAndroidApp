# Ruleta de Emociones

App Android para nombrar lo que sientes y llevar una bitácora emocional.

Una rueda interactiva de 90 emociones —6 familias, 3 anillos— donde al tocar cualquier
palabra aparece su definición corta. De ahí se pasa a registrar la fecha, la emoción y
la situación que la provocó, y a consultar la bitácora completa.

La taxonomía y los colores vienen de la rueda de emociones de Casa Therapévo; las
definiciones se apoyan en el Diccionario de la lengua española (RAE).

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
