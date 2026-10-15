# Script para compilar y empaquetar GitObserver en un ejecutable nativo para Windows (.exe)
# Requiere JDK 21 (incluye jpackage)

param (
    [string]$Version = "0.1.0",
    [string]$JdkPath = "C:\Program Files\Java\jdk-21"
)

$ErrorActionPreference = "Stop"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  Empaquetador de GitObserver para Windows v$Version       " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Configurar JAVA_HOME si existe la ruta de JDK 21
if (Test-Path $JdkPath) {
    $env:JAVA_HOME = $JdkPath
    Write-Host "[1/6] Configurado JAVA_HOME en $JdkPath" -ForegroundColor Green
} else {
    Write-Host "[1/6] Usando JAVA_HOME actual: $env:JAVA_HOME" -ForegroundColor Yellow
}

$jpackageExe = Join-Path $env:JAVA_HOME "bin\jpackage.exe"
if (-not (Test-Path $jpackageExe)) {
    throw "No se encontró jpackage.exe en $env:JAVA_HOME\bin. Asegúrate de usar JDK 21 o superior."
}

# 2. Limpieza de carpetas previas
Write-Host "[2/6] Limpiando carpetas de empaquetado..." -ForegroundColor Green
if (Test-Path "target\dist") { Remove-Item -Recurse -Force "target\dist" }
if (Test-Path "target\package-input") { Remove-Item -Recurse -Force "target\package-input" }
New-Item -ItemType Directory -Force -Path "target\package-input" | Out-Null
New-Item -ItemType Directory -Force -Path "target\dist" | Out-Null

# 3. Compilación con Maven
Write-Host "[3/6] Compilando y ejecutando pruebas unitarias..." -ForegroundColor Green
.\mvnw.cmd clean test
if ($LASTEXITCODE -ne 0) { throw "Fallaron las pruebas unitarias." }

Write-Host "[4/6] Generando archivo JAR y copiando dependencias..." -ForegroundColor Green
.\mvnw.cmd package -DskipTests
if ($LASTEXITCODE -ne 0) { throw "Falló la generación del JAR." }

.\mvnw.cmd dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target/package-input
if ($LASTEXITCODE -ne 0) { throw "Falló la copia de dependencias." }

Copy-Item "target\gitobserver-$Version.jar" "target\package-input\" -Force

# 4. Crear imagen nativa de la aplicación con jpackage
Write-Host "[5/6] Creando distribución ejecutable con runtime embebido (jpackage)..." -ForegroundColor Green

& $jpackageExe `
    --type app-image `
    --dest "target\dist" `
    --name "GitObserver" `
    --input "target\package-input" `
    --main-jar "gitobserver-$Version.jar" `
    --main-class "com.gitobserver.Launcher" `
    --app-version "$Version" `
    --vendor "Milton Andres Guerrero S." `
    --copyright "Copyright (c) 2026 Milton Andres Guerrero S." `
    --description "Monitor de GitHub para colaboradores y repositorios"

if ($LASTEXITCODE -ne 0) { throw "Error al ejecutar jpackage app-image." }

# 5. Comprimir paquete portable (.zip)
Write-Host "[6/6] Creando archivo comprimido portable (.zip)..." -ForegroundColor Green
$zipPath = "target\dist\GitObserver-portable-$Version.zip"
if (Test-Path $zipPath) { Remove-Item -Force $zipPath }
Compress-Archive -Path "target\dist\GitObserver\*" -DestinationPath $zipPath -Force

# 6. Intentar crear instalador (.exe) si WiX Toolset está presente
$hasWix = (Get-Command candle.exe -ErrorAction SilentlyContinue) -ne $null
if ($hasWix) {
    Write-Host "Generando instalador GitObserver-Setup-$Version.exe con WiX..." -ForegroundColor Cyan
    & $jpackageExe `
        --type exe `
        --dest "target\dist" `
        --name "GitObserver-Setup" `
        --app-image "target\dist\GitObserver" `
        --app-version "$Version" `
        --vendor "Milton Andres Guerrero S." `
        --win-dir-chooser `
        --win-menu `
        --win-shortcut
    Write-Host "Instalador generado en: target\dist\GitObserver-Setup-$Version.exe" -ForegroundColor Green
} else {
    Write-Host ""
    Write-Host "Nota sobre el instalador .exe:" -ForegroundColor Yellow
    Write-Host "  - Tu distribución lista para ejecutar está en: target\dist\GitObserver\GitObserver.exe" -ForegroundColor White
    Write-Host "  - Tu archivo zip portable está en: $zipPath" -ForegroundColor White
    Write-Host "  - El instalador .exe con asistente se genera automáticamente en GitHub Actions al subir un release." -ForegroundColor White
    Write-Host "  - Si deseas generar el instalador .exe localmente, instala WiX Toolset con: winget install WiX" -ForegroundColor DarkGray
}

Write-Host ""
Write-Host "==========================================================" -ForegroundColor Green
Write-Host "  ¡Empaquetado completado exitosamente!                  " -ForegroundColor Green
Write-Host "  Carpeta de salida: target\dist\GitObserver\             " -ForegroundColor Green
Write-Host "  Ejecutable: target\dist\GitObserver\GitObserver.exe    " -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
