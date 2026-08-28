import numpy as np
import random
from collections import deque

# 地图常量 (201 x 201)
GRID_MIN = -100
GRID_MAX = 100
SIZE = GRID_MAX - GRID_MIN + 1  # 201

# 瓦片类型枚举
TILE_OBSTACLE = 0
TILE_ROAD_MAIN = 1   # 主干道 (3格宽)
TILE_ROAD_BIG = 2    # 大路 (2格宽)
TILE_ROAD_SMALL = 3  # 小路 (1格宽)
TILE_RES_HIGH = 4    # 高密度住宅区
TILE_RES_LOW = 5     # 低密度住宅区
TILE_COM_HIGH = 6    # 高密度商业区
TILE_COM_LOW = 7     # 低密度商业区

ROAD_TILES = {TILE_ROAD_MAIN, TILE_ROAD_BIG, TILE_ROAD_SMALL}


class MapGenerator:
    def __init__(self, merchant_count=5):
        self.grid = np.full((SIZE, SIZE), TILE_RES_LOW, dtype=np.int8)
        self.merchants = []
        self.residential_cells = []
        self.merchant_count = merchant_count

    def _coord_to_idx(self, v):
        """将逻辑坐标 (-100~100) 转为数组下标 (0~200)"""
        return v - GRID_MIN

    def _idx_to_coord(self, idx):
        """将数组下标 (0~200) 转为逻辑坐标 (-100~100)"""
        return idx + GRID_MIN

    def _bresenham_line(self, x0, y0, x1, y1):
        """Bresenham 离散直线生成算法，返回 [(i, j), ...]"""
        points = []
        i0, j0 = self._coord_to_idx(x0), self._coord_to_idx(y0)
        i1, j1 = self._coord_to_idx(x1), self._coord_to_idx(y1)
        
        dx = abs(i1 - i0)
        dy = abs(j1 - j0)
        si = 1 if i0 < i1 else -1
        sj = 1 if j0 < j1 else -1
        err = dx - dy
        
        ci, cj = i0, j0
        while True:
            if 0 <= ci < SIZE and 0 <= cj < SIZE:
                points.append((ci, cj))
            if ci == i1 and cj == j1:
                break
            e2 = 2 * err
            if e2 > -dy:
                err -= dy
                ci += si
            if e2 < dx:
                err += dx
                cj += sj
        return points

    def _draw_polyline(self, waypoints, width, tile_type):
        """
        根据一系列拐点绘制折线/斜线道路，并根据宽度进行膨胀
        width: 1 (小路), 2 (大路), 3 (主干道)
        """
        all_line_cells = set()
        for k in range(len(waypoints) - 1):
            p0 = waypoints[k]
            p1 = waypoints[k + 1]
            line_pts = self._bresenham_line(p0[0], p0[1], p1[0], p1[1])
            all_line_cells.update(line_pts)

        # 膨胀宽度并写入网格（高优先级道路不被低优先级覆盖）
        for (ci, cj) in all_line_cells:
            radius = 0
            if width == 3:
                radius = 1 # 3x3 膨胀
            elif width == 2:
                radius = 1 # 2格宽
            
            for di in range(-radius, radius + 1):
                for dj in range(-radius, radius + 1):
                    if width == 2 and (di == 1 and dj == 1):
                        continue # 2格宽微调
                    ni, nj = ci + di, cj + dj
                    if 0 <= ni < SIZE and 0 <= nj < SIZE:
                        curr_tile = self.grid[ni, nj]
                        # 道路优先级：主干道(1) > 大路(2) > 小路(3)
                        if curr_tile == TILE_ROAD_MAIN:
                            continue
                        if curr_tile == TILE_ROAD_BIG and tile_type == TILE_ROAD_SMALL:
                            continue
                        self.grid[ni, nj] = tile_type

    def generate(self):
        # ========== 第1步：初始化地图基底为低密度住宅 ==========
        self.grid[:, :] = TILE_RES_LOW

        # ========== 第2步：随机生成 4 条主干道 (3格宽，非正交穿插且中心交汇) ==========
        # 主干道 1: 纵向偏左向右下倾斜
        p1_top = (random.randint(-80, -20), 100)
        p1_mid = (random.randint(-30, 0), random.randint(-20, 20))
        p1_bot = (random.randint(20, 80), -100)
        self._draw_polyline([p1_top, p1_mid, p1_bot], width=3, tile_type=TILE_ROAD_MAIN)

        # 主干道 2: 纵向偏右向左下倾斜
        p2_top = (random.randint(20, 80), 100)
        p2_mid = (random.randint(0, 30), random.randint(-20, 20))
        p2_bot = (random.randint(-80, -20), -100)
        self._draw_polyline([p2_top, p2_mid, p2_bot], width=3, tile_type=TILE_ROAD_MAIN)

        # 主干道 3: 横向偏上向右下倾斜
        p3_left = (-100, random.randint(20, 80))
        p3_mid = (random.randint(-20, 20), random.randint(0, 30))
        p3_right = (100, random.randint(-80, -20))
        self._draw_polyline([p3_left, p3_mid, p3_right], width=3, tile_type=TILE_ROAD_MAIN)

        # 主干道 4: 横向偏下向右上倾斜
        p4_left = (-100, random.randint(-80, -20))
        p4_mid = (random.randint(-20, 20), random.randint(-30, 0))
        p4_right = (100, random.randint(20, 80))
        self._draw_polyline([p4_left, p4_mid, p4_right], width=3, tile_type=TILE_ROAD_MAIN)

        # ========== 第3步：随机生成 8 条大路 (2格宽，斜向穿插与环通) ==========
        for _ in range(8):
            # 随机选择两个不同的边界或象限节点
            start_side = random.choice(['top', 'bottom', 'left', 'right', 'inner'])
            end_side = random.choice(['top', 'bottom', 'left', 'right', 'inner'])
            
            def get_side_point(side):
                if side == 'top': return (random.randint(-90, 90), 100)
                if side == 'bottom': return (random.randint(-90, 90), -100)
                if side == 'left': return (-100, random.randint(-90, 90))
                if side == 'right': return (100, random.randint(-90, 90))
                return (random.randint(-70, 70), random.randint(-70, 70))
            
            p_start = get_side_point(start_side)
            p_end = get_side_point(end_side)
            p_mid = (random.randint(-60, 60), random.randint(-60, 60))
            
            self._draw_polyline([p_start, p_mid, p_end], width=2, tile_type=TILE_ROAD_BIG)

        # ========== 第4步：随机生成 25~35 条街区小路 (1格宽，打通街区毛细血管) ==========
        small_road_count = random.randint(25, 35)
        for _ in range(small_road_count):
            p_start = (random.randint(-95, 95), random.randint(-95, 95))
            # 小路长度 20 ~ 60 格，随机朝向（可斜向）
            angle = random.uniform(0, 2 * np.pi)
            length = random.uniform(25, 65)
            end_x = int(np.clip(p_start[0] + length * np.cos(angle), -100, 100))
            end_y = int(np.clip(p_start[1] + length * np.sin(angle), -100, 100))
            p_end = (end_x, end_y)
            
            self._draw_polyline([p_start, p_end], width=1, tile_type=TILE_ROAD_SMALL)

        # ========== 第5步：全连通性保障 (BFS Flood Fill) ==========
        self._ensure_connectivity()

        # ========== 第6步：规划商业区与高密度住宅区 ==========
        self._generate_zones()

        # ========== 第7步：严格根据临路原则放置商家 (8-邻域临路) ==========
        self._place_merchants()

        # ========== 第8步：收集所有临路的住宅区格子作为送餐点 ==========
        self._collect_residential_delivery_cells()

        print(f"[Map] 随机地图生成完毕 (201x201). 临路商家: {len(self.merchants)}, 临路送餐点格子: {len(self.residential_cells)}")

    def _ensure_connectivity(self):
        """确保所有道路连通为一个单一的大连通网络"""
        road_cells = []
        for i in range(SIZE):
            for j in range(SIZE):
                if self.grid[i, j] in ROAD_TILES:
                    road_cells.append((i, j))

        if not road_cells:
            return

        # 找最大的连通分量 (8-邻域)
        visited = set()
        components = []

        for cell in road_cells:
            if cell not in visited:
                comp = []
                queue = deque([cell])
                visited.add(cell)
                while queue:
                    ci, cj = queue.popleft()
                    comp.append((ci, cj))
                    for di in [-1, 0, 1]:
                        for dj in [-1, 0, 1]:
                            if di == 0 and dj == 0: continue
                            ni, nj = ci + di, cj + dj
                            if 0 <= ni < SIZE and 0 <= nj < SIZE:
                                if self.grid[ni, nj] in ROAD_TILES and (ni, nj) not in visited:
                                    visited.add((ni, nj))
                                    queue.append((ni, nj))
                components.append(comp)

        # 按大小排序，最大的是主连通网
        components.sort(key=lambda c: len(c), reverse=True)
        main_comp = set(components[0])

        # 将较小的孤立路段连通到主网
        for comp in components[1:]:
            if len(comp) < 5:
                # 过短孤立段直接清除为住宅
                for (ci, cj) in comp:
                    self.grid[ci, cj] = TILE_RES_LOW
                continue
            
            # 找到孤立分量与主网距离最近的两个点并连线
            p_comp = random.choice(comp)
            # 随机在主网挑 20 个点找最近的
            sampled_main = random.sample(list(main_comp), min(30, len(main_comp)))
            best_target = min(sampled_main, key=lambda p: (p[0]-p_comp[0])**2 + (p[1]-p_comp[1])**2)
            
            # 画一条小路打通
            w1 = (self._idx_to_coord(p_comp[0]), self._idx_to_coord(p_comp[1]))
            w2 = (self._idx_to_coord(best_target[0]), self._idx_to_coord(best_target[1]))
            self._draw_polyline([w1, w2], width=1, tile_type=TILE_ROAD_SMALL)
            main_comp.update(comp)

    def _generate_zones(self):
        """在非道路区域随机规划商业区与高密度住宅区"""
        # 随机挑选 14~20 个商业区中心块 (每个块大小 8x8 ~ 16x16)
        com_blocks_count = random.randint(14, 20)
        for _ in range(com_blocks_count):
            cx = random.randint(-85, 85)
            cy = random.randint(-85, 85)
            bw = random.randint(8, 16)
            bh = random.randint(8, 16)
            tile = TILE_COM_HIGH if random.random() < 0.4 else TILE_COM_LOW
            
            i0 = max(0, self._coord_to_idx(cx - bw // 2))
            j0 = max(0, self._coord_to_idx(cy - bh // 2))
            i1 = min(SIZE - 1, self._coord_to_idx(cx + bw // 2))
            j1 = min(SIZE - 1, self._coord_to_idx(cy + bh // 2))
            
            for i in range(i0, i1 + 1):
                for j in range(j0, j1 + 1):
                    if self.grid[i, j] not in ROAD_TILES:
                        self.grid[i, j] = tile

        # 随机挑选 16~24 个高密度住宅区块
        res_blocks_count = random.randint(16, 24)
        for _ in range(res_blocks_count):
            cx = random.randint(-90, 90)
            cy = random.randint(-90, 90)
            bw = random.randint(10, 20)
            bh = random.randint(10, 20)
            
            i0 = max(0, self._coord_to_idx(cx - bw // 2))
            j0 = max(0, self._coord_to_idx(cy - bh // 2))
            i1 = min(SIZE - 1, self._coord_to_idx(cx + bw // 2))
            j1 = min(SIZE - 1, self._coord_to_idx(cy + bh // 2))
            
            for i in range(i0, i1 + 1):
                for j in range(j0, j1 + 1):
                    if self.grid[i, j] not in ROAD_TILES and self.grid[i, j] not in (TILE_COM_LOW, TILE_COM_HIGH):
                        self.grid[i, j] = TILE_RES_HIGH

    def _place_merchants(self):
        """严格在与马路 8-邻域相邻的商业区格子中挑选商家"""
        roadside_com_cells = []
        for i in range(SIZE):
            for j in range(SIZE):
                if self.grid[i, j] in (TILE_COM_LOW, TILE_COM_HIGH):
                    has_adj_road = False
                    for di in [-1, 0, 1]:
                        for dj in [-1, 0, 1]:
                            if di == 0 and dj == 0: continue
                            ni, nj = i + di, j + dj
                            if 0 <= ni < SIZE and 0 <= nj < SIZE and self.grid[ni, nj] in ROAD_TILES:
                                has_adj_road = True
                                break
                        if has_adj_road:
                            break
                    if has_adj_road:
                        roadside_com_cells.append((i, j))

        # 如果商业区临路格子不够，可退化补充与道路相邻的非道路格子
        if len(roadside_com_cells) < self.merchant_count:
            for i in range(SIZE):
                for j in range(SIZE):
                    if self.grid[i, j] not in ROAD_TILES and (i, j) not in roadside_com_cells:
                        for di in [-1, 0, 1]:
                            for dj in [-1, 0, 1]:
                                if di == 0 and dj == 0: continue
                                ni, nj = i + di, j + dj
                                if 0 <= ni < SIZE and 0 <= nj < SIZE and self.grid[ni, nj] in ROAD_TILES:
                                    roadside_com_cells.append((i, j))
                                    break

        # 随机挑选指定数量的商家
        selected_cells = random.sample(roadside_com_cells, min(self.merchant_count, len(roadside_com_cells)))
        self.merchants = []
        for idx, (cx, cy) in enumerate(selected_cells):
            self.merchants.append({
                "id": f"merchant-{idx + 1}",
                "x": self._idx_to_coord(cx),
                "y": self._idx_to_coord(cy)
            })

    def _collect_residential_delivery_cells(self):
        """严格收集所有与马路 8-邻域相邻的住宅区格子作为送餐点"""
        self.residential_cells = []
        for i in range(SIZE):
            for j in range(SIZE):
                if self.grid[i, j] in (TILE_RES_HIGH, TILE_RES_LOW):
                    has_adj_road = False
                    for di in [-1, 0, 1]:
                        for dj in [-1, 0, 1]:
                            if di == 0 and dj == 0: continue
                            ni, nj = i + di, j + dj
                            if 0 <= ni < SIZE and 0 <= nj < SIZE and self.grid[ni, nj] in ROAD_TILES:
                                has_adj_road = True
                                break
                        if has_adj_road:
                            break
                    if has_adj_road:
                        self.residential_cells.append((self._idx_to_coord(i), self._idx_to_coord(j)))

    def get_map_data(self):
        return {
            "min": GRID_MIN,
            "max": GRID_MAX,
            "grid": self.grid.tolist(),
            "merchants": self.merchants,
            "residentialCells": self.residential_cells
        }
