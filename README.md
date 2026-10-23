<p align="center">
  <img src="ui/icon.png" width="110" alt="Estela">
</p>

<h1 align="center">Estela ✦</h1>

<p align="center">
  Descargador de videos de YouTube con fines educativos para Windows,<br>
  con Hoshi, tu asistente bajo un cielo de estrellas y pétalos de sakura.
</p>

<p align="center">
  <a href="https://github.com/xDaSerr/Estela/releases/latest"><b>⬇️ Descargar la última versión</b></a>
</p>

---

## Instalación

1. Ve a [Releases](https://github.com/xDaSerr/Estela/releases/latest) y descarga `Estela-Setup-x.x.x.exe`.
2. Ábrelo y sigue el asistente. No necesita permisos de administrador.
3. Si Windows muestra **"Windows protegió su PC"**, pulsa *Más información → Ejecutar de todas formas*
   (aparece porque el instalador no tiene firma digital de pago).

Requisitos: Windows 10 u 11 de 64 bits.

## Funciones

- 🎬 Video en la mejor calidad (MP4), 📺 720p o 🎧 solo audio (MP3).
- Vista previa con miniatura, canal, duración y vistas.
- Indica si el video tiene licencia **Creative Commons**.
- **Se mantiene al día sola**: al abrirse actualiza yt-dlp (el motor de descargas),
  sus dependencias y Deno, para seguir funcionando cuando YouTube cambia algo.
  Si una actualización falla, vuelve a la versión incluida.

## Uso responsable

Estela está pensada para fines educativos. Descarga solo contenido que tengas permiso
de descargar: tus propios videos, material con licencia Creative Commons o con autorización
del autor. Respeta los derechos de autor y los Términos de Servicio de YouTube.

## Desarrollo

Necesitas Python 3.13 en Windows.

```bash
pip install -r requirements.txt
python app.py
```

| Archivo | Qué contiene |
|---|---|
| `app.py` | Ventana, búsqueda y descargas |
| `actualizador.py` | Actualización automática de yt-dlp, sus dependencias y Deno |
| `ui/index.html` | Toda la interfaz (diseño, Hoshi, animaciones) |
| `build.py` | Configuración de PyInstaller |
| `estela.iss` | Configuración del instalador (Inno Setup) |

**Crear el instalador:** instala [Inno Setup 6](https://jrsoftware.org/isinfo.php), sube la versión en
`estela.iss` (`MyAppVersion`) y ejecuta `crear_instalador.bat`. El resultado queda en `Instalador/`.

## Créditos

Hecho con [yt-dlp](https://github.com/yt-dlp/yt-dlp), [pywebview](https://pywebview.flowrl.com/),
[FFmpeg](https://ffmpeg.org/) y [Deno](https://deno.com/).
