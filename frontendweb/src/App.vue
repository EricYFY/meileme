<template>
  <div class="dashboard-wrapper">
    <!-- 地图编辑器视图 -->
    <MapEditor 
      v-if="currentView === 'editor'" 
      @close="currentView = 'simulation'" 
      @mapSaved="fetchSavedMaps" 
    />

    <!-- 模拟监控主控制台视图 -->
    <div v-else class="dashboard">
      <!-- 启动配置弹窗 -->
      <div v-if="!isSimulationStarted" class="start-modal-overlay">
        <div class="start-modal glass-panel">
          <h2>🚀 外卖调度系统启动前序</h2>
          <p class="modal-desc">系统已连接，请选择运行地图与初始参数。</p>
          
          <div class="form-group">
            <label>运行地图选择</label>
            <select v-model="selectedMapId" class="map-dropdown">
              <option value="random">🎲 随机生成全新地图</option>
              <option v-for="m in savedMaps" :key="m.id" :value="m.id">
                🗺️ {{ m.name }} ({{ m.roadCount }} 道路格)
              </option>
            </select>
          </div>

          <div class="form-group">
            <label>初始商家数量 (1-50)</label>
            <input type="number" v-model.number="merchantCount" min="1" max="50" />
          </div>
          <div class="form-group">
            <label>初始骑手数量 (1-100)</label>
            <input type="number" v-model.number="riderCount" min="1" max="100" />
          </div>
          
          <div class="modal-actions">
            <button class="btn-start" @click="startSimulation" :disabled="!isConnected || isStarting">
              {{ !isConnected ? '等待服务器连接...' : (isStarting ? '启动中...' : '启动引擎') }}
            </button>
            <button class="btn-open-editor" @click="currentView = 'editor'">
              🎨 绘制/编辑自定义地图
            </button>
          </div>
        </div>
      </div>
      <!-- 左侧主地图区域 -->
      <div class="map-section glass-panel">
        <div class="header">
          <div class="header-left">
            <h2>外卖调度模拟器 (201x201)</h2>
            <!-- 赛博风虚拟时间时钟 (1s=2min) -->
            <div v-if="isSimulationStarted" class="game-time-badge" :class="{ 'paused': isPaused }">
              <span class="time-icon">{{ isPaused ? '⏸️' : '🕒' }}</span>
              <span class="time-label">虚拟时间:</span>
              <span class="time-val mono">{{ formattedGameTime }}</span>
            </div>
          </div>
          <div class="header-actions">
            <!-- 地图编辑器入口 -->
            <button v-if="!isSimulationStarted" class="btn-editor-entry" @click="currentView = 'editor'" title="打开地图编辑器">
              🎨 地图编辑器
            </button>
            <!-- 暂停/继续按钮 -->
            <button 
              v-if="isSimulationStarted" 
              class="btn-pause" 
              :class="{ 'paused': isPaused }"
              @click="togglePause" 
              :title="isPaused ? '继续模拟' : '暂停模拟'"
            >
              {{ isPaused ? '▶️ 继续' : '⏸️ 暂停' }}
            </button>
            <button v-if="isSimulationStarted" class="btn-stop" @click="stopSimulation" title="结束当前模拟并重置">
              ⏹️ 结束模拟
            </button>
            <div class="status-indicator">
              <span class="dot" :class="{ 'connected': isConnected }"></span>
              {{ isConnected ? '服务器已连接' : '服务器断开' }}
            </div>
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
      <!-- 平台财务总账看板 -->
      <div class="stats-card glass-panel financial-panel">
        <div class="panel-header-row">
          <h3>💰 平台财务总账 (第 {{ virtualDay }} 天)</h3>
          <button class="btn-config-toggle" :class="{ active: showRateConfig }" @click="showRateConfig = !showRateConfig">
            ⚙️ {{ showRateConfig ? '收起配置' : '费率配置' }}
          </button>
        </div>

        <!-- 费率参数设置折叠面板 -->
        <div v-if="showRateConfig" class="rate-config-drawer">
          <div class="config-title">⚙️ 实时财务费率参数配置 (即时生效)</div>
          <div class="config-form-grid">
            <div class="config-field">
              <label>平台抽成比例 (%)</label>
              <div class="input-unit-wrap">
                <input 
                  type="number" 
                  v-model.number="configTakeRatePercent" 
                  min="0" 
                  max="100" 
                  step="1"
                />
                <span class="unit">%</span>
              </div>
              <span class="field-hint">范围: 0% ~ 100%</span>
            </div>

            <div class="config-field">
              <label>骑手提成下限 (¥)</label>
              <div class="input-unit-wrap">
                <input 
                  type="number" 
                  v-model.number="configBonusMin" 
                  min="0" 
                  max="20" 
                  step="0.5"
                />
                <span class="unit">元</span>
              </div>
              <span class="field-hint">范围: 0 ~ 20 元</span>
            </div>

            <div class="config-field">
              <label>骑手提成上限 (¥)</label>
              <div class="input-unit-wrap">
                <input 
                  type="number" 
                  v-model.number="configBonusMax" 
                  min="0" 
                  max="20" 
                  step="0.5"
                />
                <span class="unit">元</span>
              </div>
              <span class="field-hint">范围: 0 ~ 20 元</span>
            </div>
          </div>

          <div class="config-action-row">
            <button class="btn-save-config" @click="submitFinancialConfig">
              💾 保存并立刻生效
            </button>
            <span v-if="saveRateSuccess" class="save-success-tip">✅ 费率已即时生效！</span>
          </div>
        </div>

        <div class="stats-grid financial-grid">
          <div class="stat-item fin-item">
            <span class="label">💰 平台总收入</span>
            <span class="value text-revenue">¥{{ totalRevenue.toFixed(2) }}</span>
            <span class="sub-label">(抽成 {{ Math.round((currentTakeRate || 0.15) * 100) }}%)</span>
          </div>
          <div class="stat-item fin-item">
            <span class="label">💸 平台总支出</span>
            <span class="value text-expense">¥{{ totalExpenses.toFixed(2) }}</span>
            <span class="sub-label">(提成 [¥{{ (currentBonusMin || 3.0).toFixed(1) }}, ¥{{ (currentBonusMax || 8.0).toFixed(1) }}])</span>
          </div>
          <div class="stat-item fin-item">
            <span class="label">⚠️ 平台总罚款</span>
            <span class="value text-fines">¥{{ totalFines.toFixed(2) }}</span>
            <span class="sub-label">(失效超时赔付)</span>
          </div>
          <div class="stat-item fin-item">
            <span class="label">📊 平台净利润</span>
            <span class="value" :class="netProfit >= 0 ? 'text-profit-pos' : 'text-profit-neg'">
              {{ netProfit >= 0 ? '+' : '' }}¥{{ netProfit.toFixed(2) }}
            </span>
            <span class="sub-label">(收支汇总)</span>
          </div>
        </div>
      </div>

      <!-- 实时调度状态 -->
      <div class="stats-card glass-panel">
        <div class="panel-header-row">
          <h3>⚡ 实时调度状态 (10Hz)</h3>
        </div>
        <div class="realtime-stats-grid">
          <div class="stat-item">
            <span class="label">空闲骑手</span>
            <span class="value text-success">{{ idleRidersCount }}</span>
          </div>
          <div class="stat-item">
            <span class="label">取餐中骑手</span>
            <span class="value text-picking">{{ pickingRidersCount }}</span>
          </div>
          <div class="stat-item">
            <span class="label">送餐中骑手</span>
            <span class="value text-delivering">{{ deliveringRidersCount }}</span>
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
          <div class="stat-item">
            <span class="label">已送达订单</span>
            <span class="value text-completed">{{ completedOrderCount }}</span>
          </div>
          <div class="stat-item">
            <span class="label">已失效订单</span>
            <span class="value text-expired">{{ expiredOrderCount }}</span>
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
            <span class="detail-label">🟠 取餐点</span>
            <span class="detail-value">({{ Math.round(selectedOrder.pickupLocation.x) }}, {{ Math.round(selectedOrder.pickupLocation.y) }})</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">🟢 送达点</span>
            <span class="detail-value">({{ Math.round(selectedOrder.deliveryLocation.x) }}, {{ Math.round(selectedOrder.deliveryLocation.y) }})</span>
          </div>
          <div v-if="assignedRider" class="detail-row">
            <span class="detail-label">⚪ 负责骑手</span>
            <span class="detail-value">{{ assignedRider.id }} @ ({{ Math.round(assignedRider.currentPosition.x) }}, {{ Math.round(assignedRider.currentPosition.y) }})</span>
          </div>
        </div>
      </div>

      <!-- 下方三个 Tab 页签切换 -->
      <div class="tabs-container glass-panel">
        <div class="tabs-header">
          <button 
            class="tab-btn" 
            :class="{ active: activeTab === 'merchants' }" 
            @click="activeTab = 'merchants'"
          >
            🏢 商家列表 ({{ merchants.length }})
          </button>
          <button 
            class="tab-btn" 
            :class="{ active: activeTab === 'orders' }" 
            @click="activeTab = 'orders'"
          >
            📦 订单列表 ({{ Object.keys(orders).length }})
          </button>
          <button 
            class="tab-btn" 
            :class="{ active: activeTab === 'riders' }" 
            @click="activeTab = 'riders'"
          >
            🚴 骑手列表 ({{ riders.length }})
          </button>
        </div>

        <div class="tab-body">
          <!-- 1. 商家列表 (含佣金、订单收入与总净收益) -->
          <div v-show="activeTab === 'merchants'" class="tab-pane">
            <div class="merchant-list">
              <div v-for="m in merchants" :key="m.id" class="merchant-item">
                <div class="merchant-header">
                  <span class="merchant-name">{{ m.id }}</span>
                  <span class="merchant-rating">⭐ {{ (m.rating || 5.0).toFixed(1) }}</span>
                </div>
                <div class="merchant-financial-row">
                  <span class="fin-tag tag-comm">佣金: -¥{{ (m.commission || 0).toFixed(1) }}</span>
                  <span class="fin-tag tag-order-rev">订单: +¥{{ (m.orderRevenue || 0).toFixed(1) }}</span>
                  <span class="fin-tag tag-income" :class="(m.totalIncome || 0) >= 0 ? 'text-green' : 'text-red'">
                    总收益: ¥{{ (m.totalIncome || 0).toFixed(1) }}
                  </span>
                </div>
                <div class="merchant-details">
                  <span class="tag tag-ongoing">进行中: {{ m.ongoingOrders || 0 }}</span>
                  <span class="tag tag-completed">已完成: {{ m.completedOrders || 0 }}</span>
                  <span class="merchant-coord">({{ Math.round(m.location.x) }}, {{ Math.round(m.location.y) }})</span>
                </div>
              </div>
              <div v-if="merchants.length === 0" class="empty-state">
                暂无商家数据
              </div>
            </div>
          </div>

          <!-- 2. 订单列表 -->
          <div v-show="activeTab === 'orders'" class="tab-pane">
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

          <!-- 3. 骑手列表 (含底薪、提成与总工资) -->
          <div v-show="activeTab === 'riders'" class="tab-pane">
            <div class="rider-list">
              <div v-for="rider in riders" :key="rider.id" class="rider-item">
                <div class="rider-header">
                  <span class="rider-id">{{ rider.id }}</span>
                  <span :class="['status-badge', getRiderStatusClass(rider.status)]">{{ getRiderStatusText(rider.status) }}</span>
                </div>
                <div class="rider-salary-row">
                  <span class="salary-tag">底薪: ¥{{ (rider.baseSalary || 0).toFixed(1) }}</span>
                  <span class="salary-tag tag-bonus">提成: +¥{{ (rider.bonus || 0).toFixed(1) }}</span>
                  <span class="salary-tag tag-total">总工资: ¥{{ (rider.totalSalary || 0).toFixed(1) }}</span>
                </div>
                <div class="rider-details">
                  <div>坐标: {{ rider.currentPosition ? Math.round(rider.currentPosition.x) + ',' + Math.round(rider.currentPosition.y) : '未知' }}</div>
                  <div v-if="rider.targetPosition">目标: {{ Math.round(rider.targetPosition.x) }}, {{ Math.round(rider.targetPosition.y) }}</div>
                  <div v-if="rider.currentOrderId" class="rider-order text-accent">订单: #{{ rider.currentOrderId.substring(0,8) }}</div>
                </div>
              </div>
              <div v-if="riders.length === 0" class="empty-state">
                暂无骑手数据
              </div>
            </div>
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
import MapEditor from './components/MapEditor.vue';
import WebSocketClient from './services/WebSocketClient.js';

const currentView = ref('simulation'); // 'simulation' | 'editor'
const savedMaps = ref([]);
const selectedMapId = ref('random');

const isConnected = ref(false);
const isSimulationStarted = ref(false);
const isStarting = ref(false);
const merchantCount = ref(5);
const riderCount = ref(10);

const completedOrderCount = ref(0);
const expiredOrderCount = ref(0);

// === 平台财务数据 ===
const totalRevenue = ref(0.0);
const totalExpenses = ref(0.0);
const totalFines = ref(0.0);
const netProfit = ref(0.0);
const virtualDay = ref(1);

// 动态费率配置参数 (用户可在页面随时微调并即时生效)
const showRateConfig = ref(false);
const configTakeRatePercent = ref(15); // 0% ~ 100%
const configBonusMin = ref(3.0);       // 0 ~ 20 元
const configBonusMax = ref(8.0);       // 0 ~ 20 元
const currentTakeRate = ref(0.15);
const currentBonusMin = ref(3.0);
const currentBonusMax = ref(8.0);
const saveRateSuccess = ref(false);

const submitFinancialConfig = () => {
  let ratePercent = Math.max(0, Math.min(100, Number(configTakeRatePercent.value) || 0));
  let minB = Math.max(0, Math.min(20, Number(configBonusMin.value) || 0));
  let maxB = Math.max(0, Math.min(20, Number(configBonusMax.value) || 0));

  if (minB > maxB) {
    const temp = minB;
    minB = maxB;
    maxB = temp;
    configBonusMin.value = minB;
    configBonusMax.value = maxB;
  }

  configTakeRatePercent.value = ratePercent;
  const takeRate = ratePercent / 100.0;

  client.send({
    command: 'UPDATE_FINANCIAL_CONFIG',
    platformTakeRate: takeRate,
    riderBonusMin: minB,
    riderBonusMax: maxB
  });

  currentTakeRate.value = takeRate;
  currentBonusMin.value = minB;
  currentBonusMax.value = maxB;

  saveRateSuccess.value = true;
  setTimeout(() => {
    saveRateSuccess.value = false;
  }, 3000);
};

const activeTab = ref('merchants');
const merchants = ref([]);

// ★ 游戏虚拟时间：固定从 2026年07月01日 00:00:00 开始
const BASE_GAME_TIME = new Date(2026, 6, 1, 0, 0, 0).getTime();
const gameTimeMs = ref(BASE_GAME_TIME);
const isPaused = ref(false);

const formattedGameTime = computed(() => {
  const d = new Date(gameTimeMs.value);
  const pad = (n) => String(n).padStart(2, '0');
  const year = d.getFullYear();
  const month = pad(d.getMonth() + 1);
  const day = pad(d.getDate());
  const hours = pad(d.getHours());
  const minutes = pad(d.getMinutes());
  const seconds = pad(d.getSeconds());
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
});

let timeInterval = null;
const startTimer = () => {
  if (timeInterval) clearInterval(timeInterval);
  timeInterval = setInterval(() => {
    if (isSimulationStarted.value && !isPaused.value) {
      // 现实 100ms = 游戏 12000ms (12秒，即 1s 现实 = 120s 游戏)
      gameTimeMs.value += 12000;
    }
  }, 100);
};

const mapData = ref(null);
const riders = ref([]);
const orders = ref({});
const selectedOrderId = ref(null);

const fetchSavedMaps = async () => {
  try {
    const res = await fetch('http://localhost:8081/api/maps');
    if (res.ok) {
      savedMaps.value = await res.json();
    }
  } catch (e) {
    console.error('拉取地图列表失败:', e);
  }
};

const startSimulation = () => {
  if (isConnected.value && !isStarting.value) {
    isStarting.value = true;
    client.send({
      command: 'START_SIMULATION',
      merchantCount: merchantCount.value,
      riderCount: riderCount.value,
      mapId: selectedMapId.value
    });
  }
};

const togglePause = () => {
  if (!isConnected.value || !isSimulationStarted.value) return;
  if (isPaused.value) {
    client.send({ command: 'RESUME_SIMULATION' });
  } else {
    client.send({ command: 'PAUSE_SIMULATION' });
  }
};

const stopSimulation = () => {
  if (confirm('确定要结束当前模拟并重置吗？')) {
    client.send({
      command: 'STOP_SIMULATION'
    });
  }
};

const client = new WebSocketClient();

onMounted(() => {
  startTimer();
  fetchSavedMaps();
  client.onStatusChange = (status) => isConnected.value = status;
  
  client.onSimulationStarted = () => {
    isSimulationStarted.value = true;
    isStarting.value = false;
    isPaused.value = false;
    gameTimeMs.value = BASE_GAME_TIME;
    orders.value = {};
    riders.value = [];
    merchants.value = [];
    selectedOrderId.value = null;
    completedOrderCount.value = 0;
    expiredOrderCount.value = 0;
    // 第 1 天初始日结结算秒级呈现 (佣金 ¥50 * 商家数, 底薪 ¥100 * 骑手数)
    const initRev = 50.0 * merchantCount.value;
    const initExp = 100.0 * riderCount.value;
    totalRevenue.value = initRev;
    totalExpenses.value = initExp;
    totalFines.value = 0.0;
    netProfit.value = initRev - initExp;
    virtualDay.value = 1;
  };

  client.onSimulationPaused = () => {
    isPaused.value = true;
  };

  client.onSimulationResumed = () => {
    isPaused.value = false;
  };

  client.onSimulationStopped = () => {
    isSimulationStarted.value = false;
    isStarting.value = false;
    isPaused.value = false;
    gameTimeMs.value = BASE_GAME_TIME;
    mapData.value = null;
    orders.value = {};
    riders.value = [];
    merchants.value = [];
    selectedOrderId.value = null;
    completedOrderCount.value = 0;
    expiredOrderCount.value = 0;
    totalRevenue.value = 0.0;
    totalExpenses.value = 0.0;
    totalFines.value = 0.0;
    netProfit.value = 0.0;
    virtualDay.value = 1;
  };

  client.onFinancialUpdate = (data) => {
    if (data) {
      totalRevenue.value = data.totalRevenue || 0.0;
      totalExpenses.value = data.totalExpenses || 0.0;
      totalFines.value = data.totalFines || 0.0;
      netProfit.value = data.netProfit || 0.0;
      if (data.virtualDay) {
        virtualDay.value = data.virtualDay;
      }
      if (data.gameVirtualTimeMs) {
        // 与权威后端时钟绝对对齐
        gameTimeMs.value = data.gameVirtualTimeMs;
      }
      if (data.platformTakeRate !== undefined) {
        currentTakeRate.value = data.platformTakeRate;
        if (!showRateConfig.value) {
          configTakeRatePercent.value = Math.round(data.platformTakeRate * 100);
        }
      }
      if (data.riderBonusMin !== undefined) {
        currentBonusMin.value = data.riderBonusMin;
        if (!showRateConfig.value) {
          configBonusMin.value = data.riderBonusMin;
        }
      }
      if (data.riderBonusMax !== undefined) {
        currentBonusMax.value = data.riderBonusMax;
        if (!showRateConfig.value) {
          configBonusMax.value = data.riderBonusMax;
        }
      }
    }
  };

  client.onMapData = (data) => {
    mapData.value = data;
    if (data) {
      isSimulationStarted.value = true;
      if (data.merchants) {
        merchants.value = data.merchants;
      }
    }
  };
  
  client.onMerchantUpdate = (data) => {
    merchants.value = data;
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
      if (data.completedCount !== undefined) {
        completedOrderCount.value = data.completedCount;
      } else {
        completedOrderCount.value++;
      }
      // ★ 订单完成送达后，彻底从前端活跃订单字典中删除，释放内存与 DOM 节点
      delete orders.value[data.id];
      if (selectedOrderId.value === data.id) {
        selectedOrderId.value = null;
      }
    } else if (type === 'ORDER_EXPIRED') {
      if (data.expiredCount !== undefined) {
        expiredOrderCount.value = data.expiredCount;
      } else {
        expiredOrderCount.value++;
      }
      // ★ 订单超时失效后，彻底从前端活跃订单字典中删除
      delete orders.value[data.id];
      if (selectedOrderId.value === data.id) {
        selectedOrderId.value = null;
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
    case 0: return 'rider-status-idle';       // 空闲: 绿色
    case 1: return 'rider-status-picking';    // 接单中/取餐: 浅蓝色
    case 2: return 'rider-status-delivering'; // 配送中: 深蓝色
    default: return '';
  }
};
</script>

<style scoped>
.dashboard-wrapper {
  width: 100vw;
  height: 100vh;
  overflow: hidden;
}

.dashboard {
  display: flex;
  height: 100vh;
  width: 100vw;
  background-color: var(--bg-color);
  color: var(--text-color);
  overflow: hidden; /* 防止出现外层滚动条 */
}

/* 启动弹窗 */
.start-modal-overlay {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(10, 10, 14, 0.85);
  backdrop-filter: blur(8px);
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
}
.start-modal {
  padding: 35px;
  border-radius: 16px;
  width: 420px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  border: 1px solid rgba(255, 255, 255, 0.1);
}
.start-modal h2 {
  margin: 0;
  font-size: 1.5rem;
  color: var(--primary-color);
  text-align: center;
}
.modal-desc {
  text-align: center;
  color: #aaa;
  margin: 0 0 8px 0;
  font-size: 0.9rem;
}
.form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.form-group label {
  font-size: 0.9rem;
  font-weight: 500;
}
.form-group input, .map-dropdown {
  padding: 10px;
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  background: rgba(0, 0, 0, 0.3);
  color: #fff;
  font-size: 0.95rem;
}
.form-group input:focus, .map-dropdown:focus {
  outline: none;
  border-color: var(--primary-color);
}
.map-dropdown option {
  background: #0f172a;
  color: #fff;
}
.modal-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 8px;
}
.btn-start {
  padding: 12px;
  border-radius: 8px;
  background: var(--primary-color);
  color: #fff;
  border: none;
  font-size: 1.1rem;
  font-weight: bold;
  cursor: pointer;
  transition: all 0.2s;
}
.btn-start:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(100, 108, 255, 0.4);
}
.btn-start:disabled {
  background: #555;
  cursor: not-allowed;
  opacity: 0.7;
}

.btn-open-editor {
  padding: 10px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px dashed rgba(255, 255, 255, 0.3);
  color: #93c5fd;
  font-size: 0.95rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}
.btn-open-editor:hover {
  background: rgba(59, 130, 246, 0.2);
  border-color: #60a5fa;
}

.btn-editor-entry {
  padding: 6px 14px;
  background: rgba(59, 130, 246, 0.15);
  border: 1px solid rgba(59, 130, 246, 0.4);
  color: #93c5fd;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}
.btn-editor-entry:hover {
  background: rgba(59, 130, 246, 0.3);
  transform: translateY(-1px);
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
  flex-wrap: wrap;
  gap: 12px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.game-time-badge {
  display: flex;
  align-items: center;
  gap: 8px;
  background: rgba(16, 185, 129, 0.12);
  border: 1px solid rgba(16, 185, 129, 0.3);
  padding: 6px 12px;
  border-radius: 20px;
  font-size: 13px;
  color: #34d399;
  transition: all 0.3s ease;
}

.game-time-badge.paused {
  background: rgba(239, 68, 68, 0.12);
  border-color: rgba(239, 68, 68, 0.3);
  color: #f87171;
}

.time-label {
  color: var(--text-secondary);
  font-size: 12px;
}

.time-val {
  font-weight: 700;
  font-size: 14px;
  letter-spacing: 0.5px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.btn-pause {
  padding: 6px 14px;
  background: rgba(245, 158, 11, 0.15);
  border: 1px solid rgba(245, 158, 11, 0.4);
  color: #fbbf24;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-pause:hover {
  background: rgba(245, 158, 11, 0.3);
  transform: translateY(-1px);
}

.btn-pause.paused {
  background: rgba(59, 130, 246, 0.2);
  border-color: rgba(59, 130, 246, 0.5);
  color: #93c5fd;
}

.btn-stop {
  padding: 6px 14px;
  background: rgba(239, 68, 68, 0.15);
  border: 1px solid rgba(239, 68, 68, 0.4);
  color: #f87171;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 6px;
  transition: all 0.2s ease;
}

.btn-stop:hover {
  background: rgba(239, 68, 68, 0.3);
  border-color: #ef4444;
  color: #fff;
  transform: translateY(-1px);
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
  width: 400px;
  padding: 15px;
  display: flex;
  flex-direction: column;
  gap: 15px;
  height: 100vh;
  box-sizing: border-box;
}

.stats-card, .orders-card, .detail-card {
  padding: 15px;
  border-radius: 12px;
  display: flex;
  flex-direction: column;
}

.panel-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  border-bottom: 1px solid var(--panel-border);
  padding-bottom: 8px;
}

.panel-header-row h3 {
  margin: 0 !important;
  border-bottom: none !important;
  padding-bottom: 0 !important;
}

.btn-config-toggle {
  background: rgba(59, 130, 246, 0.15);
  border: 1px solid rgba(59, 130, 246, 0.4);
  color: #93c5fd;
  font-size: 11px;
  font-weight: 600;
  padding: 3px 8px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-config-toggle:hover, .btn-config-toggle.active {
  background: rgba(59, 130, 246, 0.35);
  border-color: #60a5fa;
  color: #fff;
}

/* 费率配置抽屉面板 */
.rate-config-drawer {
  background: rgba(0, 0, 0, 0.4);
  border: 1px solid rgba(59, 130, 246, 0.25);
  border-radius: 8px;
  padding: 10px;
  margin-bottom: 12px;
  animation: fadeIn 0.2s ease;
}

.config-title {
  font-size: 12px;
  font-weight: 700;
  color: #93c5fd;
  margin-bottom: 8px;
}

.config-form-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin-bottom: 10px;
}

.config-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.config-field label {
  font-size: 10px;
  color: #94a3b8;
  white-space: nowrap;
}

.input-unit-wrap {
  display: flex;
  align-items: center;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 4px;
  padding: 2px 6px;
}

.input-unit-wrap input {
  width: 100%;
  background: transparent;
  border: none;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  font-family: monospace;
  outline: none;
}

.input-unit-wrap .unit {
  font-size: 11px;
  color: #94a3b8;
  margin-left: 2px;
}

.field-hint {
  font-size: 9px;
  color: #64748b;
}

.config-action-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.btn-save-config {
  padding: 6px 12px;
  background: #2563eb;
  border: 1px solid #3b82f6;
  border-radius: 6px;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-save-config:hover {
  background: #1d4ed8;
  transform: translateY(-1px);
}

.save-success-tip {
  font-size: 11px;
  color: #34d399;
  font-weight: 600;
}

.financial-panel {
  background: rgba(15, 23, 42, 0.75);
  border: 1px solid rgba(59, 130, 246, 0.3);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.3);
}

.control-panel {
  width: 440px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100vh;
  box-sizing: border-box;
  overflow-y: auto;
}

.financial-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
}

.realtime-stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 6px;
}

.fin-item {
  background: rgba(0, 0, 0, 0.35);
  padding: 8px 6px;
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.05);
}

.fin-item .value {
  font-size: 17px !important;
  font-family: monospace;
  font-weight: 700;
  margin: 2px 0;
}

.sub-label {
  font-size: 10px;
  color: #64748b;
}

.text-revenue { color: #22c55e; }
.text-expense { color: #f59e0b; }
.text-fines { color: #ef4444; }
.text-profit-pos { color: #10b981; font-weight: 800; }
.text-profit-neg { color: #f87171; font-weight: 800; }

.stat-item {
  display: flex;
  flex-direction: column;
  background: rgba(0,0,0,0.2);
  padding: 8px 4px;
  border-radius: 8px;
  text-align: center;
}

.stat-item .label {
  font-size: 11px;
  color: var(--text-secondary);
  margin-bottom: 2px;
  white-space: nowrap;
}

.stat-item .value {
  font-size: 18px;
  font-weight: 700;
}

.text-accent { color: var(--accent-color); }
.text-warning { color: var(--warning); }
.text-success { color: var(--success); }
.text-danger { color: var(--danger); }
.text-picking { color: #38bdf8; }
.text-delivering { color: #1d4ed8; }
.text-completed { color: #10b981; }
.text-expired { color: #64748b; }

/* 商家与骑手财务标签 */
.merchant-financial-row, .rider-salary-row {
  display: flex;
  gap: 6px;
  margin: 6px 0;
  flex-wrap: wrap;
}

.fin-tag, .salary-tag {
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 4px;
  font-weight: 600;
  font-family: monospace;
}

.tag-comm {
  background: rgba(245, 158, 11, 0.12);
  color: #fbbf24;
  border: 1px solid rgba(245, 158, 11, 0.25);
}

.tag-order-rev {
  background: rgba(34, 197, 94, 0.12);
  color: #4ade80;
  border: 1px solid rgba(34, 197, 94, 0.25);
}

.tag-income {
  background: rgba(59, 130, 246, 0.12);
  border: 1px solid rgba(59, 130, 246, 0.25);
}

.salary-tag {
  background: rgba(255, 255, 255, 0.06);
  color: #e2e8f0;
}

.tag-bonus {
  background: rgba(56, 189, 248, 0.12);
  color: #38bdf8;
  border: 1px solid rgba(56, 189, 248, 0.25);
}

.tag-total {
  background: rgba(16, 185, 129, 0.15);
  color: #34d399;
  border: 1px solid rgba(16, 185, 129, 0.3);
  font-weight: 700;
}

.text-green { color: #34d399; }
.text-red { color: #f87171; }

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

.status-0 { border-left: 3px solid #ef4444; }
.status-0 .order-status-badge { background: rgba(239, 68, 68, 0.2); color: #f87171; }

.status-1 { border-left: 3px solid #38bdf8; }
.status-1 .order-status-badge { background: rgba(56, 189, 248, 0.2); color: #7dd3fc; }

.status-2 { border-left: 3px solid #1d4ed8; }
.status-2 .order-status-badge { background: rgba(29, 78, 216, 0.25); color: #93c5fd; }

.status-3 { border-left: 3px solid var(--success); opacity: 0.5; }

.rider-status-idle { color: #22c55e !important; font-weight: 600; }
.rider-status-picking { color: #38bdf8 !important; font-weight: 600; }
.rider-status-delivering { color: #60a5fa !important; font-weight: 600; }

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

/* Tabs 容器与头部 */
.tabs-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  padding: 14px;
}

.tabs-header {
  display: flex;
  gap: 8px;
  border-bottom: 1px solid var(--panel-border);
  padding-bottom: 10px;
  margin-bottom: 10px;
}

.tab-btn {
  flex: 1;
  padding: 8px 4px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid transparent;
  border-radius: 6px;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
  text-align: center;
}

.tab-btn:hover {
  background: rgba(255, 255, 255, 0.1);
  color: #fff;
}

.tab-btn.active {
  background: rgba(59, 130, 246, 0.2);
  border-color: rgba(59, 130, 246, 0.5);
  color: #93c5fd;
}

.tab-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.tab-pane {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

/* 商家列表样式 */
.merchant-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  flex: 1;
  overflow-y: auto;
  padding-right: 5px;
}

.merchant-item {
  background: rgba(255, 255, 255, 0.05);
  padding: 12px;
  border-radius: 8px;
  border-left: 3px solid #f97316;
}

.merchant-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.merchant-name {
  font-weight: 600;
  color: #fff;
  font-size: 14px;
}

.merchant-rating {
  font-size: 13px;
  font-weight: 700;
  color: #f97316;
  background: rgba(249, 115, 22, 0.15);
  padding: 2px 8px;
  border-radius: 12px;
  border: 1px solid rgba(249, 115, 22, 0.3);
}

.merchant-details {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  flex-wrap: wrap;
}

.tag {
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
}

.tag-ongoing {
  background: rgba(234, 179, 8, 0.15);
  color: #facc15;
  border: 1px solid rgba(234, 179, 8, 0.3);
}

.tag-completed {
  background: rgba(16, 185, 129, 0.15);
  color: #34d399;
  border: 1px solid rgba(16, 185, 129, 0.3);
}

.merchant-coord {
  color: var(--text-secondary);
  margin-left: auto;
  font-size: 11px;
}

/* 骑手列表 */
.rider-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  flex: 1;
  overflow-y: auto;
  padding-right: 5px;
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
