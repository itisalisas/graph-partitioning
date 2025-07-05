import osmnx
import folium
from geopy.distance import geodesic
import networkx as nx
from shapely.geometry import Point, box, Polygon, LineString
from math import atan2, cos, degrees, radians

center_lat = 59.93893094417527
center_lon = 30.32268115454809
d = 50

def get_bounding_box(center, distance):
    lat, lon = center
    north = geodesic(meters=distance).destination((lat, lon), 0).latitude
    south = geodesic(meters=distance).destination((lat, lon), 180).latitude
    east = geodesic(meters=distance).destination((lat, lon), 90).longitude
    west = geodesic(meters=distance).destination((lat, lon), 270).longitude
    return Polygon([(west, north), (east, north), (east, south), (west, south)])

print("write x coordinate or -:")
inputX = input()
if inputX != '-':
    center_lat = float(inputX)

print("write y coordinate or -:")
inputY = input()
if inputY != '-':
    center_lon = float(inputY)

print("write dist or -:")
inputDist = input()
if inputDist != '-':
    d = int(inputDist)

# Получаем bounding box для проверки
bounding_box = get_bounding_box((center_lat, center_lon), d)

gdf = osmnx.features.features_from_point((center_lat, center_lon), dist=d, tags={"building": True})

buildings = []
for idx, row in gdf.iterrows():
    try:
        if isinstance(row.geometry, Polygon):
            osm_id = idx[1] if isinstance(idx, tuple) else idx

            bounds = row.geometry.bounds  # (min_x, min_y, max_x, max_y)
            width = bounds[2] - bounds[0]  # долгота
            length = bounds[3] - bounds[1]  # широта

            centroid = row.geometry.centroid
            lat, lon = centroid.y, centroid.x

            if bounding_box.contains(centroid):
                buildings.append({
                    'id': osm_id,
                    'lat': lat,
                    'lon': lon,
                    'length': round(length * 111000),  # приближение к метрам
                    'width': round(width * 111000 * abs(cos(radians(lat))))
                })
    except Exception as e:
        print(f"Ошибка обработки здания {idx}: {e}")

with open("buildings_" + str(center_lat) + "_" + str(center_lon) + "_" + str(d) + '.txt', 'w', encoding='utf-8') as f:
    f.write(f"{len(buildings)}\n")
    for b in buildings:
        f.write(f"{b['id']} {b['lat']:.6f} {b['lon']:.6f} "
                f"{b['length']} {b['width']}\n")


print(f"Сохранено {len(buildings)} зданий в " + "buildings_" + str(center_lat) + "_" + str(center_lon) + "_" + str(d) + '.txt')