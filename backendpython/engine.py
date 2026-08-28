import time
import json
import math
import random
import threading
import redis
from pathfinding import AStarRouter

class SimulationEngine:
    def __init__(self, map_data, rider_count=10, redis_host="localhost", redis_port=6379):
        self.map_data = map_data
        self.grid = map_data["grid"]
        self.grid_min = map_data.get("min", -100)
        self.router = AStarRouter(self.grid, self.grid_min)
        self.rider_count = rider_count
        self.running = False
        self.paused = False
        
        # 提取地图中的商家及其就近道路坐标，供空闲寻热巡游使用
        self.merchants = map_data.get("merchants", [])
        self.merchant_road_points = []
        for m in self.merchants:
            mi, mj = self.router.find_nearest_road_idx(
                self.router._coord_to_idx(m["x"], m["y"])[0],
                self.router._coord_to_idx(m["x"], m["y"])[1]
            )
            rx, ry = self.router._idx_to_coord(mi, mj)
            self.merchant_road_points.append({
                "merchantId": m.get("id"),
                "x": rx,
                "y": ry
            })

        # Redis 客户端
        try:
            self.redis_client = redis.Redis(host=redis_host, port=redis_port, decode_responses=True)
            self.redis_client.ping()
        except Exception as e:
            print(f"[Engine] Redis 连接失败: {e}. 请确保 Redis 已启动。")
            self.redis_client = None

        # 收集所有合法的马路格子作为可选出生点 (随机分散出生)
        road_cells = list(self.router.road_set)
        if not road_cells:
            spawn_i, spawn_j = self.router.find_nearest_road_idx(
                self.router._coord_to_idx(0, 0)[0],
                self.router._coord_to_idx(0, 0)[1]
            )
            road_cells = [(spawn_i, spawn_j)]

        # 初始骑手状态 (随机分散出生在马路上)
        self.riders = {}
        for i in range(1, self.rider_count + 1):
            rider_id = f"rider-{i:03d}"
            spawn_i, spawn_j = random.choice(road_cells)
            spawn_x, spawn_y = self.router._idx_to_coord(spawn_i, spawn_j)
            spawn_speed = self.router.get_road_speed_by_coord(spawn_x, spawn_y)
            
            self.riders[rider_id] = {
                "id": rider_id,
                "currentPosition": {"x": spawn_x, "y": spawn_y},
                "targetPosition": None,
                "plannedTarget": None, # 记录当前已规划路径的目标
                "path": [],            # 关键拐点路径序列
                "speed": spawn_speed,  # 初始道路速度
                "status": 0,
                "currentOrderId": None,
                # === 空闲主动寻热巡游字段 ===
                "isCruising": False,
                "cruiseTarget": None,
                "idleWaitUntil": time.time() + random.uniform(1.0, 3.0)
            }

    def start(self):
        self.running = True
        self.paused = False
        
        # ★ 启动前彻底清空旧的 Redis 残留键，防止跨次启动的状态污染与死锁
        if self.redis_client:
            try:
                keys_to_clean = ["game:state:riders", "game:rider:status", "game:rider:orders", "game:rider:targets", "game:events:reach_target"]
                self.redis_client.delete(*keys_to_clean)
            except Exception as e:
                print(f"[Engine] 启动清空 Redis 异常: {e}")

            riders_list = self._get_riders_export()
            self.redis_client.set("game:state:riders", json.dumps(riders_list))
            print(f"[Engine] 已重置 Redis 并写入 {self.rider_count} 个骑手的初始状态")
        
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

    def _update_idle_cruising(self):
        """
        空闲骑手主动寻热巡游调度算法：
        根据商圈出单期望、通行距离及周边骑手竞争惩罚，为停滞的空闲骑手规划前往热点商圈的巡航路径。
        """
        if not self.merchant_road_points:
            return

        now = time.time()

        # 统计当前各个商圈周边或正在前往该商圈的空闲骑手数量 (用于防扎堆惩罚)
        merchant_crowd = {idx: 0 for idx in range(len(self.merchant_road_points))}
        for r in self.riders.values():
            if r["status"] == 0:
                rx = r["currentPosition"]["x"]
                ry = r["currentPosition"]["y"]
                for idx, mp in enumerate(self.merchant_road_points):
                    dist = math.hypot(rx - mp["x"], ry - mp["y"])
                    if dist < 15.0 or (r["isCruising"] and r.get("cruiseMerchantIdx") == idx):
                        merchant_crowd[idx] += 1

        # 遍历需要巡游调度的空闲骑手
        for rider in self.riders.values():
            # 只有当骑手处于空闲状态、无业务订单、且当前没有任务在身时
            if rider["status"] == 0 and rider["currentOrderId"] is None and rider["targetPosition"] is None:
                # 检查待命驻留冷却
                if now < rider["idleWaitUntil"] or rider["path"]:
                    continue

                rx = rider["currentPosition"]["x"]
                ry = rider["currentPosition"]["y"]

                # 计算各个商家的吸引力效用得分
                scores = []
                for idx, mp in enumerate(self.merchant_road_points):
                    dist = math.hypot(rx - mp["x"], ry - mp["y"])
                    crowd = merchant_crowd[idx]
                    # 效用函数: 距离衰减 + 竞争惩罚 (避免全城骑手挤在同一家店)
                    attractiveness = 1.0 / (max(dist, 2.0) * (1.0 + 0.8 * crowd))
                    scores.append(attractiveness)

                total_score = sum(scores)
                if total_score > 0:
                    chosen_idx = random.choices(range(len(scores)), weights=scores, k=1)[0]
                    chosen_point = self.merchant_road_points[chosen_idx]

                    # 规划巡游路径
                    target_pos = {"x": chosen_point["x"], "y": chosen_point["y"]}
                    path = self.router.find_path(rider["currentPosition"], target_pos)

                    if len(path) > 1:
                        rider["isCruising"] = True
                        rider["cruiseMerchantIdx"] = chosen_idx
                        rider["cruiseTarget"] = target_pos
                        rider["plannedTarget"] = target_pos
                        rider["path"] = path
                        merchant_crowd[chosen_idx] += 1
                    else:
                        # 骑手已经在该商圈路口，进入 2~4 秒驻留
                        rider["idleWaitUntil"] = now + random.uniform(2.0, 4.0)

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
                        new_status = int(status_updates[r_id])
                        if new_status != rider["status"]:
                            rider["status"] = new_status
                            # 如果状态变为非空闲(如被派单 Status 1/2)，立即打断空闲巡游
                            if new_status != 0 and rider["isCruising"]:
                                rider["isCruising"] = False
                                rider["path"] = []
                                rider["plannedTarget"] = None
                    
                    if r_id in orders:
                        new_order = orders[r_id] if orders[r_id] != "null" else None
                        if new_order != rider["currentOrderId"]:
                            rider["currentOrderId"] = new_order
                            if new_order is not None and rider["isCruising"]:
                                rider["isCruising"] = False
                                rider["path"] = []
                                rider["plannedTarget"] = None

                    # 更新目标坐标
                    if r_id in targets:
                        target_str = targets[r_id]
                        if target_str and target_str != "null":
                            target = json.loads(target_str)
                            # 只有真正下发了新的有效业务目标点时才更新并打断巡游
                            if (rider["targetPosition"] is None or 
                                abs(rider["targetPosition"]["x"] - target["x"]) > 0.1 or 
                                abs(rider["targetPosition"]["y"] - target["y"]) > 0.1):
                                if rider["isCruising"]:
                                    rider["isCruising"] = False
                                    rider["path"] = []
                                    rider["plannedTarget"] = None
                                rider["targetPosition"] = target
                        else:
                            # 目标为 null 表示骑手当前无业务目标
                            if rider["targetPosition"] is not None:
                                rider["targetPosition"] = None
                                rider["plannedTarget"] = None
                            
            except Exception as e:
                pass # 忽略 Redis 偶发读取错误

        # 2. 调度空闲骑手主动寻热巡游
        self._update_idle_cruising()

        # 3. 寻路与沿道路网移动
        for rider in self.riders.values():
            tar = rider["targetPosition"]
            
            # 若有业务目标点，优先响应业务订单目标
            if tar is not None:
                if (rider["plannedTarget"] is None or 
                    abs(rider["plannedTarget"]["x"] - tar["x"]) > 0.1 or 
                    abs(rider["plannedTarget"]["y"] - tar["y"]) > 0.1):
                    
                    # 重新规划道路路径
                    rider["path"] = self.router.find_path(rider["currentPosition"], tar)
                    rider["plannedTarget"] = {"x": tar["x"], "y": tar["y"]}
            elif not rider["isCruising"]:
                # 如果没有业务目标也不是巡游状态，清空路径
                rider["path"] = []
                rider["plannedTarget"] = None

            # 执行沿路物理移动
            if rider["path"]:
                curr_x = rider["currentPosition"]["x"]
                curr_y = rider["currentPosition"]["y"]
                
                # 根据当前所在道路类型动态调整速度 (主干道 9.0, 大路 6.5, 小路 4.0)
                # 若为空闲巡游，以正常道路速度巡航移动
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
                    if rider["isCruising"]:
                        # 到达空闲巡游商圈点，进入 3~5 秒商圈待命驻留
                        rider["isCruising"] = False
                        rider["cruiseTarget"] = None
                        rider["plannedTarget"] = None
                        rider["idleWaitUntil"] = time.time() + random.uniform(3.0, 5.0)
                    else:
                        # 业务订单到达
                        rider["targetPosition"] = None
                        rider["plannedTarget"] = None
                        
                        # 通过 Redis 发送到达事件通知 Java
                        if self.redis_client:
                            self.redis_client.lpush("game:events:reach_target", json.dumps({
                                "riderId": rider["id"],
                                "status": rider["status"],
                                "orderId": rider["currentOrderId"]
                            }))
