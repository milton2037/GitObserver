# GitObserver

Aplicación de escritorio para revisar, con uno o varios tokens de GitHub, qué colaboradores han contribuido en los repositorios de una cuenta y quién subió cambios en una fecha y hora concretas.

## Qué puedes hacer

- Guardar varios tokens, uno por cuenta, y cambiar de cuenta activa.
- Ver los repositorios a los que cada token tiene acceso.
- Consultar los commits acumulados de cada colaborador, por repositorio.
- Filtrar la actividad por fecha, hora, repositorio y colaborador.
- Consultar una sola cuenta o todas las cuentas configuradas.
- Crear issues, comentarlos, cerrarlos o reabrirlos en uno o varios repositorios.
- Revisar pull requests: comentar, pedir cambios o aprobar, con instrucciones para el autor.

Las consultas usan la rama predeterminada de cada repositorio. Un repositorio visible por dos tokens se consulta una sola vez.

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
