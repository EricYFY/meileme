<template>
  <div v-if="visible" class="rr-overlay" @click.self="$emit('close')">
    <div class="rr-panel glass-panel">
      <div class="rr-header">
        <h3>📊 经营报告</h3>
        <button class="rr-close" @click="$emit('close')">✕</button>
      </div>

      <!-- 区间选择 -->
      <div class="rr-ranges">
        <button
          v-for="d in RANGE_OPTIONS"
          :key="d"
          class="rr-range-btn"
          :class="{ active: activeRange === d }"
          @click="pick(d)"
        >
          {{ rangeLabel(d) }}
        </button>
      </div>

      <div v-if="report" class="rr-body">
        <div class="rr-span">
          第 {{ report.fromDay }} ~ {{ report.toDay }} 天 · 实际覆盖 {{ report.dayCount }} 天
        </div>

        <!-- 汇总指标 -->
        <div class="rr-grid">
          <div class="rr-item"><span>净利润</span>
            <b :class="report.netProfit >= 0 ? 'g' : 'r'">
              {{ report.netProfit >= 0 ? '+' : '' }}¥{{ fmtInt(report.netProfit) }}
            </b>
          </div>
          <div class="rr-item"><span>资金变化</span>
            <b>¥{{ fmtInt(report.cashStart) }} → ¥{{ fmtInt(report.cashEnd) }}</b>
          </div>
          <div class="rr-item"><span>累计收入</span><b>¥{{ fmtInt(report.totalRevenue) }}</b></div>
          <div class="rr-item"><span>累计支出</span><b class="r">¥{{ fmtInt(report.totalExpense) }}</b></div>
          <div class="rr-item"><span>平均入住率</span><b>{{ (report.avgOccupancyRate || 0).toFixed(1) }}%</b></div>
          <div class="rr-item"><span>接待 / 溢出</span><b>{{ report.totalGuestsServed }} / {{ report.totalTurnedAway }}</b></div>
          <div class="rr-item"><span>口碑变化</span>
            <b :class="report.reputationDelta >= 0 ? 'g' : 'r'">
              {{ (report.reputationStart || 0).toFixed(1) }} → {{ (report.reputationEnd || 0).toFixed(1) }}
            </b>
          </div>
          <div class="rr-item"><span>团建满足 / 违约</span><b>{{ report.teamOrdersAccepted }} / {{ report.teamOrdersDefaulted }}</b></div>
        </div>

        <!-- 收支明细 -->
        <div class="rr-block">
          <div class="rr-title">收支构成</div>
          <div class="rr-row"><span>客房费</span><b>¥{{ fmtInt(rev('roomFee')) }}</b></div>
          <div class="rr-row"><span>餐饮</span><b>¥{{ fmtInt(rev('dining')) }}</b></div>
          <div class="rr-row"><span>游乐</span><b>¥{{ fmtInt(rev('recreation')) }}</b></div>
          <div class="rr-row"><span>团建收入</span><b>¥{{ fmtInt(rev('teamOrder')) }}</b></div>
          <div class="rr-row"><span>员工薪资</span><b class="r">¥{{ fmtInt(exp('staffSalary')) }}</b></div>
          <div class="rr-row"><span>建筑维护</span><b class="r">¥{{ fmtInt(exp('buildingMaintenance')) }}</b></div>
          <div class="rr-row"><span>广告预算</span><b class="r">¥{{ fmtInt(exp('adBudget')) }}</b></div>
          <div class="rr-row"><span>事件支出</span><b class="r">¥{{ fmtInt(exp('eventCost')) }}</b></div>
          <div class="rr-row"><span>团建罚款</span><b class="r">¥{{ fmtInt(exp('teamPenalty')) }}</b></div>
        </div>

        <!-- 事件统计 -->
        <div class="rr-block">
          <div class="rr-title">事件统计</div>
          <div v-if="eventKeys.length" class="rr-tags">
            <span v-for="k in eventKeys" :key="k" class="rr-tag">{{ k }} ×{{ report.eventCounts[k] }}</span>
          </div>
          <div v-else class="rr-empty-inline">区间内无随机事件</div>
        </div>

        <!-- 走势 -->
        <div class="rr-block">
          <div class="rr-title">每日净利润走势</div>
          <svg v-if="chart.points" class="rr-spark" :viewBox="`0 0 ${chart.w} ${chart.h}`" preserveAspectRatio="none">
            <line x1="0" :y1="chart.zeroY" :x2="chart.w" :y2="chart.zeroY" stroke="rgba(148,163,184,0.4)" stroke-width="1" />
            <polyline :points="chart.points" fill="none" stroke="#38bdf8" stroke-width="2" />
          </svg>
          <div v-else class="rr-empty-inline">无数据</div>
          <div v-if="report.bestDay" class="rr-row">
            <span>最佳日 第 {{ report.bestDay.day }} 天（{{ report.bestDay.seasonName }}）</span>
            <b class="g">+¥{{ fmtInt(report.bestDay.netProfit) }}</b>
          </div>
          <div v-if="report.worstDay" class="rr-row">
            <span>最差日 第 {{ report.worstDay.day }} 天（{{ report.worstDay.seasonName }}）</span>
            <b class="r">¥{{ fmtInt(report.worstDay.netProfit) }}</b>
          </div>
        </div>
      </div>

      <div v-else class="rr-body">
        <div class="rr-empty-inline">点击上方区间生成报告</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  visible: { type: Boolean, default: false },
  report: { type: Object, default: null }
});
const emit = defineEmits(['close', 'request']);

const RANGE_OPTIONS = [1, 7, 30, 365];
const activeRange = computed(() => (props.report && props.report.rangeDays) || 7);

const rangeLabel = (d) => (d === 1 ? '过去 1 天' : d === 365 ? '过去 1 年' : `过去 ${d} 天`);

const fmtInt = (v) => Math.round(Number(v) || 0).toLocaleString('en-US');

const rev = (k) => ((props.report && props.report.revenueDetail) || {})[k] || 0;
const exp = (k) => ((props.report && props.report.expenseDetail) || {})[k] || 0;

const eventKeys = computed(() => Object.keys((props.report && props.report.eventCounts) || {}));

// 内联 SVG 迷你走势（净利润曲线）
const chart = computed(() => {
  const w = 300, h = 60;
  const s = (props.report && props.report.dailySeries) || [];
  if (!s.length) return { w, h, points: '', zeroY: h };
  const vals = s.map(p => Number(p.netProfit) || 0);
  const min = Math.min(...vals, 0);
  const max = Math.max(...vals, 0);
  const span = (max - min) || 1;
  const yOf = (v) => h - ((v - min) / span) * h;
  const stepX = s.length > 1 ? w / (s.length - 1) : 0;
  const points = vals
    .map((v, i) => `${(s.length > 1 ? i * stepX : w / 2).toFixed(1)},${yOf(v).toFixed(1)}`)
    .join(' ');
  return { w, h, points, zeroY: yOf(0).toFixed(1) };
});

const pick = (d) => emit('request', d);
</script>

<style scoped>
.rr-overlay {
  position: fixed; inset: 0; background: rgba(10,10,14,0.85); backdrop-filter: blur(8px);
  z-index: 9997; display: flex; align-items: center; justify-content: center;
}
.rr-panel { width: 520px; max-width: 94vw; max-height: 88vh; overflow-y: auto; padding: 20px; border-radius: 14px; display: flex; flex-direction: column; gap: 12px; border: 1px solid rgba(255,255,255,0.1); }
.rr-header { display: flex; justify-content: space-between; align-items: center; }
.rr-header h3 { margin: 0; font-size: 16px; color: var(--accent-color); }
.rr-close { background: transparent; border: none; color: #94a3b8; font-size: 16px; cursor: pointer; }
.rr-close:hover { color: #fff; }
.rr-ranges { display: flex; gap: 6px; }
.rr-range-btn { flex: 1; padding: 7px 4px; background: rgba(56,189,248,0.1); border: 1px solid rgba(56,189,248,0.3); color: #7dd3fc; border-radius: 6px; font-size: 12px; font-weight: 700; cursor: pointer; }
.rr-range-btn:hover { background: rgba(56,189,248,0.22); }
.rr-range-btn.active { background: rgba(56,189,248,0.35); color: #fff; box-shadow: 0 0 8px rgba(56,189,248,0.45); }
.rr-body { display: flex; flex-direction: column; gap: 10px; }
.rr-span { font-size: 12px; color: #94a3b8; text-align: center; }
.rr-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; }
.rr-item { display: flex; justify-content: space-between; gap: 8px; font-size: 11px; color: #94a3b8; background: rgba(0,0,0,0.25); padding: 5px 8px; border-radius: 6px; }
.rr-item b { color: #fff; font-family: monospace; }
.rr-block { background: rgba(0,0,0,0.2); border-radius: 8px; padding: 8px; display: flex; flex-direction: column; gap: 4px; }
.rr-title { font-size: 12px; font-weight: 700; color: #93c5fd; margin-bottom: 2px; }
.rr-row { display: flex; justify-content: space-between; font-size: 11px; color: #cbd5e1; }
.rr-row b { font-family: monospace; color: #e2e8f0; }
.rr-tags { display: flex; flex-wrap: wrap; gap: 5px; }
.rr-tag { font-size: 11px; padding: 2px 8px; border-radius: 4px; background: rgba(251,191,36,0.15); color: #fbbf24; }
.rr-spark { width: 100%; height: 64px; background: rgba(0,0,0,0.3); border-radius: 6px; }
.rr-empty-inline { font-size: 11px; color: #64748b; text-align: center; padding: 8px; }
.g { color: #34d399; }
.r { color: #f87171; }
</style>
