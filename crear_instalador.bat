@echo off
rem Genera el instalador Instalador\Estela-Setup-<version>.exe para cualquier PC con Windows 10/11.
rem La version se cambia en estela.iss (MyAppVersion).
cd /d "%~dp0"

echo [1/2] Empaquetando la app con PyInstaller...
python build.py onedir
if errorlevel 1 goto error

echo [2/2] Creando el instalador con Inno Setup...
"%LOCALAPPDATA%\Programs\Inno Setup 6\ISCC.exe" /Q estela.iss
if errorlevel 1 goto error

echo.
echo Listo: el instalador esta en la carpeta Instalador
explorer Instalador
exit /b 0

:error
echo.
echo Hubo un error al crear el instalador.
pause
exit /b 1
