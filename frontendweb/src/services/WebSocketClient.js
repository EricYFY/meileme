// 与后端 /game WebSocket 网关通信的客户端封装。
// 协议：下行消息统一为 { type: "...", ...payload }；上行指令统一为 { command: "...", ...params }。
export default class WebSocketClient {
    constructor() {
        this.url = 'ws://localhost:8080/game';
        this.ws = null;
        this.isConnected = false;

        // 连接状态
        this.onStatusChange = null;

        // 下行消息回调
        this.onConfig = null;        // CONFIG：静态配置目录
        this.onResortState = null;   // RESORT_STATE：全量经营状态
        this.onTimeTick = null;      // TIME_TICK：时钟推进
        this.onDayStarted = null;    // DAY_STARTED {day, date, unlocks}
        this.onDaySettled = null;    // DAY_SETTLED {settle, funds, day}
        this.onGameOver = null;      // GAME_OVER {reason, day, lastSettle, historySize}
        this.onGameStopped = null;   // GAME_STOPPED {reason}
        this.onActionQueued = null;  // ACTION_QUEUED {action, commandId, description, effectiveDay}
        this.onActionFailed = null;  // ACTION_FAILED {action, reason, ...}
        this.onDailyLog = null;      // DAILY_LOG {history}
        this.onRangeReport = null;   // RANGE_REPORT {...}
    }

    connect() {
        console.log('[WS] 尝试连接...', this.url);
        this.ws = new WebSocket(this.url);

        this.ws.onopen = () => {
            console.log('[WS] 连接成功');
            this.isConnected = true;
            if (this.onStatusChange) this.onStatusChange(true);
            this.send({ command: 'CHECK_STATUS' });
            this.send({ command: 'GET_CONFIG' });
        };

        this.ws.onclose = () => {
            console.log('[WS] 连接断开，3 秒后重连...');
            this.isConnected = false;
            if (this.onStatusChange) this.onStatusChange(false);
            setTimeout(() => this.connect(), 3000);
        };

        this.ws.onerror = (e) => {
            console.warn('[WS] 连接异常', e);
        };

        this.ws.onmessage = (event) => {
            try {
                const msg = JSON.parse(event.data);
                this.handleMessage(msg);
            } catch (e) {
                console.error('[WS] 解析消息失败', e, event.data);
            }
        };
    }

    send(data) {
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            this.ws.send(JSON.stringify(data));
        } else {
            console.warn('[WS] 未连接，无法发送', data);
        }
    }

    handleMessage(msg) {
        if (!msg || !msg.type) return;
        switch (msg.type) {
            case 'CONFIG':
                if (this.onConfig) this.onConfig(msg);
                break;
            case 'RESORT_STATE':
                if (this.onResortState) this.onResortState(msg);
                break;
            case 'TIME_TICK':
                if (this.onTimeTick) this.onTimeTick(msg);
                break;
            case 'DAY_STARTED':
                if (this.onDayStarted) this.onDayStarted(msg);
                break;
            case 'DAY_SETTLED':
                if (this.onDaySettled) this.onDaySettled(msg);
                break;
            case 'GAME_OVER':
                if (this.onGameOver) this.onGameOver(msg);
                break;
            case 'GAME_STOPPED':
                if (this.onGameStopped) this.onGameStopped(msg);
                break;
            case 'ACTION_QUEUED':
                if (this.onActionQueued) this.onActionQueued(msg);
                break;
            case 'ACTION_FAILED':
                if (this.onActionFailed) this.onActionFailed(msg);
                break;
            case 'DAILY_LOG':
                if (this.onDailyLog) this.onDailyLog(msg);
                break;
            case 'RANGE_REPORT':
                if (this.onRangeReport) this.onRangeReport(msg);
                break;
            case 'ERROR':
                console.warn('[WS] 服务端错误', msg);
                break;
        }
    }
}