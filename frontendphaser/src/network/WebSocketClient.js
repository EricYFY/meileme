// 与后端 /game WebSocket 网关通信的客户端封装 (Phaser 独立游戏端)
// 协议完全复用现有 GameWebSocketHandler

export default class WebSocketClient {
    constructor(url = 'ws://localhost:8080/game') {
        this.url = url;
        this.ws = null;
        this.isConnected = false;
        this.listeners = new Map();
    }

    on(type, callback) {
        if (!this.listeners.has(type)) {
            this.listeners.set(type, new Set());
        }
        this.listeners.get(type).add(callback);
    }

    off(type, callback) {
        if (this.listeners.has(type)) {
            this.listeners.get(type).delete(callback);
        }
    }

    emit(type, payload) {
        if (this.listeners.has(type)) {
            for (const cb of this.listeners.get(type)) {
                try {
                    cb(payload);
                } catch (e) {
                    console.error(`[WS] 回调处理异常 ${type}`, e);
                }
            }
        }
    }

    connect() {
        console.log('[Phaser WS] 尝试连接...', this.url);
        try {
            this.ws = new WebSocket(this.url);
        } catch (e) {
            console.warn('[Phaser WS] 创建连接失败', e);
            setTimeout(() => this.connect(), 3000);
            return;
        }

        this.ws.onopen = () => {
            console.log('[Phaser WS] 连接成功');
            this.isConnected = true;
            this.emit('STATUS_CHANGE', true);
        };

        this.ws.onclose = () => {
            console.log('[Phaser WS] 连接断开，3 秒后重连...');
            this.isConnected = false;
            this.emit('STATUS_CHANGE', false);
            setTimeout(() => this.connect(), 3000);
        };

        this.ws.onerror = (e) => {
            console.warn('[Phaser WS] 连接异常', e);
        };

        this.ws.onmessage = (event) => {
            try {
                const msg = JSON.parse(event.data);
                if (msg && msg.type) {
                    this.emit(msg.type, msg);
                    this.emit('ANY_MESSAGE', msg);
                }
            } catch (e) {
                console.error('[Phaser WS] 解析消息失败', e, event.data);
            }
        };
    }

    send(data) {
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            this.ws.send(JSON.stringify(data));
            return true;
        } else {
            console.warn('[Phaser WS] 未连接，无法发送', data);
            return false;
        }
    }
}
