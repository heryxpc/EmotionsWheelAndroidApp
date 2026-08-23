# Respaldo en Firebase (opcional)

La app funciona completa sin esto. Todo se guarda en el teléfono con Room; Firebase
solo agrega una copia en la nube por si pierdes o cambias de dispositivo.

Mientras no exista `app/google-services.json`, el plugin de Google Services no se
aplica, `BuildConfig.FIREBASE_CONFIGURED` queda en `false` y la pantalla de Ajustes
muestra el interruptor de respaldo deshabilitado. El proyecto compila igual.

## Pasos

1. Entra a <https://console.firebase.google.com> y crea un proyecto
   (por ejemplo `ruleta-emociones`). No necesitas Google Analytics.

2. Dentro del proyecto, **Agregar app → Android**:
   - Nombre del paquete: `com.emotionwheel.app`
   - Apodo y SHA-1: puedes dejarlos vacíos (la autenticación anónima no los pide).

3. Descarga `google-services.json` y colócalo en:

   ```
   app/google-services.json
   ```

   Ya está en `.gitignore` — no lo subas al repositorio.

4. En la consola, **Compilación → Authentication → Sign-in method**, habilita
   **Anónimo**. Con esto el teléfono obtiene un `uid` estable sin que tengas que
   crear cuenta ni contraseña.

5. En **Compilación → Firestore Database**, crea la base de datos en modo producción
   y elige la región más cercana (`nam5` o `us-central1` sirven).

6. Copia el contenido de [`firebase/firestore.rules`](../firebase/firestore.rules) en
   **Firestore → Reglas** y publica. Las reglas restringen cada documento al `uid` de
   su dueño; sin ellas cualquiera podría leer tu bitácora.

7. Recompila:

   ```
   ./gradlew :app:assembleDebug
   ```

   Ahora Ajustes deja activar **Respaldar en la nube** y aparece **Sincronizar ahora**.

## Cómo sincroniza

- Cada escritura local marca el registro como `dirty`.
- Al sincronizar, sube en lotes todo lo pendiente y luego baja lo remoto.
- Si un registro existe en los dos lados, gana el de `updatedAt` más reciente.
- Los ids son los mismos en Room y en Firestore, así que sincronizar dos veces no
  duplica nada.

Es una estrategia deliberadamente simple, adecuada para una bitácora de un solo
usuario. No resuelve edición concurrente desde dos teléfonos a la vez.

## Estructura en Firestore

```
users/{uid}/journalEntries/{entryId}
    date          number   (día como epoch day)
    emotionIds    string[] (ids de la ruleta, ej. ["impotencia", "exasperacion"])
    customEmotion string?  (emoción escrita a mano, ej. "vergüenza")
    situation     string
    createdAt     number
    updatedAt     number
```
