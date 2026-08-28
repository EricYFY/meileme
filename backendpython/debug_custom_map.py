import json
import os
import sys
import time

sys.path.append(os.path.dirname(os.path.abspath(__file__)))

from main import extract_merchants_and_res_cells
from engine import SimulationEngine

map_path = os.path.join(os.path.dirname(__file__), "saved_maps", "map_cd6b1e9f.json")
with open(map_path, "r", encoding="utf-8") as f:
    saved = json.load(f)

grid = saved["grid"]
grid_min = -100

merchants, residential_cells = extract_merchants_and_res_cells(grid, 40, grid_min)
print(f"提取商家数: {len(merchants)}, 提取送餐点数: {len(residential_cells)}")

map_data = {
    "size": len(grid),
    "min": grid_min,
    "max": grid_min + len(grid) - 1,
    "grid": grid,
    "merchants": merchants,
    "residentialCells": residential_cells
}

engine = SimulationEngine(map_data, rider_count=100, redis_port=6379)
print(f"初始骑手数量: {len(engine.riders)}")
print(f"merchant_road_points 数量: {len(engine.merchant_road_points)}")

# 运行 30 个 tick (3秒)
dt = 0.1
for tick in range(30):
    t0 = time.time()
    engine._update_riders(dt)
    cost = time.time() - t0
    
    cruising_count = sum(1 for r in engine.riders.values() if r["isCruising"])
    has_path_count = sum(1 for r in engine.riders.values() if len(r["path"]) > 0)
    print(f"Tick {tick}: 执行耗时 {cost*1000:.1f}ms, isCruising 骑手数: {cruising_count}, 有 path 骑手数: {has_path_count}")
    time.sleep(0.05)

moved = 0
for r_id, r in engine.riders.items():
    dist = (r["currentPosition"]["x"]**2 + r["currentPosition"]["y"]**2)**0.5
    if r["isCruising"] or r["path"]:
        moved += 1

print(f"\n正在移动/巡游中的骑手数量: {moved}/100")
