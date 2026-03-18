from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import List

app = FastAPI(title="DMS Telemetry Sync API")

class MicroSleepEvent(BaseModel):
    id: int
    timestamp: int
    earValue: float
    durationSeconds: float
    gpsLat: float
    gpsLng: float

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
    for event in events:
        print(f" - Event ID: {event.id}, EAR: {event.earValue}, Duration: {event.durationSeconds}s")

    return {"status": "success", "processed": len(events)}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
