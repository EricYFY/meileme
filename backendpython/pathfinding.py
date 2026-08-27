import heapq
import math

TILE_ROAD_MAIN = 1   # 主干道
TILE_ROAD_BIG = 2    # 大路
TILE_ROAD_SMALL = 3  # 小路

ROAD_SPEEDS = {
    TILE_ROAD_MAIN: 9.0,   # 主干道速度最高
    TILE_ROAD_BIG: 6.5,    # 大路速度次之
    TILE_ROAD_SMALL: 4.0   # 小路速度最低
}

MAX_SPEED = 9.0

class AStarRouter:
    def __init__(self, grid, grid_min=-100):
        """
        grid: 2D list or numpy array (SIZE x SIZE)
        grid_min: 逻辑坐标原点偏移，默认为 -100
        """
        self.grid = grid
        self.size = len(grid)
        self.grid_min = grid_min
        
        # 预先构建道路格子集合与邻接查询
        self.road_set = set()
        for i in range(self.size):
            for j in range(self.size):
                if self.grid[i][j] in (TILE_ROAD_MAIN, TILE_ROAD_BIG, TILE_ROAD_SMALL):
                    self.road_set.add((i, j))

    def _coord_to_idx(self, x, y):
        i = int(round(x - self.grid_min))
        j = int(round(y - self.grid_min))
        return i, j

    def _idx_to_coord(self, i, j):
        return float(i + self.grid_min), float(j + self.grid_min)

    def is_road(self, i, j):
        return (i, j) in self.road_set

    def get_road_speed_by_coord(self, x, y):
        i, j = self._coord_to_idx(x, y)
        if 0 <= i < self.size and 0 <= j < self.size:
            tile = self.grid[i][j]
            return ROAD_SPEEDS.get(tile, 4.0)
        return 4.0

    def find_nearest_road_idx(self, i, j):
        """如果在非道路上，在周围进行广度搜索寻找最近的道路格子"""
        if (i, j) in self.road_set:
            return i, j
        
        # BFS 寻找最近道路格子
        visited = {(i, j)}
        queue = [(i, j)]
        while queue:
            ci, cj = queue.pop(0)
            for di, dj in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
                ni, nj = ci + di, cj + dj
                if 0 <= ni < self.size and 0 <= nj < self.size and (ni, nj) not in visited:
                    if (ni, nj) in self.road_set:
                        return ni, nj
                    visited.add((ni, nj))
                    # 搜索半径限制
                    if abs(ni - i) + abs(nj - j) <= 15:
                        queue.append((ni, nj))
        return i, j

    def find_path(self, start_pos, target_pos):
        """
        start_pos: {"x": float, "y": float}
        target_pos: {"x": float, "y": float}
        返回路径点列表: [{"x": float, "y": float}, ...]
        """
        si, sj = self._coord_to_idx(start_pos["x"], start_pos["y"])
        ti, tj = self._coord_to_idx(target_pos["x"], target_pos["y"])

        si, sj = self.find_nearest_road_idx(si, sj)
        ti, tj = self.find_nearest_road_idx(ti, tj)

        if (si, sj) == (ti, tj):
            tx, ty = self._idx_to_coord(ti, tj)
            return [{"x": tx, "y": ty}]

        # A* 算法
        # 优先队列元素: (f_score, cost, (i, j))
        open_set = []
        heapq.heappush(open_set, (0.0, 0.0, (si, sj)))
        
        came_from = {}
        g_score = {(si, sj): 0.0}

        def heuristic(ci, cj):
            # 曼哈顿距离 / 最大速度 作为启发式函数
            return (abs(ci - ti) + abs(cj - tj)) / MAX_SPEED

        while open_set:
            _, current_g, current = heapq.heappop(open_set)
            ci, cj = current

            if current == (ti, tj):
                # 回溯构建路径
                path_indices = [(ti, tj)]
                while current in came_from:
                    current = came_from[current]
                    path_indices.append(current)
                path_indices.reverse()
                return self._simplify_path_indices(path_indices)

            # 4 方向扩展 (上下左右)
            for di, dj in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
                ni, nj = ci + di, cj + dj
                if (ni, nj) in self.road_set:
                    tile_type = self.grid[ni][nj]
                    speed = ROAD_SPEEDS.get(tile_type, 4.0)
                    step_cost = 1.0 / speed # 通行时间开销
                    tentative_g = current_g + step_cost

                    if (ni, nj) not in g_score or tentative_g < g_score[(ni, nj)]:
                        came_from[(ni, nj)] = (ci, cj)
                        g_score[(ni, nj)] = tentative_g
                        f = tentative_g + heuristic(ni, nj)
                        heapq.heappush(open_set, (f, tentative_g, (ni, nj)))

        # 若无连通路径，退化为直接返回目标点
        tx, ty = self._idx_to_coord(ti, tj)
        return [{"x": tx, "y": ty}]

    def _simplify_path_indices(self, path_indices):
        """
        压缩同一直线上的中间点，提取关键拐点，优化移动插值
        """
        if len(path_indices) <= 2:
            return [{"x": self._idx_to_coord(i, j)[0], "y": self._idx_to_coord(i, j)[1]} for i, j in path_indices]

        simplified = [path_indices[0]]
        
        for k in range(1, len(path_indices) - 1):
            prev_i, prev_j = path_indices[k - 1]
            curr_i, curr_j = path_indices[k]
            next_i, next_j = path_indices[k + 1]

            dir1 = (curr_i - prev_i, curr_j - prev_j)
            dir2 = (next_i - curr_i, next_j - curr_j)

            # 方向改变或道路类型改变时保留拐点
            tile_curr = self.grid[curr_i][curr_j]
            tile_next = self.grid[next_i][next_j]

            if dir1 != dir2 or tile_curr != tile_next:
                simplified.append((curr_i, curr_j))

        simplified.append(path_indices[-1])

        return [{"x": self._idx_to_coord(i, j)[0], "y": self._idx_to_coord(i, j)[1]} for i, j in simplified]
