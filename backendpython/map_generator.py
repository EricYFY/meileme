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
    def __init__(self):
        self.grid = np.full((SIZE, SIZE), TILE_RES_LOW, dtype=np.int8)
        self.merchants = []
        # 记录所有住宅区格子坐标，用于后续订单生成
        self.residential_cells = []

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

        # 2c. 主干道 (3格宽)：x=0 和 y=0
        center = self._coord_to_idx(0)
        for offset in range(-1, 2):  # -1, 0, 1 → 3格宽
            row = center + offset
            self.grid[row, :] = TILE_ROAD_MAIN  # x=0 横穿
            self.grid[:, row] = TILE_ROAD_MAIN  # y=0 纵穿

        # ========== 第3步：划分建筑街区类型 ==========
        # 沿主干道/大路旁的区块标记为商业区，其余为住宅区
        # 简单规则：距离 x=0 或 y=0 最近的一圈街区为高密度商业区
        #          距离大路最近的街区为低密度商业区
        #          其余为住宅区（已经默认填好了）

        # 围绕主干道 (x=0, y=0) 交叉口的 4 个街区设为高密度商业区
        commercial_blocks = [
            # 主干道十字路口附近的 4 个大方块
            (2, 2, 19, 19),
            (-19, 2, -2, 19),
            (2, -19, 19, -2),
            (-19, -19, -2, -2),
        ]
        for (x0, y0, x1, y1) in commercial_blocks:
            self._fill_rect(x0, y0, x1, y1, TILE_COM_HIGH)

        # 沿主干道稍远处的区块设为低密度商业区
        low_com_blocks = [
            (2, 22, 19, 39),
            (-19, 22, -2, 39),
            (2, -39, 19, -22),
            (-19, -39, -2, -22),
            (22, 2, 39, 19),
            (-39, 2, -22, 19),
            (22, -19, 39, -2),
            (-39, -19, -22, -2),
        ]
        for (x0, y0, x1, y1) in low_com_blocks:
            self._fill_rect(x0, y0, x1, y1, TILE_COM_LOW)

        # 随机将一些远离中心的街区标记为高密度住宅区
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

        # ========== 第4步：在路边放置 5 个商家 ==========
        # 商家一定紧挨着路，放在商业区或路边第一格
        merchant_positions = [
            (1, 10),     # 主干道旁
            (-15, -1),   # 主干道旁
            (25, 1),     # 大路旁
            (-1, -30),   # 主干道旁
            (41, 41),    # 大路交叉口旁
        ]
        self.merchants = []
        for idx, (mx, my) in enumerate(merchant_positions):
            self.merchants.append({
                "id": f"merchant-{idx + 1}",
                "x": mx,
                "y": my
            })

        # ========== 第5步：收集住宅区格子（用于随机生成订单送达点） ==========
        self.residential_cells = []
        for i in range(SIZE):
            for j in range(SIZE):
                if self.grid[i, j] in [TILE_RES_HIGH, TILE_RES_LOW]:
                    self.residential_cells.append((i + GRID_MIN, j + GRID_MIN))

        print(f"[Map] 生成完毕. 地图大小: {SIZE}x{SIZE}, 商家数量: {len(self.merchants)}, 住宅区格子: {len(self.residential_cells)}")

    def get_map_data(self):
        return {
            "min": GRID_MIN,
            "max": GRID_MAX,
            "grid": self.grid.tolist(),
            "merchants": self.merchants,
            "residentialCells": self.residential_cells  # 提供给 Java 做送餐点随机选择
        }
