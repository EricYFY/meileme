import time
import json
import math
import random
import threading
import redis
from pathfinding import AStarRouter

# ================= 可视化可配置参数 (集中管理) =================
TICK_INTERVAL = 0.1          # 10Hz tick 周期 (秒)
NPC_DWELL_MIN = 2.0          # NPC 到达目的地后的驻留下限 (秒)
NPC_DWELL_MAX = 4.0          # NPC 到达目的地后的驻留上限 (秒)
POOL_ALIGN_INTERVAL = 1.0    # NPC 池每秒对齐一次的周期 (秒)
ENTRANCE_SEARCH_RADIUS = 8   # 园区入口附近出生点的搜索半径 (格)

# 员工岗位 → 关联建筑类型 (员工优先在这些建筑附近往返；无相关建筑则随机)
STAFF_BUILDING_PREFERENCE = {
    "FRONT_DESK": {"STANDARD_ROOM", "LUXURY_SUITE"},
    "CLEANING": {"STANDARD_ROOM", "LUXURY_SUITE"},
    "CHEF": {"RESTAURANT"},
    "MAINTENANCE": {"SPA", "VIEWPOINT", "PARKING"},
}
# Java 尚未下发足量员工时用于补齐的默认岗位
DEFAULT_STAFF_TYPES = ["FRONT_DESK", "CLEANING", "CHEF", "MAINTENANCE"]


class ResortEngine:
    """
    度假村可视化引擎：仅驱动游客/员工沿园区路网走动并输出坐标，纯动画。
    不产生任何业务事件，也不读写任何业务 Redis 键。
    """

    def __init__(self, map_data, redis_host="localhost", redis_port=6379):
        self.map_data = map_data
        self.grid = map_data["grid"]
        self.grid_min = map_data.get("min", -100)
        self.router = AStarRouter(self.grid, self.grid_min)
        self.running = False
        self.paused = False

        # NPC 池: id -> state (游客 / 员工)
        self.guests = {}
        self.staff = {}
        self._guest_seq = 0
        self._last_align = 0.0

        # 目的地候选点 (每 tick 从 Redis 刷新)
        self._building_points = []   # [{"type":..., "x":..., "y":...}]
        self._staff_points = []      # [{"x":..., "y":...}]

        # 园区入口 (距 (0,0) 最近的道路格) 及其附近的出生点集合
        self.spawn_cells = self._build_spawn_cells()

        # Redis 客户端
        try:
            self.redis_client = redis.Redis(host=redis_host, port=redis_port, decode_responses=True)
            self.redis_client.ping()
        except Exception as e:
            print(f"[Engine] Redis 连接失败: {e}. 请确保 Redis 已启动。")
            self.redis_client = None

    # ================= 出生点 =================
    def _build_spawn_cells(self):
        """园区入口 = 距 (0,0) 最近的道路格；出生点在入口附近随机分布。"""
        ei, ej = self.router.find_nearest_road_idx(
            *self.router._coord_to_idx(0, 0)
        )
        cells = []
        for (i, j) in self.router.road_set:
            if abs(i - ei) + abs(j - ej) <= ENTRANCE_SEARCH_RADIUS:
                cells.append(self.router._idx_to_coord(i, j))
        if cells:
            return cells
        return [self.router._idx_to_coord(ei, ej)]

    def _random_spawn(self):
        x, y = random.choice(self.spawn_cells)
        return {"x": float(x), "y": float(y)}

    # ================= 生命周期 =================
    def start(self):
        self.running = True
        self.paused = False
        self._last_align = 0.0

        # 启动前清空旧的 NPC 状态键，避免跨次启动残留
        if self.redis_client:
            try:
                self.redis_client.delete("game:state:npc")
            except Exception as e:
                print(f"[Engine] 启动清空 Redis 异常: {e}")

        self._align_pools()
        self._write_state()

        self.thread = threading.Thread(target=self._tick_loop)
        self.thread.daemon = True
        self.thread.start()
        print("[Engine] 度假村可视化引擎已启动 (10Hz)")

    def pause(self):
        self.paused = True
        print("[Engine] 度假村可视化引擎已暂停")

    def resume(self):
        self.paused = False
        print("[Engine] 度假村可视化引擎已继续")

    def stop(self):
        self.running = False
        self.paused = False
        if hasattr(self, 'thread') and self.thread.is_alive():
            self.thread.join(timeout=1.0)
        if self.redis_client:
            try:
                self.redis_client.delete("game:state:npc")
            except Exception as e:
                print(f"[Engine] 清理 Redis Key 异常: {e}")
        print("[Engine] 度假村可视化引擎已停止")

    # ================= 主 tick 循环 =================
    def _tick_loop(self):
        while self.running:
            if self.paused:
                time.sleep(TICK_INTERVAL)
                continue

            # 全局倍速: 从 Redis 读取 Java 下发的倍速, 缩放物理步长 dt (0.1s * speed)
            spd = 1.0
            if self.redis_client:
                try:
                    spd = float(self.redis_client.get("game:simulation:speed") or "1.0")
                except Exception:
                    spd = 1.0
            if spd <= 0:
                spd = 1.0
            dt = TICK_INTERVAL * spd

            start_time = time.time()

            # 对齐周期随倍速缩放: 高倍速下更频繁补齐/裁剪 NPC，避免数量长期落后；下限 0.2s
            align_interval = max(POOL_ALIGN_INTERVAL / spd, 0.2)
            if not self._last_align or (start_time - self._last_align) >= align_interval:
                self._align_pools()
                self._last_align = start_time

            # 目的地候选点每 tick 从 Redis 刷新一次 (子步复用，避免高倍速下重复读 Redis)
            self._refresh_points()

            # 子步拆分: 单个子步不超过 TICK_INTERVAL，避免高倍速下单步过大导致 NPC 瞬移
            steps = max(1, int(math.ceil(dt / TICK_INTERVAL)))
            sub_dt = dt / steps
            for _ in range(steps):
                self._update_npcs(sub_dt)

            self._write_state()

            # 保持 10Hz
            elapsed = time.time() - start_time
            time.sleep(max(TICK_INTERVAL - elapsed, 0))

    # ================= NPC 池对齐 =================
    def _align_pools(self):
        """按 game:resort:npc_plan 对齐游客/员工数量：不足从园区入口附近补齐，超出则移除多余。"""
        plan = {"guests": 0, "staff": 0}
        if self.redis_client:
            try:
                raw = self.redis_client.get("game:resort:npc_plan")
                if raw:
                    d = json.loads(raw)
                    plan["guests"] = max(0, int(d.get("guests", 0)))
                    plan["staff"] = max(0, int(d.get("staff", 0)))
            except Exception:
                pass

        # ---- 游客: 全量对齐 (有多少渲染多少，禁止设上限) ----
        target_g = plan["guests"]
        while len(self.guests) < target_g:
            self._guest_seq += 1
            gid = f"g-{self._guest_seq:03d}"
            self.guests[gid] = self._new_npc(gid, None)
        while len(self.guests) > target_g:
            self.guests.popitem()  # 移除最近加入的多余游客

        # ---- 员工: 优先采用 Java 下发的员工 id/岗位，不足则补齐 ----
        target_s = plan["staff"]
        meta = self._read_staff_meta()
        desired = list(meta.items())
        k = 0
        while len(desired) < target_s:
            sid = f"s-{k + 1:03d}"
            if sid not in meta:
                desired.append((sid, {"type": DEFAULT_STAFF_TYPES[k % len(DEFAULT_STAFF_TYPES)]}))
            k += 1
        desired = desired[:target_s]

        desired_ids = set()
        for sid, info in desired:
            desired_ids.add(sid)
            if sid not in self.staff:
                self.staff[sid] = self._new_npc(sid, info.get("type"))
            else:
                self.staff[sid]["type"] = info.get("type")
        for sid in list(self.staff.keys()):
            if sid not in desired_ids:
                del self.staff[sid]

    def _new_npc(self, npc_id, npc_type):
        return {
            "id": npc_id,
            "type": npc_type,
            "currentPosition": self._random_spawn(),
            "path": [],
            "status": 0,
            "dwellUntil": time.time() + random.uniform(0.0, NPC_DWELL_MAX),
        }

    # ================= 候选点读取 =================
    def _read_building_points(self):
        """从 Redis 读取 Java 下发的建筑锚点 (含类型)，作为可视化目的地候选。"""
        pts = []
        if not self.redis_client:
            return pts
        try:
            for v in self.redis_client.hgetall("game:resort:buildings").values():
                d = json.loads(v)
                if "x" in d and "y" in d:
                    pts.append({"type": d.get("type"), "x": float(d["x"]), "y": float(d["y"])})
        except Exception:
            pass
        return pts

    def _read_staff_points(self):
        """从 Redis 读取 Java 下发的员工岗位锚点，作为可视化目的地候选。"""
        return [{"x": p["x"], "y": p["y"]} for p in self._read_staff_meta().values()
                if "x" in p and "y" in p]

    def _read_staff_meta(self):
        """读取 game:resort:staff → {staffId: {"type":..., "x":..., "y":...}}"""
        meta = {}
        if not self.redis_client:
            return meta
        try:
            for sid, v in self.redis_client.hgetall("game:resort:staff").items():
                d = json.loads(v)
                item = {"type": d.get("type")}
                if "x" in d and "y" in d:
                    item["x"] = float(d["x"])
                    item["y"] = float(d["y"])
                meta[sid] = item
        except Exception:
            pass
        return meta

    # ================= NPC 移动 =================
    def _refresh_points(self):
        """每 tick 从 Redis 刷新一次目的地候选点 (供所有子步复用)。"""
        self._building_points = self._read_building_points()
        self._staff_points = self._read_staff_points()

    def _update_npcs(self, dt):
        now = time.time()

        # 候选点集合：建筑锚点 + 员工岗位锚点
        all_points = [{"x": p["x"], "y": p["y"]} for p in self._building_points] + self._staff_points

        for npc in list(self.guests.values()):
            self._advance_npc(npc, dt, now, all_points)

        for npc in list(self.staff.values()):
            pool = self._staff_target_pool(npc) or all_points
            self._advance_npc(npc, dt, now, pool)

    def _staff_target_pool(self, npc):
        """员工优先在与其岗位相关的建筑附近往返。"""
        pref = STAFF_BUILDING_PREFERENCE.get(npc.get("type"))
        if not pref:
            return []
        return [{"x": p["x"], "y": p["y"]} for p in self._building_points if p.get("type") in pref]

    def _advance_npc(self, npc, dt, now, pool):
        # 1. 无路径且已过驻留时间 → 从候选点随机挑一个目标并 A* 寻路
        if not npc["path"]:
            if now < npc["dwellUntil"]:
                npc["status"] = 0
                return
            if not pool:
                npc["status"] = 0
                npc["dwellUntil"] = now + random.uniform(NPC_DWELL_MIN, NPC_DWELL_MAX)
                return
            tgt = random.choice(pool)
            npc["path"] = self.router.find_path(npc["currentPosition"], tgt)
            npc["status"] = 1

        # 2. 沿路点匀速前进
        if npc["path"]:
            self._move_along_path(npc, dt)
            if not npc["path"]:
                # 到达 (距离 < 0.05 由移动逻辑保证) → 驻留 2~4 秒
                npc["status"] = 0
                npc["dwellUntil"] = now + random.uniform(NPC_DWELL_MIN, NPC_DWELL_MAX)

    def _move_along_path(self, npc, dt):
        curr_x = npc["currentPosition"]["x"]
        curr_y = npc["currentPosition"]["y"]

        # 按当前所在道路类型决定速度 (沿用道路速度)
        speed = self.router.get_road_speed_by_coord(curr_x, curr_y)
        budget = speed * dt

        while budget > 0 and npc["path"]:
            nxt = npc["path"][0]
            nx, ny = nxt["x"], nxt["y"]
            dx = nx - curr_x
            dy = ny - curr_y
            seg_dist = math.sqrt(dx * dx + dy * dy)

            if seg_dist <= budget or seg_dist < 0.05:
                curr_x, curr_y = nx, ny
                budget -= seg_dist
                npc["path"].pop(0)
            else:
                curr_x += (dx / seg_dist) * budget
                curr_y += (dy / seg_dist) * budget
                budget = 0

        npc["currentPosition"]["x"] = float(curr_x)
        npc["currentPosition"]["y"] = float(curr_y)

    # ================= 状态输出 =================
    def _npc_export(self):
        guests = [
            {"id": n["id"], "x": round(n["currentPosition"]["x"], 2),
             "y": round(n["currentPosition"]["y"], 2), "status": n["status"]}
            for n in self.guests.values()
        ]
        staff = [
            {"id": n["id"], "type": n["type"], "x": round(n["currentPosition"]["x"], 2),
             "y": round(n["currentPosition"]["y"], 2)}
            for n in self.staff.values()
        ]
        return {"guests": guests, "staff": staff}

    def _write_state(self):
        if not self.redis_client:
            return
        try:
            self.redis_client.set("game:state:npc", json.dumps(self._npc_export()))
        except Exception as e:
            print(f"[Engine] 写入 game:state:npc 异常: {e}")
