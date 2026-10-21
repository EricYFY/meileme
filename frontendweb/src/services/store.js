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
    toasts: [],
});

// ===================== 上行指令 API =====================

function sendCommand(command, params = {}) {
    ws.send({ command, ...params });
}

export const api = {
    startGame: () => sendCommand('START_GAME'),
    stopGame: () => sendCommand('STOP_GAME'),
    pause: () => sendCommand('PAUSE_GAME'),
    resume: () => sendCommand('RESUME_GAME'),
    setSpeed: (speed) => sendCommand('SET_SIMULATION_SPEED', { speed }),
    build: (buildingType, plotId) => sendCommand('BUILD_BUILDING', { buildingType, plotId }),
    formLevelUp: (buildingId) => sendCommand('FORM_LEVEL_UP', { buildingId }),
    branchEvolve: (buildingId, targetFormId) => sendCommand('BRANCH_EVOLVE', { buildingId, targetFormId }),
    mergeEvolve: (buildingIdA, buildingIdB) => sendCommand('MERGE_EVOLVE', { buildingIdA, buildingIdB }),
    interiorUpgrade: (buildingId, interiorType) => sendCommand('INTERIOR_UPGRADE', { buildingId, interiorType }),
    hire: (position, level) => sendCommand('HIRE_STAFF', { position, level }),
    fire: (staffId) => sendCommand('FIRE_STAFF', { staffId }),
    unlockSkill: (skillId) => sendCommand('UNLOCK_SKILL', { skillId }),
    unlockPlot: (plotId) => sendCommand('UNLOCK_PLOT', { plotId }),
    requestDailyLog: () => sendCommand('REQUEST_DAILY_LOG'),
    requestRangeReport: (rangeDays) => sendCommand('REQUEST_RANGE_REPORT', { rangeDays }),
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
    STAFF_NOT_FOUND: '员工不存在',
    INVALID_STAFF_LEVEL: '员工等级不合法',
    PLOT_LOCKED: '地块未解锁',
    PLOT_NOT_FOUND: '地块不存在',
    PLOT_ALREADY_UNLOCKED: '地块已解锁',
    PREVIOUS_PLOT_LOCKED: '上一地块未解锁',
    ACHIEVEMENTS_UNMET: '解锁成就未达成',
    PLOT_FULL: '地块建筑数已满',
    MOTHER_NOT_BUILDABLE: '该母本当前不可建造',
    FORM_NOT_BUILDABLE: '该形态当前不可建造',
    FORM_MAX_LEVEL: '形态已满级',
    FORM_LEVEL_UNMET: '形态等级不满足进化条件',
    FORMLINE_UNSWITCHABLE: '分支已选定，不可切换',
    RECIPE_NOT_MATCHED: '不满足任何合并配方',
    INTERIOR_MAX_LEVEL: '内装已满级',
    PREREQUISITE_UNMET: '前置技能未解锁',
    SKILL_ALREADY_UNLOCKED: '技能已解锁',
    NOT_ENOUGH_SKILL_POINTS: '技能点不足',
    UNKNOWN_COMMAND: '未知指令',
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
};

ws.onActionQueued = (msg) => {
    if (msg.description) toast('已入队（次日生效）：' + msg.description, 'queued');
};

ws.onActionFailed = (msg) => {
    toast('操作失败：' + translateReason(msg.reason), 'error');
};

ws.onDailyLog = (msg) => {
    store.dailyLog = msg;
};

ws.onRangeReport = (msg) => {
    store.rangeReport = msg;
};

// ===================== 启动连接 =====================

export function connect() {
    ws.connect();
}

export { ws };