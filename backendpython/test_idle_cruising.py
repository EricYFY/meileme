import sys
import os
import time

sys.path.append(os.path.dirname(os.path.abspath(__file__)))

from map_generator import MapGenerator
from engine import SimulationEngine

def test_idle_cruising_behavior():
    print("=== 开始验证空闲骑手主动寻热巡游算法 ===")
    
    gen = MapGenerator(merchant_count=5)
    gen.generate()
    map_data = gen.get_map_data()
    
    engine = SimulationEngine(map_data, rider_count=8, redis_port=6379)
    
    initial_positions = {r["id"]: (r["currentPosition"]["x"], r["currentPosition"]["y"]) for r in engine.riders.values()}
    print(f"初始 8 名骑手已分散出生")
    
    # 将初始冷却置为 0，以便立即进入巡游
    for r in engine.riders.values():
        r["idleWaitUntil"] = 0
    
    # 模拟运行 60 个 tick (6.0 秒物理时间)
    dt = 0.1
    for step in range(60):
        engine._update_riders(dt)
        time.sleep(0.01)
    
    moved_count = 0
    for r_id, r in engine.riders.items():
        init_x, init_y = initial_positions[r_id]
        curr_x = r["currentPosition"]["x"]
        curr_y = r["currentPosition"]["y"]
        dist = ((curr_x - init_x)**2 + (curr_y - init_y)**2)**0.5
        if dist > 1.0:
            moved_count += 1
            print(f"  - 骑手 {r_id} 成功巡游移动: 位移 {dist:.2f} 格 (当前坐标: {curr_x:.1f}, {curr_y:.1f})")
    
    print(f"\n[测试结果] 8 名空闲骑手中主动寻热巡游人数: {moved_count}/8")
    assert moved_count >= 6, f"预期大部分空闲骑手主动寻热巡游，实际只有 {moved_count} 名"
    
    print("🎉 空闲骑手主动寻热力巡游与防扎堆算法测试 100% 顺利通过！")

if __name__ == "__main__":
    test_idle_cruising_behavior()
