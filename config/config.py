# --- config/config.py ---
import os
from config.path_utils import get_resource_path, get_appdata_path

# --- Versioning ---
VERSION = "1.0.0" # Springback default
try:
    version_path = get_resource_path('VERSION')
    if os.path.exists(version_path):
        with open(version_path, 'r') as f:
            VERSION = f.read().strip()
except Exception:
    pass

# ── Writable user-data directories (all under %APPDATA%\OficinaEficiencia) ──
APP_DATA_DIR  = get_appdata_path()
DATA_DIR      = get_appdata_path('data')
FACES_DIR     = get_appdata_path('data', 'faces')
ZONAS_DIR     = get_appdata_path('data', 'zonas')
CALIBRATION_DIR = get_appdata_path('data', 'config')

# Ensure sub-dirs exist (get_appdata_path already creates them, but be explicit)
for sub_dir in ['db', 'snapshots']:
    os.makedirs(os.path.join(DATA_DIR, sub_dir), exist_ok=True)

# ── Read-only resource paths (bundled with the exe) ──
MODEL_PATH = get_resource_path('yolov8n.pt')

MODE = 'local'  # Cambiar a 'remote' cuando sea necesario

# Video
LOCAL_CAMERA_INDEX = 1
REMOTE_CAMERA_URL = "rtsp://usuario:contraseña@IP:PUERTO/cam/path"

# Base de datos
LOCAL_DB_PATH = os.path.join(DATA_DIR, 'db', 'local_tracking.db')
REMOTE_DB_URL = 'mysql://usuario:contraseña@servidor_ip/dbname'

# Zonas
ZONAS_FILE = os.path.join(ZONAS_DIR, 'zonas.json')

# Snapshots
SNAPSHOTS_DIR = os.path.join(DATA_DIR, 'snapshots')

# Otros parámetros generales
FRAME_SKIP = 1  # Capturar cada frame, ajustar para pruebas
CONFIDENCE_THRESHOLD = 0.4
