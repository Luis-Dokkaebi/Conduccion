# config/path_utils.py
# Centralised path resolution for both development and PyInstaller-frozen environments.

import os
import sys


def is_frozen():
    """Returns True when running inside a PyInstaller bundle."""
    return getattr(sys, 'frozen', False) and hasattr(sys, '_MEIPASS')


def get_resource_path(relative_path=""):
    """
    Return the absolute path to a **read-only** resource bundled with the app.

    When frozen (PyInstaller):
        Uses sys._MEIPASS which points to the _internal folder in PyInstaller 6+
    When running from source:
        <project_root>/<relative_path>
    """
    if is_frozen():
        base_path = sys._MEIPASS
    else:
        # Development: project root is one level up from config/
        base_path = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))

    if relative_path:
        return os.path.join(base_path, relative_path)
    return base_path


def get_appdata_path(*subdirs):
    """
    Return (and create) a path under the user's APPDATA directory.

    Always resolves to:
        %APPDATA%/OficinaEficiencia/<subdirs>

    Creates the full directory tree on first call so the application
    never has to run as Administrator.
    """
    base = os.path.join(
        os.environ.get('APPDATA', os.path.expanduser('~')),
        'OficinaEficiencia',
    )
    path = os.path.join(base, *subdirs) if subdirs else base
    os.makedirs(path, exist_ok=True)
    return path
