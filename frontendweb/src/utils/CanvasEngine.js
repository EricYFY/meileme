const BUILDING_STYLE = {
    STANDARD_ROOM: { color: '#4ade80', label: '住' },   // 普通客房
    LUXURY_SUITE:  { color: '#fbbf24', label: '豪' },   // 豪华套房
    RESTAURANT:    { color: '#fb923c', label: '餐' },   // 餐厅
    SPA:           { color: '#22d3ee', label: '温' },   // 温泉池
    VIEWPOINT:     { color: '#a78bfa', label: '景' },   // 观景台
    PARKING:       { color: '#94a3b8', label: 'P' }     // 停车场
};

const STAFF_COLOR = {
    FRONT_DESK:  '#38bdf8', // 前台
    CLEANING:    '#f472b6', // 保洁
    CHEF:        '#fb923c', // 厨师
    MAINTENANCE: '#a78bfa'  // 维修
};

const PLOT_STYLE = {
    INITIAL:  { fill: 'rgba(34, 197, 94, 0.16)',  stroke: '#22c55e' },
    MOUNTAIN: { fill: 'rgba(180, 83, 9, 0.30)',   stroke: '#b45309' }, // 山地色
    LAKESIDE: { fill: 'rgba(14, 116, 144, 0.30)', stroke: '#06b6d4' }  // 湖畔色
};

export default class CanvasEngine {
    constructor(canvasElement) {
        this.canvas = canvasElement;
        this.ctx = this.canvas.getContext('2d', { alpha: false });

        // 视图状态
        this.scale = 1.0;
        this.offsetX = 0;
        this.offsetY = 0;
        this.isDragging = false;
        this.dragMoved = false;
        this.lastMouse = { x: 0, y: 0 };

        // 数据
        this.mapData = null;
        this.tileSize = 20;

        // 全量实体（游客/员工全部渲染，不做抽样）
        this.guests = new Map();   // id -> { current, target }
        this.staff = new Map();    // id -> { current, target, type }
        this.buildings = [];       // Building[]
        this.plots = [];           // [{ id, cells, unlocked }]

        this._bindEvents();

        this.running = true;
        this._renderLoop();
    }

    _bindEvents() {
        this.canvas.addEventListener('mousedown', (e) => {
            this.isDragging = true;
            this.dragMoved = false;
            this.lastMouse = { x: e.clientX, y: e.clientY };
            this.canvas.style.cursor = 'grabbing';
        });

        this.canvas.addEventListener('mousemove', (e) => {
            if (this.isDragging) {
                const dx = e.clientX - this.lastMouse.x;
                const dy = e.clientY - this.lastMouse.y;
                if (Math.abs(dx) > 2 || Math.abs(dy) > 2) this.dragMoved = true;
                this.offsetX += dx;
                this.offsetY += dy;
                this.lastMouse = { x: e.clientX, y: e.clientY };
            }
        });

        this.canvas.addEventListener('mouseup', () => {
            this.isDragging = false;
            this.canvas.style.cursor = 'grab';
        });

        this.canvas.addEventListener('mouseleave', () => {
            this.isDragging = false;
            this.canvas.style.cursor = 'grab';
        });

        this.canvas.addEventListener('wheel', (e) => {
            e.preventDefault();
            const zoomAmount = e.deltaY > 0 ? 0.9 : 1.1;

            const rect = this.canvas.getBoundingClientRect();
            const mouseX = e.clientX - rect.left;
            const mouseY = e.clientY - rect.top;

            const worldX = (mouseX - this.offsetX) / this.scale;
            const worldY = (mouseY - this.offsetY) / this.scale;

            this.scale *= zoomAmount;
            this.scale = Math.max(0.1, Math.min(this.scale, 5.0));

            this.offsetX = mouseX - worldX * this.scale;
            this.offsetY = mouseY - worldY * this.scale;
        });
    }

    setMap(mapData) {
        this.mapData = mapData;
        if (!mapData) {
            this.clear();
            return;
        }
        this.resetView();
    }

    /** 缩放至整图并居中（默认视野，保证一进来能看到全貌） */
    resetView() {
        if (!this.mapData || !this.canvas.width || !this.canvas.height) return;
        const totalSize = (this.mapData.max - this.mapData.min + 1) * this.tileSize;
        const fit = Math.min(this.canvas.width / totalSize, this.canvas.height / totalSize);
        this.scale = Math.max(0.05, Math.min(fit, 5.0));
        this.offsetX = (this.canvas.width - totalSize * this.scale) / 2;
        this.offsetY = (this.canvas.height - totalSize * this.scale) / 2;
    }

    /** 将镜头移动并放大到指定逻辑坐标（用于建造成功后定位新建筑） */
    focusOn(x, y) {
        if (!this.mapData) return;
        const min = this.mapData.min;
        const ts = this.tileSize;
        this.scale = Math.max(this.scale, 1.0);
        const px = (x - min) * ts + ts / 2;
        const py = (y - min) * ts + ts / 2;
        this.offsetX = this.canvas.width / 2 - px * this.scale;
        this.offsetY = this.canvas.height / 2 - py * this.scale;
    }

    setPlots(plots) {
        this.plots = Array.isArray(plots) ? plots : [];
    }

    setBuildings(buildings) {
        this.buildings = Array.isArray(buildings) ? buildings : [];
    }

    _posOf(o) {
        if (!o) return null;
        const p = o.currentPosition || o.position || o;
        const x = Array.isArray(p) ? p[0] : p.x;
        const y = Array.isArray(p) ? p[1] : p.y;
        if (x === undefined || y === undefined) return null;
        return { x, y };
    }

    // 外部同步游客坐标（全量）
    setGuests(list) {
        const incoming = Array.isArray(list) ? list : [];
        const seen = new Set();
        incoming.forEach(g => {
            const pos = this._posOf(g);
            if (!pos) return;
            seen.add(g.id);
            if (this.guests.has(g.id)) {
                this.guests.get(g.id).target = pos;
            } else {
                this.guests.set(g.id, { id: g.id, current: { ...pos }, target: { ...pos } });
            }
        });
        this.guests.forEach((_, id) => { if (!seen.has(id)) this.guests.delete(id); });
    }

    // 外部同步员工坐标（全量，含岗位类型）
    setStaff(list) {
        const incoming = Array.isArray(list) ? list : [];
        const seen = new Set();
        incoming.forEach(s => {
            const pos = this._posOf(s);
            if (!pos) return;
            seen.add(s.id);
            if (this.staff.has(s.id)) {
                const existing = this.staff.get(s.id);
                existing.target = pos;
                existing.type = s.type;
            } else {
                this.staff.set(s.id, { id: s.id, current: { ...pos }, target: { ...pos }, type: s.type });
            }
        });
        this.staff.forEach((_, id) => { if (!seen.has(id)) this.staff.delete(id); });
    }

    clear() {
        this.mapData = null;
        this.guests.clear();
        this.staff.clear();
        this.buildings = [];
        this.plots = [];
    }

    resize(width, height) {
        this.canvas.width = width;
        this.canvas.height = height;
    }

    destroy() {
        this.running = false;
    }

    _getColor(tileType) {
        switch (tileType) {
            case 0: return '#0f172a';
            case 1: return '#ffffff'; // 主干道 (白色)
            case 2: return '#94a3b8'; // 大路 (灰色)
            case 3: return '#475569'; // 小路 (深灰色)
            case 4: return '#15803d'; // 高密度住宅 (深绿)
            case 5: return '#86efac'; // 低密度住宅 (浅绿)
            case 6: return '#6b21a8'; // 高密度商业 (深紫，仅兼容手绘地图)
            case 7: return '#c084fc'; // 低密度商业 (浅紫，仅兼容手绘地图)
            case 8: return '#31402a'; // 草地/平地 (山脚)
            case 9: return '#1e5b78'; // 湖面
            case 10: return '#7a5c34'; // 山体坡面 (山腰)
            case 11: return '#57506b'; // 山峰
            default: return '#000000';
        }
    }

    _drawTiles() {
        const grid = this.mapData.grid;
        const size = grid.length;
        for (let i = 0; i < size; i++) {
            for (let j = 0; j < size; j++) {
                const tile = grid[i][j];
                // 0=不可通行; 8=草地由底色统一铺满，跳过逐格绘制以省算力
                if (tile === 0 || tile === 8) continue;
                this.ctx.fillStyle = this._getColor(tile);
                this.ctx.fillRect(i * this.tileSize, j * this.tileSize, this.tileSize - 1, this.tileSize - 1);
            }
        }
    }

    // 绘制地块区域（INITIAL / MOUNTAIN 山地 / LAKESIDE 湖畔）
    _drawPlots() {
        if (!this.plots.length) return;
        const min = this.mapData.min;
        const ts = this.tileSize;

        this.plots.forEach(plot => {
            if (!plot || !Array.isArray(plot.cells)) return;
            const style = PLOT_STYLE[plot.id] || { fill: 'rgba(148,163,184,0.15)', stroke: '#94a3b8' };
            const locked = plot.unlocked === false;

            this.ctx.globalAlpha = locked ? 0.35 : 0.9;
            this.ctx.fillStyle = style.fill;
            this.ctx.strokeStyle = style.stroke;
            this.ctx.lineWidth = locked ? 1 : 2;
            if (locked) this.ctx.setLineDash([6, 4]);
            else this.ctx.setLineDash([]);

            plot.cells.forEach(cell => {
                const gx = Array.isArray(cell) ? cell[0] : cell.x;
                const gy = Array.isArray(cell) ? cell[1] : cell.y;
                if (gx === undefined || gy === undefined) return;
                const px = (gx - min) * ts;
                const py = (gy - min) * ts;
                this.ctx.fillRect(px, py, ts, ts);
                this.ctx.strokeRect(px + 0.5, py + 0.5, ts - 1, ts - 1);
            });

            this.ctx.setLineDash([]);
            this.ctx.globalAlpha = 1.0;
        });
    }

    _drawBuilding(b) {
        const pos = this._posOf(b);
        if (!pos) return;
        const min = this.mapData.min;
        const ts = this.tileSize;
        const cx = (pos.x - min) * ts + ts / 2;
        const cy = (pos.y - min) * ts + ts / 2;
        const style = BUILDING_STYLE[b.type] || { color: '#e2e8f0', label: '?' };
        const disabled = b.status === 2;
        const building = b.status === 1;

        this.ctx.save();
        this.ctx.globalAlpha = disabled ? 0.4 : (building ? 0.55 : 1.0);
        this.ctx.fillStyle = style.color;
        this.ctx.strokeStyle = '#0f172a';
        this.ctx.lineWidth = 1.5;
        this.ctx.shadowBlur = 8;
        this.ctx.shadowColor = style.color;

        const r = ts * 0.55;
        this.ctx.beginPath();
        switch (b.type) {
            case 'LUXURY_SUITE': // 豪华套房：菱形
                this.ctx.moveTo(cx, cy - r);
                this.ctx.lineTo(cx + r, cy);
                this.ctx.lineTo(cx, cy + r);
                this.ctx.lineTo(cx - r, cy);
                this.ctx.closePath();
                break;
            case 'RESTAURANT': // 餐厅：圆形
                this.ctx.arc(cx, cy, r * 0.9, 0, Math.PI * 2);
                break;
            case 'SPA': // 温泉：圆角方块
                if (this.ctx.roundRect) {
                    this.ctx.roundRect(cx - r * 0.8, cy - r * 0.8, r * 1.6, r * 1.6, r * 0.4);
                } else {
                    this.ctx.rect(cx - r * 0.8, cy - r * 0.8, r * 1.6, r * 1.6);
                }
                break;
            case 'VIEWPOINT': // 观景台：上三角
                this.ctx.moveTo(cx, cy - r);
                this.ctx.lineTo(cx + r * 0.9, cy + r * 0.7);
                this.ctx.lineTo(cx - r * 0.9, cy + r * 0.7);
                this.ctx.closePath();
                break;
            default: // 普通客房 / 停车场：方块
                this.ctx.rect(cx - r * 0.8, cy - r * 0.8, r * 1.6, r * 1.6);
                break;
        }
        this.ctx.fill();
        this.ctx.stroke();
        this.ctx.shadowBlur = 0;

        // 图标文字
        this.ctx.fillStyle = '#0f172a';
        this.ctx.font = `bold ${Math.round(ts * 0.6)}px Inter, sans-serif`;
        this.ctx.textAlign = 'center';
        this.ctx.textBaseline = 'middle';
        this.ctx.fillText(building ? '🚧' : style.label, cx, cy + 1);
        this.ctx.restore();
    }

    _drawGuests() {
        const min = this.mapData.min;
        const ts = this.tileSize;
        this.ctx.fillStyle = '#e2e8f0';
        this.ctx.shadowBlur = 8;
        this.ctx.shadowColor = '#e2e8f0';
        // 全量绘制，无上限、无抽样
        this.guests.forEach(g => {
            const px = (g.current.x - min) * ts + ts / 2;
            const py = (g.current.y - min) * ts + ts / 2;
            this.ctx.beginPath();
            this.ctx.arc(px, py, ts * 0.28, 0, Math.PI * 2);
            this.ctx.fill();
        });
        this.ctx.shadowBlur = 0;
    }

    _drawStaff() {
        const min = this.mapData.min;
        const ts = this.tileSize;
        // 全量绘制，无上限、无抽样
        this.staff.forEach(s => {
            const px = (s.current.x - min) * ts + ts / 2;
            const py = (s.current.y - min) * ts + ts / 2;
            const color = STAFF_COLOR[s.type] || '#cbd5e1';
            this.ctx.fillStyle = color;
            this.ctx.shadowBlur = 8;
            this.ctx.shadowColor = color;
            this.ctx.fillRect(px - ts * 0.26, py - ts * 0.26, ts * 0.52, ts * 0.52);
        });
        this.ctx.shadowBlur = 0;
    }

    _renderLoop() {
        if (!this.running) return;

        // 1. Lerp 插值
        this.guests.forEach(g => {
            g.current.x += (g.target.x - g.current.x) * 0.3;
            g.current.y += (g.target.y - g.current.y) * 0.3;
        });
        this.staff.forEach(s => {
            s.current.x += (s.target.x - s.current.x) * 0.3;
            s.current.y += (s.target.y - s.current.y) * 0.3;
        });

        // 2. 清空背景
        this.ctx.fillStyle = '#0f172a';
        this.ctx.fillRect(0, 0, this.canvas.width, this.canvas.height);

        this.ctx.save();
        this.ctx.translate(this.offsetX, this.offsetY);
        this.ctx.scale(this.scale, this.scale);

        if (this.mapData) {
            const total = (this.mapData.max - this.mapData.min + 1) * this.tileSize;
            this.ctx.fillStyle = '#31402a'; // 草地底色，铺满整张地图
            this.ctx.fillRect(0, 0, total, total);
            this._drawTiles();
            this._drawPlots();
            this.buildings.forEach(b => this._drawBuilding(b));
            this._drawGuests();
            this._drawStaff();
        }

        this.ctx.restore();

        requestAnimationFrame(() => this._renderLoop());
    }
}
