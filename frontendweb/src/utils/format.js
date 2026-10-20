// 数值格式化工具。约定：货币金额一律整数展示、去掉小数、千分位逗号分隔。

export function fmtMoney(v) {
    const n = Math.round(Number(v) || 0);
    return n.toLocaleString('en-US');
}

export function fmtInt(v) {
    const n = Math.round(Number(v) || 0);
    return n.toLocaleString('en-US');
}

// 保留 1 位小数
export function fmtNum1(v) {
    const n = Number(v) || 0;
    return Math.round(n * 10) / 10;
}

// 比率 → 百分比（0.85 → "85%"）
export function fmtPercent(ratio) {
    const n = Number(ratio) || 0;
    return Math.round(n * 100) + '%';
}

// 带符号金额（+12,000 / -3,000）
export function fmtSigned(v) {
    const n = Math.round(Number(v) || 0);
    const s = n.toLocaleString('en-US');
    return n >= 0 ? '+' + s : s;
}

// 缩短长 ID（显示尾部 6 位）
export function shortId(id) {
    if (!id) return '';
    return id.length > 8 ? '…' + id.slice(-6) : id;
}

// 数字分级颜色：正收益绿、负支出红、零中性
export function moneyClass(v) {
    const n = Number(v) || 0;
    return n > 0 ? 'pos' : n < 0 ? 'neg' : 'zero';
}