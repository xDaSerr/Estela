"""Mantiene al día el motor de descargas sin tener que reinstalar Estela.

- yt-dlp y las dependencias que exija se descargan de PyPI a
  %LOCALAPPDATA%\\Estela\\motor\\<version> y se usan en el siguiente arranque.
  Si una versión nueva falla al cargarse, se descarta y se vuelve a la incluida.
- Deno (el intérprete de JavaScript que yt-dlp usa para los retos de YouTube)
  se descarga a %LOCALAPPDATA%\\Estela\\deno y se mantiene actualizado.
"""
import email
import hashlib
import importlib
import importlib.metadata
import importlib.util
import io
import json
import os
import re
import shutil
import sys
import urllib.request
import zipfile
from pathlib import Path

from packaging.requirements import Requirement
from packaging.utils import canonicalize_name
from packaging.version import InvalidVersion, Version

ESTELA_DIR = Path(os.environ.get("LOCALAPPDATA", Path.home())) / "Estela"
MOTOR_DIR = ESTELA_DIR / "motor"
ACTUAL = MOTOR_DIR / "actual.txt"
DENO_DIR = ESTELA_DIR / "deno"
DENO_EXE = DENO_DIR / "deno.exe"
DENO_VERSION = DENO_DIR / "version.txt"

_ruta_cargada = None


class RequiereReinstalar(Exception):
    """La versión nueva necesita algo que solo se puede incluir con un instalador nuevo de Estela."""


def _abrir(url, timeout=30):
    return urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "Estela"}), timeout=timeout)


def _leer_json(url):
    with _abrir(url) as r:
        return json.load(r)


# ---------------------------------------------------------------- yt-dlp

def _ruta_activa():
    try:
        nombre = ACTUAL.read_text(encoding="utf-8").strip()
    except OSError:
        return None
    ruta = MOTOR_DIR / nombre
    return ruta if (ruta / "yt_dlp").is_dir() else None


def _olvidar_modulos(ruta):
    """Quita de sys.modules todo lo que se haya cargado desde la carpeta del motor."""
    for nombre, modulo in list(sys.modules.items()):
        archivo = getattr(modulo, "__file__", None) or ""
        if nombre == "yt_dlp" or nombre.startswith("yt_dlp.") or archivo.startswith(str(ruta)):
            del sys.modules[nombre]


def cargar_yt_dlp():
    """Importa yt_dlp usando la versión actualizada si existe; si falla, usa la incluida."""
    global _ruta_cargada
    ruta = _ruta_activa()
    if ruta:
        sys.path.insert(0, str(ruta))
        importlib.invalidate_caches()
        try:
            import yt_dlp
            # Prueba rápida: crea un descargador (carga red y dependencias) y el solucionador de retos
            yt_dlp.YoutubeDL({"quiet": True}).close()
            if importlib.util.find_spec("yt_dlp_ejs"):
                importlib.import_module("yt_dlp_ejs")
            _ruta_cargada = ruta
            return yt_dlp
        except Exception:
            sys.path.remove(str(ruta))
            _olvidar_modulos(ruta)
            ACTUAL.unlink(missing_ok=True)
            shutil.rmtree(ruta, ignore_errors=True)
    import yt_dlp
    return yt_dlp


def _version_base(nombre):
    """Versión de un paquete incluido en la app (sin contar el motor descargado)."""
    rutas = [p for p in sys.path if not _ruta_cargada or Path(p) != _ruta_cargada]
    for dist in importlib.metadata.distributions(name=nombre, path=rutas):
        return dist.version
    return None


def _wheel_pura(archivos):
    return next((f for f in archivos if f["filename"].endswith("-none-any.whl") and not f.get("yanked")), None)


def _instalar_wheel(archivo, destino):
    """Descarga, verifica y extrae una wheel. Devuelve sus Requires-Dist."""
    with _abrir(archivo["url"], timeout=120) as r:
        contenido = r.read()
    if hashlib.sha256(contenido).hexdigest() != archivo["digests"]["sha256"]:
        raise ValueError(f"{archivo['filename']} no pasó la verificación")
    with zipfile.ZipFile(io.BytesIO(contenido)) as z:
        nombres = z.namelist()
        seguros = [n for n in nombres if ".." not in n and not n.startswith("/") and ".data/" not in n]
        z.extractall(destino, seguros)
        metadata = next(n for n in nombres if n.endswith(".dist-info/METADATA"))
        return email.message_from_bytes(z.read(metadata)).get_all("Requires-Dist") or []


def _instalar_dependencias(requisitos, destino, extra, hechos):
    for texto in requisitos:
        req = Requirement(texto)
        if req.marker and not req.marker.evaluate({"extra": extra}):
            continue
        clave = canonicalize_name(req.name)
        if clave in hechos:
            continue
        hechos.add(clave)
        base = _version_base(req.name)
        if base and req.specifier.contains(base, prereleases=True):
            continue  # lo que trae la app ya sirve

        candidatos = []
        for version, archivos in _leer_json(f"https://pypi.org/pypi/{req.name}/json")["releases"].items():
            try:
                v = Version(version)
            except InvalidVersion:
                continue
            wheel = _wheel_pura(archivos)
            if wheel and not v.is_prerelease and v in req.specifier:
                candidatos.append((v, wheel))
        if not candidatos:
            # Solo existe en versión compilada (p. ej. brotli): hay que reconstruir Estela
            raise RequiereReinstalar(req.name)
        _, wheel = max(candidatos, key=lambda c: c[0])
        subrequisitos = _instalar_wheel(wheel, destino)
        _instalar_dependencias(subrequisitos, destino, "", hechos)


def buscar_e_instalar(version_actual):
    """Descarga la última versión de yt-dlp (y lo que necesite) si es más nueva.

    Devuelve esa versión, o None si ya está al día. Lanza RequiereReinstalar si
    la versión nueva necesita un instalador nuevo de Estela.
    """
    datos = _leer_json("https://pypi.org/pypi/yt-dlp/json")
    nueva = datos["info"]["version"]
    instalada = ACTUAL.read_text(encoding="utf-8").strip() if ACTUAL.exists() else "0"
    if Version(nueva) <= max(Version(version_actual), Version(instalada)):
        return None

    MOTOR_DIR.mkdir(parents=True, exist_ok=True)
    temporal = MOTOR_DIR / f"{nueva}.tmp"
    shutil.rmtree(temporal, ignore_errors=True)
    try:
        requisitos = _instalar_wheel(_wheel_pura(datos["urls"]), temporal)
        _instalar_dependencias(requisitos, temporal, "default", {"yt-dlp"})
    except BaseException:
        shutil.rmtree(temporal, ignore_errors=True)
        raise
    destino = MOTOR_DIR / nueva
    shutil.rmtree(destino, ignore_errors=True)
    temporal.rename(destino)
    ACTUAL.write_text(nueva, encoding="utf-8")
    _limpiar(conservar={destino, _ruta_cargada})
    return nueva


def _limpiar(conservar):
    for ruta in MOTOR_DIR.iterdir():
        if ruta.is_dir() and ruta not in conservar:
            shutil.rmtree(ruta, ignore_errors=True)


# ---------------------------------------------------------------- Deno

def ruta_deno():
    return DENO_EXE if DENO_EXE.exists() else None


def actualizar_deno():
    """Instala o actualiza Deno. Devuelve 'installed', 'updated' o None si ya está al día."""
    with _abrir("https://dl.deno.land/release-latest.txt") as r:
        ultima = r.read().decode().strip()
    actual = DENO_VERSION.read_text(encoding="utf-8").strip() if DENO_VERSION.exists() and DENO_EXE.exists() else None
    if actual == ultima:
        return None

    url = f"https://github.com/denoland/deno/releases/download/{ultima}/deno-x86_64-pc-windows-msvc.zip"
    with _abrir(url + ".sha256sum") as r:
        esperado = re.search(r"[0-9a-fA-F]{64}", r.read().decode("utf-8", "replace")).group().lower()
    with _abrir(url, timeout=300) as r:
        contenido = r.read()
    if hashlib.sha256(contenido).hexdigest() != esperado:
        raise ValueError("Deno no pasó la verificación")

    DENO_DIR.mkdir(parents=True, exist_ok=True)
    nuevo = DENO_DIR / "deno.exe.new"
    with zipfile.ZipFile(io.BytesIO(contenido)) as z:
        nuevo.write_bytes(z.read("deno.exe"))
    try:
        os.replace(nuevo, DENO_EXE)
    except PermissionError:
        nuevo.unlink(missing_ok=True)  # está en uso; se reintenta en el próximo arranque
        return None
    DENO_VERSION.write_text(ultima, encoding="utf-8")
    return "updated" if actual else "installed"
