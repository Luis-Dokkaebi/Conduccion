import pytest
from fastapi.testclient import TestClient
from src.api import app, db_drivers

client = TestClient(app)

def setup_function():
    # Clear the simulated database before each test
    db_drivers.clear()

def test_sync_events_empty():
    response = client.post("/api/v1/telemetry/events", json=[])
    assert response.status_code == 200
    assert response.json() == {"status": "success", "message": "No events provided", "processed": 0}

def test_sync_events_microsleep():
    payload = [
        {
            "id": 1,
            "driverId": "driver_test",
            "eventType": "MICROSLEEP",
            "timestamp": 1700000000,
            "earValue": 0.15,
            "durationSeconds": 2.0,
            "gpsLat": 19.4326,
            "gpsLng": -99.1332
        }
    ]
    response = client.post("/api/v1/telemetry/events", json=payload)
    assert response.status_code == 200
    
    # Verify driver state
    assert "driver_test" in db_drivers
    state = db_drivers["driver_test"]
    assert state.frs_score == 35.0  # >1.5s microsleep gives 35 pts

def test_sync_events_yawn_and_distraction():
    payload = [
        {
            "id": 2,
            "driverId": "driver_test_2",
            "eventType": "YAWN",
            "timestamp": 1700000000,
            "earValue": 0.3,
            "durationSeconds": 3.0,
            "gpsLat": 0.0,
            "gpsLng": 0.0
        },
        {
            "id": 3,
            "driverId": "driver_test_2",
            "eventType": "DISTRACTION",
            "timestamp": 1700000010,
            "earValue": 0.3,
            "durationSeconds": 4.0,
            "gpsLat": 0.0,
            "gpsLng": 0.0
        }
    ]
    response = client.post("/api/v1/telemetry/events", json=payload)
    assert response.status_code == 200
    
    state = db_drivers["driver_test_2"]
    # 5 pts for yawn + 8 pts for distraction
    assert state.frs_score == 13.0

def test_get_clearance_blocked_fatigue():
    # Inject high FRS score manually
    from src.api import DriverState
    import time
    
    state = DriverState()
    state.frs_score = 80.0 # Above 75 is BLOCKED_FATIGUE
    state.last_update_ts = time.time()
    db_drivers["fatigued_driver"] = state
    
    response = client.get("/api/v1/mobile_dms/clearance/fatigued_driver")
    assert response.status_code == 200
    data = response.json()
    
    assert data["status"] == "BLOCKED_FATIGUE"
    assert data["mandatory_rest_minutes"] > 0
    assert "fatiga extrema" in data["message"]

def test_get_clearance_warning():
    from src.api import DriverState
    import time
    
    state = DriverState()
    state.frs_score = 60.0 # Between 50 and 75 is WARNING
    state.last_update_ts = time.time()
    db_drivers["warning_driver"] = state
    
    response = client.get("/api/v1/mobile_dms/clearance/warning_driver")
    assert response.status_code == 200
    data = response.json()
    
    assert data["status"] == "WARNING"
    assert data["mandatory_rest_minutes"] == 0

def test_get_clearance_allowed():
    response = client.get("/api/v1/mobile_dms/clearance/new_driver")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "ALLOWED"
    assert data["frs_score"] == 0.0
