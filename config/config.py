# --- config/config.py ---
import os
import sys

# Add src to sys.path so utils can be imported when running outside src
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'src')))

try:
    from utils.path_utils import get_data_path
except ImportError:
    # Fallback if somehow executed from a weird place
    def get_data_path(p):
        return p

MODE = 'local'  # Cambiar a 'remote' cuando sea necesario

# Video
LOCAL_CAMERA_INDEX = 1
REMOTE_CAMERA_URL = "rtsp://usuario:contraseña@IP:PUERTO/cam/path"

# Base de datos en APPDATA
LOCAL_DB_PATH = get_data_path('data/db/local_tracking.db')
REMOTE_DB_URL = 'mysql://usuario:contraseña@servidor_ip/dbname'

# Snapshots en APPDATA
SNAPSHOTS_DIR = get_data_path('data/snapshots')

# Zonas en APPDATA
ZONAS_PATH = get_data_path('data/zonas/zonas.json')

# Caras en APPDATA
FACES_DIR = get_data_path('data/faces')
ENCODINGS_FILE = get_data_path('data/faces/encodings.pkl')

# Reportes
REPORTS_DIR = get_data_path('data/reporting')

# Otros parámetros generales
FRAME_SKIP = 1  # Capturar cada frame, ajustar para pruebas
CONFIDENCE_THRESHOLD = 0.4
