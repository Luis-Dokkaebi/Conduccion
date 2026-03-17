# Implementación Core del Sistema de Monitoreo de Conductores

Este documento contiene la lógica de implementación técnica central. Muestra cómo calcular los biomarcadores (EAR/MAR) usando los tensores de MediaPipe y la integración de la Máquina de Estados para disparar alarmas críticas de despertar nativas (Android/Kotlin y Python Pseudo-código Backend).

---

## 1. Algoritmo Biométrico: Cálculo de EAR y MAR
**Lenguaje Referencia:** Kotlin (Móvil Edge)

El `Eye Aspect Ratio` mide la apertura de los párpados de forma invariante a la posición y tamaño del rostro en la pantalla.

```kotlin
import kotlin.math.sqrt

// Índices del modelo MediaPipe Face Mesh (468 puntos)
val LEFT_EYE_INDICES = intArrayOf(33, 160, 158, 133, 153, 144)
val RIGHT_EYE_INDICES = intArrayOf(362, 385, 387, 263, 373, 380)

/**
 * Calcula la Distancia Euclidiana entre dos puntos 3D (X, Y).
 * Asumimos landmarks normalizados [0.0 - 1.0].
 */
fun euclideanDistance(p1: NormalizedLandmark, p2: NormalizedLandmark): Float {
    return sqrt((p1.x() - p2.x()) * (p1.x() - p2.x()) + (p1.y() - p2.y()) * (p1.y() - p2.y()))
}

/**
 * Calcula el Eye Aspect Ratio (EAR) para un solo ojo dado sus 6 puntos cardinales.
 */
fun calculateEar(landmarks: List<NormalizedLandmark>, eyeIndices: IntArray): Float {
    // Esquina exterior, superior exterior, superior interior, esquina interior, inferior interior, inferior exterior
    val p1 = landmarks[eyeIndices[0]] // Esquina Ext
    val p2 = landmarks[eyeIndices[1]] // Sup Ext
    val p3 = landmarks[eyeIndices[2]] // Sup Int
    val p4 = landmarks[eyeIndices[3]] // Esquina Int
    val p5 = landmarks[eyeIndices[4]] // Inf Int
    val p6 = landmarks[eyeIndices[5]] // Inf Ext

    // Distancias Verticales entre los párpados superior e inferior
    val distV1 = euclideanDistance(p2, p6)
    val distV2 = euclideanDistance(p3, p5)

    // Distancia Horizontal de esquina a esquina
    val distH = euclideanDistance(p1, p4)

    // Ecuación EAR: suma de verticales sobre (2 * horizontal)
    val ear = (distV1 + distV2) / (2.0f * distH)
    return ear
}
```

---

## 2. Máquina de Estados Finita (FSM) de Somnolencia
**Lenguaje Referencia:** Kotlin Coroutines (Ejecutado por cada Frame capturado ~30 FPS)

Mantiene un búfer para asegurar que no es un simple parpadeo, sino un cierre sostenido.

```kotlin
class DrowsinessDetector(
    private var baseEarThreshold: Float = 0.22f, // Valor por defecto si no se calibra
    private val fps: Int = 30
) {
    // ¿Cuántos frames seguidos (Ej. 45 a 30fps = 1.5s) deben estar los ojos cerrados para alertar?
    private val requiredFramesForSleep = (fps * 1.5).toInt()

    // Contadores
    private var closedFramesCounter = 0
    var isEmergencyAlarmActive = false
        private set

    fun processFrame(faceLandmarks: List<NormalizedLandmark>) {
        val leftEar = calculateEar(faceLandmarks, LEFT_EYE_INDICES)
        val rightEar = calculateEar(faceLandmarks, RIGHT_EYE_INDICES)

        // Promedio de apertura de ambos ojos
        val currentEar = (leftEar + rightEar) / 2.0f

        // Si la apertura cae por debajo del umbral base (Chofer durmiendo o distraído cerrando los ojos)
        if (currentEar < baseEarThreshold) {
            closedFramesCounter++

            // Si los frames cerrados alcanzan el tiempo crítico (1.5s constantes)
            if (closedFramesCounter >= requiredFramesForSleep && !isEmergencyAlarmActive) {
                triggerEmergencyWakeUp()
            }
        } else {
            // Si el EAR se recupera por encima del umbral (el conductor abrió los ojos)
            // Se puede exigir que lo abra unos frames continuos para evitar falsos "despertares" de 1 frame
            if (currentEar > baseEarThreshold + 0.05f) { // Histéresis
                closedFramesCounter = 0
                if (isEmergencyAlarmActive) {
                    stopEmergencyAlarm()
                }
            }
        }
    }

    private fun triggerEmergencyWakeUp() {
        isEmergencyAlarmActive = true
        // Callback a la UI/Hardware
        HardwareAlertManager.playMaxVolumeSiren()
        HardwareAlertManager.flashScreenRed()
        HardwareAlertManager.vibrateAggressively()

        // Guardar métrica en SQLite para sincronizar al backend
        Database.insertEvent("MICROSLEEP", durationMs = closedFramesCounter * (1000/fps))
    }

    private fun stopEmergencyAlarm() {
        isEmergencyAlarmActive = false
        HardwareAlertManager.stopSiren()
        HardwareAlertManager.restoreScreen()
    }
}
```

---

## 3. Override de Hardware (Android) - Despertar Forzoso
Esta capa es vital para garantizar que la alarma se escuche, incluso si el móvil del chofer está en "No Molestar" o conectado por Bluetooth a la radio del coche.

```kotlin
object HardwareAlertManager {
    private var mediaPlayer: MediaPlayer? = null

    fun playMaxVolumeSiren(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Forzar máximo volumen de ALARMA, no de MEDIA (para saltar el modo silencio usual de Youtube/Spotify)
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0)

        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer.create(context, R.raw.loud_wake_siren)
            mediaPlayer?.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM) // Prioridad crítica
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            mediaPlayer?.isLooping = true
        }
        mediaPlayer?.start()
    }

    fun flashScreenRed(activity: Activity) {
        // Enviar brillo al 100% de hardware e ignorar Sensor de Luz ambiental
        val layoutParams = activity.window.attributes
        layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        activity.window.attributes = layoutParams

        // Mantener la pantalla encendida permanentemente mientras dure la alarma
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Mostrar fondo rojo intermitente (Implementado en el ViewModel/Compose UI)
        EventBus.publish(EmergencyUIEvent.SHOW_STROBE)
    }
}
```

---

## 4. Receptor en el Backend Python (Integración con `OficinaEficiencia`)
El backend central recibe el evento "Offline/Online" cuando el celular recupere cobertura.

```python
# src/api/telemetry.py (FastAPI Endpoint Backend)
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from storage.database_manager import DatabaseManager # Reutilizando el actual
import datetime

router = APIRouter()

class TelemetryEvent(BaseModel):
    driver_id: str
    event_type: str # "MICROSLEEP", "DISTRACTION"
    duration_ms: int
    gps_lat: float
    gps_lng: float
    timestamp: str # ISO 8601

@router.post("/v1/telemetry/events")
async def receive_driver_event(event: TelemetryEvent, db: DatabaseManager = Depends(get_db)):
    """
    Recibe la sincronización asíncrona (Pushed via Background Tasks) del móvil.
    """
    try:
        # Guardar en la base de datos central de incidentes
        db.insert_driver_alert(
            driver_id=event.driver_id,
            alert_type=event.event_type,
            duration=event.duration_ms,
            lat=event.gps_lat,
            lng=event.gps_lng,
            time=event.timestamp
        )

        # Opcional: Si es más de X segundos, mandar SMS al gerente
        if event.duration_ms > 3000:
            send_sms_to_fleet_manager(f"Chofer {event.driver_id} durmió {event.duration_ms}ms en ruta!")

        return {"status": "ok", "message": "Telemetría ingerida con éxito."}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
```
