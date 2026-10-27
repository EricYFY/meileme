// 2:1 等轴测投影坐标转换工具库

export const ISO_TILE_WIDTH = 128;
export const ISO_TILE_HEIGHT = 64;

/**
 * 网格坐标 (gx, gy) 转等轴测屏幕世界坐标 (x, y)
 */
export function gridToIso(gx, gy, originX = 0, originY = 0) {
    const halfW = ISO_TILE_WIDTH / 2;
    const halfH = ISO_TILE_HEIGHT / 2;
    const x = originX + (gx - gy) * halfW;
    const y = originY + (gx + gy) * halfH;
    return { x, y };
}

/**
 * 屏幕世界坐标 (isoX, isoY) 逆转换为网格坐标 (gx, gy)
 */
export function isoToGrid(isoX, isoY, originX = 0, originY = 0) {
    const halfW = ISO_TILE_WIDTH / 2;
    const halfH = ISO_TILE_HEIGHT / 2;
    const dx = isoX - originX;
    const dy = isoY - originY;
    const gx = Math.floor((dy / halfH + dx / halfW) / 2);
    const gy = Math.floor((dy / halfH - dx / halfW) / 2);
    return { gx, gy };
}

/**
 * 计算等轴测渲染深度
 */
export function getIsoDepth(gx, gy, layer = 0) {
    return (gx + gy) * 100 + layer;
}
