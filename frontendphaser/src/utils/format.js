// 货币与数字格式化工具

export function fmtMoney(num) {
    if (num == null) return '¥0';
    const n = Math.round(Number(num) || 0);
    return `¥${n.toLocaleString('zh-CN')}`;
}

export function fmtInt(num) {
    if (num == null) return '0';
    return Math.round(Number(num) || 0).toLocaleString('zh-CN');
}

export function fmtPct(num, digits = 1) {
    if (num == null) return '0%';
    return `${(Number(num) * 100).toFixed(digits)}%`;
}
