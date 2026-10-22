package com.meileme.resort.service;

import com.meileme.resort.config.Plot;
import com.meileme.resort.config.TerrainType;
import com.meileme.resort.model.MapCell;
import com.meileme.resort.model.PlotMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 地块地图随机生成器。
 *
 * <p>从随机起点逐格「四邻扩散」生成 {@code buildableCellCount} 个可建造空地，保证可建造格
 * <b>相对连通成片</b>（同一连通分量内）；其余格子填入随机地形（城镇 / 平原 / 山地 / 湖区）。
 * 通过固定种子保证同一地块每次生成的布局可复现。</p>
 */
public final class MapGenerator {

    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private MapGenerator() {
    }

    public static PlotMap generate(Plot plot, int seed) {
        int w = Math.max(1, plot.getWidth());
        int h = Math.max(1, plot.getHeight());
        int target = Math.min(Math.max(1, plot.getBuildableCellCount()), w * h);

        boolean[][] buildable = new boolean[h][w];
        Random rnd = new Random(seed);

        // 随机起点，随后四邻扩散直到凑满 target 个可建造格
        int sx = rnd.nextInt(w);
        int sy = rnd.nextInt(h);
        buildable[sy][sx] = true;
        int built = 1;
        while (built < target) {
            List<int[]> candidates = new ArrayList<>();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (!buildable[y][x]) {
                        continue;
                    }
                    for (int[] d : DIRS) {
                        int nx = x + d[0];
                        int ny = y + d[1];
                        if (nx >= 0 && nx < w && ny >= 0 && ny < h && !buildable[ny][nx]) {
                            candidates.add(new int[]{nx, ny});
                        }
                    }
                }
            }
            if (candidates.isEmpty()) {
                break;
            }
            int[] c = candidates.get(rnd.nextInt(candidates.size()));
            if (!buildable[c[1]][c[0]]) {
                buildable[c[1]][c[0]] = true;
                built++;
            }
        }

        List<MapCell> cells = new ArrayList<>();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                TerrainType terrain = buildable[y][x]
                        ? null
                        : TerrainType.values()[rnd.nextInt(TerrainType.values().length)];
                cells.add(new MapCell(x, y, terrain, buildable[y][x]));
            }
        }
        return new PlotMap(plot.getId(), w, h, cells);
    }
}