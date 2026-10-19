export default class WebSocketClient {
    constructor() {
        this.url = 'ws://localhost:8080/game';
        this.ws = null;
        this.isConnected = false;

        // 连接状态回调
        this.onStatusChange = null;

        // 游戏局生命周期
        this.onGameStarted = null;   // GAME_STARTED
        this.onGameStopped = null;   // GAME_STOPPED
        this.onBankruptcy = null;    // BANKRUPTCY
        this.onSpeedChanged = null;  // SIMULATION_SPEED_CHANGED（顶层 speed）
        this.onSimulationPaused = null;
        this.onSimulationResumed = null;

        // 地图与经营状态
        this.onMapData = null;       // MAP_DATA
        this.onResortState = null;   // RESORT_STATE（全量经营状态，含 pendingCommands/scheduledOrders）
        this.onDayStarted = null;    // DAY_STARTED {day, season, seasonName}
        this.onTeamOrders = null;    // TEAM_ORDERS_OFFERED（GuestOrder[]，今日待决）
        this.onDaySettled = null;    // DAY_SETTLED {day, netProfit, cash, reputation}
        this.onRangeReport = null;   // RANGE_REPORT（区间报告，只回请求方）
        this.onDailyLog = null;      // DAILY_LOG（本局全部每日结算日志，只回请求方）
        this.onEventTrigger = null;  // EVENT_TRIGGER（GameEvent）
        this.onNpcUpdate = null;     // NPC_UPDATE {guests, staff}
        this.onTimeTick = null;      // TIME_TICK {day, phase, virtualTimeMs, virtualMsPerRealSecond, speed, paused}
        this.onActionFailed = null;  // ACTION_FAILED {action, reason}
    }

    connect() {
        console.log("[WS] 尝试连接...", this.url);
        this.ws = new WebSocket(this.url);

        this.ws.onopen = () => {
            console.log("[WS] 连接成功！");
            this.isConnected = true;
            if (this.onStatusChange) this.onStatusChange(true);
            // 建立连接后立刻请求地图并检查后端运行状态
            this.requestMap();
            this.send({ command: 'CHECK_STATUS' });
        };

        this.ws.onclose = () => {
            console.log("[WS] 连接断开，3秒后重连...");
            this.isConnected = false;
            if (this.onStatusChange) this.onStatusChange(false);
            setTimeout(() => this.connect(), 3000);
        };

        this.ws.onmessage = (event) => {
            try {
                const message = JSON.parse(event.data);
                this.handleMessage(message);
            } catch (e) {
                console.error("[WS] 解析消息失败", e, event.data);
            }
        };
    }

    requestMap() {
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            console.log("[WS] 发送 GET_MAP 指令");
            this.ws.send(JSON.stringify({ command: 'GET_MAP' }));
        }
    }

    requestDailyLog() {
        this.send({ command: 'REQUEST_DAILY_LOG' });
    }

    send(data) {
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            this.ws.send(JSON.stringify(data));
        } else {
            console.warn("[WS] 未连接，无法发送", data);
        }
    }

    handleMessage(msg) {
        if (!msg || !msg.type) return;

        switch (msg.type) {
            case 'MAP_DATA':
                if (this.onMapData) this.onMapData(msg.data);
                break;
            case 'GAME_STARTED':
                if (this.onGameStarted) this.onGameStarted(msg.data);
                break;
            case 'GAME_STOPPED':
                if (this.onGameStopped) this.onGameStopped(msg.data);
                break;
            case 'RESORT_STATE':
                if (this.onResortState) this.onResortState(msg.data);
                break;
            case 'DAY_STARTED':
                if (this.onDayStarted) this.onDayStarted(msg.data);
                break;
            case 'TEAM_ORDERS_OFFERED':
                if (this.onTeamOrders) this.onTeamOrders(msg.data);
                break;
            case 'SIMULATION_SPEED_CHANGED':
                if (this.onSpeedChanged) this.onSpeedChanged(msg.speed);
                break;
            case 'SIMULATION_PAUSED':
                if (this.onSimulationPaused) this.onSimulationPaused();
                break;
            case 'SIMULATION_RESUMED':
                if (this.onSimulationResumed) this.onSimulationResumed();
                break;
            case 'DAY_SETTLED':
                if (this.onDaySettled) this.onDaySettled(msg.data);
                break;
            case 'RANGE_REPORT':
                if (this.onRangeReport) this.onRangeReport(msg.data);
                break;
            case 'DAILY_LOG':
                if (this.onDailyLog) this.onDailyLog(msg.data);
                break;
            case 'EVENT_TRIGGER':
                if (this.onEventTrigger) this.onEventTrigger(msg.data);
                break;
            case 'NPC_UPDATE':
                if (this.onNpcUpdate) this.onNpcUpdate(msg.data);
                break;
            case 'TIME_TICK':
                if (this.onTimeTick) this.onTimeTick(msg.data);
                break;
            case 'ACTION_FAILED':
                if (this.onActionFailed) this.onActionFailed(msg.data);
                break;
            case 'BANKRUPTCY':
                if (this.onBankruptcy) this.onBankruptcy(msg.data);
                break;
        }
    }
}
