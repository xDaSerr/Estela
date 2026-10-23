@echo off
rem Genera Estela.exe con su icono. Solo hace falta despues de cambiar app.py o actualizador.py
rem (los cambios en ui\index.html se ven sin recompilar).
cd /d "%~dp0"
taskkill /im Estela.exe /f >nul 2>&1
python build.py onefile
if errorlevel 1 (
  echo.
  echo Hubo un error al compilar.
  pause
  exit /b 1
)
echo.
echo Listo: Estela.exe actualizado.
