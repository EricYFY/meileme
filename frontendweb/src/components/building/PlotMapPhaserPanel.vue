<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue';
import Phaser from 'phaser';
import { store, api } from '../../services/store.js';
import { fmtMoney } from '../../utils/format.js';
import { ISO_TILE_WIDTH, ISO_TILE_HEIGHT, gridToIso, isoToGrid, getIsoDepth } from '../../utils/isoUtils.js';

const emit = defineEmits(['open-building', 'open-build']);

// 当前选中的地块
const selectedPlotId = ref(null);
const mergeMode = ref(false);
const mergeSel = ref([]);
const mapLayer = ref('normal'); // 'normal' | 'comfort'

// 已解锁地块列表
const unlockedPlots = computed(() =>
    (store.config?.plots || []).filter((p) => (store.state?.unlockedPlots || []).includes(p.id))
);

// 默认选择已解锁的最高阶地块（按 plots 配置顺序越往后越高阶）
watch(unlockedPlots, (list) => {
    if (!selectedPlotId.value || !list.some((p) => p.id === selectedPlotId.value)) {
        // 从后往前选最高阶
        selectedPlotId.value = list.length ? list[list.length - 1].id : null;
    }
}, { immediate: true });

const currentPlot = computed(() => store.config?.plotsById?.[selectedPlotId.value] || null);
const map = computed(() => store.config?.plotMapsById?.[selectedPlotId.value] || null);

// 建筑与工程快速索引
const buildingAt = computed(() => {
    const m = {};
    for (const b of store.state?.buildings || []) {
        if (b.alive && b.plotId === selectedPlotId.value) m[b.cellX + ',' + b.cellY] = b;
    }
    return m;
});

const projectAt = computed(() => {
    const m = {};
    for (const p of store.state?.projects || []) {
        if (((p.projectType || p.type) === 'BUILD') && p.plotId === selectedPlotId.value) m[p.cellX + ',' + p.cellY] = p;
    }
    return m;
});

const buildingComfortMap = computed(() => store.state?.buildingComfort || {});
function getComfortData(buildingId) {
    return buildingComfortMap.value?.[buildingId] || null;
}

// —— 合并进化配方匹配 ——
const selA = computed(() => (store.state?.buildings || []).find((b) => b.id === mergeSel.value[0]));
const selB = computed(() => (store.state?.buildings || []).find((b) => b.id === mergeSel.value[1]));

function matchRecipe(a, b) {
    if (!a || !b) return null;
    return (store.config?.mergeRecipes || []).find((r) =>
        (r.sourceFormA?.id === a.form.id && r.sourceLevelA === a.formLevel && r.sourceFormB?.id === b.form.id && r.sourceLevelB === b.formLevel) ||
        (r.sourceFormA?.id === b.form.id && r.sourceLevelA === b.formLevel && r.sourceFormB?.id === a.form.id && r.sourceLevelB === a.formLevel)
    );
}

const matched = computed(() => matchRecipe(selA.value, selB.value));
const isAdjacent = computed(() => {
    const a = selA.value;
    const b = selB.value;
    if (!a || !b) return false;
    if (a.cellX < 0 || a.cellY < 0 || b.cellX < 0 || b.cellY < 0) return false;
    return Math.abs(a.cellX - b.cellX) + Math.abs(a.cellY - b.cellY) === 1;
});

const canMerge = computed(() =>
    !!matched.value && isAdjacent.value && (store.state?.funds ?? 0) >= (matched.value.mergeCost || 0)
);

function projectOf(id) {
    return (store.state?.projects || []).find((p) => p.buildingId === id || p.secondBuildingId === id) || null;
}

function toggleSelect(b) {
    if (projectOf(b.id)) return;
    const idx = mergeSel.value.indexOf(b.id);
    if (idx >= 0) mergeSel.value.splice(idx, 1);
    else {
        if (mergeSel.value.length >= 2) mergeSel.value.shift();
        mergeSel.value.push(b.id);
    }
}

function confirmMerge() {
    if (!selA.value || !selB.value || !matched.value) return;
    api.mergeEvolve(selA.value.id, selB.value.id);
    mergeSel.value = [];
    mergeMode.value = false;
}

// ======================== Phaser 2:1 等轴测场景 ========================

const phaserContainer = ref(null);
let phaserGame = null;
let currentScene = null;

// 调色板定义
const COLOR_PALETTE = {
    TERRAIN: {
        TOWN: { top: 0x334155, sideLeft: 0x1e293b, sideRight: 0x0f172a, stroke: 0x64748b, label: '城', name: '城镇街区' },
        LAKE: { top: 0x0284c7, sideLeft: 0x0369a1, sideRight: 0x075985, stroke: 0x38bdf8, label: '湖', name: '湖区水面' },
        MOUNTAIN: { top: 0x78350f, sideLeft: 0x451a03, sideRight: 0x270f03, stroke: 0xd97706, label: '山', name: '险峻山地' },
        PLAIN: { top: 0x15803d, sideLeft: 0x166534, sideRight: 0x14532d, stroke: 0x4ade80, label: '原', name: '自然草甸' },
        BUILDABLE: { top: 0x0f172a, sideLeft: 0x0b1120, sideRight: 0x070b14, stroke: 0x10b981, label: '+', name: '可建造空地' }
    },
    BUILDING: {
        CONSTRUCTION: { top: 0xf59e0b, sideLeft: 0xd97706, sideRight: 0xb45309, stroke: 0xfef08a, badge: '建' },
        BASE: { top: 0x4f46e5, sideLeft: 0x4338ca, sideRight: 0x3730a3, stroke: 0xa5b4fc, badge: '基' },
        FAMILY: { top: 0xe11d48, sideLeft: 0xbe123c, sideRight: 0x9f1239, stroke: 0xfda4af, badge: '家' },
        BUSINESS: { top: 0x0d9488, sideLeft: 0x0f766e, sideRight: 0x115e59, stroke: 0x5eead4, badge: '商' },
        MERGE: { top: 0xb45309, sideLeft: 0x92400e, sideRight: 0x78350f, stroke: 0xfde047, badge: '合' },
        SUPPORT: { top: 0x7c3aed, sideLeft: 0x6d28d9, sideRight: 0x5b21b6, stroke: 0xc4b5fd, badge: '配' }
    }
};

function getBuildingTheme(bld) {
    if (bld.status === 'UNDER_CONSTRUCTION' || bld.underConstruction || projectOf(bld.id)) {
        return COLOR_PALETTE.BUILDING.CONSTRUCTION;
    }
    const cat = (bld.form?.category || (bld.isAccommodation ? 'ACCOMMODATION' : 'SUPPORT')).toUpperCase();
    if (cat === 'SUPPORT') return COLOR_PALETTE.BUILDING.SUPPORT;
    const line = (bld.form?.line || '').toUpperCase();
    const id = (bld.form?.id || '').toUpperCase();
    if (line === 'MERGE' || id.includes('MERGED')) return COLOR_PALETTE.BUILDING.MERGE;
    if (line === 'FAMILY' || id.includes('FAMILY')) return COLOR_PALETTE.BUILDING.FAMILY;
    if (line === 'BUSINESS' || id.includes('BUSINESS')) return COLOR_PALETTE.BUILDING.BUSINESS;
    return COLOR_PALETTE.BUILDING.BASE;
}

class PhaserMapScene extends Phaser.Scene {
    constructor() {
        super({ key: 'PhaserMapScene' });
        this.worldOriginX = 0;
        this.worldOriginY = 0;
        this.gridContainer = null;
        this.buildingsContainer = null;
        this.hoverCursor = null;
        this.selectedCursor = null;
        this.hoverTooltip = null;
        this.hoverTooltipText = null;
        this.hoveredGrid = { gx: -1, gy: -1 };
        this.isDragging = false;
        this.dragStartX = 0;
        this.dragStartY = 0;
    }

    create() {
        currentScene = this;

        this.gridContainer = this.add.container(0, 0);
        this.buildingsContainer = this.add.container(0, 0);

        // 生成高亮选框纹理
        const W = ISO_TILE_WIDTH;
        const H = ISO_TILE_HEIGHT;
        if (!this.textures.exists('iso_hover')) {
            const canvas = this.textures.createCanvas('iso_hover', W, H);
            const ctx = canvas.getContext();
            ctx.beginPath();
            ctx.moveTo(W / 2, 2);
            ctx.lineTo(W - 2, H / 2);
            ctx.lineTo(W / 2, H - 2);
            ctx.lineTo(2, H / 2);
            ctx.closePath();
            ctx.fillStyle = 'rgba(56, 189, 248, 0.22)';
            ctx.fill();
            ctx.strokeStyle = '#38bdf8';
            ctx.lineWidth = 2.5;
            ctx.stroke();
            canvas.refresh();
        }

        if (!this.textures.exists('iso_build_plus')) {
            const canvas = this.textures.createCanvas('iso_build_plus', 40, 40);
            const ctx = canvas.getContext();
            ctx.fillStyle = 'rgba(15, 23, 42, 0.88)';
            ctx.beginPath();
            ctx.arc(20, 20, 16, 0, Math.PI * 2);
            ctx.fill();
            ctx.strokeStyle = '#f59e0b';
            ctx.lineWidth = 2;
            ctx.stroke();
            ctx.strokeStyle = '#fde047';
            ctx.lineWidth = 3;
            ctx.lineCap = 'round';
            ctx.beginPath();
            ctx.moveTo(12, 20); ctx.lineTo(28, 20);
            ctx.stroke();
            ctx.beginPath();
            ctx.moveTo(20, 12); ctx.lineTo(20, 28);
            ctx.stroke();
            canvas.refresh();
        }

        this.hoverCursor = this.add.image(0, 0, 'iso_hover')
            .setOrigin(0.5, 0.5)
            .setVisible(false)
            .setDepth(9999);

        // 悬浮气泡
        this.hoverTooltip = this.add.rectangle(0, 0, 140, 28, 0x0f172a, 0.9)
            .setStrokeStyle(1.5, 0x38bdf8)
            .setVisible(false)
            .setDepth(100000);
        this.hoverTooltipText = this.add.text(0, 0, '', {
            fontSize: '11px',
            color: '#f8fafc',
            fontFamily: 'system-ui, -apple-system, sans-serif'
        }).setOrigin(0.5).setVisible(false).setDepth(100001);

        this.cameras.main.setBackgroundColor('#0b101b');
        this.cameras.main.setZoom(1.0);
        this.cameras.main.centerOn(0, 0);

        this.setupControls();
        this.rebuildMap();
    }

    setupControls() {
        this.input.on('wheel', (pointer, gameObjects, deltaX, deltaY) => {
            const currentZoom = this.cameras.main.zoom;
            let newZoom = deltaY > 0 ? currentZoom - 0.08 : currentZoom + 0.08;
            newZoom = Phaser.Math.Clamp(newZoom, 0.45, 2.2);
            this.cameras.main.setZoom(newZoom);
        });

        this.input.on('pointerdown', (pointer) => {
            this.isDragging = true;
            this.dragStartX = pointer.x;
            this.dragStartY = pointer.y;
        });

        this.input.on('pointermove', (pointer) => {
            if (this.isDragging) {
                const dx = (pointer.x - this.dragStartX) / this.cameras.main.zoom;
                const dy = (pointer.y - this.dragStartY) / this.cameras.main.zoom;
                this.cameras.main.scrollX -= dx;
                this.cameras.main.scrollY -= dy;
                this.dragStartX = pointer.x;
                this.dragStartY = pointer.y;
                return;
            }

            const worldPoint = this.cameras.main.getWorldPoint(pointer.x, pointer.y);
            const { gx, gy } = isoToGrid(worldPoint.x, worldPoint.y, this.worldOriginX, this.worldOriginY);
            const w = map.value?.width || 7;
            const h = map.value?.height || 7;

            if (gx >= 0 && gx < w && gy >= 0 && gy < h) {
                this.hoveredGrid = { gx, gy };
                const isoPos = gridToIso(gx, gy, this.worldOriginX, this.worldOriginY);
                this.hoverCursor.setPosition(isoPos.x, isoPos.y).setVisible(true);
                this.updateTooltip(gx, gy, isoPos);
            } else {
                this.hoveredGrid = { gx: -1, gy: -1 };
                this.hoverCursor.setVisible(false);
                this.hoverTooltip.setVisible(false);
                this.hoverTooltipText.setVisible(false);
            }
        });

        this.input.on('pointerup', (pointer) => {
            const wasDrag = Math.abs(pointer.x - this.dragStartX) > 4 || Math.abs(pointer.y - this.dragStartY) > 4;
            this.isDragging = false;
            if (wasDrag) return;

            const { gx, gy } = this.hoveredGrid;
            if (gx < 0 || gy < 0) return;

            const b = buildingAt.value[`${gx},${gy}`];
            const p = projectAt.value[`${gx},${gy}`];
            const cell = (map.value?.cells || []).find(c => c.x === gx && c.y === gy);

            if (b) {
                if (mergeMode.value) toggleSelect(b);
                else emit('open-building', b);
            } else if (p) {
                // 建造中
                if (mergeMode.value) return;
            } else if (cell && cell.buildable) {
                if (!mergeMode.value) {
                    emit('open-build', { plot: currentPlot.value, cellX: gx, cellY: gy });
                }
            }
        });

        this.input.on('pointerupoutside', () => { this.isDragging = false; });
    }

    updateTooltip(gx, gy, isoPos) {
        const b = buildingAt.value[`${gx},${gy}`];
        const p = projectAt.value[`${gx},${gy}`];
        const cell = (map.value?.cells || []).find(c => c.x === gx && c.y === gy);

        let text = `(${gx}, ${gy})`;
        let strokeColor = 0x38bdf8;

        if (b) {
            const c = getComfortData(b.id);
            const name = b.form?.displayName || b.mother || '建筑';
            const bldLevel = b.formLevel ?? b.level ?? 1;
            const stars = '★'.repeat(Math.max(1, bldLevel));
            const total = c?.totalComfort ? c.totalComfort.toFixed(0) : '-';
            text = `${name} Lv${bldLevel} ${stars} · 舒适度: ★${total}`;
            if (c?.staffFactor < 0.999) text += ' (⚠人力不足)';
            strokeColor = 0x38bdf8;
        } else if (p) {
            text = `🚧 建造工期中 (${p.name || '新建筑'})`;
            strokeColor = 0xf59e0b;
        } else if (cell) {
            if (cell.buildable) {
                text = `🟩 可建造空地 (${gx}, ${gy}) · 点击规划建造`;
                strokeColor = 0x10b981;
            } else {
                const meta = COLOR_PALETTE.TERRAIN[cell.terrain] || { name: '自然地貌' };
                text = `🔒 ${meta.name} · 不可建`;
                strokeColor = 0x94a3b8;
            }
        }

        const width = Math.max(130, text.length * 11 + 24);
        this.hoverTooltip.setPosition(isoPos.x, isoPos.y - 42).setSize(width, 26).setStrokeStyle(1.5, strokeColor).setVisible(true);
        this.hoverTooltipText.setPosition(isoPos.x, isoPos.y - 42).setText(text).setVisible(true);
    }

    resetCamera() {
        this.cameras.main.setZoom(1.0);
        this.cameras.main.centerOn(0, 0);
    }

    zoomIn() {
        this.cameras.main.setZoom(Phaser.Math.Clamp(this.cameras.main.zoom + 0.2, 0.45, 2.2));
    }

    zoomOut() {
        this.cameras.main.setZoom(Phaser.Math.Clamp(this.cameras.main.zoom - 0.2, 0.45, 2.2));
    }

    rebuildMap() {
        if (!this.gridContainer || !this.buildingsContainer) return;
        this.gridContainer.removeAll(true);
        this.buildingsContainer.removeAll(true);

        const currentMap = map.value;
        const w = currentMap?.width || currentPlot.value?.width || 7;
        const h = currentMap?.height || currentPlot.value?.height || 7;

        // 计算中心点使地图在世界原点 (0, 0)
        const centerIso = gridToIso((w - 1) / 2, (h - 1) / 2, 0, 0);
        this.worldOriginX = -centerIso.x;
        this.worldOriginY = -centerIso.y;

        const cellMap = {};
        for (const c of currentMap?.cells || []) {
            cellMap[`${c.x},${c.y}`] = c;
        }

        for (let gx = 0; gx < w; gx++) {
            for (let gy = 0; gy < h; gy++) {
                const cell = cellMap[`${gx},${gy}`] || { x: gx, y: gy, buildable: true, terrain: 'PLAIN' };
                const isoPos = gridToIso(gx, gy, this.worldOriginX, this.worldOriginY);

                // 1. 渲染地面颜色瓦片
                this.renderTile(cell, gx, gy, isoPos);

                // 2. 检查建筑或工程
                const b = buildingAt.value[`${gx},${gy}`];
                const p = projectAt.value[`${gx},${gy}`];

                if (b) {
                    this.renderBuildingBlock(b, gx, gy, isoPos);
                } else if (p) {
                    this.renderProjectBlock(p, gx, gy, isoPos);
                } else if (cell.buildable && !mergeMode.value) {
                    // 可建空地浮动金色 "+" 标牌
                    const markerDepth = getIsoDepth(gx, gy, 2);
                    const marker = this.add.image(isoPos.x, isoPos.y - 6, 'iso_build_plus')
                        .setOrigin(0.5, 0.5)
                        .setScale(0.75)
                        .setDepth(markerDepth);
                    this.buildingsContainer.add(marker);

                    this.tweens.add({
                        targets: marker,
                        y: isoPos.y - 14,
                        duration: 1300 + (gx + gy) * 70,
                        yoyo: true,
                        repeat: -1,
                        ease: 'Sine.easeInOut'
                    });
                }
            }
        }
    }

    renderTile(cell, gx, gy, isoPos) {
        const depth = getIsoDepth(gx, gy, 0);
        const palette = cell.buildable
            ? COLOR_PALETTE.TERRAIN.BUILDABLE
            : (COLOR_PALETTE.TERRAIN[cell.terrain] || COLOR_PALETTE.TERRAIN.PLAIN);

        const gfx = this.add.graphics();
        gfx.setDepth(depth);

        const halfW = 64;
        const halfH = 32;
        const thick = 8;

        // 左侧厚度面
        gfx.fillStyle(palette.sideLeft, 1);
        gfx.beginPath();
        gfx.moveTo(isoPos.x - halfW, isoPos.y);
        gfx.lineTo(isoPos.x, isoPos.y + halfH);
        gfx.lineTo(isoPos.x, isoPos.y + halfH + thick);
        gfx.lineTo(isoPos.x - halfW, isoPos.y + thick);
        gfx.closePath();
        gfx.fill();

        // 右侧厚度面
        gfx.fillStyle(palette.sideRight, 1);
        gfx.beginPath();
        gfx.moveTo(isoPos.x, isoPos.y + halfH);
        gfx.lineTo(isoPos.x + halfW, isoPos.y);
        gfx.lineTo(isoPos.x + halfW, isoPos.y + thick);
        gfx.lineTo(isoPos.x, isoPos.y + halfH + thick);
        gfx.closePath();
        gfx.fill();

        // 顶面菱形
        gfx.fillStyle(palette.top, 1);
        gfx.beginPath();
        gfx.moveTo(isoPos.x, isoPos.y - halfH);
        gfx.lineTo(isoPos.x + halfW, isoPos.y);
        gfx.lineTo(isoPos.x, isoPos.y + halfH);
        gfx.lineTo(isoPos.x - halfW, isoPos.y);
        gfx.closePath();
        gfx.fill();

        // 顶面描边
        gfx.lineStyle(cell.buildable ? 2.0 : 1.2, palette.stroke, cell.buildable ? 0.95 : 0.65);
        gfx.strokePath();

        this.gridContainer.add(gfx);

        // 自然地貌居中单字徽章 (城/湖/山/原)
        if (!cell.buildable && palette.label) {
            const badgeBg = this.add.circle(isoPos.x, isoPos.y, 13, 0x0f172a, 0.5).setDepth(depth + 1);
            const labelText = this.add.text(isoPos.x, isoPos.y, palette.label, {
                fontSize: '12px',
                fontFamily: 'system-ui, -apple-system, sans-serif',
                fontStyle: 'bold',
                color: '#f8fafc'
            }).setOrigin(0.5).setDepth(depth + 2);
            this.gridContainer.add([badgeBg, labelText]);
        }
    }

    renderBuildingBlock(bld, gx, gy, isoPos) {
        const depth = getIsoDepth(gx, gy, 3);
        const theme = getBuildingTheme(bld);
        const isSelected = mergeSel.value.includes(bld.id);

        const gfx = this.add.graphics();
        gfx.setDepth(depth);

        const halfW = 46;
        const halfH = 23;
        const bldHeight = 26;
        const topY = isoPos.y - bldHeight;

        // 左侧面
        gfx.fillStyle(theme.sideLeft, 1);
        gfx.beginPath();
        gfx.moveTo(isoPos.x - halfW, topY);
        gfx.lineTo(isoPos.x, topY + halfH);
        gfx.lineTo(isoPos.x, isoPos.y + halfH * 0.5);
        gfx.lineTo(isoPos.x - halfW, isoPos.y - halfH * 0.5);
        gfx.closePath();
        gfx.fill();

        // 右侧面
        gfx.fillStyle(theme.sideRight, 1);
        gfx.beginPath();
        gfx.moveTo(isoPos.x, topY + halfH);
        gfx.lineTo(isoPos.x + halfW, topY);
        gfx.lineTo(isoPos.x + halfW, isoPos.y - halfH * 0.5);
        gfx.lineTo(isoPos.x, isoPos.y + halfH * 0.5);
        gfx.closePath();
        gfx.fill();

        // 顶面
        gfx.fillStyle(theme.top, 1);
        gfx.beginPath();
        gfx.moveTo(isoPos.x, topY - halfH);
        gfx.lineTo(isoPos.x + halfW, topY);
        gfx.lineTo(isoPos.x, topY + halfH);
        gfx.lineTo(isoPos.x - halfW, topY);
        gfx.closePath();
        gfx.fill();

        // 描边 (合并选中时为金色粗发光边)
        gfx.lineStyle(isSelected ? 3.0 : 1.8, isSelected ? 0xfbbf24 : theme.stroke, isSelected ? 1.0 : 0.95);
        gfx.strokePath();

        this.buildingsContainer.add(gfx);

        // 悬浮标牌
        const form = bld.form;
        const name = form?.displayName || bld.mother || '建筑';
        const bldLevel = bld.formLevel ?? bld.level ?? 1;
        const stars = '★'.repeat(Math.max(1, bldLevel));
        const badge = theme.badge || '基';

        let title = `[${badge}] ${name} Lv${bldLevel} ${stars}`;
        if (mapLayer.value === 'comfort') {
            const c = getComfortData(bld.id);
            const total = c?.totalComfort ? c.totalComfort.toFixed(0) : '-';
            title += ` ★${total}`;
        }
        if (isSelected) title = `✓ ${title}`;

        const tagWidth = Math.max(96, title.length * 11 + 24);

        const tagBg = this.add.rectangle(isoPos.x, topY - 32, tagWidth, 22, 0x0f172a, 0.9)
            .setStrokeStyle(isSelected ? 2 : 1.2, isSelected ? 0xfbbf24 : theme.stroke)
            .setDepth(depth + 1);
        const tagText = this.add.text(isoPos.x, topY - 32, title, {
            fontSize: '11px',
            color: isSelected ? '#fbbf24' : '#f8fafc',
            fontFamily: 'system-ui, -apple-system, sans-serif'
        }).setOrigin(0.5).setDepth(depth + 2);

        this.buildingsContainer.add([tagBg, tagText]);
    }

    renderProjectBlock(p, gx, gy, isoPos) {
        const depth = getIsoDepth(gx, gy, 3);
        const gfx = this.add.graphics();
        gfx.setDepth(depth);

        const halfW = 46;
        const halfH = 23;
        const topY = isoPos.y - 20;

        gfx.fillStyle(0xd97706, 1);
        gfx.beginPath();
        gfx.moveTo(isoPos.x - halfW, topY);
        gfx.lineTo(isoPos.x, topY + halfH);
        gfx.lineTo(isoPos.x, isoPos.y + halfH * 0.5);
        gfx.lineTo(isoPos.x - halfW, isoPos.y - halfH * 0.5);
        gfx.closePath();
        gfx.fill();

        gfx.fillStyle(0xb45309, 1);
        gfx.beginPath();
        gfx.moveTo(isoPos.x, topY + halfH);
        gfx.lineTo(isoPos.x + halfW, topY);
        gfx.lineTo(isoPos.x + halfW, isoPos.y - halfH * 0.5);
        gfx.lineTo(isoPos.x, isoPos.y + halfH * 0.5);
        gfx.closePath();
        gfx.fill();

        gfx.fillStyle(0xf59e0b, 1);
        gfx.beginPath();
        gfx.moveTo(isoPos.x, topY - halfH);
        gfx.lineTo(isoPos.x + halfW, topY);
        gfx.lineTo(isoPos.x, topY + halfH);
        gfx.lineTo(isoPos.x - halfW, topY);
        gfx.closePath();
        gfx.fill();

        gfx.lineStyle(1.8, 0xfef08a, 0.95);
        gfx.strokePath();
        this.buildingsContainer.add(gfx);

        const remainDays = p.daysRemaining || 1;
        const tagBg = this.add.rectangle(isoPos.x, topY - 30, 96, 22, 0x1e293b, 0.92)
            .setStrokeStyle(1.5, 0xf59e0b)
            .setDepth(depth + 1);
        const tagText = this.add.text(isoPos.x, topY - 30, `🚧 建造中 (${remainDays}天)`, {
            fontSize: '11px',
            color: '#fbbf24',
            fontFamily: 'system-ui, -apple-system, sans-serif',
            fontStyle: 'bold'
        }).setOrigin(0.5).setDepth(depth + 2);
        this.buildingsContainer.add([tagBg, tagText]);
    }
}

// 监听 Vue 响应式状态重绘
watch([selectedPlotId, () => store.state, mapLayer, mergeSel], () => {
    if (currentScene) {
        currentScene.rebuildMap();
    }
}, { deep: true });

onMounted(() => {
    if (!phaserContainer.value) return;

    const config = {
        type: Phaser.AUTO,
        parent: phaserContainer.value,
        width: phaserContainer.value.clientWidth || 800,
        height: 580,
        scale: {
            mode: Phaser.Scale.RESIZE,
            autoCenter: Phaser.Scale.CENTER_BOTH
        },
        render: {
            antialias: true,
            pixelArt: false
        },
        scene: [PhaserMapScene]
    };

    phaserGame = new Phaser.Game(config);
});

onBeforeUnmount(() => {
    if (phaserGame) {
        phaserGame.destroy(true);
        phaserGame = null;
        currentScene = null;
    }
});
</script>

<template>
    <div class="plot-map-panel panel">
        <div class="panel-head">
            <span class="title">🏛️ 地块地图 (Phaser 3D 等轴测沙盘)</span>
            <span class="extra">
                <div class="layer-toggle">
                    <button class="layer-btn" :class="{ active: mapLayer === 'normal' }" @click="mapLayer = 'normal'">标准</button>
                    <button class="layer-btn" :class="{ active: mapLayer === 'comfort' }" @click="mapLayer = 'comfort'">舒适度热力</button>
                </div>
                <button v-for="p in unlockedPlots" :key="p.id" class="ghost" :class="{ primary: p.id === selectedPlotId }"
                        @click="selectedPlotId = p.id">{{ p.displayName }}</button>
                <button class="gold" :class="{ danger: mergeMode }" @click="mergeMode = !mergeMode; mergeSel = []">
                    {{ mergeMode ? '退出合并' : '合并进化' }}
                </button>
            </span>
        </div>

        <div class="panel-body" style="padding: 12px;">
            <div v-if="!unlockedPlots.length" class="empty faint">尚未解锁任何地块</div>
            <div v-else-if="!map" class="empty faint">地块地图数据缺失</div>
            <template v-else>
                <!-- 提示条与视口控制 -->
                <div class="map-toolbar" style="display:flex; justify-content:space-between; align-items:center; margin-bottom:8px;">
                    <span v-if="mergeMode" class="gold">合并模式：点击选择两座相邻建筑（消耗两个、新建一个）。已选 {{ mergeSel.length }} / 2</span>
                    <span v-else-if="mapLayer === 'comfort'" class="pos">舒适度视图：展示各建筑实际舒适度数值，带 ⚠ 表示人力短缺受损</span>
                    <span v-else class="faint">点击带「+」空地规划新建 · 点击建筑弹出详情（升级 / 分支进化 / 内装） · 支持鼠标滚轮缩放与按住平移</span>
                    
                    <div style="display:flex; gap:6px;">
                        <button class="ghost" style="padding:4px 8px; font-size:12px;" @click="currentScene?.resetCamera()">🎯 居中复位</button>
                        <button class="ghost" style="padding:4px 8px; font-size:12px;" @click="currentScene?.zoomIn()">🔍 放大</button>
                        <button class="ghost" style="padding:4px 8px; font-size:12px;" @click="currentScene?.zoomOut()">🔎 缩小</button>
                    </div>
                </div>

                <!-- Phaser 渲染画布容器 -->
                <div ref="phaserContainer" class="phaser-viewport-wrapper"></div>

                <!-- 图例说明 -->
                <div class="legend" style="margin-top:10px;">
                    <span class="faint">图例：</span>
                    <span class="lg"><i class="dot town" style="background:#64748b;"></i>城镇</span>
                    <span class="lg"><i class="dot plain" style="background:#22c55e;"></i>平原</span>
                    <span class="lg"><i class="dot mountain" style="background:#b45309;"></i>山地</span>
                    <span class="lg"><i class="dot lake" style="background:#0284c7;"></i>湖区</span>
                    <span class="lg"><i class="dot buildable" style="background:#10b981;"></i>可建空地</span>
                    <span class="lg"><b class="warn-star" style="color:#fbbf24;">★</b>形态星级与舒适度</span>
                </div>
            </template>

            <!-- 合并进化配方确认区 (完全复用) -->
            <div v-if="mergeMode && mergeSel.length === 2" class="panel" style="margin-top: 16px">
                <div class="panel-body">
                    <div v-if="matched">
                        <div class="kv"><span class="k">匹配配方</span><span class="v">{{ matched.displayName }}</span></div>
                        <div class="kv"><span class="k">产物形态</span><span class="v">{{ matched.resultForm?.displayName }}</span></div>
                        <div class="kv">
                            <span class="k">合并花费</span>
                            <span class="v num" :class="canMerge ? 'pos' : 'neg'">{{ fmtMoney(matched.mergeCost) }}</span>
                        </div>
                        <div v-if="!isAdjacent" class="neg hint">两栋建筑必须在网格中相邻（上下左右直接相连）</div>
                        <div v-else-if="!canMerge" class="neg hint">资金不足，无法启动合并工期</div>
                        <div v-else class="pos hint">满足全部合并条件，点击下方按钮开始融合</div>
                        <div class="btn-group right" style="margin-top: 10px">
                            <button class="gold" :disabled="!canMerge" @click="confirmMerge">确认合并（开工）</button>
                        </div>
                    </div>
                    <div v-else class="neg faint">未找到匹配的合并进化配方</div>
                </div>
            </div>
        </div>
    </div>
</template>

<style scoped>
.phaser-viewport-wrapper {
    width: 100%;
    height: 580px;
    border-radius: 10px;
    border: 1px solid rgba(255, 255, 255, 0.12);
    overflow: hidden;
    position: relative;
    background: #0b101b;
}

.phaser-viewport-wrapper :deep(canvas) {
    display: block;
    width: 100% !important;
    height: 100% !important;
    outline: none;
}
</style>
