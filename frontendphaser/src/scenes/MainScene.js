// 主场景：2:1 等轴测大地图渲染、纯净颜色格子/数字沙盘、多阶地块、地形划分与相机控制 (预留资产库模式)
import { gameStore } from '../network/gameStore.js';
import { 
    AssetKeys, 
    RENDER_MODE, 
    ColorPalette, 
    getBuildingColorTheme, 
    getTerrainTextureKey, 
    getBuildingTextureKey 
} from '../config/assetConfig.js';
import { ISO_TILE_WIDTH, ISO_TILE_HEIGHT, gridToIso, isoToGrid, getIsoDepth } from '../utils/isoUtils.js';

export default class MainScene extends Phaser.Scene {
    constructor() {
        super({ key: 'MainScene' });
        this.worldOriginX = 0;
        this.worldOriginY = 0;
        this.gridContainer = null;
        this.buildingsContainer = null;
        this.uiOverlayContainer = null;
        
        // 交互光标对象
        this.hoverCursor = null;
        this.selectedCursor = null;

        // 悬浮悬停提示框
        this.hoverTooltip = null;
        this.hoverTooltipText = null;

        // 当前悬浮网格坐标
        this.hoveredGrid = { gx: -1, gy: -1 };

        // 拖拽平移相关
        this.isDragging = false;
        this.dragStartX = 0;
        this.dragStartY = 0;
    }

    create() {
        // 创建分层容器
        this.gridContainer = this.add.container(0, 0);
        this.buildingsContainer = this.add.container(0, 0);
        this.uiOverlayContainer = this.add.container(0, 0);

        // 创建悬浮与选中等轴测菱形光标 (置于顶层)
        this.hoverCursor = this.add.image(0, 0, AssetKeys.TILES.HOVER)
            .setOrigin(0.5, 0.5)
            .setVisible(false)
            .setDepth(9999);

        this.selectedCursor = this.add.image(0, 0, AssetKeys.TILES.SELECTED)
            .setOrigin(0.5, 0.5)
            .setVisible(false)
            .setDepth(9998);

        // 创建悬浮信息气泡
        this.hoverTooltip = this.add.rectangle(0, 0, 140, 30, 0x0f172a, 0.88)
            .setStrokeStyle(1, 0x38bdf8)
            .setVisible(false)
            .setDepth(100000);
        this.hoverTooltipText = this.add.text(0, 0, '', {
            fontSize: '11px',
            color: '#f8fafc',
            fontFamily: 'system-ui, -apple-system, sans-serif'
        }).setOrigin(0.5).setVisible(false).setDepth(100001);

        // 相机配置：背景深色、居中原点
        this.cameras.main.setBackgroundColor('#0b101b');
        this.cameras.main.setZoom(1.0);
        this.cameras.main.centerOn(0, 0);

        // 绑定相机平移缩放
        this.setupCameraControls();

        // 绑定网格交互
        this.setupTileInteraction();

        // 监听 store 事件以重绘地图
        this.bindStoreEvents();

        // 尝试构建地图
        this.rebuildPlotMap();
    }

    setupCameraControls() {
        // 滚轮缩放
        this.input.on('wheel', (pointer, gameObjects, deltaX, deltaY) => {
            const currentZoom = this.cameras.main.zoom;
            let newZoom = deltaY > 0 ? currentZoom - 0.1 : currentZoom + 0.1;
            newZoom = Phaser.Math.Clamp(newZoom, 0.45, 2.2);
            this.cameras.main.setZoom(newZoom);
        });

        // 拖拽平移
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
            }
        });

        this.input.on('pointerup', () => { this.isDragging = false; });
        this.input.on('pointerupoutside', () => { this.isDragging = false; });
    }

    setupTileInteraction() {
        this.input.on('pointermove', (pointer) => {
            if (this.isDragging) return;
            const worldPoint = this.cameras.main.getWorldPoint(pointer.x, pointer.y);
            const { gx, gy } = isoToGrid(worldPoint.x, worldPoint.y, this.worldOriginX, this.worldOriginY);
            
            const plotMap = this.getCurrentPlotMap();
            const width = plotMap?.width || 7;
            const height = plotMap?.height || 7;

            if (gx >= 0 && gx < width && gy >= 0 && gy < height) {
                this.hoveredGrid = { gx, gy };
                const isoPos = gridToIso(gx, gy, this.worldOriginX, this.worldOriginY);
                this.hoverCursor.setPosition(isoPos.x, isoPos.y).setVisible(true);

                // 更新悬浮提示信息
                this.updateHoverTooltip(gx, gy, isoPos);
            } else {
                this.hoveredGrid = { gx: -1, gy: -1 };
                this.hoverCursor.setVisible(false);
                this.hoverTooltip.setVisible(false);
                this.hoverTooltipText.setVisible(false);
            }
        });

        this.input.on('pointerdown', (pointer) => {
            if (pointer.button !== 0) return;
            const { gx, gy } = this.hoveredGrid;
            if (gx < 0 || gy < 0) return;

            const plotId = this.getCurrentPlotId();
            const isoPos = gridToIso(gx, gy, this.worldOriginX, this.worldOriginY);
            this.selectedCursor.setPosition(isoPos.x, isoPos.y).setVisible(true);

            // 查询该格是否有建筑与地貌
            const building = this.getBuildingAt(plotId, gx, gy);
            const cell = this.getCellAt(gx, gy);

            if (building) {
                // 点击已建建筑
                gameStore.selectBuilding(building.id);
                gameStore.selectCell(gx, gy);
            } else if (cell && cell.buildable) {
                // 点击可建造空地：重置选中的建筑并触发落格选中，弹出建造规划
                gameStore.selectBuilding(null);
                gameStore.selectCell(gx, gy);
            } else {
                // 点击不可建自然地貌
                gameStore.selectBuilding(null);
            }
        });
    }

    updateHoverTooltip(gx, gy, isoPos) {
        const plotId = this.getCurrentPlotId();
        const bld = this.getBuildingAt(plotId, gx, gy);
        const cell = this.getCellAt(gx, gy);

        let text = `(${gx}, ${gy})`;
        let strokeColor = 0x38bdf8;

        if (bld) {
            const formName = bld.form?.displayName || '度假建筑';
            const bldLevel = bld.formLevel ?? bld.level ?? 1;
            const stars = '★'.repeat(Math.max(1, bldLevel));
            const status = (bld.status === 'UNDER_CONSTRUCTION' || bld.underConstruction) ? '🚧 施工中' : `Lv${bldLevel} ${stars}`;
            text = `${formName} ${status}`;
            strokeColor = 0x38bdf8;
        } else if (cell) {
            if (cell.buildable) {
                text = `🟩 可建造空地 (${gx}, ${gy}) · 点击规划建造`;
                strokeColor = 0x10b981; // 鲜艳绿色
            } else {
                const terrainNames = { TOWN: '城镇街区', LAKE: '湖区水面', MOUNTAIN: '山地悬崖', PLAIN: '自然草甸' };
                const tName = terrainNames[cell.terrain] || '自然地貌';
                text = `🔒 ${tName} · 自然地貌 (不可建)`;
                strokeColor = 0x94a3b8; // 灰色
            }
        }

        const width = Math.max(130, text.length * 12 + 24);
        this.hoverTooltip.setPosition(isoPos.x, isoPos.y - 45).setSize(width, 26).setStrokeStyle(1.5, strokeColor).setVisible(true);
        this.hoverTooltipText.setPosition(isoPos.x, isoPos.y - 45).setText(text).setVisible(true);
    }

    bindStoreEvents() {
        gameStore.on('PLOT_CHANGED', () => {
            this.rebuildPlotMap();
        });

        gameStore.on('STATE_UPDATED', () => {
            this.rebuildPlotMap();
        });

        gameStore.on('CONFIG_UPDATED', () => {
            this.rebuildPlotMap();
        });

        gameStore.on('PROJECT_COMPLETED', (msg) => {
            this.playCelebrationEffect(msg);
            this.rebuildPlotMap();
        });
    }

    getCurrentPlotId() {
        return gameStore.currentPlotId || gameStore.determineHighestUnlockedPlot() || 'STORMVEIL_CASTLE';
    }

    getCurrentPlotMap() {
        const plotId = this.getCurrentPlotId();
        if (gameStore.config?.plotMapsById?.[plotId]) {
            return gameStore.config.plotMapsById[plotId];
        }
        return (gameStore.config?.plotMaps || [])[0] || null;
    }

    getCellAt(gx, gy) {
        const plotMap = this.getCurrentPlotMap();
        if (!plotMap || !plotMap.cells) return null;
        return plotMap.cells.find(c => c.x === gx && c.y === gy) || null;
    }

    getBuildingAt(plotId, gx, gy) {
        const buildings = gameStore.state?.buildings || [];
        return buildings.find(b => b.plotId === plotId && b.cellX === gx && b.cellY === gy);
    }

    rebuildPlotMap() {
        // 清空容器
        this.gridContainer.removeAll(true);
        this.buildingsContainer.removeAll(true);

        const plotMap = this.getCurrentPlotMap();
        const plotConfig = gameStore.config?.plotsById?.[this.getCurrentPlotId()] || {};
        
        const width = plotMap?.width || plotConfig.width || 7;
        const height = plotMap?.height || plotConfig.height || 7;

        // 计算中心点：使地图在世界原点 (0, 0)
        const centerIso = gridToIso((width - 1) / 2, (height - 1) / 2, 0, 0);
        this.worldOriginX = -centerIso.x;
        this.worldOriginY = -centerIso.y;

        const cellMap = {};
        if (plotMap?.cells) {
            for (const c of plotMap.cells) {
                cellMap[`${c.x},${c.y}`] = c;
            }
        }

        const plotId = this.getCurrentPlotId();

        // 渲染等轴测底板与所有瓦片
        for (let gx = 0; gx < width; gx++) {
            for (let gy = 0; gy < height; gy++) {
                const cell = cellMap[`${gx},${gy}`] || { x: gx, y: gy, buildable: true, terrain: 'PLAIN' };
                const isoPos = gridToIso(gx, gy, this.worldOriginX, this.worldOriginY);

                if (RENDER_MODE === 'COLOR_BLOCK') {
                    // 1. 颜色格子模式：清晰等轴测立体地盘
                    this.renderColorBlockTile(cell, gx, gy, isoPos);
                } else {
                    // 2. 预留资产库模式：Kenney PNG 贴图
                    const tileTexture = getTerrainTextureKey(cell.buildable, cell.terrain, gx, gy);
                    const tex = this.textures.get(tileTexture);
                    const texHeight = tex?.source?.[0]?.height || 83;
                    const originY = 32 / texHeight;

                    const tileSprite = this.add.image(isoPos.x, isoPos.y, tileTexture)
                        .setOrigin(0.5, originY)
                        .setDepth(getIsoDepth(gx, gy, 0));
                    this.gridContainer.add(tileSprite);
                }

                // 检查该格是否有建筑
                const bld = this.getBuildingAt(plotId, gx, gy);
                if (bld) {
                    if (RENDER_MODE === 'COLOR_BLOCK') {
                        // 颜色格子模式：立体彩色建筑方块
                        this.renderColorBlockBuilding(bld, gx, gy, isoPos);
                    } else {
                        // 预留资产库模式：City Kit 立绘
                        this.renderBuildingInstance(bld, gx, gy, isoPos);
                    }
                } else if (cell.buildable) {
                    // 可建造空地：渲染醒目的 '+' 建造标记与浮动动画
                    const markerDepth = getIsoDepth(gx, gy, 2);
                    const marker = this.add.image(isoPos.x, isoPos.y - 6, AssetKeys.TILES.BUILD_MARKER)
                        .setOrigin(0.5, 0.5)
                        .setScale(0.8)
                        .setDepth(markerDepth);
                    this.buildingsContainer.add(marker);

                    // 缓动漂浮动画
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

    /**
     * 渲染纯净等轴测颜色瓦片 (带立体侧面厚度与单字地貌标识)
     */
    renderColorBlockTile(cell, gx, gy, isoPos) {
        const depth = getIsoDepth(gx, gy, 0);
        const palette = cell.buildable 
            ? ColorPalette.TERRAIN.BUILDABLE 
            : (ColorPalette.TERRAIN[cell.terrain] || ColorPalette.TERRAIN.PLAIN);

        const gfx = this.add.graphics();
        gfx.setDepth(depth);

        const halfW = 64; // 128 / 2
        const halfH = 32; // 64 / 2
        const thick = 8;  // 立体基座厚度

        // 1. 左侧面厚度
        gfx.fillStyle(palette.sideLeft, 1);
        gfx.beginPath();
        gfx.moveTo(isoPos.x - halfW, isoPos.y);
        gfx.lineTo(isoPos.x, isoPos.y + halfH);
        gfx.lineTo(isoPos.x, isoPos.y + halfH + thick);
        gfx.lineTo(isoPos.x - halfW, isoPos.y + thick);
        gfx.closePath();
        gfx.fill();

        // 2. 右侧面厚度
        gfx.fillStyle(palette.sideRight, 1);
        gfx.beginPath();
        gfx.moveTo(isoPos.x, isoPos.y + halfH);
        gfx.lineTo(isoPos.x + halfW, isoPos.y);
        gfx.lineTo(isoPos.x + halfW, isoPos.y + thick);
        gfx.lineTo(isoPos.x, isoPos.y + halfH + thick);
        gfx.closePath();
        gfx.fill();

        // 3. 顶面菱形
        gfx.fillStyle(palette.top, 1);
        gfx.beginPath();
        gfx.moveTo(isoPos.x, isoPos.y - halfH);
        gfx.lineTo(isoPos.x + halfW, isoPos.y);
        gfx.lineTo(isoPos.x, isoPos.y + halfH);
        gfx.lineTo(isoPos.x - halfW, isoPos.y);
        gfx.closePath();
        gfx.fill();

        // 4. 轮廓描边
        gfx.lineStyle(cell.buildable ? 2.0 : 1.2, palette.stroke, cell.buildable ? 0.95 : 0.65);
        gfx.strokePath();

        this.gridContainer.add(gfx);

        // 自然地貌中心徽章文字 (城/湖/山/原)
        if (!cell.buildable && palette.label) {
            const badgeBg = this.add.circle(isoPos.x, isoPos.y, 13, 0x0f172a, 0.5)
                .setDepth(depth + 1);
            const labelText = this.add.text(isoPos.x, isoPos.y, palette.label, {
                fontSize: '12px',
                fontFamily: 'system-ui, -apple-system, sans-serif',
                fontStyle: 'bold',
                color: '#f8fafc'
            }).setOrigin(0.5).setDepth(depth + 2);
            this.gridContainer.add([badgeBg, labelText]);
        }
    }

    /**
     * 渲染纯净等轴测立体建筑颜色方块 (带有类别、星级与状态标牌)
     */
    renderColorBlockBuilding(bld, gx, gy, isoPos) {
        const depth = getIsoDepth(gx, gy, 3);
        const form = bld.form;
        const isUnderConstruction = bld.status === 'UNDER_CONSTRUCTION' || bld.underConstruction;
        const theme = getBuildingColorTheme(form, isUnderConstruction);

        const gfx = this.add.graphics();
        gfx.setDepth(depth);

        // 建筑方块参数 (略收缩以突出地基)
        const halfW = 46;
        const halfH = 23;
        const bldHeight = 26; // 方块立高
        const topY = isoPos.y - bldHeight;

        // 1. 左侧面
        gfx.fillStyle(theme.sideLeft, 1);
        gfx.beginPath();
        gfx.moveTo(isoPos.x - halfW, topY);
        gfx.lineTo(isoPos.x, topY + halfH);
        gfx.lineTo(isoPos.x, isoPos.y + halfH * 0.5);
        gfx.lineTo(isoPos.x - halfW, isoPos.y - halfH * 0.5);
        gfx.closePath();
        gfx.fill();

        // 2. 右侧面
        gfx.fillStyle(theme.sideRight, 1);
        gfx.beginPath();
        gfx.moveTo(isoPos.x, topY + halfH);
        gfx.lineTo(isoPos.x + halfW, topY);
        gfx.lineTo(isoPos.x + halfW, isoPos.y - halfH * 0.5);
        gfx.lineTo(isoPos.x, isoPos.y + halfH * 0.5);
        gfx.closePath();
        gfx.fill();

        // 3. 顶面菱形
        gfx.fillStyle(theme.top, 1);
        gfx.beginPath();
        gfx.moveTo(isoPos.x, topY - halfH);
        gfx.lineTo(isoPos.x + halfW, topY);
        gfx.lineTo(isoPos.x, topY + halfH);
        gfx.lineTo(isoPos.x - halfW, topY);
        gfx.closePath();
        gfx.fill();

        // 4. 顶面高光线
        gfx.lineStyle(1.8, theme.stroke, 0.95);
        gfx.strokePath();

        this.buildingsContainer.add(gfx);

        // 5. 悬浮精致信息标牌
        if (isUnderConstruction) {
            const remainDays = bld.constructionDaysRemaining || 1;
            const tagBg = this.add.rectangle(isoPos.x, topY - 32, 96, 22, 0x1e293b, 0.92)
                .setStrokeStyle(1.5, 0xf59e0b)
                .setDepth(depth + 1);
            const tagText = this.add.text(isoPos.x, topY - 32, `🚧 施工中 (${remainDays}天)`, {
                fontSize: '11px',
                color: '#fbbf24',
                fontFamily: 'system-ui, -apple-system, sans-serif',
                fontStyle: 'bold'
            }).setOrigin(0.5).setDepth(depth + 2);
            this.buildingsContainer.add([tagBg, tagText]);
        } else {
            const name = form?.displayName || '度假建筑';
            const bldLevel = bld.formLevel ?? bld.level ?? 1;
            const stars = '★'.repeat(Math.max(1, bldLevel));
            const badge = theme.badge || '基';
            const title = `[${badge}] ${name} Lv${bldLevel} ${stars}`;
            const tagWidth = Math.max(96, title.length * 11 + 24);

            const tagBg = this.add.rectangle(isoPos.x, topY - 32, tagWidth, 22, 0x0f172a, 0.9)
                .setStrokeStyle(1.2, theme.stroke)
                .setDepth(depth + 1);
            const tagText = this.add.text(isoPos.x, topY - 32, title, {
                fontSize: '11px',
                color: '#f8fafc',
                fontFamily: 'system-ui, -apple-system, sans-serif'
            }).setOrigin(0.5).setDepth(depth + 2);
            this.buildingsContainer.add([tagBg, tagText]);
        }
    }

    /**
     * 预留：图片素材模式建筑渲染 (随时可切换回)
     */
    renderBuildingInstance(bld, gx, gy, isoPos) {
        const depth = getIsoDepth(gx, gy, 3);
        const form = bld.form;
        const isUnderConstruction = bld.status === 'UNDER_CONSTRUCTION' || bld.underConstruction;

        if (isUnderConstruction) {
            const scaffold = this.add.image(isoPos.x, isoPos.y, AssetKeys.BUILDINGS.SCAFFOLD)
                .setOrigin(0.5, 0.82)
                .setDepth(depth);
            this.buildingsContainer.add(scaffold);

            const remainDays = bld.constructionDaysRemaining || 1;
            const tagBg = this.add.rectangle(isoPos.x, isoPos.y - 75, 84, 22, 0x1e293b, 0.88)
                .setStrokeStyle(1, 0xf59e0b)
                .setDepth(depth + 1);
            const tagText = this.add.text(isoPos.x, isoPos.y - 75, `🚧 工期 ${remainDays}天`, {
                fontSize: '11px',
                color: '#fbbf24',
                fontFamily: 'sans-serif',
                fontStyle: 'bold'
            }).setOrigin(0.5).setDepth(depth + 2);
            this.buildingsContainer.add([tagBg, tagText]);
        } else {
            const formId = form?.id || form?.formId;
            const level = bld.formLevel ?? bld.level ?? 1;
            const texKey = getBuildingTextureKey(formId, level, bld.id);

            const buildingSprite = this.add.image(isoPos.x, isoPos.y, texKey)
                .setOrigin(0.5, 0.81)
                .setScale(1.1)
                .setDepth(depth);
            this.buildingsContainer.add(buildingSprite);

            const stars = '★'.repeat(Math.max(1, level));
            const name = form?.displayName || '度假建筑';
            const title = `${name} Lv${level} ${stars}`;
            const tagWidth = Math.max(86, title.length * 11 + 22);
            
            const tagBg = this.add.rectangle(isoPos.x, isoPos.y - 88, tagWidth, 22, 0x0f172a, 0.85)
                .setStrokeStyle(1, 0x38bdf8)
                .setDepth(depth + 1);
            const tagText = this.add.text(isoPos.x, isoPos.y - 88, title, {
                fontSize: '11px',
                color: '#ffffff',
                fontFamily: 'sans-serif'
            }).setOrigin(0.5).setDepth(depth + 2);

            this.buildingsContainer.add([tagBg, tagText]);
        }
    }

    /**
     * 播放竣工/升星/图鉴解锁金色闪耀粒子特效
     */
    playCelebrationEffect(msg) {
        const { x, y } = this.cameras.main.midPoint;
        const emitter = this.add.particles(x, y, AssetKeys.PARTICLES.SPARKLE, {
            speed: { min: 100, max: 280 },
            angle: { min: 0, max: 360 },
            scale: { start: 1.2, end: 0 },
            lifespan: 1200,
            quantity: 35,
            blendMode: 'ADD'
        });
        emitter.setDepth(99999);
        this.time.delayedCall(1300, () => emitter.destroy());
    }
}
