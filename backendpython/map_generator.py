import numpy as np
import random

# 地图常量
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


class MapGenerator:
    def __init__(self, merchant_count=5):
        self.grid = np.full((SIZE, SIZE), TILE_RES_LOW, dtype=np.int8)
        self.merchants = []
        # 记录所有住宅区格子坐标，用于后续订单生成
        self.residential_cells = []
        self.merchant_count = merchant_count

    def _coord_to_idx(self, v):
        """将逻辑坐标 (-100~100) 转为数组下标 (0~200)"""
        return v - GRID_MIN

    def _fill_rect(self, x_start, y_start, x_end, y_end, tile_type):
        """用指定瓦片类型填充一个矩形区域 (逻辑坐标)"""
        i0 = max(0, self._coord_to_idx(x_start))
        j0 = max(0, self._coord_to_idx(y_start))
        i1 = min(SIZE - 1, self._coord_to_idx(x_end))
        j1 = min(SIZE - 1, self._coord_to_idx(y_end))
        self.grid[i0:i1 + 1, j0:j1 + 1] = tile_type

    def generate(self):
        # ========== 第1步：先把整个地图填满低密度住宅区 ==========
        self.grid[:, :] = TILE_RES_LOW

        # ========== 第2步：画道路 ==========
        # 设计理念：
        #   - 主干道 (3格宽)：x=0 和 y=0 两条十字大道
        #   - 大路 (2格宽)：每隔 40 格一条 (x=±40, ±80; y=±40, ±80)
        #   - 小路 (1格宽)：每隔 20 格一条 (x=±20, ±60; y=±20, ±60)
        # 注意：主干道 > 大路 > 小路，优先级从高到低覆盖

        # 2a. 小路 (1格宽)：间隔20，但排除主干道(0)和大路(±40, ±80)位置
        small_road_coords = []
        for v in range(-100, 101, 20):
            if v == 0:
                continue  # 主干道位置，跳过
            if v % 40 == 0:
                continue  # 大路位置，跳过
            small_road_coords.append(v)

        for v in small_road_coords:
            idx = self._coord_to_idx(v)
            # 横向小路 (沿 y 方向铺满)
            self.grid[idx, :] = TILE_ROAD_SMALL
            # 纵向小路 (沿 x 方向铺满)
            self.grid[:, idx] = TILE_ROAD_SMALL

        # 2b. 大路 (2格宽)：间隔40，排除主干道(0)
        big_road_coords = []
        for v in range(-100, 101, 40):
            if v == 0:
                continue
            big_road_coords.append(v)

        for v in big_road_coords:
            idx = self._coord_to_idx(v)
            # 2格宽：v 和 v+1
            for offset in range(2):
                row = min(SIZE - 1, idx + offset)
                self.grid[row, :] = TILE_ROAD_BIG
                self.grid[:, row] = TILE_ROAD_BIG
                
        # 2c. 主干道 (3格宽)：中心十字
        center = self._coord_to_idx(0)
        # 3格宽：-1, 0, 1
        for offset in [-1, 0, 1]:
            c_idx = center + offset
            if 0 <= c_idx < SIZE:
                self.grid[c_idx, :] = TILE_ROAD_MAIN
                self.grid[:, c_idx] = TILE_ROAD_MAIN

        # ========== 第3步：商业区与高密度住宅 ==========
        low_com_blocks = [
            (2, 2, 19, 19),
            (-19, 2, -2, 19),
            (2, -19, 19, -2),
            (-19, -19, -2, -2),
            (22, 22, 39, 39),
            (-39, 22, -22, 39),
            (22, -39, 39, -22),
            (-39, -39, -22, -22),
            (22, 2, 39, 19),
            (-39, 2, -22, 19),
            (22, -19, 39, -2),
            (-39, -19, -22, -2),
        ]
        for (x0, y0, x1, y1) in low_com_blocks:
            self._fill_rect(x0, y0, x1, y1, TILE_COM_LOW)

        high_res_blocks = [
            (42, 42, 59, 59),
            (-59, 42, -42, 59),
            (42, -59, 59, -42),
            (-59, -59, -42, -42),
            (62, 2, 79, 19),
            (-79, 2, -62, 19),
            (62, -19, 79, -2),
            (-79, -19, -62, -2),
        ]
        for (x0, y0, x1, y1) in high_res_blocks:
            self._fill_rect(x0, y0, x1, y1, TILE_RES_HIGH)

        # ========== 第4步：在临近马路的商业区放置商家 ==========
        # 筛选出与道路直接相邻(4-邻域)的商业区格子
        road_tiles = {TILE_ROAD_MAIN, TILE_ROAD_BIG, TILE_ROAD_SMALL}
        roadside_com_cells = []
        for i in range(SIZE):
            for j in range(SIZE):
                if self.grid[i, j] in (TILE_COM_LOW, TILE_COM_HIGH):
                    # 判断上下左右是否有道路
                    has_adjacent_road = False
                    for di, dj in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
                        ni, nj = i + di, j + dj
                        if 0 <= ni < SIZE and 0 <= nj < SIZE and self.grid[ni, nj] in road_tiles:
                            has_adjacent_road = True
                            break
                    if has_adjacent_road:
                        roadside_com_cells.append((i, j))
        
        # 随机挑选指定数量的临路商业点作为商家
        selected_cells = random.sample(roadside_com_cells, min(self.merchant_count, len(roadside_com_cells)))
        
        for idx, (cx, cy) in enumerate(selected_cells):
            self.merchants.append({
                "id": f"merchant-{idx + 1}",
                "x": cx + GRID_MIN,
                "y": cy + GRID_MIN
            })

        # ========== 第5步：收集所有临路的住宅区格子 ==========
        # 只有与马路直接相邻的住宅格子才能作为有效送餐点，确保骑手可达
        self.residential_cells = []
        for i in range(SIZE):
            for j in range(SIZE):
                if self.grid[i, j] in [TILE_RES_HIGH, TILE_RES_LOW]:
                    has_adjacent_road = False
                    for di, dj in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
                        ni, nj = i + di, j + dj
                        if 0 <= ni < SIZE and 0 <= nj < SIZE and self.grid[ni, nj] in road_tiles:
                            has_adjacent_road = True
                            break
                    if has_adjacent_road:
                        self.residential_cells.append((i + GRID_MIN, j + GRID_MIN))

        print(f"[Map] 生成完毕. 地图大小: {SIZE}x{SIZE}, 临路商家: {len(self.merchants)}, 临路送餐点格子: {len(self.residential_cells)}")

    def get_map_data(self):
        return {
            "min": GRID_MIN,
            "max": GRID_MAX,
            "grid": self.grid.tolist(),
            "merchants": self.merchants,
            "residentialCells": self.residential_cells  # 提供给 Java 做送餐点随机选择
        }
