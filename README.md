# GitObserver

Aplicación de escritorio para revisar, con uno o varios tokens de GitHub, qué colaboradores han contribuido en los repositorios de una cuenta y quién subió cambios en una fecha y hora concretas.

## Qué puedes hacer

- Guardar varios tokens, uno por cuenta, y cambiar de cuenta activa.
- Proteger el acceso a la aplicación con un usuario y contraseña opcionales.
- Ver los repositorios a los que cada token tiene acceso.
- Consultar los commits acumulados de cada colaborador, por repositorio.
- Filtrar la actividad por fecha, hora, repositorio y colaborador.
- Consultar una sola cuenta o todas las cuentas configuradas.
- Crear issues, comentarlos, cerrarlos o reabrirlos en uno o varios repositorios.
- Revisar pull requests: comentar, pedir cambios o aprobar, con instrucciones para el autor.

Las consultas usan la rama predeterminada de cada repositorio. Un repositorio visible por dos tokens se consulta una sola vez.

## Seguridad y acceso opcional

En la sección **Configuración** puedes configurar de manera opcional un usuario y una contraseña:

- **Inicio de sesión protegido:** Si está habilitada, GitObserver pedirá el usuario y la contraseña cada vez que se inicie la aplicación o se pulse **Bloquear aplicación** en la barra lateral.
- **Seguridad:** Las credenciales se almacenan localmente con hash derivado mediante PBKDF2 (HmacSHA256 con 65.536 iteraciones y sal aleatoria criptográfica).
- **Opcional:** Si no se configura o se desactiva, la aplicación inicia directamente como de costumbre.
- **Gestión:** Puedes cambiar las credenciales o desactivar la protección en cualquier momento desde la pantalla de Configuración.

## Requisitos

- JDK 21
- Conexión a internet la primera vez, para descargar Maven y las dependencias

## Ejecutar

En PowerShell, desde esta carpeta. Si `JAVA_HOME` apunta a otra versión, cámbialo a JDK 21 solo en esa ventana:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
.\mvnw.cmd javafx:run
```

En el editor, ejecuta `com.gitobserver.Launcher`. Si lanzas `GitObserverApp` directamente, el JDK responde que faltan los componentes de JavaFX.

## Distribución en Windows (.exe) y Actualizaciones desde GitHub

GitObserver puede distribuirse como aplicación de escritorio nativa para que tus usuarios la instalen o ejecuten sin necesidad de instalar Java, Maven ni configurar variables de entorno.

### 1. Empaquetar el ejecutable en tu equipo

Para compilar y generar la distribución ejecutable para Windows en tu equipo, ejecuta:

```powershell
.\package-windows.ps1
```

El script genera en `target\dist\`:
- **Ejecutable nativo:** `target\dist\GitObserver\GitObserver.exe` (con runtime Java embebido y dependencias).
- **Paquete portable:** `target\dist\GitObserver-portable-0.1.0.zip` listo para descomprimir y usar en cualquier PC con Windows.

### 2. Publicación automática con GitHub Actions

El repositorio cuenta con integración continua en `.github/workflows/release.yml`. Para publicar una nueva versión con instalador:

1. Crea y sube una etiqueta de versión:
   ```powershell
   git tag v0.1.0
   git push origin v0.1.0
   ```
2. GitHub Actions compilará el proyecto en un entorno Windows con WiX Toolset, generará el instalador `GitObserver-Setup.exe` y creará automáticamente una **Release** en GitHub con el instalador adjunto.
3. Tus usuarios solo descargan el archivo `GitObserver-Setup.exe`, lo instalan con un clic y lo usan como cualquier programa de Windows.

### 3. Actualizaciones desde la aplicación

- GitObserver incluye un servicio de verificación de versiones ([UpdateService](file:///c:/Users/usuario/Documents/DEVELOPER/GitObserver/src/main/java/com/gitobserver/update/UpdateService.java)) que consulta GitHub Releases.
- Si publicas una nueva versión en GitHub, la aplicación muestra una alerta en la barra lateral y en la barra de estado.
- Desde **Créditos → Actualizaciones**, los usuarios pueden pulsar **Buscar actualizaciones** y **Descargar nueva versión** para obtener el instalador más reciente.

## Token de GitHub

1. En GitHub abre **Settings → Developer settings → Personal access tokens**.
2. Crea un token con permiso sobre los repositorios que quieras vigilar.
   - Token fino: **Contents: Read** para ver commits. Para issues, comentarios y revisiones, añade **Issues** y **Pull requests** en lectura y escritura.
   - Token clásico: alcance `repo` si necesitas repositorios privados, issues o pull requests.
3. En la aplicación abre **Configuración**, pulsa **Agregar cuenta** y pega el token.
4. Usa **Probar conexión** para confirmar el usuario antes de guardar.

Puedes repetir el alta con otro alias y otro token para una segunda cuenta.

Los tokens se guardan solo en este equipo, en:

`%USERPROFILE%\.gitobserver\accounts.json`

No los subas a un repositorio ni los compartas. Quien tenga el archivo puede leer los repositorios de esas cuentas.
