from fastapi import FastAPI, HTTPException, BackgroundTasks
from pydantic import BaseModel
from typing import List, Dict, Optional
import time
import math

app = FastAPI(title="DMS Telemetry Sync API")

class MicroSleepEvent(BaseModel):
    id: int
    driverId: str = "driver_123" # Default for backwards compatibility
    eventType: str = "MICROSLEEP" # e.g. MICROSLEEP, YAWN, DISTRACTION
    timestamp: int
    earValue: float
    durationSeconds: float
    gpsLat: float
    gpsLng: float

# Simulated Database
class DriverState(BaseModel):
    frs_score: float = 0.0
    last_update_ts: float = 0.0
    reached_red: bool = False
    events_history: List[dict] = []

db_drivers: Dict[str, DriverState] = {}

def calculate_decay(driver_id: str, current_ts: float):
    """
    Task 8.1.2: Restar 20pts por cada hora real de descanso.
    """
    if driver_id not in db_drivers:
        return

    state = db_drivers[driver_id]
    if state.last_update_ts == 0.0:
        return

    hours_passed = (current_ts - state.last_update_ts) / 3600.0
    if hours_passed > 0:
        decay = 20.0 * hours_passed
        state.frs_score = max(0.0, state.frs_score - decay)
        state.last_update_ts = current_ts

@app.post("/api/v1/telemetry/events")
async def sync_events(events: List[MicroSleepEvent]):
    """
    Task 6.2.1: Crear endpoint POST /api/v1/telemetry/events (FastAPI).
    Receives JSON events from the mobile devices and simulates processing.
    """
    if not events:
        return {"status": "success", "message": "No events provided", "processed": 0}

    # Simulate saving events to a database or updating the web panel
    print(f"Received {len(events)} telemetry events.")

    current_time = time.time()

    for event in events:
        driver_id = event.driverId
        if driver_id not in db_drivers:
            db_drivers[driver_id] = DriverState()
            db_drivers[driver_id].last_update_ts = current_time

        state = db_drivers[driver_id]

        # Apply decay first
        calculate_decay(driver_id, current_time)

        # Task 8.1.1: Asignar puntajes FRS (Fatigue Risk Score)
        points = 0.0
        if event.eventType == "MICROSLEEP":
            if event.durationSeconds >= 1.5:
                points = 35.0 # Critical
            elif event.durationSeconds >= 0.8:
                points = 10.0 # Aborted
        elif event.eventType == "YAWN":
            points = 5.0
        elif event.eventType == "DISTRACTION":
            points = 8.0

        state.frs_score = min(100.0, state.frs_score + points)
        if state.frs_score >= 75.0:
            state.reached_red = True

        state.events_history.append({
            "timestamp": event.timestamp,
            "type": event.eventType,
            "duration": event.durationSeconds,
            "points": points
        })
        state.last_update_ts = current_time

        print(f" - Event ID: {event.id}, Type: {event.eventType}, Duration: {event.durationSeconds}s. Added {points} pts. Total FRS: {state.frs_score}")

    return {"status": "success", "processed": len(events)}

@app.post("/api/v1/mobile_dms/end_shift/{driver_id}")
async def end_shift(driver_id: str, background_tasks: BackgroundTasks):
    """
    Task 8.3.3: Generar PDF al finalizar el turno del chofer.
    """
    if driver_id not in db_drivers:
        return {"status": "error", "message": "No data for driver"}

    state = db_drivers[driver_id]

    # Import and run pdf_report logic in background
    from pdf_report import generate_shift_summary_pdf
    background_tasks.add_task(generate_shift_summary_pdf, driver_id, state.events_history, state.reached_red)

    return {"status": "success", "message": "Shift ended, report queued."}

@app.get("/api/v1/mobile_dms/clearance/{driver_id}")
async def get_clearance(driver_id: str):
    """
    Task 8.2.1: Crear endpoint /api/v1/mobile_dms/clearance/{driver_id}
    que devuelva ALLOWED, WARNING, BLOCKED_FATIGUE con sus tiempos obligatorios.
    """
    current_time = time.time()

    if driver_id not in db_drivers:
        return {
            "status": "ALLOWED",
            "frs_score": 0.0,
            "message": "Clear for Dispatch",
            "mandatory_rest_minutes": 0
        }

    state = db_drivers[driver_id]
    calculate_decay(driver_id, current_time) # Apply any pending decay

    frs = state.frs_score
    status = ""
    message = ""
    mandatory_rest_minutes = 0

    if frs < 50.0:
        status = "ALLOWED"
        message = "Clear for Dispatch"
    elif frs < 75.0:
        status = "WARNING"
        message = "Fatiga acumulada detectada. Por favor, tómese un café en los próximos 15 minutos."
    else:
        status = "BLOCKED_FATIGUE"
        message = "Por su seguridad y la de sus pasajeros, su cuenta está pausada por fatiga extrema."
        # If FRS is 85, to get below 50 at 20pts/hour, it needs (85-49) / 20 = 1.8 hours = 108 minutes
        points_to_lose = frs - 49.0
        mandatory_rest_minutes = math.ceil((points_to_lose / 20.0) * 60)

    return {
        "status": status,
        "frs_score": round(frs, 1),
        "message": message,
        "mandatory_rest_minutes": mandatory_rest_minutes
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
