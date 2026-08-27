import time
import threading
import json
import redis
import math
from pathfinding import AStarRouter

class SimulationEngine:
    def __init__(self, map_data, rider_count=10):
        self.map_data = map_data
        self.running = False
        self.paused = False
        self.rider_count = rider_count
        
        # 初始化道路 A* 寻路器
        self.router = AStarRouter(map_data["grid"], map_data["min"])
        
        # 连接 Redis
        try:
            self.redis_client = redis.Redis(host='localhost', port=6379, db=0, decode_responses=True)
            self.redis_client.ping()
            print("[Engine] 成功连接到 Redis")
            
            # ★ 每次启动时清理上一轮的残留数据，确保干净的初始状态
            stale_keys = self.redis_client.keys("game:*")
            if stale_keys:
                self.redis_client.delete(*stale_keys)
                print(f"[Engine] 已清理 {len(stale_keys)} 个残留 Redis Key")
                
        except Exception as e:
            print(f"[Engine] Redis 连接失败: {e}. 请确保 Redis 已启动。")
            self.redis_client = None

        # 初始骑手状态 (出生在主干道十字中心 0,0)
        self.riders = {}
        for i in range(1, self.rider_count + 1):
            rider_id = f"rider-{i:03d}"
            self.riders[rider_id] = {
                "id": rider_id,
                "currentPosition": {"x": 0.0, "y": 0.0},
                "targetPosition": None,
                "plannedTarget": None, # 记录当前已规划路径的目标
                "path": [],            # 关键拐点路径序列
                "speed": 9.0,          # 初始主干道速度
                "status": 0,
                "currentOrderId": None
            }

    def start(self):
        self.running = True
        self.paused = False
        
        # ★ 启动前先把初始骑手状态写入 Redis，让 Java 能立刻读到
        if self.redis_client:
            riders_list = self._get_riders_export()
            self.redis_client.set("game:state:riders", json.dumps(riders_list))
            print(f"[Engine] 已写入 {self.rider_count} 个骑手的初始状态到 Redis")
        
        self.thread = threading.Thread(target=self._tick_loop)
        self.thread.daemon = True
        self.thread.start()
        print("[Engine] 物理模拟引擎已启动 (10Hz)")

    def pause(self):
        self.paused = True
        print("[Engine] 物理模拟引擎已暂停")

    def resume(self):
        self.paused = False
        print("[Engine] 物理模拟引擎已继续")

    def stop(self):
        self.running = False
        self.paused = False
        if hasattr(self, 'thread') and self.thread.is_alive():
            self.thread.join(timeout=1.0)
        if self.redis_client:
            try:
                keys_to_clean = ["game:state:riders", "game:rider:status", "game:rider:orders", "game:rider:targets", "game:events:reach_target"]
                self.redis_client.delete(*keys_to_clean)
                print("[Engine] 已清理模拟相关 Redis Key")
            except Exception as e:
                print(f"[Engine] 清理 Redis Key 异常: {e}")
        print("[Engine] 物理模拟引擎已停止")

    def _get_riders_export(self):
        """导出精简的骑手状态供前端和 Java 使用"""
        export_list = []
        for r in self.riders.values():
            export_list.append({
                "id": r["id"],
                "currentPosition": {"x": r["currentPosition"]["x"], "y": r["currentPosition"]["y"]},
                "targetPosition": r["targetPosition"],
                "speed": r["speed"],
                "status": r["status"],
                "currentOrderId": r["currentOrderId"]
            })
        return export_list

    def _tick_loop(self):
        dt = 0.1 # 100ms
        while self.running:
            if self.paused:
                time.sleep(0.1)
                continue
                
            start_time = time.time()
            self._update_riders(dt)
            
            # 同步到 Redis 供 Java 读取
            if self.redis_client:
                riders_list = self._get_riders_export()
                self.redis_client.set("game:state:riders", json.dumps(riders_list))

            # 保持 10Hz
            elapsed = time.time() - start_time
            sleep_time = max(0.1 - elapsed, 0)
            time.sleep(sleep_time)

    def _update_riders(self, dt):
        # 1. 从 Redis 读取 Java 下发的控制指令 (例如目标点变更)
        if self.redis_client:
            try:
                targets = self.redis_client.hgetall("game:rider:targets")
                status_updates = self.redis_client.hgetall("game:rider:status")
                orders = self.redis_client.hgetall("game:rider:orders")
                
                for r_id, rider in self.riders.items():
                    # 更新状态
                    if r_id in status_updates:
                        rider["status"] = int(status_updates[r_id])
                    
                    if r_id in orders:
                        rider["currentOrderId"] = orders[r_id] if orders[r_id] != "null" else None

                    # 更新目标坐标
                    if r_id in targets:
                        target_str = targets[r_id]
                        if target_str and target_str != "null":
                            target = json.loads(target_str)
                            rider["targetPosition"] = target
                        else:
                            rider["targetPosition"] = None
                            
            except Exception as e:
                pass # 忽略 Redis 偶发读取错误

        # 2. 寻路与沿道路网移动
        for rider in self.riders.values():
            tar = rider["targetPosition"]
            
            # 判断是否需要重新进行 A* 寻路
            if tar is not None:
                if (rider["plannedTarget"] is None or 
                    abs(rider["plannedTarget"]["x"] - tar["x"]) > 0.1 or 
                    abs(rider["plannedTarget"]["y"] - tar["y"]) > 0.1):
                    
                    # 重新规划道路路径
                    rider["path"] = self.router.find_path(rider["currentPosition"], tar)
                    rider["plannedTarget"] = {"x": tar["x"], "y": tar["y"]}
            else:
                rider["path"] = []
                rider["plannedTarget"] = None

            # 执行沿路移动
            if rider["path"]:
                curr_x = rider["currentPosition"]["x"]
                curr_y = rider["currentPosition"]["y"]
                
                # 根据当前所在道路类型动态调整速度 (主干道 9.0, 大路 6.5, 小路 4.0)
                rider["speed"] = self.router.get_road_speed_by_coord(curr_x, curr_y)
                
                budget = rider["speed"] * dt
                
                while budget > 0 and rider["path"]:
                    next_wp = rider["path"][0]
                    nx, ny = next_wp["x"], next_wp["y"]
                    dx = nx - curr_x
                    dy = ny - curr_y
                    seg_dist = math.sqrt(dx * dx + dy * dy)
                    
                    if seg_dist <= budget or seg_dist < 0.05:
                        curr_x, curr_y = nx, ny
                        budget -= seg_dist
                        rider["path"].pop(0)
                    else:
                        curr_x += (dx / seg_dist) * budget
                        curr_y += (dy / seg_dist) * budget
                        budget = 0
                
                rider["currentPosition"]["x"] = float(curr_x)
                rider["currentPosition"]["y"] = float(curr_y)
                
                # 路径消耗完毕，到达终点
                if not rider["path"]:
                    rider["targetPosition"] = None
                    rider["plannedTarget"] = None
                    
                    # 通过 Redis 发送到达事件通知 Java
                    if self.redis_client:
                        self.redis_client.lpush("game:events:reach_target", json.dumps({
                            "riderId": rider["id"],
                            "status": rider["status"],
                            "orderId": rider["currentOrderId"]
                        }))

