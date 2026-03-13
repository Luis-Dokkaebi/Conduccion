import os
import sys

def get_app_data_dir():
    """Returns the %APPDATA%/OficinaEficiencia directory for persistent data."""
    if sys.platform == "win32":
        app_data = os.environ.get("APPDATA")
        if not app_data:
            app_data = os.path.expanduser("~")
        base_dir = os.path.join(app_data, "OficinaEficiencia")
    else:
        # Fallback for non-Windows (e.g. Linux/Mac)
        base_dir = os.path.join(os.path.expanduser("~"), ".OficinaEficiencia")

    os.makedirs(base_dir, exist_ok=True)
    return base_dir

def get_data_path(relative_path):
    """Gets a path relative to the persistent app data directory."""
    base_dir = get_app_data_dir()
    full_path = os.path.join(base_dir, relative_path)
    # Ensure directory exists
    os.makedirs(os.path.dirname(full_path), exist_ok=True)
    return full_path

def get_resource_path(relative_path):
    """Gets the absolute path to a static resource, works for dev and PyInstaller."""
    try:
        # PyInstaller creates a temp folder and stores path in _MEIPASS
        base_path = sys._MEIPASS
    except Exception:
        base_path = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))

    return os.path.join(base_path, relative_path)
