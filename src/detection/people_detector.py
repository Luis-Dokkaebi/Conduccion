import cv2
import os
import json
from ultralytics import YOLO

class PeopleDetector:
    def __init__(self, source=0, zonas_path=None, model_path="yolov8n.pt"):
        try:
            from utils.path_utils import get_resource_path
            model_path = get_resource_path(model_path)
        except Exception:
            import sys, os
            base_path = getattr(sys, "_MEIPASS", os.path.dirname(os.path.abspath(__file__)))
            model_path = os.path.join(base_path, model_path)

        if zonas_path is None:
            from config import config
            zonas_path = config.ZONAS_PATH
        self.source = source  # 0 para webcam, o URL de cámara IP
        self.model = YOLO(model_path)
        self.zonas = self.load_zonas(zonas_path)

    def load_zonas(self, path):
        full_path = os.path.abspath(path)
        if not os.path.exists(full_path):
            print("❌ Archivo de zonas no encontrado:", full_path)
            return {}
        with open(full_path, 'r') as f:
            data = json.load(f)
        print("✅ Zonas cargadas:", list(data.keys()))
        return data

    def point_in_polygon(self, point, polygon):
        # Algoritmo de ray casting
        x, y = point
        inside = False
        n = len(polygon)
        for i in range(n):
            xi, yi = polygon[i]
            xj, yj = polygon[(i + 1) % n]
            if ((yi > y) != (yj > y)) and \
               (x < (xj - xi) * (y - yi) / ((yj - yi) + 1e-9) + xi):
                inside = not inside
        return inside

    def run(self):
        cap = cv2.VideoCapture(self.source)

        if not cap.isOpened():
            print("❌ No se pudo abrir la cámara o fuente de video.")
            return

        while True:
            ret, frame = cap.read()
            if not ret:
                break

            results = self.model(frame)[0]

            for result in results.boxes.data:
                x1, y1, x2, y2, conf, cls = result.tolist()
                if int(cls) != 0:  # Solo clase 0 = persona
                    continue

                cx, cy = int((x1 + x2) / 2), int((y1 + y2) / 2)
                estado = "Fuera de zona"

                for zona_nombre, puntos in self.zonas.items():
                    if self.point_in_polygon((cx, cy), puntos):
                        estado = f"En zona: {zona_nombre}"
                        break

                color = (0, 255, 0) if "En zona" in estado else (0, 0, 255)
                cv2.rectangle(frame, (int(x1), int(y1)), (int(x2), int(y2)), color, 2)
                cv2.circle(frame, (cx, cy), 4, color, -1)
                cv2.putText(frame, estado, (int(x1), int(y1) - 10),
                            cv2.FONT_HERSHEY_SIMPLEX, 0.5, color, 2)

            cv2.imshow("Detección de personas", frame)
            if cv2.waitKey(1) & 0xFF == ord('q'):
                break

        cap.release()
        cv2.destroyAllWindows()
