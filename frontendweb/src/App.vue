<template>
  <div class="dashboard-wrapper">
    <!-- 地图编辑器视图 -->
    <MapEditor
      v-if="currentView === 'editor'"
      @close="currentView = 'simulation'"
      @mapSaved="fetchSavedMaps"
    />

    <!-- 经营主控制台视图 -->
    <div v-else class="dashboard">
      <!-- 启动配置弹窗 -->
      <div v-if="!isSimulationStarted" class="start-modal-overlay">
        <div class="start-modal glass-panel">
          <h2>🏞️ 山间民宿度假村经营模拟</h2>
          <p class="modal-desc">挂机经营 · 逐日结算 · 淡旺季与随机事件</p>

          <div class="form-group">
            <label>运行地图选择</label>
            <select v-model="selectedMapId" class="map-dropdown">
              <option value="random">🎲 随机生成全新山地地图</option>
              <option v-for="m in savedMaps" :key="m.id" :value="m.id">
                🗺️ {{ m.name }} ({{ m.roadCount }} 道路格)
              </option>
            </select>
          </div>

          <div class="modal-actions">
            <button class="btn-start" @click="startGame" :disabled="!isConnected || isStarting">
              {{ !isConnected ? '等待服务器连接...' : (isStarting ? '开局中...' : '🏁 开始经营') }}
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
          <h2>💥 度假村破产</h2>
          <p class="bankrupt-sub">资金耗尽，资不抵债，本局结束。</p>
          <div class="bankrupt-stats">
            <div class="bs-row"><span class="bs-label">📅 存活天数</span><span class="bs-value">{{ bankruptcy.daysSurvived }} 天</span></div>
            <div class="bs-row"><span class="bs-label">💰 最终资金</span><span class="bs-value text-red">¥{{ fmtMoney(bankruptcy.finalCash) }}</span></div>
            <div class="bs-row"><span class="bs-label">⚠️ 破产原因</span><span class="bs-value">{{ bankruptcy.endReason }}</span></div>
          </div>

          <!-- 末日收支明细：让玩家看清钱花在哪 -->
          <div v-if="bankruptcy.lastDay" class="bankrupt-breakdown">
            <div class="bb-title">第 {{ bankruptcy.lastDay }} 天（末日）收支明细</div>
            <div class="bb-cols">
              <div class="bb-col">
                <div class="bb-head">收入</div>
                <div class="bb-row"><span>🛏️ 客房</span><span>¥{{ fmtMoney(bankruptcy.revenueDetail && bankruptcy.revenueDetail.roomFee) }}</span></div>
                <div class="bb-row"><span>🍽️ 餐饮</span><span>¥{{ fmtMoney(bankruptcy.revenueDetail && bankruptcy.revenueDetail.dining) }}</span></div>
                <div class="bb-row"><span>🎡 游乐</span><span>¥{{ fmtMoney(bankruptcy.revenueDetail && bankruptcy.revenueDetail.recreation) }}</span></div>
                <div class="bb-row"><span>🎉 团建</span><span>¥{{ fmtMoney(bankruptcy.revenueDetail && bankruptcy.revenueDetail.teamOrder) }}</span></div>
                <div class="bb-row bb-total"><span>收入合计</span><span>¥{{ fmtMoney(bankruptcy.lastTotalRevenue) }}</span></div>
              </div>
              <div class="bb-col">
                <div class="bb-head">支出</div>
                <div class="bb-row"><span>👷 工资</span><span>¥{{ fmtMoney(bankruptcy.expenseDetail && bankruptcy.expenseDetail.staffSalary) }}</span></div>
                <div class="bb-row"><span>🏢 维护</span><span>¥{{ fmtMoney(bankruptcy.expenseDetail && bankruptcy.expenseDetail.buildingMaintenance) }}</span></div>
                <div class="bb-row"><span>📣 广告</span><span>¥{{ fmtMoney(bankruptcy.expenseDetail && bankruptcy.expenseDetail.adBudget) }}</span></div>
                <div class="bb-row"><span>⚠️ 事件</span><span>¥{{ fmtMoney(bankruptcy.expenseDetail && bankruptcy.expenseDetail.eventCost) }}</span></div>
                <div class="bb-row"><span>🚫 团建罚款</span><span>¥{{ fmtMoney(bankruptcy.expenseDetail && bankruptcy.expenseDetail.teamPenalty) }}</span></div>
                <div class="bb-row bb-total"><span>支出合计</span><span>¥{{ fmtMoney(bankruptcy.lastTotalExpense) }}</span></div>
              </div>
            </div>
            <div class="bb-net">
              当日起始资金 ¥{{ fmtMoney(bankruptcy.lastCashBefore) }} · 当日净利
              <span :class="bankruptcy.lastNetProfit >= 0 ? 'text-green' : 'text-red'">{{ bankruptcy.lastNetProfit >= 0 ? '+' : '' }}¥{{ fmtMoney(bankruptcy.lastNetProfit) }}</span>
            </div>
          </div>

          <!-- 本局每日结算日志（内嵌，可滚动，最多近 365 天） -->
          <div v-if="bankruptcy.dailyReports && bankruptcy.dailyReports.length" class="bankrupt-log">
            <div class="bb-title">本局每日结算日志（共 {{ bankruptcy.dailyReports.length }} 天）</div>
            <div class="bankrupt-log-body">
              <div v-for="d in bankruptcy.dailyReports" :key="d.day" class="blog-row">
                <span class="blog-day">第 {{ d.day }} 天</span>
                <span :class="d.netProfit >= 0 ? 'text-green' : 'text-red'">净利 {{ d.netProfit >= 0 ? '+' : '-' }}¥{{ fmtMoney(Math.abs(d.netProfit)) }}</span>
                <span class="blog-cash">资金 ¥{{ fmtMoney(d.cashAfter) }}</span>
                <span class="blog-rep">⭐ {{ (d.reputationAfter || 0).toFixed(1) }}</span>
              </div>
            </div>
          </div>

          <button class="btn-start" @click="restartGame">🔄 重新开局</button>
        </div>
      </div>

      <!-- 左侧主地图区域 -->
      <div class="map-section glass-panel">
        <div class="header">
          <div class="header-left">
            <!-- 全局状态条 -->
            <div v-if="isSimulationStarted" class="status-bar">
              <div class="sb-row">
                <span class="sb-item">📅 第 {{ resortState.day }} 天</span>
                <span class="sb-item sb-season">{{ seasonLabel }}</span>
                <span class="sb-item sb-clock" :class="{ 'sb-clock-idle': !clockAdvancing }" title="当日游戏时刻">
                  🕘 {{ clockText }}
                </span>
                <span class="sb-item sb-cash">💰 ¥{{ fmtMoney(resortState.cash) }}</span>
                <span class="sb-item sb-rep">⭐ {{ (resortState.reputation || 0).toFixed(1) }}</span>
                <span class="sb-phase phase-running">{{ phaseText }}</span>
              </div>
              <!-- 当日进度条：00:00 → 24:00 -->
              <div class="sb-progress">
                <div class="sb-progress-fill" :style="{ width: (dayProgress * 100).toFixed(2) + '%' }"></div>
              </div>
            </div>
            <h2 v-else>山间民宿度假村经营模拟</h2>
          </div>
          <div class="header-actions">
            <button v-if="!isSimulationStarted" class="btn-editor-entry" @click="currentView = 'editor'" title="打开地图编辑器">
              🎨 地图编辑器
            </button>
            <template v-if="isSimulationStarted">
              <button class="btn-report" @click="openReport" title="自选区间生成经营报告">📊 生成报告</button>
              <button class="btn-log" @click="requestDailyLog" title="查看本局完整每日结算日志（近 365 天）">📜 完整日志</button>
              <button class="btn-pause" :class="{ 'paused': isPaused }" @click="togglePause" :title="isPaused ? '继续' : '暂停'">
                {{ isPaused ? '▶️ 继续' : '⏸️ 暂停' }}
              </button>
              <div class="speed-control" title="切换全局倍速">
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
              <button class="btn-fitview" @click="resetView" title="缩放至整图">🗺️ 整图</button>
              <button class="btn-stop" @click="stopGame" title="结束并重置">⏹️ 结束并重置</button>
            </template>
            <div class="status-indicator">
              <span class="dot" :class="{ 'connected': isConnected }"></span>
              {{ isConnected ? '服务器已连接' : '服务器断开' }}
            </div>
          </div>
        </div>

        <!-- 仿真运行中横幅已移除：实时经营允许随时操作 -->
        <div class="map-wrapper">
          <MapRenderer
            ref="mapRef"
            :mapData="mapData"
            :guests="guests"
            :staff="npcStaff"
            :buildings="buildings"
            :plots="plots"
          />
        </div>
      </div>

      <!-- 右侧控制面板 -->
      <div class="control-panel">
        <!-- 经营控制面板 -->
        <div class="stats-card glass-panel">
          <div class="panel-header-row">
            <h3>🏗️ 经营控制面板</h3>
            <span class="phase-tag" :class="canOperate ? 'ok' : 'lock'">
              {{ canOperate ? '可操作' : '已锁定' }}
            </span>
          </div>

          <!-- 建造面板 -->
          <div class="panel-section">
            <div class="section-title">🏢 建造 ({{ resortState.buildings.length }})</div>
            <div class="plot-select-row">
              <label>建造地块</label>
              <select v-model="selectedPlotId" class="mini-select" :disabled="!canOperate">
                <option v-for="p in resortState.unlockedPlots" :key="p" :value="p">{{ plotName(p) }}</option>
              </select>
            </div>
            <div class="building-grid">
              <div v-for="bt in BUILDING_TYPES" :key="bt.type" class="building-card">
                <div class="bc-head">
                  <span class="bc-name">{{ bt.icon }} {{ bt.name }}</span>
                </div>
                <div class="bc-meta">
                  <span>造价 ¥{{ bt.cost }}</span>
                  <span>维护 ¥{{ bt.maintenance }}/天</span>
                  <span>容量 {{ bt.capacity }}</span>
                </div>
                <button class="btn-op" :disabled="!canOperate || resortState.cash < bt.cost" @click="buildBuilding(bt.type)">
                  建造
                </button>
              </div>
            </div>
          </div>

          <!-- 已建造建筑详情：逐座列出（所在地块 + 容量 + 维护费）并可停用/启用/拆除 -->
          <div class="panel-section">
            <div class="section-title">🏘️ 已建造建筑 ({{ buildings.length }})</div>
            <div class="built-list">
              <div v-for="b in buildings" :key="b.id" class="built-item" :class="{ 'built-off': buildingStatus(b) !== 'operating' }">
                <div class="built-line">
                  <span class="built-name">{{ buildingIcon(b.type) }} {{ b.typeName || buildingName(b.type) }}</span>
                  <span class="built-plot">{{ plotName(b.plotId) }}</span>
                  <span class="built-status" :class="'bs-' + buildingStatus(b)">{{ buildingStatusLabel(b) }}</span>
                </div>
                <div class="built-line built-line-sub">
                  <span class="built-meta">容量 {{ buildingStatus(b) === 'operating' ? buildingCapacity(b.type) : 0 }}</span>
                  <span class="built-meta">{{ buildingStatus(b) === 'manual-off' ? '免维护' : '维护 ¥' + fmtMoney(buildingMaintenance(b.type)) + '/天' }}</span>
                  <span class="built-ops">
                    <button v-if="buildingStatus(b) === 'operating'" class="btn-op" :disabled="!canOperate || isQueuedBuilding(b.id)" @click="toggleBuilding(b)">停用</button>
                    <button v-if="buildingStatus(b) === 'manual-off'" class="btn-op" :disabled="!canOperate || isQueuedBuilding(b.id) || resortState.cash < rebuildFee(b)" @click="toggleBuilding(b)">启用 ¥{{ fmtMoney(rebuildFee(b)) }}</button>
                    <button v-if="buildingStatus(b) === 'operating'" class="btn-fire" :disabled="!canOperate || isQueuedBuilding(b.id)" @click="demolishBuildingRow(b)">拆除</button>
                    <span v-if="isQueuedBuilding(b.id)" class="built-queued">⏳ 已排队</span>
                  </span>
                </div>
              </div>
              <div v-if="buildings.length === 0" class="empty-inline">暂无建筑</div>
            </div>
          </div>

          <!-- 员工管理 -->
          <div class="panel-section">
            <div class="section-title">👷 招聘员工 ({{ resortState.staffList.length }} 人)</div>
            <div class="hire-row" v-for="st in STAFF_TYPES" :key="st.type">
              <span class="hire-name">{{ st.name }}</span>
              <span class="hire-base">底薪 ¥{{ st.baseSalary }}</span>
              <select v-model="salaryMode[st.type]" class="mini-select" :disabled="!canOperate">
                <option value="normal">标准</option>
                <option value="high">高薪</option>
                <option value="low">廉价</option>
              </select>
              <button class="btn-op" :disabled="!canOperate" @click="hireStaff(st.type)">招聘</button>
            </div>
            <div class="staff-list">
              <div v-for="s in resortState.staffList" :key="s.id" class="staff-item">
                <span class="staff-type">{{ s.typeName || staffName(s.type) }}</span>
                <span class="staff-salary">¥{{ fmtMoney(s.dailySalary) }}/天</span>
                <span class="staff-mode">{{ salaryModeLabel(s.salaryMode) }}</span>
                <button class="btn-fire" :disabled="!canOperate" @click="fireStaff(s.id)">解雇</button>
              </div>
              <div v-if="resortState.staffList.length === 0" class="empty-inline">暂无员工</div>
            </div>
          </div>

          <!-- 定价与广告 -->
          <div class="panel-section">
            <div class="section-title">💵 定价与广告</div>
            <div class="form-row-2">
              <div class="form-field">
                <label>普通房价 (¥)</label>
                <input type="number" v-model.number="priceForm.standardRoomPrice" min="0" step="10" :disabled="!canOperate" />
              </div>
              <div class="form-field">
                <label>豪华房价 (¥)</label>
                <input type="number" v-model.number="priceForm.luxuryRoomPrice" min="0" step="10" :disabled="!canOperate" />
              </div>
            </div>
            <div class="form-field">
              <label>每日广告预算 (¥)</label>
              <input type="number" v-model.number="priceForm.adBudget" min="0" step="100" :disabled="!canOperate" />
            </div>
            <button class="btn-primary-op" :disabled="!canOperate" @click="submitPricing">💾 应用定价与广告</button>
          </div>

          <!-- 地块解锁 -->
          <div class="panel-section">
            <div class="section-title">🗺️ 地块解锁</div>
            <div class="unlock-row" v-for="p in PLOT_UNLOCKS" :key="p.id">
              <span class="unlock-name">{{ p.name }} ({{ resortState.unlockedPlots.includes(p.id) ? '已解锁' : '¥' + p.cost }})</span>
              <button class="btn-op" :disabled="!canOperate || resortState.unlockedPlots.includes(p.id) || resortState.cash < p.cost" @click="unlockPlot(p.id)">
                {{ resortState.unlockedPlots.includes(p.id) ? '已拥有' : '解锁' }}
              </button>
            </div>
          </div>

          <!-- 团建订单：今日待决 + 已接单待生效 -->
          <div class="panel-section">
            <div class="section-title">🎉 今日团建订单 ({{ activeTeamOrders.length }})</div>
            <div class="team-order-list">
              <div v-for="o in activeTeamOrders" :key="o.id" class="team-order-card">
                <div class="to-head">
                  <span class="to-size">{{ o.sizeName || sizeName(o.size) }}</span>
                  <span class="to-status">当天有效</span>
                </div>
                <div class="to-meta">
                  <span>需客房 {{ o.requiredRooms }} 间</span>
                  <span>规模 {{ o.guestCount }} 人</span>
                </div>
                <div class="to-meta">
                  <span class="text-green">收益 ¥{{ fmtMoney(o.revenue) }}</span>
                  <span class="text-red">违约罚款 ¥{{ fmtMoney(o.penalty) }}</span>
                </div>
                <div class="to-req">
                  必备配套:
                  <span v-if="o.requiresRestaurant" class="req-tag">餐厅</span>
                  <span v-if="o.requiresSpa" class="req-tag">温泉池</span>
                  <span v-if="o.requiresViewpoint" class="req-tag">观景台</span>
                  <span v-if="!o.requiresRestaurant && !o.requiresSpa && !o.requiresViewpoint" class="req-tag none">无</span>
                </div>
                <div class="to-actions">
                  <button class="btn-accept" :disabled="!canOperate || isQueuedOrder(o.id, 'ACCEPT')" @click="acceptTeamOrder(o.id)">
                    {{ isQueuedOrder(o.id, 'ACCEPT') ? '已排队' : '接受' }}
                  </button>
                  <button class="btn-reject" :disabled="!canOperate" @click="rejectTeamOrder(o.id)">拒绝</button>
                </div>
              </div>
              <div v-if="activeTeamOrders.length === 0" class="empty-inline">今日暂无待决团建订单</div>
            </div>

            <div class="section-title sub-title">📌 已接单 · 待生效 ({{ scheduledOrders.length }})</div>
            <div class="team-order-list">
              <div v-for="o in scheduledOrders" :key="o.id" class="team-order-card to-accepted">
                <div class="to-head">
                  <span class="to-size">{{ o.sizeName || sizeName(o.size) }}</span>
                  <span class="to-status">第 {{ o.effectiveDay }} 天生效</span>
                </div>
                <div class="to-meta">
                  <span>需客房 {{ o.requiredRooms }} 间</span>
                  <span class="text-green">收益 ¥{{ fmtMoney(o.revenue) }}</span>
                </div>
              </div>
              <div v-if="scheduledOrders.length === 0" class="empty-inline">暂无待生效订单</div>
            </div>
          </div>

          <!-- 进行中：次日 0 点生效 -->
          <div class="panel-section">
            <div class="section-title">⏳ 进行中 · 次日 0 点生效</div>

            <!-- 施工中 -->
            <template v-if="constructingBuildings.length">
              <div class="sub-title">🏗️ 施工中 ({{ constructingBuildings.length }})</div>
              <div class="pending-list">
                <div v-for="b in constructingBuildings" :key="b.id" class="pending-item">
                  <span class="pending-summary">{{ buildingIcon(b.type) }} {{ b.typeName || buildingName(b.type) }} · {{ plotName(b.plotId) }}</span>
                  <span class="pending-day">次日完工</span>
                </div>
              </div>
            </template>

            <!-- 入职中 -->
            <template v-if="pendingHires.length">
              <div class="sub-title">👷 入职中 ({{ pendingHires.length }})</div>
              <div class="pending-list">
                <div v-for="c in pendingHires" :key="c.id" class="pending-item">
                  <span class="pending-summary">{{ c.summary }}</span>
                  <span class="pending-day">次日上岗</span>
                </div>
              </div>
            </template>

            <!-- 开垦中 -->
            <template v-if="pendingUnlocks.length">
              <div class="sub-title">🗺️ 开垦中 ({{ pendingUnlocks.length }})</div>
              <div class="pending-list">
                <div v-for="c in pendingUnlocks" :key="c.id" class="pending-item">
                  <span class="pending-summary">{{ plotName(c.text1) }} 开垦中</span>
                  <span class="pending-day">次日可建造</span>
                </div>
              </div>
            </template>

            <!-- 其它排队指令 -->
            <template v-if="otherPending.length">
              <div class="sub-title">📋 其它排队 ({{ otherPending.length }})</div>
              <div class="pending-list">
                <div v-for="c in otherPending" :key="c.id" class="pending-item">
                  <span class="pending-summary">{{ c.summary }}</span>
                  <span class="pending-day">第 {{ c.queuedDay }} 天排队</span>
                </div>
              </div>
            </template>

            <div v-if="!pendingCommands.length && !constructingBuildings.length" class="empty-inline">暂无进行中操作</div>
          </div>
        </div>

        <!-- 实时事件流 -->
        <div class="stats-card glass-panel">
          <div class="panel-header-row">
            <h3>⚡ 实时事件</h3>
          </div>
          <div v-for="(e, i) in eventLog" :key="i" class="event-row">
            <span class="event-type">第{{ e.day }}天</span>
            <span class="event-desc">{{ e.description }}</span>
          </div>
          <div v-if="eventLog.length === 0" class="empty-inline">暂无实时事件</div>
        </div>
      </div>
    </div>

    <!-- 操作失败/提示轻提示 -->
    <transition name="tip">
      <div v-if="actionTip" class="tip-toast">{{ actionTip }}</div>
    </transition>

    <!-- 区间经营报告弹窗 -->
    <RangeReport
      :visible="reportVisible"
      :report="rangeReport"
      @close="reportVisible = false"
      @request="requestReport"
    />

    <!-- 完整日志弹窗：逐日结算详情（近 365 天） -->
    <div v-if="logVisible" class="log-overlay" @click.self="logVisible = false">
      <div class="log-panel glass-panel">
        <div class="log-head">
          <h3>📜 {{ logTitle }}（近 {{ dailyLogs.length }} 天）</h3>
          <button class="log-close" @click="logVisible = false">✕</button>
        </div>
        <div class="log-body">
          <div v-for="d in dailyLogs" :key="d.day" class="log-day">
            <div class="log-day-head">
              <span class="ld-day">第 {{ d.day }} 天</span>
              <span class="ld-season">{{ d.seasonName }}</span>
              <span class="ld-net" :class="d.netProfit >= 0 ? 'text-green' : 'text-red'">
                净利 {{ d.netProfit >= 0 ? '+' : '-' }}¥{{ fmtMoney(Math.abs(d.netProfit)) }}
              </span>
              <span class="ld-cash">资金 ¥{{ fmtMoney(d.cashAfter) }}</span>
              <span class="ld-rep">⭐ {{ (d.reputationAfter || 0).toFixed(1) }}
                (<span :class="d.reputationDelta >= 0 ? 'text-green' : 'text-red'">{{ d.reputationDelta >= 0 ? '+' : '' }}{{ (d.reputationDelta || 0).toFixed(1) }}</span>)
              </span>
            </div>
            <div class="log-day-detail">
              入住率 {{ (d.occupancyRate || 0).toFixed(1) }}% · 接待 {{ d.guestsServed || 0 }} 人 · 溢出未接待 {{ d.turnedAway || 0 }} 人
            </div>
            <div class="log-day-detail">
              收入 客房 ¥{{ fmtMoney(d.revenueDetail && d.revenueDetail.roomFee) }} · 餐饮 ¥{{ fmtMoney(d.revenueDetail && d.revenueDetail.dining) }} · 游乐 ¥{{ fmtMoney(d.revenueDetail && d.revenueDetail.recreation) }} · 团建 ¥{{ fmtMoney(d.revenueDetail && d.revenueDetail.teamOrder) }}
            </div>
            <div class="log-day-detail">
              支出 工资 ¥{{ fmtMoney(d.expenseDetail && d.expenseDetail.staffSalary) }} · 维护 ¥{{ fmtMoney(d.expenseDetail && d.expenseDetail.buildingMaintenance) }} · 广告 ¥{{ fmtMoney(d.expenseDetail && d.expenseDetail.adBudget) }} · 事件 ¥{{ fmtMoney(d.expenseDetail && d.expenseDetail.eventCost) }} · 罚款 ¥{{ fmtMoney(d.expenseDetail && d.expenseDetail.teamPenalty) }}
            </div>
            <div v-if="d.teamOrders && d.teamOrders.length" class="log-day-lines">
              <div v-for="(t, i) in d.teamOrders" :key="'t' + i" class="log-line">🎉 {{ t }}</div>
            </div>
            <div v-if="d.events && d.events.length" class="log-day-lines">
              <div v-for="(ev, i) in d.events" :key="'e' + i" class="log-line">⚡ {{ ev }}</div>
            </div>
          </div>
          <div v-if="dailyLogs.length === 0" class="empty-inline">暂无日志</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, reactive, watch, onMounted, onBeforeUnmount } from 'vue';
import MapRenderer from './components/MapRenderer.vue';
import MapEditor from './components/MapEditor.vue';
import RangeReport from './components/RangeReport.vue';
import WebSocketClient from './services/WebSocketClient.js';

// ===== 静态配置（数值来源与后端 GameConfig 保持一致，仅用于 UI 展示/预校验）=====
const BUILDING_TYPES = [
  { type: 'STANDARD_ROOM', name: '普通客房', icon: '🏠', cost: 3000, maintenance: 120, capacity: 2 },
  { type: 'LUXURY_SUITE',  name: '豪华套房', icon: '🏰', cost: 8000, maintenance: 350, capacity: 4 },
  { type: 'RESTAURANT',    name: '餐厅',     icon: '🍽️', cost: 6000, maintenance: 220, capacity: 8 },
  { type: 'SPA',           name: '温泉池',   icon: '♨️', cost: 7500, maintenance: 280, capacity: 6 },
  { type: 'VIEWPOINT',     name: '观景台',   icon: '🗼', cost: 4000, maintenance: 150, capacity: 10 },
  { type: 'PARKING',       name: '停车场',   icon: '🅿️', cost: 2500, maintenance: 80,  capacity: 20 }
];

const STAFF_TYPES = [
  { type: 'FRONT_DESK',  name: '前台', baseSalary: 180 },
  { type: 'CLEANING',    name: '保洁', baseSalary: 160 },
  { type: 'CHEF',        name: '厨师', baseSalary: 220 },
  { type: 'MAINTENANCE', name: '维修', baseSalary: 200 }
];

const PLOT_UNLOCKS = [
  { id: 'MOUNTAIN', name: '山地', cost: 35000 },
  { id: 'LAKESIDE', name: '湖畔', cost: 60000 }
];

const SEASON_LABEL = { SPRING: '春季', SUMMER: '夏季', AUTUMN: '秋季', WINTER: '冬季' };
const REASON_MAP = {
  GAME_NOT_RUNNING: '本局尚未开始或已结束',
  INVALID_SPEED: '不支持的倍速档位',
  INVALID_PRICE: '房价必须大于 0',
  INSUFFICIENT_FUNDS: '资金不足',
  PLOT_LIMIT_EXCEEDED: '该地块建筑数量已达上限',
  NO_SPACE: '该地块无可建造空位',
  PLOT_NOT_UNLOCKED: '地块尚未解锁（解锁次日生效）',
  ALREADY_UNLOCKED: '该地块已解锁',
  ALREADY_QUEUED: '该操作已在排队中',
  UNKNOWN_PLOT: '未知地块',
  ORDER_NOT_FOUND: '未找到该团建订单',
  ORDER_NOT_PENDING: '该团建订单已处理',
  STAFF_NOT_FOUND: '未找到该员工',
  BUILDING_NOT_FOUND: '未找到该建筑',
  BUILDING_UNDER_CONSTRUCTION: '施工中的建筑不可停用/拆除',
  BUILDING_NOT_OPERATING: '仅正式运营中的建筑可拆除',
  BUILDING_AUTO_DISABLED: '该建筑为临时停用（水管爆裂），次日自动恢复，不能手动启用'
};

// ===== 视图与连接 =====
const currentView = ref('simulation');
const savedMaps = ref([]);
const selectedMapId = ref('random');
const isConnected = ref(false);
const isSimulationStarted = ref(false);
const isStarting = ref(false);
const isBankrupt = ref(false);
const bankruptcy = ref({
  daysSurvived: 0, finalCash: 0, endReason: '',
  lastDay: undefined, lastCashBefore: 0, lastNetProfit: 0,
  lastTotalRevenue: 0, lastTotalExpense: 0, revenueDetail: null, expenseDetail: null,
  dailyReports: []
});
const mapRef = ref(null);

// ===== 全局经营状态 =====
const resortState = ref({
  cash: 20000,
  reputation: 70,
  season: 'SPRING',
  seasonName: '春季',
  day: 1,
  phase: 'RUNNING',
  standardRoomPrice: 280,
  luxuryRoomPrice: 680,
  adBudget: 0,
  unlockedPlots: ['INITIAL'],
  buildings: [],
  staffList: [],
  teamOrders: [],
  pendingCommands: [],
  scheduledOrders: []
});

const phase = computed(() => resortState.value.phase || 'RUNNING');
// 实时经营：只要本局未结束即可操作（不再有决策窗口概念）
const canOperate = computed(() => isSimulationStarted.value && !isBankrupt.value);
const phaseText = computed(() => {
  switch (phase.value) {
    case 'RUNNING': return '实时经营中';
    case 'GAME_OVER': return '已结束';
    default: return phase.value;
  }
});
const pendingCommands = computed(() => resortState.value.pendingCommands || []);
const scheduledOrders = computed(() => resortState.value.scheduledOrders || []);
const seasonLabel = computed(() => resortState.value.seasonName || SEASON_LABEL[resortState.value.season] || '');
const buildings = computed(() => resortState.value.buildings || []);
// 施工中（status===1）建筑 / 各类排队中的指令，用于「进行中」反馈
const constructingBuildings = computed(() => buildings.value.filter(b => b.status === 1));
const pendingHires = computed(() => pendingCommands.value.filter(c => c.type === 'HIRE_STAFF'));
const pendingUnlocks = computed(() => pendingCommands.value.filter(c => c.type === 'UNLOCK_PLOT'));
const otherPending = computed(() => pendingCommands.value.filter(c =>
  c.type !== 'HIRE_STAFF' && c.type !== 'UNLOCK_PLOT' && c.type !== 'BUILD_BUILDING'));

// ===== 地图与可视化 =====
const mapData = ref(null);
const guests = ref([]);
const npcStaff = ref([]);
const selectedPlotId = ref('INITIAL');

// 地块几何：优先使用地图自带 plots；退化时按 buildableCells 的 plotId 分组
const plots = computed(() => {
  const unlocked = resortState.value.unlockedPlots || [];
  const base = mapData.value && mapData.value.plots;
  if (Array.isArray(base) && base.length) {
    return base.map(p => ({ id: p.id, cells: p.cells || [], unlocked: unlocked.includes(p.id) }));
  }
  const cells = mapData.value && mapData.value.buildableCells;
  if (Array.isArray(cells) && cells.length) {
    const groups = {};
    cells.forEach(c => {
      const x = Array.isArray(c) ? c[0] : c.x;
      const y = Array.isArray(c) ? c[1] : c.y;
      const pid = Array.isArray(c) ? c[2] : c.plotId;
      if (pid === undefined || pid === null) return;
      (groups[pid] = groups[pid] || []).push([x, y]);
    });
    const list = Object.keys(groups).map(id => ({ id, cells: groups[id], unlocked: unlocked.includes(id) }));
    if (list.length) return list;
  }
  return [];
});

// ===== 倍速与暂停 =====
const speedOptions = [0.5, 1, 2, 4, 8, 16];
const speed = ref(1.0);
const isPaused = ref(false);

// ===== 仿真时钟：服务端 TIME_TICK + 本地插值（让玩家看得见时间在流逝）=====
const timeTick = ref(null);        // 最近一次服务端时钟快照
const tickReceivedAt = ref(0);     // 收到快照时的本地时刻
const clockNow = ref(Date.now());  // 本地推进的「现在」，用于两次推送之间平滑走字
let clockTimer = null;

const interpVirtualMs = computed(() => {
  const t = timeTick.value;
  if (!t) return null;
  const dt = Math.max(0, clockNow.value - tickReceivedAt.value); // 现实毫秒
  // virtualMsPerRealSecond 是「每现实 1 秒推进的虚拟毫秒」，故先 /1000 换成现实秒
  return t.virtualTimeMs + (dt / 1000) * (t.virtualMsPerRealSecond || 0);
});
const gameDayVirtualMs = computed(() => (timeTick.value && timeTick.value.gameDayVirtualMs) || 86400000);
const clockAdvancing = computed(() => !!(timeTick.value && timeTick.value.virtualMsPerRealSecond > 0));
const clockText = computed(() => {
  const v = interpVirtualMs.value;
  if (v === null) return '00:00';
  const total = gameDayVirtualMs.value;
  const inDay = ((v % total) + total) % total;
  const h = Math.floor(inDay / 3600000);
  const m = Math.floor((inDay % 3600000) / 60000);
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
});
const dayProgress = computed(() => {
  const v = interpVirtualMs.value;
  if (v === null) return 0;
  const total = gameDayVirtualMs.value;
  const inDay = ((v % total) + total) % total;
  return inDay / total;
});

// ===== 面板表单状态 =====
const priceForm = reactive({ standardRoomPrice: 280, luxuryRoomPrice: 680, adBudget: 0 });
const salaryMode = reactive({ FRONT_DESK: 'normal', CLEANING: 'normal', CHEF: 'normal', MAINTENANCE: 'normal' });

// ===== 团建单 / 报告 / 事件 =====
const teamOrders = ref([]);
const activeTeamOrders = computed(() => teamOrders.value.filter(o => (o.status || 'PENDING') === 'PENDING'));
const reportVisible = ref(false);
const rangeReport = ref(null);
const eventLog = ref([]);

// ===== 完整日志（每日结算详情，近 365 天）=====
const dailyLogs = ref([]);
const logVisible = ref(false);
const logTitle = ref('本局完整日志');

const actionTip = ref(null);
let actionTipTimer = null;
const showTip = (text) => {
  actionTip.value = text;
  if (actionTipTimer) clearTimeout(actionTipTimer);
  actionTipTimer = setTimeout(() => { actionTip.value = null; }, 2400);
};

// ===== 工具函数 =====
// 金额统一按整数展示，超过三位加千分位
const fmtMoney = (v) => Math.round(Number(v) || 0).toLocaleString('en-US');
const plotName = (p) => ({ INITIAL: '初始地块', MOUNTAIN: '山地', LAKESIDE: '湖畔' }[p] || p);
const staffName = (t) => (STAFF_TYPES.find(s => s.type === t) || {}).name || t;
const sizeName = (s) => ({ SMALL: '小型团建', MEDIUM: '中型团建', LARGE: '大型团建' }[s] || s);
const salaryModeLabel = (m) => ({ NORMAL: '标准', HIGH: '高薪', LOW: '廉价' }[m] || m || '');
const buildingMeta = (t) => BUILDING_TYPES.find(x => x.type === t) || {};
const buildingName = (t) => buildingMeta(t).name || t;
const buildingIcon = (t) => buildingMeta(t).icon || '🏗️';
const buildingCapacity = (t) => { const m = buildingMeta(t); return m.capacity != null ? m.capacity : '-'; };
const buildingMaintenance = (t) => { const m = buildingMeta(t); return m.maintenance != null ? m.maintenance : 0; };
// 与后端 GameConfig 对应：启用经费比例（启用按钮标注 ¥费用）
const REBUILD_RATIO = 0.3;
const rebuildFee = (b) => Math.round((buildingMeta(b && b.type).cost || 0) * REBUILD_RATIO);

// ===== 建筑停用/启用/拆除 =====
// 建筑状态：operating 正式运营 / manual-off 玩家手动停用 / auto-off 水管爆裂自动停用 / building 施工中
const buildingStatus = (b) => {
  if (!b) return 'operating';
  if (b.status === 1) return 'building';
  if (b.status === 2) return b.disableSource === 'MANUAL' ? 'manual-off' : 'auto-off';
  return 'operating';
};
const buildingStatusLabel = (b) => ({
  operating: '运营中', building: '施工中', 'manual-off': '已停用', 'auto-off': '临时停用'
}[buildingStatus(b)]);
// 该建筑是否已有排队中的停用/启用/拆除指令
const isQueuedBuilding = (id) => pendingCommands.value.some(c =>
  (c.type === 'TOGGLE_BUILDING_DISABLE' || c.type === 'DEMOLISH_BUILDING') && c.text1 === id);

const toggleBuilding = (b) => {
  const s = buildingStatus(b);
  if (s !== 'operating' && s !== 'manual-off') {
    // 施工中 / 水管爆裂临时停用 → 不可手动操作
    showTip(s === 'building' ? '施工中的建筑不可停用/启用' : '该建筑为临时停用，次日将自动恢复');
    return;
  }
  client.send({ command: 'TOGGLE_BUILDING_DISABLE', buildingId: b.id });
};

const demolishBuildingRow = (b) => {
  if (buildingStatus(b) !== 'operating') { showTip('仅正式运营中的建筑可拆除'); return; }
  client.send({ command: 'DEMOLISH_BUILDING', buildingId: b.id });
};
// 该团建单是否已有排队中的接受指令
const isQueuedOrder = (orderId, kind) => {
  const t = kind === 'ACCEPT' ? 'ACCEPT_TEAM_ORDER' : 'REJECT_TEAM_ORDER';
  return pendingCommands.value.some(c => c.type === t && c.text1 === orderId);
};

// ===== WebSocket 客户端 =====
const client = new WebSocketClient();

const syncPriceForm = () => {
  priceForm.standardRoomPrice = resortState.value.standardRoomPrice;
  priceForm.luxuryRoomPrice = resortState.value.luxuryRoomPrice;
  priceForm.adBudget = resortState.value.adBudget;
};

// 建造落地反馈：点击后立即出现施工幽灵，弹提示 + 镜头自动定位到施工位置
let prevBuildingCount = -1;
const notifyBuildSuccess = (list) => {
  const n = list.length;
  if (prevBuildingCount >= 0 && n > prevBuildingCount) {
    const b = list[n - 1];
    const meta = BUILDING_TYPES.find(x => x.type === b.type);
    showTip(`🏗️ 开始施工：${meta ? meta.name : (b.typeName || b.type)}（次日 0 点完工）`);
    const pos = b.position;
    if (mapRef.value && pos) mapRef.value.focusOn(pos.x, pos.y);
  }
  prevBuildingCount = n;
};

const resetView = () => { if (mapRef.value) mapRef.value.resetView(); };

const applyResortState = (data) => {
  if (!data) return;
  const s = resortState.value;
  if (data.cash !== undefined) s.cash = data.cash;
  if (data.reputation !== undefined) s.reputation = data.reputation;
  if (data.season !== undefined) s.season = data.season;
  if (data.seasonName !== undefined) s.seasonName = data.seasonName;
  if (data.day !== undefined) s.day = data.day;
  if (data.phase !== undefined) s.phase = data.phase;
  if (data.standardRoomPrice !== undefined) s.standardRoomPrice = data.standardRoomPrice;
  if (data.luxuryRoomPrice !== undefined) s.luxuryRoomPrice = data.luxuryRoomPrice;
  if (data.adBudget !== undefined) s.adBudget = data.adBudget;
  if (Array.isArray(data.unlockedPlots)) s.unlockedPlots = data.unlockedPlots;
  if (Array.isArray(data.buildings)) { notifyBuildSuccess(data.buildings); s.buildings = data.buildings; }
  if (Array.isArray(data.staffList)) s.staffList = data.staffList;
  if (Array.isArray(data.pendingCommands)) s.pendingCommands = data.pendingCommands;
  if (Array.isArray(data.scheduledOrders)) s.scheduledOrders = data.scheduledOrders;
  if (Array.isArray(data.teamOrders)) teamOrders.value = data.teamOrders;
};

// 新的一天：同步表单为后端已生效值
watch(() => resortState.value.day, () => {
  syncPriceForm();
});

// 解锁地块后校正当前建造地块选择
watch(() => resortState.value.unlockedPlots.join(','), () => {
  if (!resortState.value.unlockedPlots.includes(selectedPlotId.value)) {
    selectedPlotId.value = resortState.value.unlockedPlots[0] || 'INITIAL';
  }
});

const fetchSavedMaps = async () => {
  try {
    const res = await fetch('http://localhost:8081/api/maps');
    if (res.ok) savedMaps.value = await res.json();
  } catch (e) {
    console.error('拉取地图列表失败:', e);
  }
};

// ===== 指令发送 =====
const startGame = () => {
  if (!isConnected.value || isStarting.value) return;
  isStarting.value = true;
  client.send({ command: 'START_GAME', mapId: selectedMapId.value });
};

const restartGame = () => {
  isBankrupt.value = false;
  isStarting.value = true;
  client.send({ command: 'START_GAME', mapId: selectedMapId.value });
};

const stopGame = () => {
  if (confirm('确定要结束当前经营并重置吗？')) client.send({ command: 'STOP_GAME' });
};

const togglePause = () => {
  if (!isSimulationStarted.value) return;
  if (isPaused.value) {
    client.send({ command: 'RESUME_GAME' });
    isPaused.value = false;
  } else {
    client.send({ command: 'PAUSE_GAME' });
    isPaused.value = true;
  }
};

const setSpeed = (s) => {
  if (!isSimulationStarted.value) return;
  speed.value = s;
  client.send({ command: 'SET_SIMULATION_SPEED', speed: s });
};

const buildBuilding = (type) => client.send({ command: 'BUILD_BUILDING', buildingType: type, plotId: selectedPlotId.value });
const hireStaff = (type) => client.send({ command: 'HIRE_STAFF', staffType: type, salaryMode: salaryMode[type] || 'normal' });
const fireStaff = (staffId) => client.send({ command: 'FIRE_STAFF', staffId });

const submitPricing = () => {
  client.send({
    command: 'SET_ROOM_PRICE',
    standardRoomPrice: Math.max(0, Number(priceForm.standardRoomPrice) || 0),
    luxuryRoomPrice: Math.max(0, Number(priceForm.luxuryRoomPrice) || 0)
  });
  client.send({ command: 'SET_AD_BUDGET', adBudget: Math.max(0, Number(priceForm.adBudget) || 0) });
};

const unlockPlot = (plotId) => client.send({ command: 'UNLOCK_PLOT', plotId });

// 团建单：入队后不改本地状态，等待后端次日 0 点生效后由 RESORT_STATE 回推
const acceptTeamOrder = (orderId) => client.send({ command: 'ACCEPT_TEAM_ORDER', orderId });
const rejectTeamOrder = (orderId) => client.send({ command: 'REJECT_TEAM_ORDER', orderId });

// ===== 区间报告 =====
const openReport = () => {
  reportVisible.value = true;
  requestReport(7);
};
const requestReport = (rangeDays) => {
  if (!isSimulationStarted.value) return;
  client.send({ command: 'REQUEST_RANGE_REPORT', rangeDays });
};

// ===== 完整日志 =====
const requestDailyLog = () => {
  if (!isSimulationStarted.value) return;
  logTitle.value = '本局完整日志';
  client.requestDailyLog();
};

// ===== 生命周期 =====
onMounted(() => {
  fetchSavedMaps();

  client.onStatusChange = (status) => { isConnected.value = status; };

  client.onMapData = (data) => {
    mapData.value = data;
    if (data && data.buildableCells) { /* 供 plots 计算使用 */ }
  };

  client.onGameStarted = () => {
    isSimulationStarted.value = true;
    isStarting.value = false;
    isPaused.value = false;
    isBankrupt.value = false;
    rangeReport.value = null;
    reportVisible.value = false;
    logVisible.value = false;
    dailyLogs.value = [];
    eventLog.value = [];
    teamOrders.value = [];
    guestCleanState();
    prevBuildingCount = -1;
    syncPriceForm();
  };

  client.onGameStopped = (data) => {
    // 手动「结束并重置」：后端随 GAME_STOPPED 带回本局全部每日日志，弹窗展示
    if (data && Array.isArray(data.dailyReports) && data.dailyReports.length) {
      dailyLogs.value = data.dailyReports;
      logTitle.value = '本局完整日志（已结束）';
      logVisible.value = true;
    }
    isSimulationStarted.value = false;
    isStarting.value = false;
    isPaused.value = false;
    isBankrupt.value = false;
    mapData.value = null;
    guests.value = [];
    npcStaff.value = [];
    teamOrders.value = [];
    rangeReport.value = null;
    reportVisible.value = false;
    eventLog.value = [];
    prevBuildingCount = -1;
    timeTick.value = null;
    resortState.value = {
      cash: 20000, reputation: 70, season: 'SPRING', seasonName: '春季', day: 1,
      phase: 'RUNNING', standardRoomPrice: 280, luxuryRoomPrice: 680, adBudget: 0,
      unlockedPlots: ['INITIAL'], buildings: [], staffList: [],
      pendingCommands: [], scheduledOrders: []
    };
    syncPriceForm();
  };

  client.onResortState = (data) => {
    isSimulationStarted.value = true;
    isStarting.value = false;
    applyResortState(data);
  };

  client.onDayStarted = (data) => {
    if (data && data.day !== undefined) resortState.value.day = data.day;
    if (data && data.season) resortState.value.season = data.season;
    if (data && data.seasonName) resortState.value.seasonName = data.seasonName;
  };

  client.onTeamOrders = (data) => {
    teamOrders.value = Array.isArray(data) ? data : [];
  };

  // 每日结算：不弹日报，只在事件日志追加一行换日提示
  client.onDaySettled = (data) => {
    if (!data) return;
    const sign = (data.netProfit || 0) >= 0 ? '+' : '';
    eventLog.value.unshift({
      day: data.day,
      description: `第 ${data.day} 天结算 · 净利润 ${sign}¥${Math.round(data.netProfit || 0).toLocaleString('en-US')} · 资金 ¥${Math.round(data.cash || 0).toLocaleString('en-US')}`
    });
    if (eventLog.value.length > 30) eventLog.value.pop();
    if (data.cash !== undefined) resortState.value.cash = data.cash;
    if (data.reputation !== undefined) resortState.value.reputation = data.reputation;
  };

  client.onRangeReport = (data) => {
    rangeReport.value = data || null;
  };

  // 完整日志（玩家主动请求）：只回请求方
  client.onDailyLog = (data) => {
    dailyLogs.value = Array.isArray(data) ? data : [];
    logTitle.value = '本局完整日志';
    logVisible.value = true;
  };

  client.onEventTrigger = (data) => {
    if (!data) return;
    eventLog.value.unshift({ day: data.day || resortState.value.day, description: data.description || '' });
    if (eventLog.value.length > 30) eventLog.value.pop();
  };

  client.onNpcUpdate = (data) => {
    if (!data) return;
    guests.value = Array.isArray(data.guests) ? data.guests : [];
    npcStaff.value = Array.isArray(data.staff) ? data.staff : [];
  };

  // 当日时钟：收到推送即校准，两次推送之间由本地时钟平滑插值
  client.onTimeTick = (data) => {
    if (!data) return;
    timeTick.value = data;
    tickReceivedAt.value = Date.now();
    if (data.day !== undefined) resortState.value.day = data.day;
    if (data.phase !== undefined) resortState.value.phase = data.phase;
    if (data.speed !== undefined && speed.value !== data.speed) speed.value = data.speed;
    if (data.paused !== undefined) isPaused.value = data.paused;
  };

  client.onActionFailed = (data) => {
    const reason = data && data.reason;
    showTip(REASON_MAP[reason] || '操作失败，请重试');
  };

  client.onSpeedChanged = (s) => { speed.value = s; };
  client.onSimulationPaused = () => { isPaused.value = true; };
  client.onSimulationResumed = () => { isPaused.value = false; };

  client.onBankruptcy = (data) => {
    bankruptcy.value = {
      daysSurvived: (data && data.daysSurvived) || 0,
      finalCash: (data && data.finalCash) || 0,
      endReason: (data && data.endReason) || '资金耗尽',
      lastDay: data ? data.lastDay : undefined,
      lastCashBefore: (data && data.lastCashBefore) || 0,
      lastNetProfit: (data && data.lastNetProfit) || 0,
      lastTotalRevenue: (data && data.lastTotalRevenue) || 0,
      lastTotalExpense: (data && data.lastTotalExpense) || 0,
      revenueDetail: (data && data.revenueDetail) || null,
      expenseDetail: (data && data.expenseDetail) || null,
      dailyReports: (data && Array.isArray(data.dailyReports)) ? data.dailyReports : []
    };
    if (data && data.finalCash !== undefined) resortState.value.cash = data.finalCash;
    isStarting.value = false;
    isBankrupt.value = true;
    isSimulationStarted.value = false;
  };

  client.connect();

  clockTimer = setInterval(() => { clockNow.value = Date.now(); }, 250);

  onBeforeUnmount(() => {
    if (actionTipTimer) clearTimeout(actionTipTimer);
    if (clockTimer) clearInterval(clockTimer);
  });
});

const guestCleanState = () => {
  guests.value = [];
  npcStaff.value = [];
};
</script>

<style scoped>
.dashboard-wrapper { width: 100vw; height: 100vh; overflow: hidden; }
.dashboard { display: flex; height: 100vh; width: 100vw; background-color: var(--bg-dark); color: var(--text-primary); overflow: hidden; }

/* 启动弹窗 */
.start-modal-overlay {
  position: fixed; top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(10, 10, 14, 0.85); backdrop-filter: blur(8px);
  z-index: 9999; display: flex; align-items: center; justify-content: center;
}
.start-modal { padding: 35px; border-radius: 16px; width: 420px; display: flex; flex-direction: column; gap: 16px; border: 1px solid rgba(255,255,255,0.1); }
.start-modal h2 { margin: 0; font-size: 1.5rem; color: var(--accent-color); text-align: center; }
.modal-desc { text-align: center; color: #aaa; margin: 0 0 8px 0; font-size: 0.9rem; }
.form-group { display: flex; flex-direction: column; gap: 6px; }
.form-group label { font-size: 0.9rem; font-weight: 500; }
.form-group input, .map-dropdown { padding: 10px; border-radius: 8px; border: 1px solid rgba(255,255,255,0.2); background: rgba(0,0,0,0.3); color: #fff; font-size: 0.95rem; }
.map-dropdown option { background: #0f172a; color: #fff; }
.modal-actions { display: flex; flex-direction: column; gap: 10px; margin-top: 8px; }
.btn-start { padding: 12px; border-radius: 8px; background: var(--accent-color); color: #fff; border: none; font-size: 1.1rem; font-weight: bold; cursor: pointer; transition: all 0.2s; }
.btn-start:hover:not(:disabled) { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(59,130,246,0.4); }
.btn-start:disabled { background: #555; cursor: not-allowed; opacity: 0.7; }
.btn-open-editor { padding: 10px; border-radius: 8px; background: rgba(255,255,255,0.08); border: 1px dashed rgba(255,255,255,0.3); color: #93c5fd; font-size: 0.95rem; font-weight: 600; cursor: pointer; transition: all 0.2s; }
.btn-open-editor:hover { background: rgba(59,130,246,0.2); border-color: #60a5fa; }

.btn-editor-entry { padding: 6px 14px; background: rgba(59,130,246,0.15); border: 1px solid rgba(59,130,246,0.4); color: #93c5fd; border-radius: 6px; font-size: 13px; font-weight: 600; cursor: pointer; transition: all 0.2s ease; }
.btn-editor-entry:hover { background: rgba(59,130,246,0.3); transform: translateY(-1px); }

.map-section { flex: 3; display: flex; flex-direction: column; padding: 16px; min-width: 0; }
.header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; flex-wrap: wrap; gap: 12px; }
.header-left { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.header h2 { font-size: 18px; font-weight: 600; margin: 0; }
.header-actions { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }

/* 全局状态条 */
.status-bar { display: flex; flex-direction: column; gap: 6px; }
.sb-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.sb-item { padding: 5px 12px; border-radius: 20px; font-size: 13px; font-weight: 700; border: 1px solid var(--panel-border); background: rgba(255,255,255,0.05); }
.sb-season { background: rgba(34,197,94,0.14); border-color: rgba(34,197,94,0.4); color: #4ade80; }
.sb-cash { background: rgba(16,185,129,0.12); border-color: rgba(16,185,129,0.4); color: #34d399; }
.sb-rep { background: rgba(245,158,11,0.12); border-color: rgba(245,158,11,0.4); color: #fbbf24; }
.sb-clock { background: rgba(56,189,248,0.12); border-color: rgba(56,189,248,0.4); color: #7dd3fc; font-variant-numeric: tabular-nums; }
.sb-clock-idle { opacity: 0.5; }
.sb-progress { width: 100%; max-width: 560px; height: 6px; border-radius: 3px; background: rgba(148,163,184,0.22); overflow: hidden; }
.sb-progress-fill { height: 100%; border-radius: 3px; background: linear-gradient(90deg, #22d3ee, #38bdf8); transition: width 250ms linear; }
.sb-phase { padding: 5px 10px; border-radius: 8px; font-size: 12px; font-weight: 700; }
.phase-running { background: rgba(34,197,94,0.18); color: #4ade80; }

.btn-report { padding: 6px 12px; background: rgba(34,197,94,0.15); border: 1px solid rgba(34,197,94,0.4); color: #4ade80; border-radius: 6px; font-size: 12px; font-weight: 700; cursor: pointer; }
.btn-report:hover { background: rgba(34,197,94,0.3); color: #fff; }
.btn-log { padding: 6px 12px; background: rgba(168,85,247,0.15); border: 1px solid rgba(168,85,247,0.4); color: #c084fc; border-radius: 6px; font-size: 12px; font-weight: 700; cursor: pointer; }
.btn-log:hover { background: rgba(168,85,247,0.3); color: #fff; }

.btn-pause { padding: 6px 12px; background: rgba(245,158,11,0.15); border: 1px solid rgba(245,158,11,0.4); color: #fbbf24; border-radius: 6px; font-size: 12px; font-weight: 600; cursor: pointer; }
.btn-pause:hover { background: rgba(245,158,11,0.3); }
.btn-stop { padding: 6px 12px; background: rgba(239,68,68,0.15); border: 1px solid rgba(239,68,68,0.4); color: #f87171; border-radius: 6px; font-size: 12px; font-weight: 600; cursor: pointer; }
.btn-stop:hover { background: rgba(239,68,68,0.3); color: #fff; }
.btn-fitview { padding: 6px 12px; background: rgba(56,189,248,0.12); border: 1px solid rgba(56,189,248,0.4); color: #7dd3fc; border-radius: 6px; font-size: 12px; font-weight: 600; cursor: pointer; }
.btn-fitview:hover { background: rgba(56,189,248,0.3); color: #fff; }
.speed-control { display: flex; align-items: center; gap: 2px; padding: 2px; background: rgba(56,189,248,0.08); border: 1px solid rgba(56,189,248,0.3); border-radius: 6px; }
.btn-speed { padding: 4px 8px; background: transparent; border: none; color: #7dd3fc; border-radius: 4px; font-size: 12px; font-weight: 600; cursor: pointer; }
.btn-speed:hover { background: rgba(56,189,248,0.2); }
.btn-speed.active { background: rgba(56,189,248,0.35); color: #fff; box-shadow: 0 0 6px rgba(56,189,248,0.5); }

.status-indicator { display: flex; align-items: center; gap: 6px; font-size: 12px; color: var(--text-secondary); }
.dot { width: 8px; height: 8px; border-radius: 50%; background: var(--danger); box-shadow: 0 0 6px var(--danger); }
.dot.connected { background: var(--success); box-shadow: 0 0 6px var(--success); }

.map-wrapper { flex: 1; min-height: 0; border-radius: 12px; overflow: hidden; box-shadow: inset 0 0 20px rgba(0,0,0,0.5); }

/* 控制面板 */
.control-panel { width: 460px; padding: 12px; display: flex; flex-direction: column; gap: 10px; height: 100vh; box-sizing: border-box; overflow-y: auto; }
.stats-card { padding: 12px; border-radius: 12px; display: flex; flex-direction: column; gap: 8px; }
.panel-header-row { display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid var(--panel-border); padding-bottom: 6px; }
.panel-header-row h3 { font-size: 14px; margin: 0; }
.phase-tag { font-size: 11px; font-weight: 700; padding: 2px 8px; border-radius: 6px; }
.phase-tag.ok { background: rgba(34,197,94,0.18); color: #4ade80; }
.phase-tag.lock { background: rgba(148,163,184,0.18); color: #94a3b8; }

.panel-section { display: flex; flex-direction: column; gap: 6px; padding: 8px; background: rgba(0,0,0,0.25); border: 1px solid var(--panel-border); border-radius: 8px; }
.section-title { font-size: 12px; font-weight: 700; color: #93c5fd; }
.sub-title { margin-top: 4px; color: #4ade80; }
.plot-select-row { display: flex; align-items: center; gap: 6px; font-size: 11px; color: #94a3b8; }
.mini-select { padding: 3px 6px; background: rgba(0,0,0,0.4); border: 1px solid rgba(255,255,255,0.15); border-radius: 4px; color: #fff; font-size: 11px; }
.mini-select:disabled { opacity: 0.5; }

.building-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; }
.building-card { background: rgba(255,255,255,0.04); border: 1px solid var(--panel-border); border-radius: 6px; padding: 6px; display: flex; flex-direction: column; gap: 4px; }
.bc-name { font-size: 12px; font-weight: 700; color: #fff; }
.bc-meta { display: flex; flex-direction: column; gap: 1px; font-size: 10px; color: #94a3b8; font-family: monospace; }

.btn-op { padding: 4px 8px; background: rgba(59,130,246,0.18); border: 1px solid rgba(59,130,246,0.4); color: #93c5fd; border-radius: 5px; font-size: 11px; font-weight: 700; cursor: pointer; }
.btn-op:hover:not(:disabled) { background: rgba(59,130,246,0.35); color: #fff; }
.btn-op:disabled { opacity: 0.4; cursor: not-allowed; }

.hire-row { display: flex; align-items: center; gap: 6px; font-size: 11px; }
.hire-name { font-weight: 700; color: #fff; width: 32px; }
.hire-base { color: #94a3b8; font-family: monospace; flex: 1; }

.staff-list { display: flex; flex-direction: column; gap: 4px; margin-top: 4px; }
.staff-item { display: flex; align-items: center; gap: 8px; font-size: 11px; background: rgba(255,255,255,0.03); padding: 4px 6px; border-radius: 5px; }
.staff-type { font-weight: 700; color: #38bdf8; }
.staff-salary { color: #e2e8f0; font-family: monospace; }
.staff-mode { color: #94a3b8; margin-left: auto; }
.btn-fire { padding: 2px 8px; background: rgba(239,68,68,0.15); border: 1px solid rgba(239,68,68,0.4); color: #f87171; border-radius: 4px; font-size: 10px; font-weight: 700; cursor: pointer; }
.btn-fire:disabled { opacity: 0.4; cursor: not-allowed; }

.form-row-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.form-field { display: flex; flex-direction: column; gap: 3px; }
.form-field label { font-size: 10px; color: #cbd5e1; }
.form-field input { padding: 6px 8px; background: rgba(0,0,0,0.4); border: 1px solid rgba(255,255,255,0.15); border-radius: 5px; color: #fff; font-family: monospace; font-size: 12px; }
.form-field input:focus { outline: none; border-color: #3b82f6; }
.form-field input:disabled { opacity: 0.5; }
.btn-primary-op { padding: 6px; background: #2563eb; border: 1px solid #3b82f6; border-radius: 6px; color: #fff; font-size: 12px; font-weight: 700; cursor: pointer; }
.btn-primary-op:disabled { opacity: 0.4; cursor: not-allowed; }

.unlock-row { display: flex; align-items: center; justify-content: space-between; font-size: 11px; }
.unlock-name { color: #cbd5e1; }

.team-order-list { display: flex; flex-direction: column; gap: 6px; }
.team-order-card { background: rgba(255,255,255,0.03); border: 1px solid var(--panel-border); border-radius: 6px; padding: 6px; display: flex; flex-direction: column; gap: 3px; }
.team-order-card.to-accepted { border-color: rgba(34,197,94,0.5); }
.team-order-card.to-rejected { opacity: 0.55; }
.to-head { display: flex; justify-content: space-between; align-items: center; }
.to-size { font-weight: 700; color: #fbbf24; font-size: 12px; }
.to-status { font-size: 10px; color: #94a3b8; }
.to-meta { display: flex; gap: 10px; font-size: 11px; color: #cbd5e1; }
.to-req { font-size: 10px; color: #94a3b8; display: flex; align-items: center; gap: 4px; flex-wrap: wrap; }
.req-tag { background: rgba(56,189,248,0.15); color: #7dd3fc; padding: 1px 6px; border-radius: 4px; }
.req-tag.none { background: rgba(148,163,184,0.15); color: #94a3b8; }
.to-actions { display: flex; gap: 6px; margin-top: 2px; }
.btn-accept { flex: 1; padding: 4px; background: rgba(34,197,94,0.2); border: 1px solid rgba(34,197,94,0.5); color: #4ade80; border-radius: 5px; font-size: 11px; font-weight: 700; cursor: pointer; }
.btn-accept:disabled { opacity: 0.4; cursor: not-allowed; }
.btn-reject { flex: 1; padding: 4px; background: rgba(239,68,68,0.15); border: 1px solid rgba(239,68,68,0.4); color: #f87171; border-radius: 5px; font-size: 11px; font-weight: 700; cursor: pointer; }
.btn-reject:disabled { opacity: 0.4; cursor: not-allowed; }

/* 进行中（施工中 / 入职中 / 开垦中 / 其它排队）列表 */
.pending-list { display: flex; flex-direction: column; gap: 4px; }
.pending-item { display: flex; align-items: center; justify-content: space-between; gap: 8px; background: rgba(56,189,248,0.08); border: 1px solid rgba(56,189,248,0.25); border-radius: 5px; padding: 4px 8px; font-size: 11px; }
.pending-summary { color: #e2e8f0; }
.pending-day { color: #7dd3fc; white-space: nowrap; font-size: 10px; }

.empty-inline { font-size: 11px; color: #64748b; text-align: center; padding: 6px; }

.event-row { display: flex; gap: 6px; font-size: 11px; color: #cbd5e1; padding: 2px 0; }
.event-type { color: #fbbf24; font-weight: 700; white-space: nowrap; }
.event-desc { color: #e2e8f0; }

.text-green { color: #34d399; }
.text-red { color: #f87171; }

/* 操作提示 */
.tip-toast { position: fixed; top: 84px; left: 50%; transform: translateX(-50%); background: rgba(15,23,42,0.95); border: 1px solid rgba(239,68,68,0.5); color: #fca5a5; padding: 10px 18px; border-radius: 8px; font-size: 13px; font-weight: 600; box-shadow: 0 6px 18px rgba(0,0,0,0.5); z-index: 9998; }
.tip-enter-active, .tip-leave-active { transition: all 0.3s ease; }
.tip-enter-from, .tip-leave-to { opacity: 0; transform: translateX(-50%) translateY(-8px); }

/* 破产结算画面 */
.bankrupt-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(10,10,14,0.9); backdrop-filter: blur(10px); z-index: 10000; display: flex; align-items: center; justify-content: center; }
.bankrupt-panel { padding: 36px; border-radius: 18px; width: 480px; max-width: 90vw; max-height: 88vh; overflow-y: auto; display: flex; flex-direction: column; gap: 18px; border: 1px solid rgba(239,68,68,0.5); box-shadow: 0 0 40px rgba(239,68,68,0.25); background: rgba(15,23,42,0.96); }
.bankrupt-panel h2 { margin: 0; text-align: center; color: #f87171; font-size: 1.6rem; }
.bankrupt-sub { text-align: center; color: #cbd5e1; margin: 0; font-size: 0.9rem; }
.bankrupt-stats { display: flex; flex-direction: column; gap: 12px; }
.bs-row { display: flex; justify-content: space-between; align-items: center; padding: 10px 12px; background: rgba(255,255,255,0.04); border-radius: 10px; }
.bs-label { color: #94a3b8; font-size: 13px; }
.bs-value { color: #fff; font-size: 14px; font-weight: 700; }
.bankrupt-breakdown { margin-top: 12px; padding: 12px; background: rgba(255,255,255,0.03); border: 1px solid rgba(255,255,255,0.08); border-radius: 10px; }
.bb-title { font-size: 13px; font-weight: 700; color: #fbbf24; margin-bottom: 10px; }
.bb-cols { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.bb-col { display: flex; flex-direction: column; gap: 4px; }
.bb-head { font-size: 12px; font-weight: 700; color: #93c5fd; margin-bottom: 2px; }
.bb-row { display: flex; justify-content: space-between; font-size: 12px; color: #cbd5e1; }
.bb-row span:last-child { color: #e2e8f0; }
.bb-total { margin-top: 4px; padding-top: 4px; border-top: 1px dashed rgba(255,255,255,0.15); font-weight: 700; color: #fff; }
.bb-net { margin-top: 10px; font-size: 12px; color: #94a3b8; text-align: center; }

/* 已建造建筑详情列表 */
.built-list { display: flex; flex-direction: column; gap: 4px; }
.built-item { display: flex; flex-direction: column; align-items: stretch; gap: 3px; padding: 6px 10px; background: rgba(255,255,255,0.04); border-radius: 8px; font-size: 12px; }
.built-off { opacity: 0.68; }
.built-line { display: flex; align-items: center; gap: 8px; }
.built-line-sub { flex-wrap: wrap; }
.built-name { flex: 1; font-weight: 600; color: #e2e8f0; }
.built-plot { color: #93c5fd; }
.built-meta { color: #94a3b8; white-space: nowrap; }
.built-status { padding: 1px 6px; border-radius: 4px; font-size: 10px; font-weight: 700; white-space: nowrap; }
.bs-operating { background: rgba(34,197,94,0.18); color: #4ade80; }
.bs-building { background: rgba(56,189,248,0.18); color: #38bdf8; }
.bs-manual-off { background: rgba(239,68,68,0.18); color: #f87171; }
.bs-auto-off { background: rgba(251,191,36,0.18); color: #fbbf24; }
.built-ops { margin-left: auto; display: flex; align-items: center; gap: 6px; }
.built-queued { font-size: 10px; font-weight: 700; color: #38bdf8; white-space: nowrap; }

/* 破产弹窗内嵌的每日结算日志 */
.bankrupt-log { margin-top: 4px; padding: 12px; background: rgba(255,255,255,0.03); border: 1px solid rgba(255,255,255,0.08); border-radius: 10px; }
.bankrupt-log-body { max-height: 220px; overflow-y: auto; display: flex; flex-direction: column; gap: 3px; }
.blog-row { display: flex; align-items: center; gap: 10px; padding: 4px 8px; font-size: 12px; border-radius: 6px; background: rgba(255,255,255,0.02); }
.blog-day { flex: 1; color: #cbd5e1; font-weight: 600; }
.blog-cash { color: #94a3b8; }
.blog-rep { color: #fbbf24; }

/* 完整日志弹窗 */
.log-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(10,10,14,0.88); backdrop-filter: blur(8px); z-index: 10001; display: flex; align-items: center; justify-content: center; }
.log-panel { width: 760px; max-width: 92vw; max-height: 86vh; display: flex; flex-direction: column; border-radius: 14px; border: 1px solid rgba(168,85,247,0.45); background: rgba(15,23,42,0.97); overflow: hidden; }
.log-head { display: flex; justify-content: space-between; align-items: center; padding: 14px 18px; border-bottom: 1px solid rgba(255,255,255,0.1); }
.log-head h3 { margin: 0; font-size: 15px; color: #c084fc; }
.log-close { background: transparent; border: none; color: #94a3b8; font-size: 16px; cursor: pointer; padding: 2px 8px; border-radius: 6px; }
.log-close:hover { background: rgba(255,255,255,0.1); color: #fff; }
.log-body { flex: 1; overflow-y: auto; padding: 12px 16px; display: flex; flex-direction: column; gap: 8px; }
.log-day { padding: 8px 10px; background: rgba(255,255,255,0.03); border: 1px solid rgba(255,255,255,0.06); border-radius: 8px; }
.log-day-head { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; font-size: 12px; }
.ld-day { font-weight: 700; color: #e2e8f0; }
.ld-season { color: #4ade80; }
.ld-net { font-weight: 700; }
.ld-cash { color: #34d399; }
.ld-rep { color: #fbbf24; }
.log-day-detail { margin-top: 4px; font-size: 11px; color: #94a3b8; line-height: 1.5; }
.log-day-lines { margin-top: 3px; }
.log-line { font-size: 11px; color: #cbd5e1; line-height: 1.5; }
</style>
