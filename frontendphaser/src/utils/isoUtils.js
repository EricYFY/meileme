// 等轴测 (Isometric 2:1) 坐标转换与深度排序工具

export const ISO_TILE_WIDTH = 128;
export const ISO_TILE_HEIGHT = 64;

/**
 * 2D 逻辑网格坐标 (gx, gy) 转换为屏幕投影坐标 (相对于地块原点)
 * @param {number} gx 网格 X 坐标 (列)
 * @param {number} gy 网格 Y 坐标 (行)
 * @param {number} originX 地块在世界中的原点 X
 * @param {number} originY 地块在世界中的原点 Y
 * @returns {{x: number, y: number}}
 */
export function gridToIso(gx, gy, originX = 0, originY = 0) {
    const halfW = ISO_TILE_WIDTH / 2;
    const halfH = ISO_TILE_HEIGHT / 2;
    const x = originX + (gx - gy) * halfW;
    const y = originY + (gx + gy) * halfH;
    return { x, y };
}

/**
 * 世界坐标 (wx, wy) 反向投影为 2D 逻辑网格坐标 (gx, gy)
 * @param {number} wx 世界坐标 X
 * @param {number} wy 世界坐标 Y
 * @param {number} originX 地块在世界中的原点 X
 * @param {number} originY 地块在世界中的原点 Y
 * @returns {{gx: number, gy: number}}
 */
export function isoToGrid(wx, wy, originX = 0, originY = 0) {
    const halfW = ISO_TILE_WIDTH / 2;
    const halfH = ISO_TILE_HEIGHT / 2;
    const dx = wx - originX;
    const dy = wy - originY;
    
    // (gx - gy) = dx / halfW
    // (gx + gy) = dy / halfH
    const gx = Math.floor((dy / halfH + dx / halfW) / 2);
    const gy = Math.floor((dy / halfH - dx / halfW) / 2);
    return { gx, gy };
}

/**
 * 计算等轴测深度排序层级 (从上至下、从左至右渲染排序)
 * @param {number} gx 网格 X 坐标
 * @param {number} gy 网格 Y 坐标
 * @param {number} layer 渲染子层级 (0: 地形, 1: 网格线, 2: 阴影/脚手架, 3: 建筑实体, 4: 浮标)
 * @returns {number}
 */
export function getIsoDepth(gx, gy, layer = 3) {
    return (gx + gy) * 100 + layer;
}
