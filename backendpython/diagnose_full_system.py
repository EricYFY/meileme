import json
import os
import sys
import redis

sys.path.append(os.path.dirname(os.path.abspath(__file__)))

from main import extract_merchants_and_res_cells
from pathfinding import AStarRouter

r = redis.Redis(host='localhost', port=6379, decode_responses=True)

print("=== 1. Redis 键状态检查 ===")
for key in ["game:state:riders", "game:rider:targets", "game:rider:status", "game:rider:orders"]:
    t = r.type(key)
    if t == 'string':
        val = r.get(key)
        print(f"{key}: string, 长度 {len(val) if val else 0}")
    elif t == 'hash':
        val = r.hgetall(key)
        print(f"{key}: hash, 字段数 {len(val)}")
    else:
        print(f"{key}: {t}")

print("\n=== 2. 自制地图 map_cd6b1e9f 路网连通性与寻路诊断 ===")
map_path = os.path.join(os.path.dirname(__file__), "saved_maps", "map_cd6b1e9f.json")
with open(map_path, "r", encoding="utf-8") as f:
    saved = json.load(f)

grid = saved["grid"]
router = AStarRouter(grid, -100)

road_cells = list(router.road_set)
print(f"自制地图总道路格子数: {len(road_cells)}")

merchants, _ = extract_merchants_and_res_cells(grid, 30, -100)
print(f"提取商家数: {len(merchants)}")

# 提取商家就近道路点
merchant_road_points = []
for m in merchants:
    mi, mj = router.find_nearest_road_idx(
        router._coord_to_idx(m["x"], m["y"])[0],
        router._coord_to_idx(m["x"], m["y"])[1]
    )
    rx, ry = router._idx_to_coord(mi, mj)
    merchant_road_points.append({"x": rx, "y": ry})

# 测试 50 对随机道路点到商家的寻路成功率
success_count = 0
total_tests = 50
for i in range(total_tests):
    spawn_i, spawn_j = road_cells[i % len(road_cells)]
    sx, sy = router._idx_to_coord(spawn_i, spawn_j)
    tar = merchant_road_points[i % len(merchant_road_points)]
    
    path = router.find_path({"x": sx, "y": sy}, tar)
    if len(path) > 1:
        success_count += 1
    else:
        print(f"寻路失败或原地: 起点 ({sx},{sy}) -> 目标 ({tar['x']},{tar['y']}), 返回 path 长度 {len(path)}")

print(f"寻路成功率: {success_count}/{total_tests} ({success_count/total_tests*100:.1f}%)")
