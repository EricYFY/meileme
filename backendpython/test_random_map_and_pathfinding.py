import sys
import os
import random

sys.path.append(os.path.dirname(os.path.abspath(__file__)))

from map_generator import MapGenerator, TILE_ROAD_MAIN, TILE_ROAD_BIG, TILE_ROAD_SMALL
from pathfinding import AStarRouter

def test_random_map_generation():
    print("=== 开始测试随机地图生成与 8 方向 A* 寻路 ===")
    
    for round_idx in range(1, 6):
        gen = MapGenerator(merchant_count=15)
        gen.generate()
        map_data = gen.get_map_data()
        
        merchants = map_data["merchants"]
        res_cells = map_data["residentialCells"]
        grid = map_data["grid"]
        
        print(f"\n[轮次 {round_idx}] 地图已生成:")
        print(f"  - 商家数量: {len(merchants)}")
        print(f"  - 临路送餐点数量: {len(res_cells)}")
        
        assert len(merchants) == 15, f"商家数应为 15，实际为 {len(merchants)}"
        assert len(res_cells) > 500, f"临路住宅送餐点过少: {len(res_cells)}"
        
        # 统计道路格子数量
        road_count = sum(row.count(1) + row.count(2) + row.count(3) for row in grid)
        print(f"  - 道路总格子数: {road_count}")
        assert road_count > 1000, "道路网格数量过少"
        
        # 测试 8 方向 A* 寻路
        router = AStarRouter(grid, map_data["min"])
        
        diagonal_move_detected = False
        success_paths = 0
        total_tests = 50
        
        for _ in range(total_tests):
            m = random.choice(merchants)
            r_coord = random.choice(res_cells)
            
            start_pos = {"x": m["x"], "y": m["y"]}
            target_pos = {"x": r_coord[0], "y": r_coord[1]}
            
            path = router.find_path(start_pos, target_pos)
            assert len(path) > 0, "寻路失败，返回空路径"
            success_paths += 1
            
            # 检查是否有斜向移动 (dx != 0 and dy != 0)
            for k in range(len(path) - 1):
                dx = abs(path[k+1]["x"] - path[k]["x"])
                dy = abs(path[k+1]["y"] - path[k]["y"])
                if dx > 0.01 and dy > 0.01:
                    diagonal_move_detected = True
        
        print(f"  - 50 对随机寻路成功率: {success_paths}/{total_tests} (100%)")
        print(f"  - 是否检测到斜向 (8方向) 移动路径: {'✅ 是' if diagonal_move_detected else '⚠️ 否'}")
        assert diagonal_move_detected, "未检测到斜向移动路径"
        
    print("\n🎉 全部 5 轮随机地图与 8 方向寻路测试 100% 顺利通过！")

if __name__ == "__main__":
    test_random_map_generation()
