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
          <h2>🚀 外卖调度拟真模拟器</h2>
          <p class="modal-desc">201x201 (20.1km*20.1km) 真实环境模拟</p>
          
          <div class="form-group">
            <label>运行地图选择</label>
            <select v-model="selectedMapId" class="map-dropdown">
              <option value="random">🎲 随机生成全新拟真地图</option>
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
              {{ !isConnected ? '等待服务器连接...' : (isStarting ? '启动中...' : '启动拟真模拟引擎') }}
            </button>
            <button class="btn-open-editor" @click="currentView = 'editor'">
              🎨 绘制/编辑自定义地图
            </button>
          </div>
        </div>
      </div>

      <!-- 破产结算画面 -->
      <div v-if="isBankrupt" class="bankrupt-overlay">
        <div class="bankrupt-panel glass-panel">
          <h2>💥 公司破产</h2>
          <p class="bankrupt-sub">公司现金账户为负，资不抵债，本局结束。</p>
          <div class="bankrupt-stats">
            <div class="bs-row"><span class="bs-label">📅 存活天数</span><span class="bs-value">{{ bankruptSurvivalDays }} 天</span></div>
            <div class="bs-row"><span class="bs-label">🏆 最佳评级</span><span class="bs-value">{{ bankruptBestRating.toFixed(2) }} / 5.0</span></div>
            <div class="bs-row"><span class="bs-label">💰 期末现金</span><span class="bs-value text-red">¥{{ bankruptCash.toFixed(2) }}</span></div>
            <div class="bs-row"><span class="bs-label">⚠️ 破产原因</span><span class="bs-value">{{ bankruptReason }}</span></div>
          </div>
          <button class="btn-start" @click="restartAfterBankruptcy">🔄 一键重开</button>
        </div>
      </div>

      <!-- 单个商家经营参数配置模态窗 -->
      <div v-if="showMerchantModal" class="merchant-modal-overlay">
        <div class="merchant-modal glass-panel" @click.stop>
          <div class="modal-header">
            <h3>⚙️ 商家经营参数配置 ({{ editingMerchant?.id }})</h3>
            <button class="modal-close-btn" @click="showMerchantModal = false" title="关闭">✕</button>
          </div>
          
          <div class="merchant-form-body">
            <div class="form-section-title">📦 定价与耗材</div>
            <div class="form-row-2">
              <div class="form-field">
                <label>客单价下限 (¥)</label>
                <input type="number" v-model.number="merchantForm.minOrderPrice" min="1" step="1" />
              </div>
              <div class="form-field">
                <label>客单价上限 (¥)</label>
                <input type="number" v-model.number="merchantForm.maxOrderPrice" min="1" step="1" />
              </div>
            </div>

            <div class="form-field">
              <label>订单耗材成本占比: <span class="highlight-val">{{ merchantForm.materialCostRatePercent }}%</span> (直接影响餐品质量好评)</label>
              <input type="range" v-model.number="merchantForm.materialCostRatePercent" min="10" max="90" step="5" class="slider-input" />
            </div>

            <div class="form-section-title">🏢 运营与广告成本 (每日固定)</div>
            <div class="form-row-2">
              <div class="form-field">
                <label>门面租金 (¥/天)</label>
                <input type="number" v-model.number="merchantForm.rentCost" min="0" step="10" />
              </div>
              <div class="form-field">
                <label>人工成本 (¥/天)</label>
                <input type="number" v-model.number="merchantForm.laborCost" min="0" step="10" />
              </div>
            </div>
            <div class="field-tip">💡 提示：门面租金 + 人工成本越高，后厨人手越多，订单备餐越快，可大幅降低骑手等餐超时风险。</div>

            <div class="form-row-2">
              <div class="form-field">
                <label>每日广告投入 (¥/天)</label>
                <input type="number" v-model.number="merchantForm.adSpend" min="0" step="5" />
              </div>
              <div class="form-field">
                <label>每日平台加盟费 (¥/天)</label>
                <input type="number" v-model.number="merchantForm.dailyFranchiseFee" min="0" step="10" />
              </div>
            </div>
            <div class="field-tip">💡 提示：广告投入越高，在用户端的曝光率越高，产生订单概率越大。</div>
          </div>

          <div class="modal-footer">
            <button class="btn-cancel" @click="showMerchantModal = false">取消</button>
            <button class="btn-save" @click="saveMerchantConfig">💾 保存并立刻下发</button>
          </div>
        </div>
      </div>

      <!-- 左侧主地图区域 -->
      <div class="map-section glass-panel">
        <div class="header">
          <div class="header-left">
            <h2>外卖调度模拟器 (201x201 / 20.1km*20.1km)</h2>
            <!-- 公司现金栏 (常驻顶部) -->
            <div v-if="isSimulationStarted" class="cash-bar" :class="cashBarClass">
              <span class="cash-label">💰 公司现金</span>
              <span class="cash-value mono">¥{{ cash.toFixed(2) }}</span>
              <span v-if="cash < 200 && cash >= 0" class="cash-warn-tag">⚠️ 资金紧张</span>
              <span v-if="cash < 0" class="cash-danger-tag">🔴 资不抵债</span>
              <transition name="cash-float">
                <span v-if="cashFlash" class="cash-float-text" :class="cashFlash.type">{{ cashFlash.text }}</span>
              </transition>
            </div>
            <!-- 赛博风虚拟时间时钟 (1s=2min) -->
            <div v-if="isSimulationStarted" class="game-time-badge" :class="{ 'paused': isPaused }">
              <span class="time-icon">{{ isPaused ? '⏸️' : '🕒' }}</span>
              <span class="time-label">虚拟时间:</span>
              <span class="time-val mono">{{ formattedGameTime }}</span>
              <span class="peak-badge">{{ currentPeakLabel }}</span>
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
            <!-- 全局倍速切换 (0.5x / 1x / 2x / 4x) -->
            <div v-if="isSimulationStarted" class="speed-control" title="切换全局倍速">
              <button
                v-for="s in speedOptions"
                :key="s"
                class="btn-speed"
                :class="{ 'active': speed === s }"
                @click="setSpeed(s)"
              >
                {{ s }}x
              </button>
            </div>
            <!-- 派单模式切换 (全自动 / 全手动) -->
            <div v-if="isSimulationStarted" class="dispatch-mode-control" title="切换派单模式">
              <button
                class="btn-mode"
                :class="{ 'active': dispatchMode === 'AUTO' }"
                @click="setDispatchMode('AUTO')"
              >
                🤖 全自动派单
              </button>
              <button
                class="btn-mode"
                :class="{ 'active': dispatchMode === 'MANUAL' }"
                @click="setDispatchMode('MANUAL')"
              >
                🎯 全手动派单
              </button>
            </div>
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
            :manualMode="isManualDispatch"
            :movingRiderId="movingRiderId"
            @orderSelected="onMapOrderSelected"
            @riderSelected="onRiderSelected"
            @riderMoveSelected="onRiderMoveSelected"
            @riderMoveTarget="onRiderMoveTarget"
          />
        </div>
        <transition name="tip">
          <div v-if="manualTip" class="manual-tip-toast">{{ manualTip }}</div>
        </transition>
      </div>

      <!-- 右侧控制面板 -->
      <div class="control-panel">
        <!-- 平台财务总账看板 -->
        <div class="stats-card glass-panel financial-panel">
          <div class="panel-header-row">
            <h3>💰 平台财务总账 (第 {{ virtualDay }} 天)</h3>
            <button class="btn-config-toggle" :class="{ active: showRateConfig }" @click="showRateConfig = !showRateConfig">
              ⚙️ {{ showRateConfig ? '收起配置' : '平台费率' }}
            </button>
          </div>

          <!-- 费率参数设置折叠面板 -->
          <div v-if="showRateConfig" class="rate-config-drawer">
            <div class="config-title">⚙️ 平台抽成与骑手提成费率 (即时生效)</div>
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
                <span class="field-hint">默认 25%</span>
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
                💾 保存费率
              </button>
              <span v-if="saveRateSuccess" class="save-success-tip">✅ 费率已生效！</span>
            </div>
          </div>

          <div class="stats-grid financial-grid">
            <div class="stat-item fin-item">
              <span class="label">💰 平台总收入</span>
              <span class="value text-revenue">¥{{ totalRevenue.toFixed(2) }}</span>
              <span class="sub-label">(抽成 {{ Math.round((currentTakeRate || 0.25) * 100) }}% + 加盟费)</span>
            </div>
            <div class="stat-item fin-item">
              <span class="label">💸 平台总支出</span>
              <span class="value text-expense">¥{{ totalExpenses.toFixed(2) }}</span>
              <span class="sub-label">(底薪+提成 [¥{{ (currentBonusMin || 3.0).toFixed(1) }}, ¥{{ (currentBonusMax || 8.0).toFixed(1) }}])</span>
            </div>
            <div class="stat-item fin-item">
              <span class="label">⚠️ 平台总罚款</span>
              <span class="value text-fines">¥{{ totalFines.toFixed(2) }}</span>
              <span class="sub-label">(超时违约赔偿)</span>
            </div>
            <div class="stat-item fin-item">
              <span class="label">📊 平台净利润</span>
              <span class="value" :class="netProfit >= 0 ? 'text-profit-pos' : 'text-profit-neg'">
                {{ netProfit >= 0 ? '+' : '' }}¥{{ netProfit.toFixed(2) }}
              </span>
              <span class="sub-label">(收支结算)</span>
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
              <span class="label">已完成送达</span>
              <span class="value text-completed">{{ completedOrderCount }}</span>
            </div>
            <div class="stat-item">
              <span class="label">备餐待派</span>
              <span class="value text-danger">{{ pendingOrdersCount }}</span>
            </div>
            <div class="stat-item">
              <span class="label">骑手到店中</span>
              <span class="value text-warning">{{ dispatchedOrdersCount }}</span>
            </div>
            <div class="stat-item">
              <span class="label">在店等餐中</span>
              <span class="value text-meal-wait">{{ mealWaitingOrdersCount }}</span>
            </div>
            <div class="stat-item">
              <span class="label">送餐配送中</span>
              <span class="value text-accent">{{ deliveringOrdersCount }}</span>
            </div>
            <div class="stat-item stat-item-danger">
              <span class="label">失效订单总数</span>
              <span class="value text-danger font-bold">{{ expiredOrderCount }}</span>
            </div>
            <div class="stat-item stat-item-sub-fail">
              <span class="label">超时未接单</span>
              <span class="value text-sub-expire">{{ unassignedExpiredCount }}</span>
            </div>
            <div class="stat-item stat-item-sub-fail">
              <span class="label">超时未出餐</span>
              <span class="value text-sub-expire">{{ mealTimeoutExpiredCount }}</span>
            </div>
          </div>
        </div>

        <!-- 选中订单详情 -->
        <div v-if="selectedOrder" class="detail-card glass-panel">
          <div class="detail-header">
            <h3>📦 订单详情 (#{{ selectedOrder.id.substring(0, 8) }})</h3>
            <button class="close-btn" @click="selectOrder(null)">✕</button>
          </div>
          <div class="detail-body">
            <div class="detail-row">
              <span class="detail-label">当前状态</span>
              <span class="detail-value" :class="'text-status-' + selectedOrder.status">
                {{ getStatusText(selectedOrder.status) }}
              </span>
            </div>
            <div class="detail-row">
              <span class="detail-label">💵 订单金额</span>
              <span class="detail-value mono">标价: ¥{{ (selectedOrder.orderPrice || 20).toFixed(2) }} (耗材: ¥{{ (selectedOrder.materialCost || 10).toFixed(2) }})</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">🟠 商家取餐点</span>
              <span class="detail-value">({{ Math.round(selectedOrder.pickupLocation.x) }}, {{ Math.round(selectedOrder.pickupLocation.y) }})</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">🟢 客户送达点</span>
              <span class="detail-value">({{ Math.round(selectedOrder.deliveryLocation.x) }}, {{ Math.round(selectedOrder.deliveryLocation.y) }})</span>
            </div>
            <div v-if="assignedRider" class="detail-row">
              <span class="detail-label">⚪ 负责骑手</span>
              <span class="detail-value">{{ assignedRider.id }} @ ({{ Math.round(assignedRider.currentPosition.x) }}, {{ Math.round(assignedRider.currentPosition.y) }})</span>
            </div>
            <div v-if="selectedOrder.status === 2" class="detail-row highlight-row">
              <span class="detail-label">⏳ 等餐计时</span>
              <span class="detail-value text-red mono font-bold">已等 {{ selectedOrder.waitDurationSec || 0 }}s / 15s (超15s罚款原价)</span>
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
              📦 进行中订单 ({{ Object.keys(orders).length }})
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
            <!-- 1. 商家列表 (含真实经济全景与配置入口) -->
            <div v-show="activeTab === 'merchants'" class="tab-pane">
              <div class="merchant-list">
                <div v-for="m in sortedMerchants" :key="m.id" class="merchant-item">
                  <div class="merchant-header">
                    <div class="m-title-group">
                      <span class="merchant-name">{{ m.id }}</span>
                      <span class="merchant-rating">⭐ {{ (m.rating || 5.0).toFixed(1) }}</span>
                    </div>
                    <button class="btn-edit-m" @click.stop="openMerchantConfig(m)">
                      ⚙️ 经营配置
                    </button>
                  </div>
                  
                  <!-- 经营参数摘要 -->
                  <div class="merchant-param-tags">
                    <span class="m-tag">单价: ¥{{ m.minOrderPrice || 15 }}-{{ m.maxOrderPrice || 35 }}</span>
                    <span class="m-tag">耗材: {{ Math.round((m.materialCostRate || 0.5) * 100) }}%</span>
                    <span class="m-tag">固定: ¥{{ (m.rentCost || 100) + (m.laborCost || 150) }}/天</span>
                    <span class="m-tag">广告: ¥{{ m.adSpend || 20 }}/天</span>
                  </div>

                  <!-- 财务核算指标 -->
                  <div class="merchant-financial-row">
                    <span class="fin-tag tag-rev">流水: ¥{{ (m.totalRevenue || 0).toFixed(1) }}</span>
                    <span class="fin-tag tag-take">抽成: -¥{{ (m.totalCommissionPaid || 0).toFixed(1) }}</span>
                    <span class="fin-tag tag-mat">耗材: -¥{{ (m.totalMaterialCost || 0).toFixed(1) }}</span>
                    <span class="fin-tag tag-fixed">固定: -¥{{ (m.totalFixedCost || 0).toFixed(1) }}</span>
                    <span class="fin-tag tag-ad">广告: -¥{{ (m.totalAdSpend || 0).toFixed(1) }}</span>
                    <span class="fin-tag tag-comm">加盟: -¥{{ (m.totalFranchisePaid || 0).toFixed(1) }}</span>
                    <span v-if="m.totalFines > 0" class="fin-tag tag-fines">罚款: -¥{{ m.totalFines.toFixed(1) }}</span>
                    <span class="fin-tag tag-income" :class="(m.totalIncome || 0) >= 0 ? 'text-green font-bold' : 'text-red font-bold'">
                      净利润: ¥{{ (m.totalIncome || 0).toFixed(1) }}
                    </span>
                  </div>

                  <div class="merchant-details">
                    <span class="tag tag-ongoing">进行中: {{ m.ongoingOrders || 0 }}</span>
                    <span class="tag tag-completed">已完成: {{ m.completedOrders || 0 }}</span>
                    <span class="merchant-coord">坐标: ({{ Math.round(m.location.x) }}, {{ Math.round(m.location.y) }})</span>
                  </div>
                </div>
                <div v-if="merchants.length === 0" class="empty-state">
                  暂无商家数据
                </div>
              </div>
            </div>

            <!-- 2. 订单列表 (含备餐与等餐倒计时) -->
            <div v-show="activeTab === 'orders'" class="tab-pane">
              <div class="order-list">
                <TransitionGroup name="list">
                  <div 
                    v-for="order in sortedOrders" 
                    :key="order.id" 
                    class="order-item" 
                    :class="['status-' + order.status, { 'selected': order.id === selectedOrderId }]"
                    @click="selectOrder(order.id)"
                  >
                    <div class="order-header">
                      <span class="order-id">#{{ order.id.substring(0,8) }} ({{ order.merchantId }})</span>
                      <span class="order-status-badge" :class="'badge-status-' + order.status">
                        {{ getStatusText(order.status) }}
                      </span>
                    </div>
                    
                    <div class="order-meta-row">
                      <span class="order-price-badge">💵 标价: ¥{{ (order.orderPrice || 20).toFixed(1) }}</span>
                      <span class="order-mat-badge">耗材: ¥{{ (order.materialCost || 10).toFixed(1) }}</span>
                      <span v-if="order.status === 0" class="order-prep-badge">🍳 预计备餐: {{ order.prepDurationSec || 0 }}s</span>
                      <span v-if="order.status === 2" class="order-wait-badge">⏳ 已等餐: {{ order.waitDurationSec || 0 }}s/15s</span>
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
import { ref, computed, onMounted, onBeforeUnmount, reactive } from 'vue';
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
const unassignedExpiredCount = ref(0);
const mealTimeoutExpiredCount = ref(0);

// === 平台财务数据 ===
const totalRevenue = ref(0.0);
const totalExpenses = ref(0.0);
const totalFines = ref(0.0);
const netProfit = ref(0.0);
const virtualDay = ref(1);

// === 公司现金账户与破产 ===
const cash = ref(800.0);
const bestRating = ref(0.0);
const isBankrupt = ref(false);
const bankruptSurvivalDays = ref(0);
const bankruptBestRating = ref(0.0);
const bankruptCash = ref(0.0);
const bankruptReason = ref('');
const cashFlash = ref(null);

const cashBarClass = computed(() => {
    if (cash.value < 0) return 'cash-danger';
    if (cash.value < 200) return 'cash-warning';
    return 'cash-normal';
});

// 动态费率配置参数 (用户可在页面随时微调并即时生效)
const showRateConfig = ref(false);
const configTakeRatePercent = ref(25); // 0% ~ 100% (默认 25%)
const configBonusMin = ref(3.0);       // 0 ~ 20 元
const configBonusMax = ref(8.0);       // 0 ~ 20 元
const currentTakeRate = ref(0.25);
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

// === 商家经营参数配置模态窗 ===
const showMerchantModal = ref(false);
const editingMerchant = ref(null);
const merchantForm = reactive({
  minOrderPrice: 15,
  maxOrderPrice: 35,
  materialCostRatePercent: 50,
  rentCost: 100,
  laborCost: 150,
  adSpend: 20,
  dailyFranchiseFee: 50
});

const openMerchantConfig = (m) => {
  editingMerchant.value = m;
  merchantForm.minOrderPrice = m.minOrderPrice || 15;
  merchantForm.maxOrderPrice = m.maxOrderPrice || 35;
  merchantForm.materialCostRatePercent = Math.round((m.materialCostRate || 0.5) * 100);
  merchantForm.rentCost = m.rentCost || 100;
  merchantForm.laborCost = m.laborCost || 150;
  merchantForm.adSpend = m.adSpend || 20;
  merchantForm.dailyFranchiseFee = m.dailyFranchiseFee || 50;
  showMerchantModal.value = true;
};

const saveMerchantConfig = () => {
  if (!editingMerchant.value) return;

  let minP = Math.max(1, Number(merchantForm.minOrderPrice) || 15);
  let maxP = Math.max(minP, Number(merchantForm.maxOrderPrice) || 35);
  let matRate = Math.max(0.1, Math.min(0.9, (Number(merchantForm.materialCostRatePercent) || 50) / 100.0));
  let rent = Math.max(0, Number(merchantForm.rentCost) || 0);
  let labor = Math.max(0, Number(merchantForm.laborCost) || 0);
  let ad = Math.max(0, Number(merchantForm.adSpend) || 0);
  let fran = Math.max(0, Number(merchantForm.dailyFranchiseFee) || 50);

  client.send({
    command: 'UPDATE_MERCHANT_CONFIG',
    merchantId: editingMerchant.value.id,
    minOrderPrice: minP,
    maxOrderPrice: maxP,
    materialCostRate: matRate,
    rentCost: rent,
    laborCost: labor,
    adSpend: ad,
    dailyFranchiseFee: fran
  });

  // 同步本地缓存
  editingMerchant.value.minOrderPrice = minP;
  editingMerchant.value.maxOrderPrice = maxP;
  editingMerchant.value.materialCostRate = matRate;
  editingMerchant.value.rentCost = rent;
  editingMerchant.value.laborCost = labor;
  editingMerchant.value.adSpend = ad;
  editingMerchant.value.dailyFranchiseFee = fran;

  showMerchantModal.value = false;
};

const activeTab = ref('merchants');
const merchants = ref([]);

// 商家列表按编号顺序（merchant-001, merchant-002...）自然排序展示
const sortedMerchants = computed(() => {
  return [...merchants.value].sort((a, b) => {
    return (a.id || '').localeCompare(b.id || '', undefined, { numeric: true, sensitivity: 'base' });
  });
});

// ★ 游戏虚拟时间：固定从 2026年07月01日 00:00:00 开始
const BASE_GAME_TIME = new Date(2026, 6, 1, 0, 0, 0).getTime();
const gameTimeMs = ref(BASE_GAME_TIME);
const isPaused = ref(false);
// 全局倍速 (0.5 / 1 / 2 / 4)
const speedOptions = [0.5, 1, 2, 4];
const speed = ref(1.0);

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

const currentPeakLabel = computed(() => {
  const d = new Date(gameTimeMs.value);
  const h = d.getHours();
  if (h >= 11 && h < 13) return '🔥 午饭最大高峰 (2.5x)';
  if (h >= 17 && h < 19) return '⚡ 晚饭中高峰 (2.0x)';
  if (h >= 6 && h < 9) return '🥐 早饭小高峰 (1.2x)';
  if (h >= 14 && h < 15) return '☕ 下午茶 (1.0x)';
  if (h >= 21 && h < 22) return '🍢 夜宵时段 (1.0x)';
  if (h >= 22 || h < 6) return '🌙 深夜低谷 (0.1x)';
  return '🌱 常规平峰 (0.5x)';
});

let timeInterval = null;
const startTimer = () => {
  if (timeInterval) clearInterval(timeInterval);
  timeInterval = setInterval(() => {
    if (isSimulationStarted.value && !isPaused.value) {
      gameTimeMs.value += 12000;
    }
  }, 100);
};

const mapData = ref(null);
const riders = ref([]);
const orders = ref({});
const selectedOrderId = ref(null);
// 派单模式：AUTO = 全自动派单 / MANUAL = 全手动派单 (页面顶部切换)
const dispatchMode = ref('AUTO');
const isManualDispatch = computed(() => dispatchMode.value === 'MANUAL');
// 全手动模式下点击空闲骑手后进入"前往某地点"待选态
const movingRiderId = ref(null);
// 轻提示
const manualTip = ref(null);
let manualTipTimer = null;

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

const setSpeed = (s) => {
  if (!isConnected.value || !isSimulationStarted.value) return;
  speed.value = s;
  client.send({ command: 'SET_SIMULATION_SPEED', speed: s });
};

const stopSimulation = () => {
  if (confirm('确定要结束当前模拟并重置吗？')) {
    client.send({
      command: 'STOP_SIMULATION'
    });
  }
};

const client = new WebSocketClient();

let cashFloatTimer = null;
const showCashFloat = (text, type) => {
  cashFlash.value = { text, type };
  if (cashFloatTimer) clearTimeout(cashFloatTimer);
  cashFloatTimer = setTimeout(() => { cashFlash.value = null; }, 1600);
};

const restartAfterBankruptcy = () => {
  isBankrupt.value = false;
  isStarting.value = true;
  client.send({
    command: 'START_SIMULATION',
    merchantCount: merchantCount.value,
    riderCount: riderCount.value,
    mapId: selectedMapId.value
  });
};

onMounted(() => {
  startTimer();
  fetchSavedMaps();
  client.onStatusChange = (status) => isConnected.value = status;
  
  client.onSimulationStarted = () => {
    isSimulationStarted.value = true;
    isStarting.value = false;
    isPaused.value = false;
    speed.value = 1.0;
    gameTimeMs.value = BASE_GAME_TIME;
    orders.value = {};
    riders.value = [];
    merchants.value = [];
    selectedOrderId.value = null;
    dispatchMode.value = 'AUTO';
    movingRiderId.value = null;
    manualTip.value = null;
    completedOrderCount.value = 0;
    expiredOrderCount.value = 0;
    unassignedExpiredCount.value = 0;
    mealTimeoutExpiredCount.value = 0;
    const initRev = 50.0 * merchantCount.value;
    const initExp = 0; // 骑手日薪次日 00:00 才结算，开局无支出
    totalRevenue.value = initRev;
    totalExpenses.value = initExp;
    totalFines.value = 0.0;
    netProfit.value = initRev - initExp;
    virtualDay.value = 1;
    cash.value = 800.0 + initRev; // 开局 ¥800 + 第 1 天加盟费收入
    bestRating.value = 0.0;
    isBankrupt.value = false;
    cashFlash.value = null;
  };

  client.onSimulationPaused = () => isPaused.value = true;
  client.onSimulationResumed = () => isPaused.value = false;
  client.onSpeedChanged = (s) => speed.value = s;
  // 派单模式变更广播 (后端确认为准)
  client.onDispatchModeChanged = (mode) => {
    dispatchMode.value = mode === 'MANUAL' ? 'MANUAL' : 'AUTO';
    if (dispatchMode.value === 'AUTO') movingRiderId.value = null;
  };

  client.onSimulationStopped = () => {
    isSimulationStarted.value = false;
    isStarting.value = false;
    isPaused.value = false;
    speed.value = 1.0;
    gameTimeMs.value = BASE_GAME_TIME;
    mapData.value = null;
    orders.value = {};
    riders.value = [];
    merchants.value = [];
    selectedOrderId.value = null;
    dispatchMode.value = 'AUTO';
    movingRiderId.value = null;
    manualTip.value = null;
    completedOrderCount.value = 0;
    expiredOrderCount.value = 0;
    unassignedExpiredCount.value = 0;
    mealTimeoutExpiredCount.value = 0;
    totalRevenue.value = 0.0;
    totalExpenses.value = 0.0;
    totalFines.value = 0.0;
    netProfit.value = 0.0;
    virtualDay.value = 1;
    cash.value = 800.0;
    bestRating.value = 0.0;
    isBankrupt.value = false;
    cashFlash.value = null;
  };

  client.onFinancialUpdate = (data) => {
    if (data) {
      isSimulationStarted.value = true;
      isStarting.value = false;
      totalRevenue.value = data.totalRevenue || 0.0;
      totalExpenses.value = data.totalExpenses || 0.0;
      totalFines.value = data.totalFines || 0.0;
      netProfit.value = data.netProfit || 0.0;
      if (data.virtualDay) virtualDay.value = data.virtualDay;
      if (data.gameVirtualTimeMs) gameTimeMs.value = data.gameVirtualTimeMs;
      if (data.completedOrderCount !== undefined) completedOrderCount.value = data.completedOrderCount;
      if (data.expiredOrderCount !== undefined) expiredOrderCount.value = data.expiredOrderCount;
      if (data.unassignedExpiredCount !== undefined) unassignedExpiredCount.value = data.unassignedExpiredCount;
      if (data.mealTimeoutExpiredCount !== undefined) mealTimeoutExpiredCount.value = data.mealTimeoutExpiredCount;
      if (data.platformTakeRate !== undefined) {
        currentTakeRate.value = data.platformTakeRate;
        if (!showRateConfig.value) configTakeRatePercent.value = Math.round(data.platformTakeRate * 100);
      }
      if (data.riderBonusMin !== undefined) {
        currentBonusMin.value = data.riderBonusMin;
        if (!showRateConfig.value) configBonusMin.value = data.riderBonusMin;
      }
      if (data.riderBonusMax !== undefined) {
        currentBonusMax.value = data.riderBonusMax;
        if (!showRateConfig.value) configBonusMax.value = data.riderBonusMax;
      }
      if (data.cash !== undefined) {
        const prev = cash.value;
        const next = Number(data.cash) || 0;
        cash.value = next;
        const delta = Math.round((next - prev) * 100) / 100;
        if (delta > 0.005) showCashFloat('+' + delta.toFixed(2), 'up');
        else if (delta < -0.005) showCashFloat(delta.toFixed(2), 'down');
      }
      if (data.bestRating !== undefined) bestRating.value = data.bestRating || 0.0;
    }
  };

  client.onBankruptcy = (data) => {
    if (data) {
      bankruptSurvivalDays.value = data.survivalDays || 0;
      bankruptBestRating.value = data.bestRating || 0.0;
      bankruptCash.value = data.cash || 0.0;
      bankruptReason.value = data.reason || '公司现金账户为负';
      cash.value = data.cash || 0.0;
      isStarting.value = false;
      isBankrupt.value = true;
    }
  };

  client.onMapData = (data) => {
    mapData.value = data;
    if (data && data.merchants) merchants.value = data.merchants;
    if (data && (data.roads || (data.grid && data.grid.length > 0))) {
      isSimulationStarted.value = true;
      isStarting.value = false;
    }
  };

  client.onOrdersSync = (data) => {
    if (Array.isArray(data)) {
      const newOrders = { ...orders.value };
      data.forEach(o => { newOrders[o.id] = o; });
      orders.value = newOrders;
      isSimulationStarted.value = true;
      isStarting.value = false;
    }
  };
  
  client.onMerchantUpdate = (data) => {
    merchants.value = data;
    if (data && data.length > 0) {
      isSimulationStarted.value = true;
      isStarting.value = false;
    }
  };

  client.onRiderUpdate = (data) => {
    riders.value = data;
    if (data && data.length > 0) {
      isSimulationStarted.value = true;
      isStarting.value = false;
    }
  };
  
  client.onOrderEvent = (type, data) => {
    if (type === 'ORDER_CREATED') {
      orders.value[data.id] = data;
    } else if (type === 'RIDER_ASSIGNED') {
      if (data.currentOrderId && orders.value[data.currentOrderId]) {
        orders.value[data.currentOrderId].status = 1;
      }
      // 派单成功：该骑手退出"前往某地点"待选态
      if (data.id && movingRiderId.value === data.id) {
        movingRiderId.value = null;
      }
      // 成功派单的订单若正被选中，清除选中以便继续处理其他订单
      if (data.currentOrderId && data.currentOrderId === selectedOrderId.value) {
        selectedOrderId.value = null;
      }
    } else if (type === 'ORDER_STATUS_CHANGED') {
      if (orders.value[data.id]) {
        orders.value[data.id].status = data.status;
        if (data.waitDurationSec !== undefined) {
          orders.value[data.id].waitDurationSec = data.waitDurationSec;
        }
      }
      // 订单进入取完餐/配送阶段后不可改派：若正选中则清选中并解锁
      if (data.status >= 2 && selectedOrderId.value === data.id) {
        selectOrder(null);
      }
    } else if (type === 'ORDER_COMPLETED') {
      if (data.completedCount !== undefined) {
        completedOrderCount.value = data.completedCount;
      } else {
        completedOrderCount.value++;
      }
      delete orders.value[data.id];
      if (selectedOrderId.value === data.id) selectOrder(null);
    } else if (type === 'ORDER_EXPIRED') {
      if (data.expiredCount !== undefined) {
        expiredOrderCount.value = data.expiredCount;
      } else {
        expiredOrderCount.value++;
      }
      if (data.unassignedExpiredCount !== undefined) {
        unassignedExpiredCount.value = data.unassignedExpiredCount;
      }
      if (data.mealTimeoutExpiredCount !== undefined) {
        mealTimeoutExpiredCount.value = data.mealTimeoutExpiredCount;
      }
      delete orders.value[data.id];
      if (selectedOrderId.value === data.id) selectOrder(null);
    }
  };

  // 手动派单/调度失败轻提示
  client.onManualAssignFailed = (data) => {
    const reasonMap = {
      'MODE_NOT_MANUAL': '当前为全自动派单模式，请先切换到全手动派单',
      'ORDER_NOT_FOUND': '未找到该订单，请重试',
      'ORDER_NOT_ASSIGNABLE': '该订单已进入配送流程，无法改派',
      'RIDER_NOT_FOUND': '未找到该骑手，请重试',
      'RIDER_NOT_IDLE': '该骑手已非空闲状态，请选择其他空闲骑手'
    };
    showManualTip(reasonMap[data && data.reason] || '操作失败，请重试');
  };
  
  // ESC 取消"前往某地点"待选态；卸载时清理监听与计时器
  window.addEventListener('keydown', handleEscKey);
  onBeforeUnmount(() => {
    window.removeEventListener('keydown', handleEscKey);
    if (manualTipTimer) clearTimeout(manualTipTimer);
  });

  client.connect();
});

// 选中订单 (地图或侧边栏)
const selectOrder = (orderId) => {
  selectedOrderId.value = orderId;
};

const onMapOrderSelected = (orderId) => { selectOrder(orderId); };

// 切换派单模式 (顶部：全自动 / 全手动)。立即本地更新，由 DISPATCH_MODE_CHANGED 广播校正。
const setDispatchMode = (mode) => {
  if (!isSimulationStarted.value) return;
  const normalized = mode === 'MANUAL' ? 'MANUAL' : 'AUTO';
  client.send({ command: 'SET_DISPATCH_MODE', mode: normalized });
  dispatchMode.value = normalized;
  if (normalized === 'AUTO') movingRiderId.value = null;
};

// 点击空闲骑手 -> 派单 (成功结果通过 RIDER_ASSIGNED 广播回传后再清选中)
const onRiderSelected = (riderId) => {
  if (!selectedOrderId.value) return;
  client.send({ command: 'MANUAL_ASSIGN', orderId: selectedOrderId.value, riderId });
};

// 点击空闲骑手 -> 进入/退出"前往某地点"待选态 (再点地图任意位置下发前往指令)
const onRiderMoveSelected = (riderId) => {
  movingRiderId.value = (movingRiderId.value === riderId) ? null : riderId;
};

// 点击地图目标点 -> 下发骑手前往指令
const onRiderMoveTarget = (payload) => {
  if (!payload || !payload.riderId) return;
  client.send({ command: 'MOVE_RIDER', riderId: payload.riderId, x: payload.x, y: payload.y });
  movingRiderId.value = null;
};

// ESC 取消"前往某地点"待选态
const handleEscKey = (e) => {
  if (e.key === 'Escape') movingRiderId.value = null;
};

const showManualTip = (text) => {
  manualTip.value = text;
  if (manualTipTimer) clearTimeout(manualTipTimer);
  manualTipTimer = setTimeout(() => { manualTip.value = null; }, 2400);
};

const selectedOrder = computed(() => {
  if (!selectedOrderId.value) return null;
  return orders.value[selectedOrderId.value] || null;
});

const assignedRider = computed(() => {
  if (!selectedOrderId.value || !riders.value.length) return null;
  return riders.value.find(r => r.currentOrderId === selectedOrderId.value) || null;
});

const sortedOrders = computed(() => {
  return Object.values(orders.value).sort((a, b) => b.createTime - a.createTime);
});

const idleRidersCount = computed(() => riders.value.filter(r => r.status === 0).length);
const pickingRidersCount = computed(() => riders.value.filter(r => r.status === 1).length);
const deliveringRidersCount = computed(() => riders.value.filter(r => r.status === 2).length);
const pendingOrdersCount = computed(() => Object.values(orders.value).filter(o => o.status === 0).length);
const dispatchedOrdersCount = computed(() => Object.values(orders.value).filter(o => o.status === 1).length);
const mealWaitingOrdersCount = computed(() => Object.values(orders.value).filter(o => o.status === 2).length);
const deliveringOrdersCount = computed(() => Object.values(orders.value).filter(o => o.status === 3).length);

const getStatusText = (status) => {
  switch(status) {
    case 0: return '🍳 备餐待派';
    case 1: return '🚴 骑手到店中';
    case 2: return '⏳ 在店等餐中';
    case 3: return '🚀 送餐配送中';
    case 4: return '✅ 订单已送达';
    case 5: return '⚠️ 等餐超时失效';
    default: return '未知';
  }
};

const getRiderStatusText = (status) => {
  switch(status) {
    case 0: return '空闲巡游';
    case 1: return '前往商家';
    case 2: return '送餐配送';
    default: return '未知';
  }
};

const getRiderStatusClass = (status) => {
  switch(status) {
    case 0: return 'rider-status-idle';
    case 1: return 'rider-status-picking';
    case 2: return 'rider-status-delivering';
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
  overflow: hidden;
}

/* 启动弹窗 */
.start-modal-overlay, .merchant-modal-overlay {
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
.start-modal h2 { margin: 0; font-size: 1.5rem; color: var(--primary-color); text-align: center; }
.modal-desc { text-align: center; color: #aaa; margin: 0 0 8px 0; font-size: 0.9rem; }
.form-group { display: flex; flex-direction: column; gap: 6px; }
.form-group label { font-size: 0.9rem; font-weight: 500; }
.form-group input, .map-dropdown { padding: 10px; border-radius: 8px; border: 1px solid rgba(255, 255, 255, 0.2); background: rgba(0, 0, 0, 0.3); color: #fff; font-size: 0.95rem; }
.form-group input:focus, .map-dropdown:focus { outline: none; border-color: var(--primary-color); }
.map-dropdown option { background: #0f172a; color: #fff; }
.modal-actions { display: flex; flex-direction: column; gap: 10px; margin-top: 8px; }

.btn-start { padding: 12px; border-radius: 8px; background: var(--primary-color); color: #fff; border: none; font-size: 1.1rem; font-weight: bold; cursor: pointer; transition: all 0.2s; }
.btn-start:hover:not(:disabled) { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(100, 108, 255, 0.4); }
.btn-start:disabled { background: #555; cursor: not-allowed; opacity: 0.7; }

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

/* 商家配置弹窗 */
.merchant-modal {
  width: 480px;
  max-width: 90vw;
  padding: 24px;
  border-radius: 16px;
  background: rgba(15, 23, 42, 0.95);
  border: 1px solid rgba(59, 130, 246, 0.4);
  box-shadow: 0 10px 30px rgba(0,0,0,0.6);
  display: flex;
  flex-direction: column;
  gap: 16px;
  animation: fadeIn 0.2s ease;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  padding-bottom: 10px;
}
.modal-header h3 { margin: 0; color: #60a5fa; font-size: 16px; }
.modal-close-btn { background: transparent; border: none; color: #94a3b8; font-size: 16px; cursor: pointer; }
.modal-close-btn:hover { color: #fff; }

.merchant-form-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 60vh;
  overflow-y: auto;
  padding-right: 6px;
}
.form-section-title { font-size: 13px; font-weight: 700; color: #93c5fd; margin-top: 4px; border-left: 3px solid #3b82f6; padding-left: 8px; }
.form-row-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.form-field { display: flex; flex-direction: column; gap: 4px; }
.form-field label { font-size: 11px; color: #cbd5e1; }
.form-field input[type="number"] { padding: 8px 10px; background: rgba(0, 0, 0, 0.4); border: 1px solid rgba(255, 255, 255, 0.15); border-radius: 6px; color: #fff; font-family: monospace; font-size: 13px; }
.form-field input[type="number"]:focus { outline: none; border-color: #3b82f6; }
.slider-input { width: 100%; accent-color: #3b82f6; cursor: pointer; }
.highlight-val { color: #38bdf8; font-weight: 700; }
.field-tip { font-size: 10px; color: #64748b; line-height: 1.4; background: rgba(0, 0, 0, 0.2); padding: 4px 8px; border-radius: 4px; }
.modal-footer { display: flex; justify-content: flex-end; gap: 12px; border-top: 1px solid rgba(255, 255, 255, 0.1); padding-top: 12px; }
.btn-cancel { padding: 8px 16px; background: rgba(255, 255, 255, 0.1); border: 1px solid rgba(255, 255, 255, 0.2); border-radius: 6px; color: #cbd5e1; cursor: pointer; }
.btn-save { padding: 8px 18px; background: #2563eb; border: 1px solid #3b82f6; border-radius: 6px; color: #fff; font-weight: 700; cursor: pointer; transition: all 0.2s; }
.btn-save:hover { background: #1d4ed8; }

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
.btn-editor-entry:hover { background: rgba(59, 130, 246, 0.3); transform: translateY(-1px); }

.map-section {
  flex: 3;
  display: flex;
  flex-direction: column;
  padding: 16px;
  min-width: 0;
}

.header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; flex-wrap: wrap; gap: 12px; }
.header-left { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.header h2 { font-size: 18px; font-weight: 600; margin: 0; }

.game-time-badge {
  display: flex;
  align-items: center;
  gap: 8px;
  background: rgba(16, 185, 129, 0.12);
  border: 1px solid rgba(16, 185, 129, 0.3);
  padding: 5px 12px;
  border-radius: 20px;
  font-size: 12px;
  color: #34d399;
  transition: all 0.3s ease;
}
.game-time-badge.paused { background: rgba(239, 68, 68, 0.12); border-color: rgba(239, 68, 68, 0.3); color: #f87171; }
.time-label { color: var(--text-secondary); font-size: 11px; }
.time-val { font-weight: 700; font-size: 13px; letter-spacing: 0.5px; }
.peak-badge { background: rgba(245, 158, 11, 0.2); color: #fbbf24; padding: 2px 6px; border-radius: 10px; font-size: 11px; font-weight: 600; }

.header-actions { display: flex; align-items: center; gap: 10px; }
.btn-pause { padding: 6px 12px; background: rgba(245, 158, 11, 0.15); border: 1px solid rgba(245, 158, 11, 0.4); color: #fbbf24; border-radius: 6px; font-size: 12px; font-weight: 600; cursor: pointer; transition: all 0.2s ease; }
.btn-pause:hover { background: rgba(245, 158, 11, 0.3); }
.btn-stop { padding: 6px 12px; background: rgba(239, 68, 68, 0.15); border: 1px solid rgba(239, 68, 68, 0.4); color: #f87171; border-radius: 6px; font-size: 12px; font-weight: 600; cursor: pointer; transition: all 0.2s ease; }
.btn-stop:hover { background: rgba(239, 68, 68, 0.3); color: #fff; }
.dispatch-mode-control { display: flex; align-items: center; gap: 2px; padding: 2px; background: rgba(148, 163, 184, 0.1); border: 1px solid rgba(148, 163, 184, 0.3); border-radius: 6px; }
.btn-mode { padding: 4px 10px; background: transparent; border: none; color: #cbd5e1; border-radius: 4px; font-size: 12px; font-weight: 600; cursor: pointer; transition: all 0.2s ease; }
.btn-mode:hover { background: rgba(148, 163, 184, 0.2); }
.btn-mode.active { background: rgba(34, 197, 94, 0.35); color: #fff; box-shadow: 0 0 6px rgba(34, 197, 94, 0.5); }

.manual-tip-toast {
  position: fixed;
  top: 84px;
  left: 50%;
  transform: translateX(-50%);
  background: rgba(15, 23, 42, 0.95);
  border: 1px solid rgba(239, 68, 68, 0.5);
  color: #fca5a5;
  padding: 10px 18px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.5);
  z-index: 9998;
}
.tip-enter-active, .tip-leave-active { transition: all 0.3s ease; }
.tip-enter-from, .tip-leave-to { opacity: 0; transform: translateX(-50%) translateY(-8px); }
.speed-control { display: flex; align-items: center; gap: 2px; padding: 2px; background: rgba(56, 189, 248, 0.08); border: 1px solid rgba(56, 189, 248, 0.3); border-radius: 6px; }
.btn-speed { padding: 4px 8px; background: transparent; border: none; color: #7dd3fc; border-radius: 4px; font-size: 12px; font-weight: 600; cursor: pointer; transition: all 0.2s ease; }
.btn-speed:hover { background: rgba(56, 189, 248, 0.2); }
.btn-speed.active { background: rgba(56, 189, 248, 0.35); color: #fff; box-shadow: 0 0 6px rgba(56, 189, 248, 0.5); }

.status-indicator { display: flex; align-items: center; gap: 6px; font-size: 12px; color: var(--text-secondary); }
.dot { width: 8px; height: 8px; border-radius: 50%; background: var(--danger); box-shadow: 0 0 6px var(--danger); }
.dot.connected { background: var(--success); box-shadow: 0 0 6px var(--success); }

.map-wrapper { flex: 1; min-height: 0; border-radius: 12px; overflow: hidden; box-shadow: inset 0 0 20px rgba(0,0,0,0.5); }

/* 控制面板 */
.control-panel { width: 440px; padding: 12px; display: flex; flex-direction: column; gap: 10px; height: 100vh; box-sizing: border-box; overflow-y: auto; }
.stats-card, .detail-card, .tabs-container { padding: 12px; border-radius: 12px; display: flex; flex-direction: column; }
.panel-header-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; border-bottom: 1px solid var(--panel-border); padding-bottom: 6px; }
.panel-header-row h3 { font-size: 14px; margin: 0 !important; }
.btn-config-toggle { background: rgba(59, 130, 246, 0.15); border: 1px solid rgba(59, 130, 246, 0.4); color: #93c5fd; font-size: 11px; font-weight: 600; padding: 3px 8px; border-radius: 6px; cursor: pointer; transition: all 0.2s; }
.btn-config-toggle:hover, .btn-config-toggle.active { background: rgba(59, 130, 246, 0.35); border-color: #60a5fa; color: #fff; }

.rate-config-drawer { background: rgba(0, 0, 0, 0.4); border: 1px solid rgba(59, 130, 246, 0.25); border-radius: 8px; padding: 8px; margin-bottom: 8px; animation: fadeIn 0.2s ease; }
.config-title { font-size: 11px; font-weight: 700; color: #93c5fd; margin-bottom: 6px; }
.config-form-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 6px; margin-bottom: 8px; }
.config-field { display: flex; flex-direction: column; gap: 2px; }
.config-field label { font-size: 10px; color: #94a3b8; white-space: nowrap; }
.input-unit-wrap { display: flex; align-items: center; background: rgba(255, 255, 255, 0.08); border: 1px solid rgba(255, 255, 255, 0.15); border-radius: 4px; padding: 2px 4px; }
.input-unit-wrap input { width: 100%; background: transparent; border: none; color: #fff; font-size: 12px; font-weight: 700; font-family: monospace; outline: none; }
.input-unit-wrap .unit { font-size: 10px; color: #94a3b8; margin-left: 2px; }
.field-hint { font-size: 9px; color: #64748b; }
.config-action-row { display: flex; align-items: center; justify-content: space-between; }
.btn-save-config { padding: 4px 10px; background: #2563eb; border: 1px solid #3b82f6; border-radius: 4px; color: #fff; font-size: 11px; font-weight: 700; cursor: pointer; }
.save-success-tip { font-size: 11px; color: #34d399; font-weight: 600; }

.financial-panel { background: rgba(15, 23, 42, 0.75); border: 1px solid rgba(59, 130, 246, 0.3); }
.financial-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 6px; }
.realtime-stats-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 5px; }
.fin-item { background: rgba(0, 0, 0, 0.35); padding: 6px 8px; border-radius: 8px; border: 1px solid rgba(255, 255, 255, 0.05); }
.fin-item .value { font-size: 16px !important; font-family: monospace; font-weight: 700; margin: 1px 0; }
.sub-label { font-size: 9px; color: #64748b; }
.text-revenue { color: #22c55e; }
.text-expense { color: #f59e0b; }
.text-fines { color: #ef4444; }
.text-profit-pos { color: #10b981; font-weight: 800; }
.text-profit-neg { color: #f87171; font-weight: 800; }

.stat-item { display: flex; flex-direction: column; background: rgba(0,0,0,0.2); padding: 6px 4px; border-radius: 6px; text-align: center; }
.stat-item .label { font-size: 10px; color: var(--text-secondary); margin-bottom: 2px; white-space: nowrap; }
.stat-item .value { font-size: 15px; font-weight: 700; }

.text-accent { color: var(--accent-color); }
.text-warning { color: var(--warning); }
.text-success { color: var(--success); }
.text-danger { color: var(--danger); }
.text-picking { color: #38bdf8; }
.text-meal-wait { color: #fb923c; font-weight: 700; }
.text-delivering { color: #3b82f6; }
.text-completed { color: #10b981; }
.text-sub-expire { color: #f87171; font-weight: 700; }
.stat-item-danger { background: rgba(239, 68, 68, 0.15) !important; border: 1px solid rgba(239, 68, 68, 0.3); }
.stat-item-sub-fail { background: rgba(244, 63, 94, 0.08) !important; border: 1px dashed rgba(244, 63, 94, 0.25); }

/* 详情卡片 */
.detail-card { border: 1px solid rgba(59, 130, 246, 0.3); animation: fadeIn 0.3s ease; }
.detail-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; border-bottom: 1px solid var(--panel-border); padding-bottom: 6px; }
.detail-header h3 { font-size: 13px; margin: 0; }
.close-btn { width: 20px; height: 20px; border-radius: 50%; background: rgba(255,255,255,0.1); border: none; color: #fff; font-size: 11px; cursor: pointer; }
.detail-body { display: flex; flex-direction: column; gap: 6px; }
.detail-row { display: flex; justify-content: space-between; align-items: center; font-size: 12px; }
.highlight-row { background: rgba(239, 68, 68, 0.1); padding: 4px 6px; border-radius: 4px; border: 1px dashed rgba(239, 68, 68, 0.3); }

/* Tab页签与内容 */
.tabs-container { flex: 1; min-height: 0; display: flex; flex-direction: column; padding: 10px; }
.tabs-header { display: flex; gap: 6px; border-bottom: 1px solid var(--panel-border); padding-bottom: 6px; margin-bottom: 8px; }
.tab-btn { flex: 1; background: rgba(255, 255, 255, 0.05); border: 1px solid transparent; color: #94a3b8; padding: 6px 4px; border-radius: 6px; font-size: 11px; font-weight: 600; cursor: pointer; transition: all 0.2s; white-space: nowrap; }
.tab-btn:hover { background: rgba(255, 255, 255, 0.1); color: #fff; }
.tab-btn.active { background: rgba(59, 130, 246, 0.25); border-color: rgba(59, 130, 246, 0.5); color: #93c5fd; }
.tab-body { flex: 1; min-height: 0; overflow-y: auto; }

.merchant-list, .order-list, .rider-list { display: flex; flex-direction: column; gap: 8px; padding-right: 4px; }
.merchant-item, .order-item, .rider-item { background: rgba(255, 255, 255, 0.03); border: 1px solid var(--panel-border); border-radius: 8px; padding: 10px; transition: all 0.2s; }
.merchant-item:hover, .order-item:hover, .rider-item:hover { background: rgba(255, 255, 255, 0.06); }

.merchant-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.m-title-group { display: flex; align-items: center; gap: 8px; }
.merchant-name { font-weight: 700; color: #f97316; font-size: 13px; }
.merchant-rating { font-size: 12px; color: #fbbf24; font-weight: 600; }
.btn-edit-m { padding: 3px 8px; background: rgba(59, 130, 246, 0.15); border: 1px solid rgba(59, 130, 246, 0.4); color: #93c5fd; border-radius: 4px; font-size: 11px; font-weight: 600; cursor: pointer; transition: all 0.2s; }
.btn-edit-m:hover { background: rgba(59, 130, 246, 0.35); color: #fff; }

.merchant-param-tags { display: flex; gap: 4px; flex-wrap: wrap; margin-bottom: 6px; }
.m-tag { background: rgba(255, 255, 255, 0.06); color: #cbd5e1; font-size: 10px; padding: 1px 5px; border-radius: 3px; font-family: monospace; }
.merchant-financial-row, .rider-salary-row { display: flex; gap: 4px; flex-wrap: wrap; margin-bottom: 6px; }
.fin-tag, .salary-tag { font-size: 10px; padding: 2px 5px; border-radius: 4px; font-weight: 600; font-family: monospace; }

.tag-rev { background: rgba(34, 197, 94, 0.1); color: #4ade80; border: 1px solid rgba(34, 197, 94, 0.2); }
.tag-take { background: rgba(245, 158, 11, 0.1); color: #fbbf24; }
.tag-mat { background: rgba(168, 85, 247, 0.1); color: #c084fc; }
.tag-fixed { background: rgba(100, 116, 139, 0.15); color: #94a3b8; }
.tag-ad { background: rgba(236, 72, 153, 0.1); color: #f472b6; }
.tag-comm { background: rgba(234, 179, 8, 0.1); color: #facc15; }
.tag-fines { background: rgba(239, 68, 68, 0.15); color: #f87171; border: 1px solid rgba(239, 68, 68, 0.3); }
.tag-income { background: rgba(59, 130, 246, 0.12); border: 1px solid rgba(59, 130, 246, 0.3); }

.tag-ongoing { background: rgba(59, 130, 246, 0.15); color: #93c5fd; }
.tag-completed { background: rgba(34, 197, 94, 0.15); color: #86efac; }
.merchant-coord { font-size: 10px; color: #64748b; margin-left: auto; }
.merchant-details { display: flex; align-items: center; gap: 6px; font-size: 11px; }

/* 订单卡片 */
.order-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.order-id { font-size: 12px; font-weight: 700; color: #fff; }
.order-status-badge { font-size: 10px; font-weight: 700; padding: 2px 6px; border-radius: 4px; }
.badge-status-0 { background: rgba(244, 63, 94, 0.15); color: #fda4af; }
.badge-status-1 { background: rgba(251, 191, 36, 0.15); color: #fcd34d; }
.badge-status-2 { background: rgba(249, 115, 22, 0.2); color: #fdba74; border: 1px solid rgba(249, 115, 22, 0.4); }
.badge-status-3 { background: rgba(59, 130, 246, 0.15); color: #93c5fd; }
.badge-status-4 { background: rgba(34, 197, 94, 0.15); color: #86efac; }
.badge-status-5 { background: rgba(239, 68, 68, 0.2); color: #f87171; }

.order-meta-row { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 4px; font-size: 11px; }
.order-price-badge { color: #4ade80; font-weight: 700; font-family: monospace; }
.order-mat-badge { color: #c084fc; font-family: monospace; }
.order-prep-badge { color: #fca5a5; font-size: 10px; }
.order-wait-badge { color: #f97316; font-weight: 700; font-size: 11px; }
.order-detail { display: flex; justify-content: space-between; font-size: 11px; color: #94a3b8; }

/* 骑手卡片 */
.rider-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.rider-id { font-weight: 700; color: #38bdf8; font-size: 12px; }
.rider-details { display: flex; gap: 8px; font-size: 10px; color: #94a3b8; flex-wrap: wrap; }
.status-badge { font-size: 10px; padding: 2px 6px; border-radius: 4px; font-weight: 600; }
.rider-status-idle { background: rgba(34, 197, 94, 0.15); color: #4ade80; }
.rider-status-picking { background: rgba(56, 189, 248, 0.15); color: #38bdf8; }
.rider-status-delivering { background: rgba(59, 130, 246, 0.2); color: #93c5fd; font-weight: 700; }
.salary-tag { background: rgba(255, 255, 255, 0.05); color: #e2e8f0; }
.tag-bonus { background: rgba(56, 189, 248, 0.12); color: #38bdf8; }
.tag-total { background: rgba(16, 185, 129, 0.15); color: #34d399; font-weight: 700; }
.empty-state { text-align: center; color: #64748b; font-size: 12px; padding: 20px; }
.text-green { color: #34d399; }
.text-red { color: #f87171; }
.font-bold { font-weight: 700; }

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(-8px); }
  to { opacity: 1; transform: translateY(0); }
}

/* === 公司现金栏 === */
.cash-bar {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 14px;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 700;
  border: 1px solid var(--panel-border);
  background: rgba(255, 255, 255, 0.04);
  transition: all 0.3s;
  position: relative;
}
.cash-normal { background: rgba(16, 185, 129, 0.08); border-color: rgba(16, 185, 129, 0.35); }
.cash-normal .cash-value { color: #34d399; }
.cash-warning { background: rgba(245, 158, 11, 0.12); border-color: rgba(245, 158, 11, 0.55); }
.cash-warning .cash-value { color: #fbbf24; }
.cash-danger { background: rgba(239, 68, 68, 0.16); border-color: rgba(239, 68, 68, 0.6); }
.cash-danger .cash-value { color: #f87171; }
.cash-label { color: #cbd5e1; font-weight: 600; }
.cash-value { font-size: 15px; letter-spacing: 0.5px; }
.cash-warn-tag { font-size: 10px; color: #fbbf24; }
.cash-danger-tag { font-size: 10px; color: #f87171; }

.cash-float-text {
  position: absolute;
  left: 50%;
  top: -22px;
  transform: translateX(-50%);
  font-size: 13px;
  font-weight: 800;
  pointer-events: none;
  white-space: nowrap;
}
.cash-float-text.up { color: #34d399; }
.cash-float-text.down { color: #f87171; }
.cash-float-enter-active, .cash-float-leave-active { transition: all 0.5s ease; }
.cash-float-enter-from, .cash-float-leave-to { opacity: 0; transform: translateX(-50%) translateY(10px); }

/* === 破产结算画面 === */
.bankrupt-overlay {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(10, 10, 14, 0.9);
  backdrop-filter: blur(10px);
  z-index: 10000;
  display: flex;
  align-items: center;
  justify-content: center;
}
.bankrupt-panel {
  padding: 36px;
  border-radius: 18px;
  width: 420px;
  max-width: 90vw;
  display: flex;
  flex-direction: column;
  gap: 18px;
  border: 1px solid rgba(239, 68, 68, 0.5);
  box-shadow: 0 0 40px rgba(239, 68, 68, 0.25);
  background: rgba(15, 23, 42, 0.96);
  animation: fadeIn 0.3s ease;
}
.bankrupt-panel h2 { margin: 0; text-align: center; color: #f87171; font-size: 1.6rem; }
.bankrupt-sub { text-align: center; color: #cbd5e1; margin: 0; font-size: 0.9rem; }
.bankrupt-stats { display: flex; flex-direction: column; gap: 12px; }
.bs-row { display: flex; justify-content: space-between; align-items: center; padding: 10px 12px; background: rgba(255, 255, 255, 0.04); border-radius: 10px; }
.bs-label { color: #94a3b8; font-size: 13px; }
.bs-value { color: #fff; font-size: 14px; font-weight: 700; }
</style>
