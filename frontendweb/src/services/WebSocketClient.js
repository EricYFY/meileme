export default class WebSocketClient {
    constructor() {
        this.url = 'ws://localhost:8080/game';
        this.ws = null;
        this.isConnected = false;
        
        // 事件回调
        this.onMapData = null;
        this.onRiderUpdate = null;
        this.onOrderEvent = null; // 合并订单状态变化的事件
        this.onStatusChange = null;
    }

    connect() {
        console.log("[WS] 尝试连接...", this.url);
        this.ws = new WebSocket(this.url);
        
        this.ws.onopen = () => {
            console.log("[WS] 连接成功！");
            this.isConnected = true;
            if (this.onStatusChange) this.onStatusChange(true);
            // 建立连接后立刻请求地图
            this.requestMap();
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

    send(data) {
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            this.ws.send(JSON.stringify(data));
        } else {
            console.warn("[WS] 未连接，无法发送", data);
        }
    }

    handleMessage(msg) {
        if (!msg || !msg.type) return;
        
        switch(msg.type) {
            case 'MAP_DATA':
                if (this.onMapData) this.onMapData(msg.data);
                break;
            case 'RIDER_UPDATE':
                if (this.onRiderUpdate) this.onRiderUpdate(msg.data);
                break;
            case 'SIMULATION_STARTED':
                if (this.onSimulationStarted) this.onSimulationStarted();
                break;
            case 'SIMULATION_STOPPED':
                if (this.onSimulationStopped) this.onSimulationStopped();
                break;
            case 'ORDER_CREATED':
            case 'RIDER_ASSIGNED':
            case 'ORDER_STATUS_CHANGED':
            case 'ORDER_COMPLETED':
            case 'ORDER_EXPIRED':
                if (this.onOrderEvent) this.onOrderEvent(msg.type, msg.data);
                break;
        }
    }
}
