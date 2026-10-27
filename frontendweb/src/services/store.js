// 前端单一响应式状态仓库：持有配置目录、全量经营状态、时钟字段，并封装上行指令 API。
import { reactive } from 'vue';
import WebSocketClient from './WebSocketClient.js';

const ws = new WebSocketClient();

export const store = reactive({
    connected: false,
    booting: true,          // 尚未收到首帧 RESORT_STATE
    config: null,
    state: null,            // RESORT_STATE 全量
    tick: { day: 1, date: '', progress: 0, speed: 1, paused: false, funds: 0 },
    lastSettle: null,       // 最近一次日结快照
    dailyLog: null,         // DAILY_LOG 载荷（结算日志弹窗）
    rangeReport: null,      // RANGE_REPORT 载荷
    adminConfig: null,      // 当前管理员查看/编辑的全量配置
    isCustomConfig: false,  // 当前是否为自定义 admin-config.json
    toasts: [],
});

// ===================== 上行指令 API =====================

function sendCommand(command, params = {}) {
    const ok = ws.send({ command, ...params });
    if (!ok) {
        toast(`网络未连接，指令 ${command} 发送失败，请确认后端已启动！`, 'alert');
    }
    return ok;
}

export const api = {
    startGame: () => sendCommand('START_GAME'),
    stopGame: () => sendCommand('STOP_GAME'),
    pause: () => sendCommand('PAUSE_GAME'),
    resume: () => sendCommand('RESUME_GAME'),
    setSpeed: (speed) => sendCommand('SET_SIMULATION_SPEED', { speed }),
    build: (buildingType, plotId, cellX = 0, cellY = 0) => sendCommand('BUILD_BUILDING', { buildingType, plotId, cellX, cellY }),
    formLevelUp: (buildingId) => sendCommand('FORM_LEVEL_UP', { buildingId }),
    branchEvolve: (buildingId, targetFormId) => sendCommand('BRANCH_EVOLVE', { buildingId, targetFormId }),
    mergeEvolve: (buildingIdA, buildingIdB) => sendCommand('MERGE_EVOLVE', { buildingIdA, buildingIdB }),
    interiorUpgrade: (buildingId, interiorType) => sendCommand('INTERIOR_UPGRADE', { buildingId, interiorType }),
    hire: (position) => sendCommand('HIRE_STAFF', { position }),
    train: (staffId) => sendCommand('TRAIN_STAFF', { staffId }),
    fire: (staffId) => sendCommand('FIRE_STAFF', { staffId }),
    unlockSkill: (skillId) => sendCommand('UNLOCK_SKILL', { skillId }),
    unlockPlot: (plotId) => sendCommand('UNLOCK_PLOT', { plotId }),
    requestDailyLog: () => sendCommand('REQUEST_DAILY_LOG'),
    requestRangeReport: (rangeDays) => sendCommand('REQUEST_RANGE_REPORT', { rangeDays }),
    getAdminConfig: async () => {
        try {
            const res = await fetch('http://localhost:8080/api/admin/config');
            if (res.ok) {
                const data = await res.json();
                store.adminConfig = data.config;
                store.isCustomConfig = Boolean(data.isCustom);
                return data;
            }
        } catch (e) {
            console.warn('[HTTP] 获取配置失败，回退 WS', e);
        }
        return sendCommand('GET_ADMIN_CONFIG');
    },
    saveAdminConfig: async (config) => {
        try {
            const res = await fetch('http://localhost:8080/api/admin/config', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ config })
            });
            const data = await res.json();
            if (res.ok && data.success) {
                store.adminConfig = config;
                store.isCustomConfig = true;
                const pathStr = data.filePath ? `\n路径: ${data.filePath}` : '';
                toast(`配置已成功保存至 admin-config.json 并生效！${pathStr}`, 'unlock');
                return { success: true, filePath: data.filePath };
            } else {
                toast(`保存失败: ${data.message || '未知错误'}`, 'alert');
                return { success: false, message: data.message };
            }
        } catch (e) {
            console.warn('[HTTP] 保存配置失败，回退 WS', e);
            const ok = sendCommand('SAVE_ADMIN_CONFIG', { config });
            return { success: ok, fallbackWs: true };
        }
    },
    resetAdminConfig: async () => {
        try {
            const res = await fetch('http://localhost:8080/api/admin/config/reset', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' }
            });
            const data = await res.json();
            if (res.ok && data.success) {
                store.isCustomConfig = false;
                toast('已删除 admin-config.json 并恢复默认 default-config.json！', 'info');
                api.getAdminConfig();
                return { success: true };
            } else {
                toast(`重置失败: ${data.message || '未知错误'}`, 'alert');
                return { success: false, message: data.message };
            }
        } catch (e) {
            console.warn('[HTTP] 重置配置失败，回退 WS', e);
            const ok = sendCommand('RESET_ADMIN_CONFIG');
            return { success: ok, fallbackWs: true };
        }
    },
};

// ===================== 提示 Toast =====================

let seq = 0;
export function toast(text, kind = 'info') {
    const id = ++seq;
    store.toasts.push({ id, text, kind });
    setTimeout(() => {
        const i = store.toasts.findIndex((t) => t.id === id);
        if (i >= 0) store.toasts.splice(i, 1);
    }, 4200);
}

// 后端失败原因 → 中文
const REASONS = {
    GAME_NOT_RUNNING: '对局未开始',
    INSUFFICIENT_FUNDS: '资金不足',
    BUILDING_NOT_FOUND: '建筑不存在',
    BUILDING_TYPE_NOT_FOUND: '建筑类型不存在',
    STAFF_NOT_FOUND: '员工不存在',
    POSITION_NOT_FOUND: '岗位不存在',
    STAFF_MAX_LEVEL: '员工已满级',
    TRAINING_NOT_READY: '在岗时长未达标，暂不可培训',
    PLOT_NOT_FOUND: '地块不存在',
    PLOT_NOT_UNLOCKED: '地块未解锁',
    PLOT_ALREADY_UNLOCKED: '地块已解锁',
    PREVIOUS_PLOT_LOCKED: '上一地块未解锁',
    ACHIEVEMENTS_UNMET: '解锁成就未达成',
    PLOT_MAP_NOT_FOUND: '地块地图不存在',
    CELL_OUT_OF_BOUNDS: '落格坐标越界',
    CELL_NOT_BUILDABLE: '该格不可建造（非可建造空地）',
    CELL_OCCUPIED: '该格已被占用',
    MOTHER_NOT_BUILDABLE_IN_PHASE_1: '该母本当前阶段不可建造',
    MOTHER_NOT_ALLOWED_IN_PLOT: '该地块不允许建造此母本',
    FORM_NOT_CONFIGURED: '该形态未配置',
    FORM_LEVEL_MAX_REACHED: '形态已满级',
    FORM_NOT_MAX_LEVEL: '形态未满级，需升满后分支进化',
    EVOLVE_PATH_NOT_ALLOWED: '目标形态不是当前形态的分支',
    EVOLVE_LINE_LOCKED: '分支已选定，不可切换',
    MERGE_FORM_CANNOT_BRANCH: '合并形态不可分支进化',
    PLOT_EVOLVE_TIER_CAP_EXCEEDED: '超过地块进化层级上限',
    TARGET_FORM_NOT_FOUND: '目标形态不存在',
    MERGE_SOURCE_DUPLICATED: '两座合并源建筑相同',
    MERGE_RECIPE_NOT_FOUND: '不存在匹配的合并配方',
    MERGE_RECIPE_NOT_MATCHED: '不满足合并配方',
    MERGE_SOURCE_NOT_ADJACENT: '两座建筑在地图上不相邻（需上下左右相邻）',
    INTERIOR_NOT_FOUND: '内装不存在',
    INTERIOR_NOT_APPLICABLE: '该内装不适用于此建筑',
    INTERIOR_LEVEL_CAP_REACHED: '内装已达当前形态等级上限',
    PROJECT_IN_PROGRESS: '该建筑已有进行中的工程',
    SKILL_NOT_FOUND: '技能不存在',
    SKILL_ALREADY_UNLOCKED: '技能已解锁',
    NO_SKILL_POINT: '技能点不足',
    PREREQUISITE_NOT_UNLOCKED: '前置技能未解锁',
    UNKNOWN_PROJECT: '工程类型异常',
    UNKNOWN_COMMAND: '未知指令',
    INVALID_SPEED: '倍速参数无效',
};
function translateReason(reason) {
    return REASONS[reason] || reason || '未知原因';
}

// ===================== 配置目录索引 =====================

function indexConfig(cfg) {
    if (!cfg) return null;
    cfg.regions = cfg.regions || [];
    cfg.regionsById = Object.fromEntries(cfg.regions.map((x) => [x.id, x]));
    cfg.formsById = Object.fromEntries((cfg.buildingForms || []).map((x) => [x.id, x]));
    cfg.plotsById = Object.fromEntries((cfg.plots || []).map((x) => [x.id, x]));
    cfg.plotMapsById = Object.fromEntries((cfg.plotMaps || []).map((x) => [x.plotId, x]));
    cfg.achievementsById = Object.fromEntries((cfg.achievements || []).map((x) => [x.id, x]));
    cfg.skillsById = Object.fromEntries((cfg.skills || []).map((x) => [x.id, x]));
    cfg.mothersById = Object.fromEntries((cfg.mothers || []).map((x) => [x.id, x]));
    cfg.positionsById = Object.fromEntries((cfg.positions || []).map((x) => [x.id, x]));
    cfg.segmentsById = Object.fromEntries((cfg.segments || []).map((x) => [x.id, x]));
    cfg.abilitiesById = Object.fromEntries((cfg.abilities || []).map((x) => [x.id, x]));
    cfg.interiorsById = Object.fromEntries((cfg.interiors || []).map((x) => [x.id, x]));
    return cfg;
}

// ===================== 下行消息接线 =====================

ws.onStatusChange = (connected) => {
    store.connected = connected;
};

ws.onConfig = (msg) => {
    store.config = indexConfig(msg);
};

ws.onResortState = (msg) => {
    store.state = msg;
    store.booting = false;
    if (msg && msg.funds != null) store.tick.funds = msg.funds;
    if (msg && msg.day != null) store.tick.day = msg.day;
};

ws.onTimeTick = (msg) => {
    Object.assign(store.tick, msg);
};

ws.onDayStarted = (msg) => {
    const unlocks = msg.unlocks || [];
    unlocks.forEach((u) => toast(u, 'unlock'));
};

ws.onDaySettled = (msg) => {
    store.lastSettle = msg.settle || null;
};

ws.onGameStopped = () => {
    store.booting = false;
    if (store.state) {
        store.state.running = false;
    }
};

ws.onActionQueued = (msg) => {
    if (msg.description) toast('已入队（次日生效）：' + msg.description, 'queued');
};

ws.onActionFailed = (msg) => {
    toast('操作失败：' + translateReason(msg.reason), 'error');
};

ws.onProjectStarted = (msg) => {
    toast(`已开工：${msg.displayName}（${msg.totalDays} 天，第 ${msg.finishDay} 天完成）`, 'queued');
};

ws.onProjectCompleted = (msg) => {
    toast(`已完成：${msg.displayName}`, 'unlock');
};

ws.onProjectFailed = (msg) => {
    const name = msg.project?.displayName || '工程';
    toast(`${name} 失败：${translateReason(msg.reason)}，已退还 ${msg.refund ?? 0}`, 'error');
};

ws.onDailyLog = (msg) => {
    store.dailyLog = msg;
};

ws.onRangeReport = (msg) => {
    store.rangeReport = msg;
};

ws.onAdminConfigData = (msg) => {
    store.adminConfig = msg.config;
    store.isCustomConfig = Boolean(msg.isCustom);
};

ws.onAdminConfigSaved = (msg) => {
    store.adminConfig = msg.config;
    store.isCustomConfig = true;
    const pathStr = msg.filePath ? `\n路径: ${msg.filePath}` : '';
    toast(`配置已成功保存至 admin-config.json 并生效！${pathStr}`, 'unlock');
};

ws.onAdminConfigReset = (msg) => {
    store.adminConfig = msg.config;
    store.isCustomConfig = false;
    toast('已删除 admin-config.json 并恢复默认 default-config.json！', 'info');
};

ws.onCodexUnlocked = (msg) => {
    const categoryNames = {
        FORM: '建筑形态',
        INTERIOR: '内装设施',
        PLOT: '度假村地块',
        POSITION: '员工岗位'
    };
    const cat = categoryNames[msg.category] || '图鉴';
    toast(`🎉【${cat}】图鉴已解锁：${msg.name || msg.id}！可在“📖 图鉴”页面查看`, 'unlock');
};

ws.onError = (msg) => {
    const reason = msg.reason || msg.message || '未知异常';
    toast(`操作失败: ${reason}`, 'alert');
};

// ===================== 启动连接 =====================

export function connect() {
    ws.connect();
}

export { ws };