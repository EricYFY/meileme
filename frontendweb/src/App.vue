<template>
  <div class="dashboard">
    <!-- 左侧主地图区域 -->
    <div class="map-section glass-panel">
      <div class="header">
        <h2>外卖调度模拟器 (201x201)</h2>
        <div class="status-indicator">
          <span class="dot" :class="{ 'connected': isConnected }"></span>
          {{ isConnected ? '服务器已连接' : '服务器断开' }}
        </div>
      </div>
      <div class="map-wrapper">
        <MapRenderer 
          :mapData="mapData" 
          :riders="riders" 
          :orders="orders"
          :selectedOrderId="selectedOrderId"
          @orderSelected="onMapOrderSelected"
        />
      </div>
    </div>

    <!-- 右侧控制面板 -->
    <div class="control-panel">
      <!-- 骑手统计 -->
      <div class="stats-card glass-panel">
        <h3>实时状态 (10Hz)</h3>
        <div class="stats-grid">
          <div class="stat-item">
            <span class="label">空闲骑手</span>
            <span class="value text-success">{{ idleRidersCount }}</span>
          </div>
          <div class="stat-item">
            <span class="label">取餐中骑手</span>
            <span class="value text-warning">{{ pickingRidersCount }}</span>
          </div>
          <div class="stat-item">
            <span class="label">送餐中骑手</span>
            <span class="value text-accent">{{ deliveringRidersCount }}</span>
          </div>
          <div class="stat-item">
            <span class="label">待处理订单</span>
            <span class="value text-danger">{{ pendingOrdersCount }}</span>
          </div>
          <div class="stat-item">
            <span class="label">待取餐订单</span>
            <span class="value text-warning">{{ pickupOrdersCount }}</span>
          </div>
          <div class="stat-item">
            <span class="label">配送中订单</span>
            <span class="value text-accent">{{ deliveringOrdersCount }}</span>
          </div>
        </div>
      </div>

      <!-- 选中订单详情 -->
      <div v-if="selectedOrder" class="detail-card glass-panel">
        <div class="detail-header">
          <h3>📦 订单详情</h3>
          <button class="close-btn" @click="selectedOrderId = null">✕</button>
        </div>
        <div class="detail-body">
          <div class="detail-row">
            <span class="detail-label">订单号</span>
            <span class="detail-value mono">#{{ selectedOrder.id.substring(0, 8) }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">状态</span>
            <span class="detail-value" :class="'text-status-' + selectedOrder.status">
              {{ getStatusText(selectedOrder.status) }}
            </span>
          </div>
          <div class="detail-row">
            <span class="detail-label">🟡 取餐点</span>
            <span class="detail-value">({{ Math.round(selectedOrder.pickupLocation.x) }}, {{ Math.round(selectedOrder.pickupLocation.y) }})</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">🟢 送达点</span>
            <span class="detail-value">({{ Math.round(selectedOrder.deliveryLocation.x) }}, {{ Math.round(selectedOrder.deliveryLocation.y) }})</span>
          </div>
          <div v-if="assignedRider" class="detail-row">
            <span class="detail-label">⚪ 骑手</span>
            <span class="detail-value">{{ assignedRider.id }} @ ({{ Math.round(assignedRider.currentPosition.x) }}, {{ Math.round(assignedRider.currentPosition.y) }})</span>
          </div>
        </div>
      </div>

      <!-- 订单流 -->
      <div class="orders-card glass-panel">
        <h3>实时订单流</h3>
        <div class="order-list">
          <TransitionGroup name="list">
            <div 
              v-for="order in sortedOrders" 
              :key="order.id" 
              class="order-item" 
              :class="['status-' + order.status, { 'selected': order.id === selectedOrderId }]"
              @click="selectedOrderId = order.id"
            >
              <div class="order-header">
                <span class="order-id">#{{ order.id.substring(0,8) }}</span>
                <span class="order-status-badge">{{ getStatusText(order.status) }}</span>
              </div>
              <div class="order-detail">
                <span>取: ({{ Math.round(order.pickupLocation.x) }}, {{ Math.round(order.pickupLocation.y) }})</span>
                <span>送: ({{ Math.round(order.deliveryLocation.x) }}, {{ Math.round(order.deliveryLocation.y) }})</span>
              </div>
            </div>
          </TransitionGroup>
          <div v-if="Object.keys(orders).length === 0" class="empty-state">
            暂无进行中的订单
          </div>
        </div>
      </div>

      <!-- 骑手列表 -->
      <div class="stats-card glass-panel" style="margin-top: 15px; max-height: 400px; overflow-y: auto;">
        <h3>骑手列表</h3>
        <div class="rider-list">
          <div v-for="rider in riders" :key="rider.id" class="rider-item">
            <div class="rider-header">
              <span class="rider-id">{{ rider.id }}</span>
              <span :class="['status-badge', getRiderStatusClass(rider.status)]">{{ getRiderStatusText(rider.status) }}</span>
            </div>
            <div class="rider-details">
              <div>坐标: {{ rider.currentPosition ? Math.round(rider.currentPosition.x) + ',' + Math.round(rider.currentPosition.y) : '未知' }}</div>
              <div v-if="rider.targetPosition">目标: {{ Math.round(rider.targetPosition.x) }},{{ Math.round(rider.targetPosition.y) }}</div>
              <div v-if="rider.currentOrderId" class="rider-order text-accent">订单: {{ rider.currentOrderId.substring(0,8) }}</div>
            </div>
          </div>
        </div>
      </div>

    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import MapRenderer from './components/MapRenderer.vue';
import WebSocketClient from './services/WebSocketClient.js';

const isConnected = ref(false);
const mapData = ref(null);
const riders = ref([]);
const orders = ref({});
const selectedOrderId = ref(null);

const client = new WebSocketClient();

onMounted(() => {
  client.onStatusChange = (status) => isConnected.value = status;
  
  client.onMapData = (data) => {
    mapData.value = data;
  };
  
  client.onRiderUpdate = (data) => {
    riders.value = data;
  };
  
  client.onOrderEvent = (type, data) => {
    if (type === 'ORDER_CREATED') {
      orders.value[data.id] = data;
    } else if (type === 'RIDER_ASSIGNED') {
      if (data.currentOrderId && orders.value[data.currentOrderId]) {
        orders.value[data.currentOrderId].status = 1;
      }
    } else if (type === 'ORDER_STATUS_CHANGED') {
      // 订单状态变化（如从取餐中变为配送中）
      if (orders.value[data.id]) {
        orders.value[data.id].status = data.status;
      }
    } else if (type === 'ORDER_COMPLETED') {
      if (orders.value[data.id]) {
        orders.value[data.id].status = 3;
      }
    }
  };
  
  // 延迟清理逻辑应在事件处理逻辑外部或正确包裹
  // 这里简化演示以符合逻辑
  client.connect();
});

// 地图上点击订单的回调
const onMapOrderSelected = (orderId) => {
  selectedOrderId.value = orderId;
};

// 选中的订单对象
const selectedOrder = computed(() => {
  if (!selectedOrderId.value) return null;
  return orders.value[selectedOrderId.value] || null;
});

// 负责选中订单的骑手
const assignedRider = computed(() => {
  if (!selectedOrderId.value || !riders.value.length) return null;
  return riders.value.find(r => r.currentOrderId === selectedOrderId.value) || null;
});

const sortedOrders = computed(() => {
  return Object.values(orders.value).sort((a, b) => b.createTime - a.createTime);
});

// 骑手统计
const idleRidersCount = computed(() => {
  return riders.value.filter(r => r.status === 0).length;
});

const pickingRidersCount = computed(() => {
  return riders.value.filter(r => r.status === 1).length;
});

const deliveringRidersCount = computed(() => {
  return riders.value.filter(r => r.status === 2).length;
});

// 订单统计
const pendingOrdersCount = computed(() => {
  return Object.values(orders.value).filter(o => o.status === 0).length;
});

const pickupOrdersCount = computed(() => {
  return Object.values(orders.value).filter(o => o.status === 1).length;
});

const deliveringOrdersCount = computed(() => {
  return Object.values(orders.value).filter(o => o.status === 2).length;
});

const getStatusText = (status) => {
  switch(status) {
    case 0: return '待接单';
    case 1: return '前往取餐';
    case 2: return '配送中';
    case 3: return '已送达';
    default: return '未知';
  }
};

const getRiderStatusText = (status) => {
  switch(status) {
    case 0: return '空闲';
    case 1: return '取餐中';
    case 2: return '送餐中';
    default: return '未知';
  }
};

const getRiderStatusClass = (status) => {
  switch(status) {
    case 0: return 'text-success';
    case 1: return 'text-warning';
    case 2: return 'text-accent';
    default: return '';
  }
};
</script>

<style scoped>
.dashboard {
  display: flex;
  width: 100%;
  height: 100%;
  gap: 20px;
}

.map-section {
  flex: 3;
  display: flex;
  flex-direction: column;
  padding: 20px;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.header h2 {
  font-size: 20px;
  font-weight: 600;
  letter-spacing: 0.5px;
}

.status-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: var(--text-secondary);
}

.dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--danger);
  box-shadow: 0 0 8px var(--danger);
}
.dot.connected {
  background: var(--success);
  box-shadow: 0 0 8px var(--success);
}

.map-wrapper {
  flex: 1;
  min-height: 0;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: inset 0 0 20px rgba(0,0,0,0.5);
}

.control-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 320px;
}

.stats-card, .orders-card, .detail-card {
  padding: 20px;
  display: flex;
  flex-direction: column;
}

.stats-card h3, .orders-card h3 {
  font-size: 16px;
  margin-bottom: 15px;
  color: #fff;
  border-bottom: 1px solid var(--panel-border);
  padding-bottom: 10px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  background: rgba(0,0,0,0.2);
  padding: 12px;
  border-radius: 8px;
  text-align: center;
}

.stat-item .label {
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 4px;
}

.stat-item .value {
  font-size: 24px;
  font-weight: 700;
}

.text-accent { color: var(--accent-color); }
.text-warning { color: var(--warning); }
.text-success { color: var(--success); }
.text-danger { color: var(--danger); }

/* 订单详情卡片 */
.detail-card {
  border: 1px solid rgba(59, 130, 246, 0.3);
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(-10px); }
  to { opacity: 1; transform: translateY(0); }
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  border-bottom: 1px solid var(--panel-border);
  padding-bottom: 10px;
}

.detail-header h3 {
  font-size: 15px;
  color: #fff;
  margin: 0;
}

.close-btn {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: rgba(255,255,255,0.1);
  color: #fff;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.close-btn:hover {
  background: rgba(255,255,255,0.2);
}

.detail-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.detail-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
}

.detail-label {
  color: var(--text-secondary);
}

.detail-value {
  color: var(--text-primary);
  font-weight: 500;
}

.detail-value.mono {
  font-family: monospace;
}

.text-status-0 { color: #fca5a5; }
.text-status-1 { color: #fcd34d; }
.text-status-2 { color: #93c5fd; }
.text-status-3 { color: #6ee7b7; }

/* 订单列表 */
.orders-card {
  flex: 1;
  min-height: 0;
}

.order-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-right: 5px;
}

.order-item {
  background: rgba(255,255,255,0.03);
  border: 1px solid var(--panel-border);
  border-radius: 8px;
  padding: 12px;
  transition: all 0.3s ease;
  cursor: pointer;
}

.order-item:hover {
  background: rgba(255,255,255,0.06);
  transform: translateX(-2px);
}

.order-item.selected {
  background: rgba(59, 130, 246, 0.15);
  border-color: rgba(59, 130, 246, 0.5);
  box-shadow: 0 0 12px rgba(59, 130, 246, 0.2);
}

.order-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.order-id {
  font-family: monospace;
  font-size: 14px;
  color: var(--text-primary);
}

.order-status-badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 12px;
  background: rgba(255,255,255,0.1);
}

.status-0 { border-left: 3px solid var(--danger); }
.status-0 .order-status-badge { background: rgba(239, 68, 68, 0.2); color: #fca5a5; }

.status-1 { border-left: 3px solid var(--warning); }
.status-1 .order-status-badge { background: rgba(245, 158, 11, 0.2); color: #fcd34d; }

.status-2 { border-left: 3px solid var(--accent-color); }
.status-2 .order-status-badge { background: rgba(59, 130, 246, 0.2); color: #93c5fd; }

.status-3 { border-left: 3px solid var(--success); opacity: 0.5; }

.order-detail {
  display: flex;
  flex-direction: column;
  font-size: 12px;
  color: var(--text-secondary);
  gap: 4px;
}

.empty-state {
  text-align: center;
  color: var(--text-secondary);
  font-size: 13px;
  margin-top: 20px;
}

/* 列表动画 */
.list-enter-active, .list-leave-active {
  transition: all 0.4s ease;
}
.list-enter-from, .list-leave-to {
  opacity: 0;
  transform: translateX(30px);
}

/* 骑手列表 */
.rider-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.rider-item {
  background: rgba(255, 255, 255, 0.05);
  padding: 10px;
  border-radius: 8px;
  border-left: 3px solid var(--accent-color);
}
.rider-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 5px;
}
.rider-id {
  font-weight: 600;
  color: #fff;
}
.rider-details {
  font-size: 0.85rem;
  color: #aaa;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
</style>
