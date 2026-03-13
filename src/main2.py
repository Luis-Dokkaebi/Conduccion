import os
import sys

# Asegura que src esté en el path
sys.path.append(os.path.join(os.path.dirname(__file__), "src"))

from detection.people_detector import PeopleDetector

def main():
    # Usa 0 para webcam local, o reemplaza por una URL si usas cámara IP
    source = 1  # o por ejemplo: "http://192.168.1.100:8080/video"

    # Crea el detector con ruta al modelo y zonas
    detector = PeopleDetector(
        source=source,
        zonas_path=None,
        model_path="yolov8n.pt"  # Se descarga automáticamente si no lo tienes
    )

    detector.run()

if __name__ == "__main__":
    main()
