// 纯状态中心与业务指令封装 (Phaser 独立工程专用)
import WebSocketClient from './WebSocketClient.js';

class GameStore {
    constructor() {
        this.ws = new WebSocketClient();
        this.connected = false;
        this.config = null;
        this.state = null;
        this.tick = { day: 1, date: '', progress: 0, speed: 1, paused: false, funds: 0 };
        this.lastSettle = null;
        this.dailyLog = null;
        this.currentPlotId = null; // 当前正在查看的地块 ID
        this.selectedCell = null;   // { x, y } 当前等轴测选中的网格
        this.selectedBuildingId = null; // 当前选中的建筑 ID
        
        // 事件监听器
        this.eventListeners = new Map();

        this.init();
    }

    on(event, handler) {
        if (!this.eventListeners.has(event)) {
            this.eventListeners.set(event, new Set());
        }
        this.eventListeners.get(event).add(handler);
    }

    off(event, handler) {
        if (this.eventListeners.has(event)) {
            this.eventListeners.get(event).delete(handler);
        }
    }

    emit(event, data) {
        if (this.eventListeners.has(event)) {
            for (const h of this.eventListeners.get(event)) {
                try {
                    h(data);
                } catch (e) {
                    console.error(`[GameStore] 事件处理异常 ${event}`, e);
                }
            }
        }
    }

    init() {
        this.ws.on('STATUS_CHANGE', (status) => {
            this.connected = status;
            this.emit('CONNECTED_CHANGE', status);
        });

        this.ws.on('CONFIG', (msg) => {
            // 后端下发的 CONFIG 消息字段直接平铺在 msg 根对象上，部分包装环境可能在 msg.config
            this.config = msg.config || msg;
            if (this.config) {
                // 辅助索引
                if (this.config.regions) {
                    this.config.regionsById = Object.fromEntries(this.config.regions.map(r => [r.id, r]));
                }
                if (this.config.plots) {
                    this.config.plotsById = Object.fromEntries(this.config.plots.map(p => [p.id, p]));
                }
                if (this.config.plotMaps) {
                    this.config.plotMapsById = Object.fromEntries(this.config.plotMaps.map(m => [m.plotId, m]));
                }
                if (this.config.buildingForms) {
                    this.config.buildingFormsById = Object.fromEntries(this.config.buildingForms.map(f => [f.id, f]));
                }
                if (this.config.interiors) {
                    this.config.interiorsById = Object.fromEntries(this.config.interiors.map(i => [i.id, i]));
                }
                if (this.config.positions) {
                    this.config.positionsById = Object.fromEntries(this.config.positions.map(p => [p.id, p]));
                }
                if (this.config.mothers) {
                    this.config.mothersById = Object.fromEntries(this.config.mothers.map(m => [m.id, m]));
                }
            }
            if (!this.currentPlotId) {
                this.currentPlotId = this.determineHighestUnlockedPlot();
            }
            this.emit('CONFIG_UPDATED', this.config);
        });

        this.ws.on('RESORT_STATE', (msg) => {
            this.state = msg;
            if (msg.funds != null) this.tick.funds = msg.funds;
            if (msg.day != null) this.tick.day = msg.day;
            if (msg.running != null) this.tick.running = msg.running;
            if (msg.paused != null) this.tick.paused = msg.paused;
            if (msg.speed != null) this.tick.speed = msg.speed;
            if (msg.date != null) this.tick.date = msg.date;

            // 默认展示已解锁的最高阶地块（按后管配置定义的顺序，越往后越高阶）
            if (!this.userManuallySelectedPlot || !msg.unlockedPlots?.includes(this.currentPlotId)) {
                this.currentPlotId = this.determineHighestUnlockedPlot();
            }

            this.emit('STATE_UPDATED', this.state);
        });

        this.ws.on('TIME_TICK', (msg) => {
            Object.assign(this.tick, msg);
            this.emit('TIME_TICK', this.tick);
        });

        this.ws.on('DAY_STARTED', (msg) => {
            this.emit('DAY_STARTED', msg);
        });

        this.ws.on('DAY_SETTLED', (msg) => {
            this.lastSettle = msg.settle;
            if (msg.funds != null) this.tick.funds = msg.funds;
            if (msg.day != null) this.tick.day = msg.day;
            this.emit('DAY_SETTLED', msg);
        });

        this.ws.on('CODEX_UNLOCKED', (msg) => {
            this.emit('CODEX_UNLOCKED', msg);
        });

        this.ws.on('PROJECT_STARTED', (msg) => {
            this.emit('PROJECT_STARTED', msg);
        });

        this.ws.on('PROJECT_COMPLETED', (msg) => {
            this.emit('PROJECT_COMPLETED', msg);
        });

        this.ws.on('PROJECT_FAILED', (msg) => {
            this.emit('PROJECT_FAILED', msg);
        });

        this.ws.on('ACTION_QUEUED', (msg) => {
            this.emit('ACTION_QUEUED', msg);
        });

        this.ws.on('ACTION_FAILED', (msg) => {
            this.emit('ACTION_FAILED', msg);
        });

        this.ws.on('DAILY_LOG', (msg) => {
            this.dailyLog = msg.history;
            this.emit('DAILY_LOG', msg);
        });

        // 连上后自动查询状态和配置
        this.ws.on('STATUS_CHANGE', (status) => {
            if (status) {
                this.sendCommand('CHECK_STATUS');
                this.sendCommand('GET_CONFIG');
            }
        });

        this.ws.connect();
    }

    determineHighestUnlockedPlot() {
        if (!this.config?.plots || this.config.plots.length === 0) return 'STORMVEIL_CASTLE';
        const unlockedPlots = new Set(this.state?.unlockedPlots || []);
        // 按后管配置定义的顺序，越往后越高阶，从后往前找已解锁的第一个
        for (let i = this.config.plots.length - 1; i >= 0; i--) {
            const p = this.config.plots[i];
            if (unlockedPlots.has(p.id)) {
                return p.id;
            }
        }
        // 如果尚无已解锁数据，寻找默认解锁或第一个
        const def = this.config.plots.find(p => p.unlockedByDefault);
        return def?.id || this.config.plots[0]?.id || 'STORMVEIL_CASTLE';
    }

    selectPlot(plotId) {
        this.userManuallySelectedPlot = true;
        this.currentPlotId = plotId;
        this.selectedCell = null;
        this.selectedBuildingId = null;
        this.emit('PLOT_CHANGED', plotId);
    }

    selectCell(x, y) {
        this.selectedCell = { x, y };
        this.emit('CELL_SELECTED', this.selectedCell);
    }

    selectBuilding(buildingId) {
        this.selectedBuildingId = buildingId;
        this.emit('BUILDING_SELECTED', buildingId);
    }

    // ===================== 指令发送 API =====================

    sendCommand(command, params = {}) {
        return this.ws.send({ command, ...params });
    }

    startGame() { return this.sendCommand('START_GAME'); }
    stopGame() { return this.sendCommand('STOP_GAME'); }
    pauseGame() { return this.sendCommand('PAUSE_GAME'); }
    resumeGame() { return this.sendCommand('RESUME_GAME'); }
    setSpeed(speed) { return this.sendCommand('SET_SIMULATION_SPEED', { speed }); }

    buildBuilding(buildingType, plotId, cellX = 0, cellY = 0) {
        return this.sendCommand('BUILD_BUILDING', { buildingType, plotId, cellX, cellY });
    }

    formLevelUp(buildingId) {
        return this.sendCommand('FORM_LEVEL_UP', { buildingId });
    }

    branchEvolve(buildingId, targetFormId) {
        return this.sendCommand('BRANCH_EVOLVE', { buildingId, targetFormId });
    }

    mergeEvolve(buildingIdA, buildingIdB) {
        return this.sendCommand('MERGE_EVOLVE', { buildingIdA, buildingIdB });
    }

    interiorUpgrade(buildingId, interiorType) {
        return this.sendCommand('INTERIOR_UPGRADE', { buildingId, interiorType });
    }

    hireStaff(position) {
        return this.sendCommand('HIRE_STAFF', { position });
    }

    trainStaff(staffId) {
        return this.sendCommand('TRAIN_STAFF', { staffId });
    }

    fireStaff(staffId) {
        return this.sendCommand('FIRE_STAFF', { staffId });
    }

    unlockSkill(skillId) {
        return this.sendCommand('UNLOCK_SKILL', { skillId });
    }

    unlockPlot(plotId) {
        return this.sendCommand('UNLOCK_PLOT', { plotId });
    }

    requestDailyLog() {
        return this.sendCommand('REQUEST_DAILY_LOG');
    }
}

export const gameStore = new GameStore();
