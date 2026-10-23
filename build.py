"""Compila Estela con PyInstaller.

    python build.py onefile   -> Estela.exe en esta carpeta (uso personal)
    python build.py onedir    -> build\\dist\\Estela\\ (lo que empaqueta el instalador)
"""
import importlib.util
import shutil
import sys
from pathlib import Path

import imageio_ffmpeg
import PyInstaller.__main__

BASE = Path(__file__).resolve().parent

# Dependencias de yt-dlp[default]: se copia su metadata para que el actualizador
# sepa qué versiones trae la app y solo descargue lo que haga falta.
DEPENDENCIAS = [
    "yt-dlp", "yt-dlp-ejs", "requests", "urllib3", "websockets", "certifi",
    "mutagen", "brotli", "pycryptodomex", "charset-normalizer", "idna", "packaging",
]

# Partes de la biblioteca estándar que no hacen falta (interfaces gráficas, pruebas, etc.)
EXCLUIR_STDLIB = {
    "tkinter", "_tkinter", "turtle", "turtledemo", "idlelib", "test", "unittest",
    "lib2to3", "ensurepip", "venv", "pydoc_data", "__phello__", "antigravity", "this",
}


def stdlib():
    """Toda la biblioteca estándar, para que una versión nueva de yt-dlp encuentre cualquier módulo."""
    args = []
    for nombre in sorted(sys.stdlib_module_names - EXCLUIR_STDLIB):
        try:
            spec = importlib.util.find_spec(nombre)
        except (ImportError, ValueError):
            continue
        if spec is None:
            continue
        args += ["--collect-submodules" if spec.submodule_search_locations else "--hidden-import", nombre]
    return args


def preparar_ffmpeg():
    """Deja bin/ffmpeg.exe listo para el instalador (no se sube a git)."""
    destino = BASE / "bin" / "ffmpeg.exe"
    if not destino.exists():
        destino.parent.mkdir(exist_ok=True)
        shutil.copy(imageio_ffmpeg.get_ffmpeg_exe(), destino)


def main():
    preparar_ffmpeg()
    modo = sys.argv[1] if len(sys.argv) > 1 else "onedir"
    args = [
        str(BASE / "app.py"),
        "--noconfirm",
        "--windowed",
        "--name", "Estela",
        "--icon", str(BASE / "icon.ico"),
        "--collect-submodules", "yt_dlp",
        "--collect-all", "yt_dlp_ejs",
        "--specpath", str(BASE / "build"),
        *[a for dep in DEPENDENCIAS for a in ("--copy-metadata", dep)],
        *stdlib(),
    ]
    if modo == "onefile":
        args += ["--onefile", "--distpath", str(BASE), "--workpath", str(BASE / "build" / "work-onefile")]
    else:
        args += ["--onedir", "--distpath", str(BASE / "build" / "dist"), "--workpath", str(BASE / "build" / "work")]
    PyInstaller.__main__.run(args)


if __name__ == "__main__":
    main()
