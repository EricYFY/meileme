import time
import threading
import json
import redis
import math

class SimulationEngine:
    def __init__(self, map_data):
        self.map_data = map_data
        self.running = False
        
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

        # 初始骑手状态
        self.riders = {}
        for i in range(1, 11):
            rider_id = f"rider-{i:03d}"
            self.riders[rider_id] = {
                "id": rider_id,
                "currentPosition": {"x": float(0), "y": float(0)},
                "targetPosition": None,
                "speed": 5.0,
                "status": 0,
                "currentOrderId": None
            }

    def start(self):
        self.running = True
        
        # ★ 启动前先把初始骑手状态写入 Redis，让 Java 能立刻读到
        if self.redis_client:
            riders_list = list(self.riders.values())
            self.redis_client.set("game:state:riders", json.dumps(riders_list))
            print("[Engine] 已写入 10 个骑手的初始状态到 Redis")
        
        self.thread = threading.Thread(target=self._tick_loop)
        self.thread.daemon = True
        self.thread.start()
        print("[Engine] 物理模拟引擎已启动 (10Hz)")

    def _tick_loop(self):
        dt = 0.1 # 100ms
        while self.running:
            start_time = time.time()
            self._update_riders(dt)
            
            # 同步到 Redis 供 Java 读取
            if self.redis_client:
                # 把最新的骑手状态序列化为 JSON 写入 Redis 的一个 Key 中
                riders_list = list(self.riders.values())
                self.redis_client.set("game:state:riders", json.dumps(riders_list))

            # 保持 10Hz
            elapsed = time.time() - start_time
            sleep_time = max(0.1 - elapsed, 0)
            time.sleep(sleep_time)

    def _update_riders(self, dt):
        # 从 Redis 读取 Java 下发的控制指令 (例如目标点变更)
        # 这里简化处理：我们假设 Java 将指令写入 Hash: game:rider:targets (key=rider_id, value=JSON坐标)
        if self.redis_client:
            try:
                targets = self.redis_client.hgetall("game:rider:targets")
                status_updates = self.redis_client.hgetall("game:rider:status")
                orders = self.redis_client.hgetall("game:rider:orders")
                
                for r_id in self.riders.keys():
                    # 更新状态
                    if r_id in status_updates:
                        self.riders[r_id]["status"] = int(status_updates[r_id])
                    
                    if r_id in orders:
                        self.riders[r_id]["currentOrderId"] = orders[r_id] if orders[r_id] != "null" else None

                    # 更新目标坐标
                    if r_id in targets:
                        target_str = targets[r_id]
                        if target_str and target_str != "null":
                            target = json.loads(target_str)
                            self.riders[r_id]["targetPosition"] = target
                        else:
                            self.riders[r_id]["targetPosition"] = None
                            
            except Exception as e:
                pass # 忽略 Redis 偶尔的读取错误

        # 执行移动逻辑
        for rider in self.riders.values():
            if rider["targetPosition"] is not None:
                curr_x = rider["currentPosition"]["x"]
                curr_y = rider["currentPosition"]["y"]
                tar_x = rider["targetPosition"]["x"]
                tar_y = rider["targetPosition"]["y"]
                
                dx = tar_x - curr_x
                dy = tar_y - curr_y
                dist = math.sqrt(dx*dx + dy*dy)
                
                if dist > 0.0:
                    move_dist = rider["speed"] * dt
                    # 如果剩余距离小于等于一步的移动距离，或者本身就已经非常接近了（<0.05）
                    if move_dist >= dist or dist < 0.05:
                        rider["currentPosition"]["x"] = float(tar_x)
                        rider["currentPosition"]["y"] = float(tar_y)
                        
                        # 到达目标后，立刻清除目标，防止下一个 tick 重复触发
                        rider["targetPosition"] = None
                        
                        # 通过 Redis 通知 Java
                        if self.redis_client:
                            self.redis_client.lpush("game:events:reach_target", json.dumps({
                                "riderId": rider["id"],
                                "status": rider["status"],
                                "orderId": rider["currentOrderId"]
                            }))
                    else:
                        rider["currentPosition"]["x"] = curr_x + (dx/dist) * move_dist
                        rider["currentPosition"]["y"] = curr_y + (dy/dist) * move_dist

