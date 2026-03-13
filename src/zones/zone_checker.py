# src/zones/zone_checker.py

import json
import os
from shapely.geometry import Point, Polygon

class ZoneChecker:
    def __init__(self, zones_path=None):
        if zones_path is None:
            from config import config
            zones_path = config.ZONAS_PATH
        self.zones = {}
        try:
            with open(zones_path, 'r') as f:
                self.zones = json.load(f)
        except FileNotFoundError:
            print(f"Aviso: Archivo de zonas '{zones_path}' no encontrado. Se asume que no hay zonas. Configúrelas en la GUI.")

        self.polygons = {}
        for name, points in self.zones.items():
            self.polygons[name] = Polygon(points)

    def check(self, x, y):
        point = Point(x, y)
        results = {}
        for name, polygon in self.polygons.items():
            results[name] = polygon.contains(point)
        return results
