# Tinta para Android

La app de Tinta para el móvil. Funciona sin conexión: guarda una copia de todo en el móvil y una cola con los cambios, y los envía al servicio cuando vuelve la red (ver `docs/api.md`, «Sincronización»).

## Compilar

No hace falta instalar el SDK de Android: todo corre en Docker con `compilar.sh`. La primera vez construye la imagen `tinta-android-build:local` (receta en `build.Dockerfile`) y guarda la caché de Gradle en el volumen `tinta-android-gradle`.

| Orden | Qué hace |
|---|---|
| `android/compilar.sh` | APK de depuración en `app/build/outputs/apk/debug/` |
| `android/compilar.sh test` | Pruebas unitarias |
| `android/compilar.sh recordRoborazziDebug` | Pruebas y capturas de las pantallas, en claro y oscuro, en `app/build/outputs/roborazzi/` |
| `TINTA_DEV_URL=http://127.0.0.1:8091 android/compilar.sh test` | Además, la prueba de sincronización y de arranque contra la copia de desarrollo (`scripts/dev-servidor.sh`) |

Para liberar espacio se puede borrar todo y el script lo recrea: `docker volume rm tinta-android-gradle` y `docker rmi tinta-android-build:local`.

## Firma

Android solo instala una actualización si va firmada con la misma clave que la versión instalada. **Si la clave se pierde, la app ya no se puede actualizar: habría que desinstalarla.**

- La clave vive en la torre, en `~/.config/tinta-android/`: `tinta.jks` (PKCS12, alias `tinta`) y `clave.txt` (su contraseña). Guarda una copia de las dos en Vaultwarden.
- En GitHub va en dos secretos del repo: `ANDROID_KEYSTORE` (el `.jks` en base64) y `ANDROID_KEYSTORE_PASSWORD`.
- Para firmar en local: `TINTA_KEYSTORE=~/.config/tinta-android/tinta.jks TINTA_KEYSTORE_PASSWORD="$(cat ~/.config/tinta-android/clave.txt)" android/compilar.sh assembleRelease`.
- El `.jks` nunca entra en el repo (`.gitignore`).

## Publicar una versión

1. Sube una etiqueta anotada con las novedades en el mensaje: `git tag -a v0.1.0 -m "Novedades…" && git push origin v0.1.0`.
2. El flujo `.github/workflows/android.yml` compila el APK firmado con ese número de versión y crea la Release con `tinta.apk` y `SHA256SUMS`.
3. La app lo ve sola: busca la última Release al abrirse (como mucho cada seis horas) y una vez al día en segundo plano. Comprueba la huella antes de instalar.

El número de versión de la app sale de la etiqueta; sin etiqueta, de `tintaVersion` en `gradle.properties`. El `versionCode` es `mayor·10000 + menor·100 + parche` (0.1.0 → 100), como en Atalaya, para que siempre crezca.
