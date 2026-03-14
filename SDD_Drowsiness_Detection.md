# Spec-Driven Development (SDD) - Sistema de Monitoreo de Conductores (Somnolencia y Distracción)

## 1. Visión General
El objetivo de esta especificación es definir la arquitectura y los pasos técnicos necesarios para adaptar el sistema de visión artificial actual (OficinaEficiencia) a un caso de uso de **Monitoreo de Conductores (Driver Monitoring System - DMS)**, enfocado en aplicaciones de movilidad (Uber, Didi, inDrive).

El sistema deberá detectar si un conductor se está quedando dormido (somnolencia/microsueños) o si no está mirando el camino (distracción), y generar alertas tempranas en tiempo real para prevenir accidentes.

## 2. Análisis del Requisito (Referencia TikTok - Somnolencia)
A partir de la aclaración del caso de uso, se identifican las siguientes funcionalidades clave para el sistema de conductor:
* **Detección de Somnolencia:** Monitoreo del parpadeo y cierre prolongado de los ojos mediante el cálculo del **Eye Aspect Ratio (EAR)**. Si el EAR cae por debajo de un umbral durante un tiempo determinado (frames), se activa una alerta de sueño.
* **Detección de Distracción:** Monitoreo de la dirección de la mirada y pose de la cabeza (**Head Pose Estimation**) para determinar si el conductor está mirando al frente (camino) o hacia los lados/abajo por tiempo prolongado.
* **Sistema de Alertas:** Generación de señales visuales críticas en pantalla y alertas sonoras para despertar o alertar al conductor.

## 3. Análisis de la Arquitectura Actual
* **Base Tecnológica Actual:** El sistema utiliza OpenCV, YOLOv8 (`ultralytics`) para detección de personas/celulares, ByteTrack (`supervision`) para tracking, y `face_recognition` para identificar empleados.
* **Limitación Actual:** `face_recognition` es útil para identificar *quién* es la persona, pero no extrae los puntos clave faciales (Facial Landmarks) detallados y en tiempo real con la precisión y ligereza necesarias para medir el cierre de los párpados o la rotación exacta de la cabeza cuadro por cuadro.

## 4. Propuesta Arquitectónica para DMS

Para lograr este nuevo objetivo, es necesario incorporar una solución robusta y ligera de estimación de landmarks faciales. **MediaPipe (Google)** es el estándar en la industria para esto por su rendimiento en tiempo real en CPU.

### 4.1. Nuevas Dependencias
Se requerirá instalar:
`pip install mediapipe`

### 4.2. Nuevo Módulo: `src/detection/driver_monitor.py`
Se creará una nueva clase `DriverMonitor` dedicada exclusivamente a analizar el rostro del conductor en primer plano.

#### Funcionalidad Principal de la Clase `DriverMonitor`
1. **Inicialización:** Cargar el modelo `FaceMesh` de MediaPipe.
2. **Cálculo de EAR (Eye Aspect Ratio):**
   - Extraer las coordenadas 3D de los puntos clave de los ojos (ej. índices 33, 160, 158, 133, 153, 144 para el ojo izquierdo).
   - Calcular la distancia vertical y horizontal entre los párpados.
   - Si $EAR < UMBRAL\_EAR$ durante $X$ frames consecutivos $\rightarrow$ **¡ALERTA DE SUEÑO!**.
3. **Head Pose Estimation (Opcional/Avanzado):**
   - Usar puntos clave de la nariz, ojos y barbilla.
   - Resolver la perspectiva de N puntos (`cv2.solvePnP`) contra un modelo 3D genérico del rostro para obtener los ángulos de cabeceo (pitch), guiñada (yaw) y alabeo (roll).
   - Si los ángulos indican que no mira al frente por $Y$ segundos $\rightarrow$ **¡ALERTA DE DISTRACCIÓN!**.

## 5. Especificaciones Técnicas (Snippets de Implementación)

### 5.1. Clase `DriverMonitor` (Cálculo de EAR con MediaPipe)
```python
# src/detection/driver_monitor.py
import cv2
import math
import mediapipe as mp

class DriverMonitor:
    def __init__(self, ear_threshold=0.25, frame_consec_frames=15):
        self.mp_face_mesh = mp.solutions.face_mesh
        self.face_mesh = self.mp_face_mesh.FaceMesh(
            max_num_faces=1,
            refine_landmarks=True, # Necesario para mayor precisión en ojos y labios
            min_detection_confidence=0.5,
            min_tracking_confidence=0.5
        )

        self.EAR_THRESHOLD = ear_threshold
        self.FRAME_CONSEC_FRAMES = frame_consec_frames
        self.sleep_frames_counter = 0
        self.alarm_on = False

    def _euclidean_distance(self, point1, point2):
        return math.sqrt((point1.x - point2.x)**2 + (point1.y - point2.y)**2)

    def _calculate_ear(self, landmarks, eye_indices):
        # Índices de FaceMesh. Ej. Ojo Izquierdo: P1=33(ext), P2=160(sup_ext), P3=158(sup_int), P4=133(int), P5=153(inf_int), P6=144(inf_ext)
        p1 = landmarks[eye_indices[0]]
        p2 = landmarks[eye_indices[1]]
        p3 = landmarks[eye_indices[2]]
        p4 = landmarks[eye_indices[3]]
        p5 = landmarks[eye_indices[4]]
        p6 = landmarks[eye_indices[5]]

        # Distancias verticales
        dist_v1 = self._euclidean_distance(p2, p6)
        dist_v2 = self._euclidean_distance(p3, p5)

        # Distancia horizontal
        dist_h = self._euclidean_distance(p1, p4)

        # Ratio
        ear = (dist_v1 + dist_v2) / (2.0 * dist_h)
        return ear

    def process_frame(self, frame):
        # MediaPipe requiere RGB
        rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        results = self.face_mesh.process(rgb_frame)

        state = "Normal"
        color = (0, 255, 0)
        ear = 0.0

        if results.multi_face_landmarks:
            landmarks = results.multi_face_landmarks[0].landmark

            # Índices simplificados para ejemplo (se deben afinar según la doc de FaceMesh)
            LEFT_EYE = [33, 160, 158, 133, 153, 144]
            RIGHT_EYE = [362, 385, 387, 263, 373, 380]

            left_ear = self._calculate_ear(landmarks, LEFT_EYE)
            right_ear = self._calculate_ear(landmarks, RIGHT_EYE)

            ear = (left_ear + right_ear) / 2.0

            # Lógica de Alarma
            if ear < self.EAR_THRESHOLD:
                self.sleep_frames_counter += 1
                if self.sleep_frames_counter >= self.FRAME_CONSEC_FRAMES:
                    self.alarm_on = True
                    state = "¡DURMIENDO!"
                    color = (0, 0, 255) # Rojo
            else:
                self.sleep_frames_counter = 0
                self.alarm_on = False

        return state, color, ear, self.alarm_on
```

### 5.2. Integración en el Pipeline (Ejemplo para `main.py` o un nuevo `main_dms.py`)
Dado que el enfoque cambia de una cámara panorámica de oficina a una cámara enfocada en el conductor (tipo dashcam interior), se recomienda crear un flujo simplificado o integrar el módulo solo cuando la cámara esté en "Modo Vehículo".

```python
# En main.py o un script dedicado main_driver.py
from detection.driver_monitor import DriverMonitor

driver_monitor = DriverMonitor(ear_threshold=0.22, frame_consec_frames=20)

# Dentro del bucle while True:
ret, frame = cap.read()
if not ret: break

state, color, ear_val, is_sleeping = driver_monitor.process_frame(frame)

# Alerta visual en pantalla
if is_sleeping:
    cv2.putText(frame, "¡ALERTA: CONDUCTOR DURMIENDO!", (50, 50),
                cv2.FONT_HERSHEY_SIMPLEX, 1.2, (0, 0, 255), 3)
    # Aquí se podría lanzar un hilo para reproducir un sonido (pygame.mixer o simpleaudio)

cv2.putText(frame, f"EAR: {ear_val:.2f}", (50, 100), cv2.FONT_HERSHEY_SIMPLEX, 0.7, color, 2)
```

## 6. Integración con el Sistema Existente
Para no romper la lógica actual de `OficinaEficiencia`:
1. El `StateManager` actual (`src/analysis/state_manager.py`) podría recibir el estado `is_sleeping` y registrar el evento en la base de datos (SQLite) bajo una nueva categoría "Microsueño".
2. Las capturas (`DatabaseManager.insert_snapshot`) se pueden reutilizar para guardar evidencia fotográfica del conductor dormido para auditorías de las plataformas (Didi, Uber).

## 7. Roadmap de Implementación (Próximos Pasos)
1. **Fase 1:** Instalar `mediapipe` en el entorno.
2. **Fase 2:** Implementar la clase `DriverMonitor` en `src/detection/driver_monitor.py`.
3. **Fase 3:** Crear un script de pruebas aislado (`tests/test_drowsiness.py`) usando la cámara web para calibrar el `EAR_THRESHOLD` (usualmente entre 0.20 y 0.25) según la iluminación y el hardware.
4. **Fase 4:** Integrar el llamado a `DriverMonitor` en el bucle de la aplicación principal o crear un `main_driver_mode.py` específico para este producto.

*Nota: Siguiendo las instrucciones de documentación estratégica, en este pull request sólo se está generando este documento SDD; el código fuente actual no ha sido alterado.*