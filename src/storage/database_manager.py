# src/storage/database_manager.py

import sqlite3
import os
from datetime import datetime

class DatabaseManager:
    def __init__(self, db_path=None):
        if db_path is None:
            from config import config
            db_path = config.LOCAL_DB_PATH
        self.db_path = db_path
        self._create_table()

    def _create_table(self):
        os.makedirs(os.path.dirname(self.db_path), exist_ok=True)
        conn = sqlite3.connect(self.db_path)
        c = conn.cursor()
        c.execute('''CREATE TABLE IF NOT EXISTS tracking (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        track_id INTEGER,
                        timestamp TEXT,
                        x REAL,
                        y REAL,
                        zone TEXT,
                        inside_zone INTEGER
                    )''')
        c.execute('''CREATE TABLE IF NOT EXISTS snapshots (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        track_id INTEGER,
                        timestamp TEXT,
                        zone TEXT,
                        snapshot_path TEXT
                    )''')
        
        c.execute('''CREATE TABLE IF NOT EXISTS daily_attendance (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        employee_name TEXT,
                        date TEXT,
                        arrival_time TEXT,
                        departure_time TEXT
                    )''')
        c.execute('''CREATE TABLE IF NOT EXISTS workday_states (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        employee_name TEXT,
                        timestamp TEXT,
                        state TEXT
                    )''')
        
        # Check for employee_name column in snapshots
        c.execute("PRAGMA table_info(snapshots)")
        columns = [info[1] for info in c.fetchall()]
        if 'employee_name' not in columns:
            print("Adding employee_name column to snapshots table...")
            c.execute("ALTER TABLE snapshots ADD COLUMN employee_name TEXT")

        # Check for employee_name column in tracking
        c.execute("PRAGMA table_info(tracking)")
        tracking_cols = [info[1] for info in c.fetchall()]
        if 'employee_name' not in tracking_cols:
            print("Adding employee_name column to tracking table...")
            c.execute("ALTER TABLE tracking ADD COLUMN employee_name TEXT")

        conn.commit()
        conn.close()

    def insert_record(self, track_id, x, y, zone, inside_zone, employee_name=None):
        conn = sqlite3.connect(self.db_path)
        c = conn.cursor()
        timestamp = datetime.now().isoformat()
        c.execute("INSERT INTO tracking (track_id, timestamp, x, y, zone, inside_zone, employee_name) VALUES (?, ?, ?, ?, ?, ?, ?)",
                  (track_id, timestamp, x, y, zone, inside_zone, employee_name))
        conn.commit()
        conn.close()

    def insert_snapshot(self, track_id, zone, snapshot_path, employee_name=None):
        conn = sqlite3.connect(self.db_path)
        c = conn.cursor()
        timestamp = datetime.now().isoformat()
        c.execute("INSERT INTO snapshots (track_id, timestamp, zone, snapshot_path, employee_name) VALUES (?, ?, ?, ?, ?)",
                  (track_id, timestamp, zone, snapshot_path, employee_name))
        conn.commit()
        conn.close()

    def get_all_records(self):
        conn = sqlite3.connect(self.db_path)
        c = conn.cursor()
        c.execute("SELECT * FROM tracking")
        rows = c.fetchall()
        conn.close()
        return rows

    def insert_state(self, employee_name, state):
        conn = sqlite3.connect(self.db_path)
        c = conn.cursor()
        timestamp = datetime.now().isoformat()
        c.execute("INSERT INTO workday_states (employee_name, timestamp, state) VALUES (?, ?, ?)",
                  (employee_name, timestamp, state))
        conn.commit()
        conn.close()

    def update_attendance(self, employee_name):
        conn = sqlite3.connect(self.db_path)
        c = conn.cursor()
        now = datetime.now()
        date_str = now.strftime("%Y-%m-%d")
        time_str = now.strftime("%H:%M:%S")

        # Check if record exists for today
        c.execute("SELECT id FROM daily_attendance WHERE employee_name = ? AND date = ?", (employee_name, date_str))
        row = c.fetchone()

        if row:
            # Update departure time
            c.execute("UPDATE daily_attendance SET departure_time = ? WHERE id = ?", (time_str, row[0]))
        else:
            # Insert new record (first seen today)
            c.execute("INSERT INTO daily_attendance (employee_name, date, arrival_time, departure_time) VALUES (?, ?, ?, ?)",
                      (employee_name, date_str, time_str, time_str))
        
        conn.commit()
        conn.close()
