// 纯前端「游客走动」简化模拟。
// 不接后端地形 / A* 数据，仅在总览面板内生成一批随机漂移的游客光点，用于装饰性可视化。
// 光点数量由「今日预计接待人数」决定，上限截断避免过密。

const PALETTE = ['#6ea8ff', '#7ee0a3', '#ffd37a', '#f4a5d0', '#9fb7ff', '#7ad7e0'];

function pick(arr) {
    return arr[Math.floor(Math.random() * arr.length)];
}

// 生成若干游客光点描述（百分比定位 + CSS 动画参数）
export function buildGuestDots(guestCount, maxDots = 50) {
    const n = Math.max(0, Math.min(Math.round(guestCount || 0), maxDots));
    const dots = [];
    for (let i = 0; i < n; i++) {
        dots.push({
            id: 'guest-' + i,
            left: Math.round(Math.random() * 96),
            top: Math.round(Math.random() * 96),
            duration: (8 + Math.random() * 14).toFixed(1),
            delay: (-Math.random() * 20).toFixed(1),
            size: (4 + Math.random() * 4).toFixed(1),
            color: pick(PALETTE),
        });
    }
    return dots;
}