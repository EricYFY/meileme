import math
import random
from collections import deque

import numpy as np

# ================= 地图常量 (201 x 201) =================
GRID_MIN = -100
GRID_MAX = 100
SIZE = GRID_MAX - GRID_MIN + 1  # 201

# ---- 瓦片类型 ----
TILE_OBSTACLE = 0    # 不可通行
TILE_ROAD_MAIN = 1   # 主干道 (3格宽)
TILE_ROAD_BIG = 2    # 大路 (2格宽)
TILE_ROAD_SMALL = 3  # 山路/小路 (1格宽)
# 4~7 为「地图编辑器」保留的旧分区瓦片，随机生成器不再使用 (仅兼容手绘地图)
TILE_RES_HIGH = 4
TILE_RES_LOW = 5
TILE_COM_HIGH = 6
TILE_COM_LOW = 7
TILE_GROUND = 8      # 平地/草地 (山脚地形)
TILE_LAKE = 9        # 湖面
TILE_MOUNTAIN = 10   # 山体坡面 (山腰)
TILE_PEAK = 11       # 山峰 (不可建造)

ROAD_TILES = {TILE_ROAD_MAIN, TILE_ROAD_BIG, TILE_ROAD_SMALL}
WATER_TILES = {TILE_LAKE}
MOUNTAIN_TILES = {TILE_MOUNTAIN}
PEAK_TILES = {TILE_PEAK}

# ---- 地块分区参数 ----
FOOTHILL_RADIUS = 46     # 山脚地块: 距园区中心 (0,0) 的半径 (格)
LAKE_SHORE_RADIUS = 7    # 湖畔地块: 距湖面 LAKE_SHORE_RADIUS 格以内

PLOT_INITIAL = "INITIAL"     # 山脚
PLOT_MOUNTAIN = "MOUNTAIN"   # 山腰
PLOT_LAKESIDE = "LAKESIDE"   # 湖畔


def analyze_map(grid, grid_min=GRID_MIN):
    """
    从任意网格推导「地块区域」与「可建造格」：
      - plots:       每个地块覆盖的全部格子 (供前端渲染分区)
      - buildable:   地块内且与道路 8-邻域相邻的格子，形如 [x, y, plotId]

    分区规则：山体坡面 → 山腰；湖面附近的平地 → 湖畔；其余平地 → 山脚。
    对没有山/湖的自定义地图 (地图编辑器手绘)，整图默认归入山脚地块。
    """
    size = len(grid)

    water_cells = []
    has_mountain = False
    for i in range(size):
        for j in range(size):
            t = grid[i][j]
            if t in WATER_TILES:
                water_cells.append((i, j))
            elif t in MOUNTAIN_TILES:
                has_mountain = True

    # 湖面距离场 (4-邻域 BFS，截断在 LAKE_SHORE_RADIUS+1 以内以省算力)
    INF = 127
    dist = [[INF] * size for _ in range(size)]
    dq = deque()
    for (i, j) in water_cells:
        dist[i][j] = 0
        dq.append((i, j))
    while dq:
        i, j = dq.popleft()
        d = dist[i][j]
        if d >= LAKE_SHORE_RADIUS + 1:
            continue
        for di, dj in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            ni, nj = i + di, j + dj
            if 0 <= ni < size and 0 <= nj < size and dist[ni][nj] > d + 1:
                dist[ni][nj] = d + 1
                dq.append((ni, nj))

    has_relief = bool(water_cells) or has_mountain
    ci = cj = 0 - grid_min  # 园区中心 (0,0) 的数组下标

    zone_of = {}
    for i in range(size):
        for j in range(size):
            t = grid[i][j]
            if t in ROAD_TILES or t in WATER_TILES or t in PEAK_TILES:
                continue
            if t in MOUNTAIN_TILES:
                zone_of[(i, j)] = PLOT_MOUNTAIN
            elif not has_relief:
                zone_of[(i, j)] = PLOT_INITIAL      # 自定义地图兜底
            elif dist[i][j] <= LAKE_SHORE_RADIUS:
                zone_of[(i, j)] = PLOT_LAKESIDE
            elif (i - ci) ** 2 + (j - cj) ** 2 <= FOOTHILL_RADIUS ** 2:
                zone_of[(i, j)] = PLOT_INITIAL

    plots = {PLOT_INITIAL: [], PLOT_MOUNTAIN: [], PLOT_LAKESIDE: []}
    buildable = []
    for (i, j), pid in zone_of.items():
        x = i + grid_min
        y = j + grid_min
        plots[pid].append([x, y])

        # 与道路 8-邻域相邻才可建造
        adj_road = False
        for di in (-1, 0, 1):
            for dj in (-1, 0, 1):
                if di == 0 and dj == 0:
                    continue
                ni, nj = i + di, j + dj
                if 0 <= ni < size and 0 <= nj < size and grid[ni][nj] in ROAD_TILES:
                    adj_road = True
                    break
            if adj_road:
                break
        if adj_road:
            buildable.append([x, y, pid])

    plot_list = [{"id": pid, "cells": cells} for pid, cells in plots.items() if cells]
    return buildable, plot_list


class MapGenerator:
    """随机生成一座「山间民宿度假村」地图：山脚园区 + 山腰山体 + 湖畔湖面。"""

    def __init__(self):
        self.grid = np.full((SIZE, SIZE), TILE_GROUND, dtype=np.int8)
        self.buildable_cells = []
        self.plots = []
        self.lake_center = (0, 0)
        self.lake_radius = (20, 16)
        self.mountain_center = (0, 0)

    # ================= 坐标换算 =================
    def _coord_to_idx(self, v):
        """将逻辑坐标 (-100~100) 转为数组下标 (0~200)。"""
        return v - GRID_MIN

    def _idx_to_coord(self, idx):
        """将数组下标 (0~200) 转为逻辑坐标 (-100~100)。"""
        return idx + GRID_MIN

    # ================= 道路绘制 =================
    def _bresenham_line(self, x0, y0, x1, y1):
        """Bresenham 离散直线，返回 [(i, j), ...]。"""
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
        根据拐点绘制折线山路，并按宽度膨胀。
        width: 1 (山路/小路), 2 (大路), 3 (主干道)
        """
        all_line_cells = set()
        for k in range(len(waypoints) - 1):
            p0 = waypoints[k]
            p1 = waypoints[k + 1]
            all_line_cells.update(self._bresenham_line(p0[0], p0[1], p1[0], p1[1]))

        for (ci, cj) in all_line_cells:
            radius = 0
            if width == 3:
                radius = 1  # 3x3 膨胀
            elif width == 2:
                radius = 1  # 2格宽

            for di in range(-radius, radius + 1):
                for dj in range(-radius, radius + 1):
                    if width == 2 and (di == 1 and dj == 1):
                        continue  # 2格宽微调
                    ni, nj = ci + di, cj + dj
                    if 0 <= ni < SIZE and 0 <= nj < SIZE:
                        curr_tile = self.grid[ni, nj]
                        # 道路优先级：主干道(1) > 大路(2) > 小路(3)
                        if curr_tile == TILE_ROAD_MAIN:
                            continue
                        if curr_tile == TILE_ROAD_BIG and tile_type == TILE_ROAD_SMALL:
                            continue
                        self.grid[ni, nj] = tile_type

    # ================= 地形形体 =================
    def _fill_blob(self, cx, cy, rx, ry, tile, inner_tile=None, inner_ratio=0.0):
        """用不规则椭圆填充一片地形；inner_ratio 以内区域填 inner_tile (如山峰)。"""
        ci, cj = self._coord_to_idx(cx), self._coord_to_idx(cy)
        ph1, ph2 = random.uniform(0, 6.283), random.uniform(0, 6.283)
        i0 = max(0, ci - int(rx * 1.4) - 2)
        i1 = min(SIZE - 1, ci + int(rx * 1.4) + 2)
        j0 = max(0, cj - int(ry * 1.4) - 2)
        j1 = min(SIZE - 1, cj + int(ry * 1.4) + 2)

        for i in range(i0, i1 + 1):
            for j in range(j0, j1 + 1):
                nx = (i - ci) / rx
                ny = (j - cj) / ry
                r = math.sqrt(nx * nx + ny * ny)
                theta = math.atan2(ny, nx)
                # 边界做小幅正弦扰动，让湖岸/山形更自然
                edge = 1.0 + 0.12 * math.sin(3 * theta + ph1) + 0.08 * math.sin(5 * theta + ph2)
                if r <= edge:
                    if inner_tile is not None and r <= inner_ratio:
                        self.grid[i, j] = inner_tile
                    else:
                        self.grid[i, j] = tile

    def _gen_lake(self):
        """在右下方位生成一片湖 (湖畔地块)。"""
        cx = random.randint(38, 74)
        cy = random.randint(-74, -38)
        rx = random.randint(18, 28)
        ry = random.randint(14, 22)
        self.lake_center = (cx, cy)
        self.lake_radius = (rx, ry)
        self._fill_blob(cx, cy, rx, ry, TILE_LAKE)

    def _gen_mountain(self):
        """在左上方位生成一座山 (山腰地块 + 不可建山峰)。"""
        cx = random.randint(-78, -42)
        cy = random.randint(42, 78)
        rx = random.randint(30, 42)
        ry = random.randint(26, 38)
        self.mountain_center = (cx, cy)
        self._fill_blob(cx, cy, rx, ry, TILE_MOUNTAIN, inner_tile=TILE_PEAK, inner_ratio=0.42)

    # ================= 道路网络 =================
    def _gen_roads(self):
        """铺设园区主干道 + 山脚小路网 + 盘山公路 + 湖畔道路。"""
        # 1) 园区中心十字主干道 (山脚集散)
        self._draw_polyline([(-82, -8), (0, 0), (82, 8)], width=3, tile_type=TILE_ROAD_MAIN)
        self._draw_polyline([(-8, -82), (0, 0), (8, 82)], width=3, tile_type=TILE_ROAD_MAIN)

        # 2) 山脚小路网 (打通园区毛细血管)
        for _ in range(20):
            ox = random.randint(-44, 44)
            oy = random.randint(-44, 44)
            angle = random.uniform(0, 2 * math.pi)
            length = random.uniform(18, 52)
            ex = int(max(-100, min(100, ox + length * math.cos(angle))))
            ey = int(max(-100, min(100, oy + length * math.sin(angle))))
            self._draw_polyline([(ox, oy), (ex, ey)], width=1, tile_type=TILE_ROAD_SMALL)

        # 3) 盘山公路: 从山脚蜿蜒上山腰 (终点停在山腰，不进入山峰)
        mcx, mcy = self.mountain_center
        tx, ty = int(mcx * 0.68), int(mcy * 0.68)
        mid1 = (int(mcx * 0.30) + random.randint(-14, 14), int(mcy * 0.30) + random.randint(-14, 14))
        mid2 = (int(mcx * 0.52) + random.randint(-12, 12), int(mcy * 0.52) + random.randint(-12, 12))
        self._draw_polyline([(0, 0), mid1, mid2, (tx, ty)], width=1, tile_type=TILE_ROAD_SMALL)

        # 4) 湖畔道路: 从山脚通往湖岸，并沿湖岸绕行一段
        lcx, lcy = self.lake_center
        lrx, lry = self.lake_radius
        shore_in = (int(lcx + lrx * 1.10), int(lcy + lry * 0.15))
        mid = (int(lcx * 0.55) + random.randint(-14, 14), int(lcy * 0.55) + random.randint(-14, 14))
        self._draw_polyline([(0, 0), mid, shore_in], width=1, tile_type=TILE_ROAD_SMALL)

        # 沿湖岸的弧形小路 (让整段湖岸均可建造)
        arc_pts = []
        for k in range(9):
            a = math.radians(-120 + 240 * k / 8)
            arc_pts.append((int(lcx + (lrx + 2) * math.cos(a)), int(lcy + (lry + 2) * math.sin(a))))
        self._draw_polyline(arc_pts, width=1, tile_type=TILE_ROAD_SMALL)

    def _ensure_connectivity(self):
        """确保所有道路连通为单一网络。"""
        road_cells = []
        for i in range(SIZE):
            for j in range(SIZE):
                if self.grid[i, j] in ROAD_TILES:
                    road_cells.append((i, j))
        if not road_cells:
            return

        visited = set()
        components = []
        for cell in road_cells:
            if cell in visited:
                continue
            comp = []
            queue = deque([cell])
            visited.add(cell)
            while queue:
                ci, cj = queue.popleft()
                comp.append((ci, cj))
                for di in (-1, 0, 1):
                    for dj in (-1, 0, 1):
                        if di == 0 and dj == 0:
                            continue
                        ni, nj = ci + di, cj + dj
                        if 0 <= ni < SIZE and 0 <= nj < SIZE:
                            if self.grid[ni, nj] in ROAD_TILES and (ni, nj) not in visited:
                                visited.add((ni, nj))
                                queue.append((ni, nj))
            components.append(comp)

        components.sort(key=lambda c: len(c), reverse=True)
        main_comp = set(components[0])

        for comp in components[1:]:
            if len(comp) < 5:
                for (ci, cj) in comp:
                    self.grid[ci, cj] = TILE_GROUND  # 过短孤立段还原为草地
                continue
            p_comp = random.choice(comp)
            sampled_main = random.sample(list(main_comp), min(30, len(main_comp)))
            best_target = min(sampled_main, key=lambda p: (p[0] - p_comp[0]) ** 2 + (p[1] - p_comp[1]) ** 2)
            w1 = (self._idx_to_coord(p_comp[0]), self._idx_to_coord(p_comp[1]))
            w2 = (self._idx_to_coord(best_target[0]), self._idx_to_coord(best_target[1]))
            self._draw_polyline([w1, w2], width=1, tile_type=TILE_ROAD_SMALL)
            main_comp.update(comp)

    # ================= 入口 =================
    def generate(self):
        self.grid[:, :] = TILE_GROUND

        self._gen_lake()
        self._gen_mountain()
        self._gen_roads()
        self._ensure_connectivity()

        # 从地形推导 地块分区 与 可建造格 (带 plotId)
        self.buildable_cells, self.plots = analyze_map(self.grid, GRID_MIN)

        counts = {p["id"]: len(p["cells"]) for p in self.plots}
        print(f"[Map] 度假村地图生成完毕 (201x201). 可建造格: {len(self.buildable_cells)}, 分区格数: {counts}")

    def get_map_data(self):
        return {
            "min": GRID_MIN,
            "max": GRID_MAX,
            "grid": self.grid.tolist(),
            "buildableCells": self.buildable_cells,
            "plots": self.plots,
        }
