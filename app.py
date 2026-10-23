"""Estela"""
import ctypes
import json
import os
import shutil
import subprocess
import sys
import threading
from pathlib import Path

import imageio_ffmpeg
import webview

import actualizador

# Carga el motor de descargas más reciente que se haya bajado (o el incluido)
yt_dlp = actualizador.cargar_yt_dlp()

# Como Estela.exe, usa la carpeta del .exe para que ui/ se pueda editar sin recompilar
if getattr(sys, "frozen", False):
    BASE_DIR = Path(sys.executable).resolve().parent
else:
    BASE_DIR = Path(__file__).resolve().parent
BIN_DIR = BASE_DIR / "bin"
DEFAULT_DIR = Path.home() / "Downloads" / "Estela"
ICON = str(BASE_DIR / "icon.ico")

FORMATS = {
    "video": {
        "format": "bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4]/bv*+ba/b",
        "merge_output_format": "mp4",
    },
    "video720": {
        "format": "bv*[height<=720][ext=mp4]+ba[ext=m4a]/b[height<=720][ext=mp4]/bv*[height<=720]+ba/b[height<=720]",
        "merge_output_format": "mp4",
    },
    "audio": {
        "format": "ba/b",
        "postprocessors": [
            {"key": "FFmpegExtractAudio", "preferredcodec": "mp3", "preferredquality": "192"}
        ],
    },
}


def base_opts():
    """Opciones comunes de yt-dlp: usa el Deno descargado para resolver los retos de YouTube."""
    opts = {"quiet": True, "noplaylist": True}
    deno = actualizador.ruta_deno()
    if deno:
        opts["js_runtimes"] = {"deno": {"path": str(deno)}}
    return opts


def ensure_ffmpeg():
    """Copia el ffmpeg de imageio-ffmpeg como bin/ffmpeg.exe para que yt-dlp lo reconozca."""
    target = BIN_DIR / "ffmpeg.exe"
    if not target.exists():
        BIN_DIR.mkdir(exist_ok=True)
        shutil.copy(imageio_ffmpeg.get_ffmpeg_exe(), target)
    return str(BIN_DIR)


class Api:
    def __init__(self):
        self._window = None
        self._folder = DEFAULT_DIR
        self._busy = False

    def _emit(self, event, data):
        if self._window:
            self._window.run_js(f"window.onBackend({json.dumps(event)}, {json.dumps(data)})")

    def get_folder(self):
        return str(self._folder)

    def choose_folder(self):
        result = self._window.create_file_dialog(webview.FileDialog.FOLDER, directory=str(self._folder))
        if result:
            self._folder = Path(result[0])
        return str(self._folder)

    def open_folder(self):
        self._folder.mkdir(parents=True, exist_ok=True)
        os.startfile(self._folder)

    def get_info(self, url):
        opts = {**base_opts(), "skip_download": True}
        try:
            with yt_dlp.YoutubeDL(opts) as ydl:
                info = ydl.extract_info(url, download=False)
        except Exception as e:
            return {"ok": False, "error": str(e).replace("ERROR: ", "")}
        return {
            "ok": True,
            "title": info.get("title"),
            "channel": info.get("channel") or info.get("uploader"),
            "duration": info.get("duration_string"),
            "thumbnail": info.get("thumbnail"),
            "views": info.get("view_count"),
            "license": info.get("license"),
        }

    def download(self, url, kind):
        if self._busy:
            return {"ok": False, "error": "Ya hay una descarga en curso"}
        self._busy = True
        threading.Thread(target=self._download, args=(url, kind), daemon=True).start()
        return {"ok": True}

    def _download(self, url, kind):
        def hook(d):
            if d["status"] == "downloading":
                total = d.get("total_bytes") or d.get("total_bytes_estimate") or 0
                done = d.get("downloaded_bytes", 0)
                self._emit("progress", {
                    "percent": round(done * 100 / total, 1) if total else None,
                    "speed": d.get("_speed_str", "").strip(),
                    "eta": d.get("_eta_str", "").strip(),
                })
            elif d["status"] == "finished":
                self._emit("processing", {})

        self._folder.mkdir(parents=True, exist_ok=True)
        opts = {
            **base_opts(),
            "noprogress": True,
            "outtmpl": str(self._folder / "%(title)s.%(ext)s"),
            "ffmpeg_location": ensure_ffmpeg(),
            "progress_hooks": [hook],
            **FORMATS[kind],
        }
        try:
            with yt_dlp.YoutubeDL(opts) as ydl:
                ydl.download([url])
            self._emit("done", {"folder": str(self._folder)})
        except Exception as e:
            self._emit("error", {"error": str(e).replace("ERROR: ", "")})
        finally:
            self._busy = False

    def check_update(self):
        actual = yt_dlp.version.__version__
        result = {"version": actual}
        try:
            nueva = actualizador.buscar_e_instalar(actual)
            result["status"] = "updated" if nueva else "ok"
            result["new"] = nueva
        except actualizador.RequiereReinstalar:
            result["status"] = "reinstall"
        except Exception:
            result["status"] = "offline"
        try:
            result["deno"] = actualizador.actualizar_deno() or "ok"
        except Exception:
            result["deno"] = "ok" if actualizador.ruta_deno() else "missing"
        return result

    def restart(self):
        if self._busy:
            return {"ok": False, "error": "Espera a que termine la descarga"}
        if getattr(sys, "frozen", False):
            cmd = [sys.executable]
        else:
            cmd = [sys.executable, str(Path(__file__).resolve())]
        # Para que un Estela.exe de un solo archivo arranque como instancia nueva
        env = dict(os.environ, PYINSTALLER_RESET_ENVIRONMENT="1")
        subprocess.Popen(cmd, cwd=BASE_DIR, env=env)
        self._window.destroy()
        return {"ok": True}


def set_window_icon(window):
    """Pone icon.ico en la ventana y en la barra de tareas (pythonw usa el de Python por defecto)."""
    user32 = ctypes.windll.user32
    user32.FindWindowW.restype = ctypes.c_void_p
    user32.LoadImageW.restype = ctypes.c_void_p
    user32.LoadImageW.argtypes = [ctypes.c_void_p, ctypes.c_wchar_p, ctypes.c_uint, ctypes.c_int, ctypes.c_int, ctypes.c_uint]
    user32.SendMessageW.argtypes = [ctypes.c_void_p, ctypes.c_uint, ctypes.c_size_t, ctypes.c_void_p]

    hwnd = user32.FindWindowW(None, window.title)
    if not hwnd:
        return
    IMAGE_ICON, LR_LOADFROMFILE, WM_SETICON = 1, 0x10, 0x80
    # (ICON_BIG, SM_CXICON) y (ICON_SMALL, SM_CXSMICON)
    for which, metric in ((1, 11), (0, 49)):
        size = user32.GetSystemMetrics(metric)
        hicon = user32.LoadImageW(None, ICON, IMAGE_ICON, size, size, LR_LOADFROMFILE)
        if hicon:
            user32.SendMessageW(hwnd, WM_SETICON, which, hicon)


if __name__ == "__main__":
    # ID propio para que la barra de tareas muestre el icono de Estela y no el de Python
    ctypes.windll.shell32.SetCurrentProcessExplicitAppUserModelID("Estela.Downloader")
    api = Api()
    window = webview.create_window(
        "Estela ✦",
        str(BASE_DIR / "ui" / "index.html"),
        js_api=api,
        width=980,
        height=680,
        min_size=(820, 600),
        background_color="#1a1033",
    )
    api._window = window
    window.events.shown += lambda: set_window_icon(window)
    webview.start(icon=ICON)
