<script setup>
// 今日客群洞察：接待构成（环形图） / 客群转化漏斗与吸引力得分（双视图自由切换）
import { computed, ref } from 'vue';
import { store } from '../../services/store.js';
import { fmtInt } from '../../utils/format.js';

const SEGMENT_COLORS = {
    FAMILY: '#7ee0a3',
    BUSINESS: '#8ab4ff',
    ROMANTIC: '#f4a5d0',
    WELLNESS: '#ffd37a',
};

const SEGMENT_ICONS = {
    FAMILY: '👨👩👧',
    BUSINESS: '💼',
    ROMANTIC: '👩❤️👨',
    WELLNESS: '🧘',
};

// 当前视图模式：'donut'（接待构成）或 'funnel'（转化漏斗与吸引力）
const activeView = ref('funnel');

// 环形图几何参数
const R = 44;
const CIRC = 2 * Math.PI * R;

const p = computed(() => store.state?.todayPreview || null);

const segments = computed(() => {
    const preview = p.value;
    return (store.config?.segments || []).map((s) => {
        const potential = Number(preview?.potentialBySegment?.[s.id] || 0);
        const intent = Number(preview?.intentBySegment?.[s.id] || 0);
        const served = Number(preview?.servedBySegment?.[s.id] || 0);
        const attraction = Number(preview?.attractionBySegment?.[s.id] || 0);
        const conversionRaw = Number(preview?.conversionBySegment?.[s.id] || 0);
        const conversion = conversionRaw > 0 ? conversionRaw : (potential > 0 ? intent / potential : 0);
        const overflow = Math.max(0, intent - served);
        return {
            id: s.id,
            name: s.displayName,
            icon: s.icon || SEGMENT_ICONS[s.id] || '👥',
            color: s.color || SEGMENT_COLORS[s.id] || '#8b9bc0',
            potential,
            attraction,
            conversion,
            intent,
            served,
            overflow,
        };
    });
});

const totalPotential = computed(() => segments.value.reduce((a, s) => a + s.potential, 0));
const totalIntent = computed(() => segments.value.reduce((a, s) => a + s.intent, 0));
const totalServed = computed(() => segments.value.reduce((a, s) => a + s.served, 0));
const totalAttraction = computed(() => segments.value.reduce((a, s) => a + s.attraction, 0));
const avgConversion = computed(() => totalPotential.value > 0 ? (totalIntent.value / totalPotential.value) : 0);
const overflow = computed(() => Math.max(0, totalIntent.value - totalServed.value));
const maxIntent = computed(() => Math.max(1, ...segments.value.map((s) => s.intent)));
const maxPotential = computed(() => Math.max(1, ...segments.value.map((s) => s.potential)));

// 环形图切片：按实际接待人数占比换算弧长与偏移
const slices = computed(() => {
    const total = totalServed.value;
    if (total <= 0) return [];
    let acc = 0;
    const out = [];
    for (const s of segments.value) {
        if (s.served <= 0) continue;
        const len = (s.served / total) * CIRC;
        out.push({ ...s, len, offset: acc, share: s.served / total });
        acc += len;
    }
    return out;
});

/** 占比百分比（保留 1 位小数） */
function share(v, total) {
    if (!total) return '0%';
    return (Math.round((v / total) * 1000) / 10) + '%';
}

function pct(v) {
    return (Math.round(v * 1000) / 10) + '%';
}

function fmtNum2(v) {
    if (v === undefined || v === null || isNaN(v)) return '0.00';
    return Number(v).toFixed(2);
}
</script>

<template>
    <div class="panel">
        <div class="panel-head">
            <span class="title">今日客群洞察</span>
            <div class="view-switch">
                <button :class="{ active: activeView === 'funnel' }" @click="activeView = 'funnel'">
                    吸引力漏斗
                </button>
                <button :class="{ active: activeView === 'donut' }" @click="activeView = 'donut'">
                    接待构成
                </button>
            </div>
        </div>

        <div class="panel-body" v-if="p">
            <!-- 视图一：全链路客流转化漏斗与吸引力得分 -->
            <div v-if="activeView === 'funnel'" class="funnel-container">
                <div class="kpi-strip">
                    <div class="kpi-item">
                        <span class="kpi-label">客群综合吸引力</span>
                        <span class="kpi-val gold">{{ fmtNum2(totalAttraction) }}</span>
                    </div>
                    <div class="kpi-item">
                        <span class="kpi-label">平均客流转化率</span>
                        <span class="kpi-val cyan">{{ pct(avgConversion) }}</span>
                    </div>
                    <div class="kpi-item">
                        <span class="kpi-label">床位满载溢出</span>
                        <span class="kpi-val" :class="overflow > 0 ? 'warn' : 'pos'">{{ fmtNum2(overflow) }} 人</span>
                    </div>
                </div>

                <div class="funnel-list">
                    <div v-for="s in segments" :key="s.id" class="funnel-card">
                        <div class="card-top">
                            <span class="seg-title">
                                <span class="seg-icon">{{ s.icon }}</span>
                                <span class="seg-name" :style="{ color: s.color }">{{ s.name }}</span>
                            </span>
                            <div class="attr-badge">
                                <span class="faint">吸引力</span>
                                <span class="score gold">{{ fmtNum2(s.attraction) }}</span>
                                <span class="rate-pill" :style="{ borderColor: s.color, color: s.color }">
                                    转化率 {{ pct(s.conversion) }}
                                </span>
                            </div>
                        </div>

                        <!-- 漏斗步骤条 -->
                        <div class="funnel-bars">
                            <div class="f-step">
                                <div class="step-lbl"><span class="faint">潜在客源</span> <b>{{ fmtNum2(s.potential) }}</b></div>
                                <div class="step-bar">
                                    <span class="fill potential" :style="{ width: (s.potential / maxPotential * 100) + '%' }"></span>
                                </div>
                            </div>
                            <div class="funnel-arrow">➔</div>
                            <div class="f-step">
                                <div class="step-lbl"><span class="faint">意向到访</span> <b :style="{ color: s.color }">{{ fmtNum2(s.intent) }}</b></div>
                                <div class="step-bar">
                                    <span class="fill intent" :style="{ width: (s.intent / maxPotential * 100) + '%', background: s.color }"></span>
                                </div>
                            </div>
                            <div class="funnel-arrow">➔</div>
                            <div class="f-step">
                                <div class="step-lbl">
                                    <span class="faint">实际入住</span> <b>{{ fmtNum2(s.served) }}</b>
                                    <span v-if="s.overflow > 0" class="warn-text">(流失 {{ fmtNum2(s.overflow) }})</span>
                                </div>
                                <div class="step-bar">
                                    <span class="fill served" :style="{ width: (s.served / maxPotential * 100) + '%' }"></span>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
                <div class="muted-note">
                    吸引力由各建筑基础偏好、内装加成与综合舒适度共同决定；意向客流受床位上限约束，溢出部分流失。
                </div>
            </div>

            <!-- 视图二：环形饼图与意向客流 -->
            <div v-else>
                <div class="mix">
                    <div class="donut-wrap">
                        <svg viewBox="0 0 120 120" class="donut">
                            <circle cx="60" cy="60" :r="R" class="donut-track" />
                            <circle v-for="s in slices" :key="s.id" cx="60" cy="60" :r="R" class="donut-slice"
                                    :stroke="s.color"
                                    :stroke-dasharray="`${s.len} ${CIRC - s.len}`"
                                    :stroke-dashoffset="-s.offset"
                                    transform="rotate(-90 60 60)" />
                        </svg>
                        <div class="donut-center">
                            <div class="num big">{{ fmtNum2(totalServed) }}</div>
                            <div class="faint small">实际接待</div>
                        </div>
                    </div>

                    <div class="legend-col">
                        <div v-for="s in segments" :key="s.id" class="lg-row">
                            <span class="dot" :style="{ background: s.color }"></span>
                            <span class="nm">{{ s.name }}</span>
                            <span class="num">{{ fmtNum2(s.served) }} 人
                                <span class="faint">· {{ share(s.served, totalServed) }}</span></span>
                        </div>
                        <div v-if="!totalServed" class="faint small">今日暂无实际入住</div>
                    </div>
                </div>

                <h4 class="sub-title">今日各客群意向客流</h4>
                <div class="intent-list">
                    <div v-for="s in segments" :key="s.id" class="intent-row">
                        <div class="line">
                            <span class="nm"><span class="dot" :style="{ background: s.color }"></span>{{ s.name }}</span>
                            <span class="num">{{ fmtNum2(s.intent) }} 人
                                <span class="faint">· {{ share(s.intent, totalIntent) }}</span></span>
                        </div>
                        <div class="pbar">
                            <span :style="{ width: (s.intent / maxIntent * 100) + '%', background: s.color }"></span>
                        </div>
                    </div>
                </div>

                <div class="muted-note">
                    意向客流 = 潜在客流 × 转化率；床位容量不足时超出部分直接流失（今日溢出 {{ fmtNum2(overflow) }} 人）。
                </div>
            </div>
        </div>

        <div class="panel-body empty" v-else>
            <span class="big">·</span>暂无客群数据
        </div>
    </div>
</template>

<style scoped>
.mix {
    display: flex;
    align-items: center;
    gap: 18px;
    flex-wrap: wrap;
}

.donut-wrap {
    position: relative;
    width: 132px;
    height: 132px;
    flex: 0 0 auto;
}

.donut {
    width: 132px;
    height: 132px;
    display: block;
}

.donut-track {
    fill: none;
    stroke: var(--panel-3);
    stroke-width: 14;
}

.donut-slice {
    fill: none;
    stroke-width: 14;
    stroke-linecap: butt;
    transition: stroke-dasharray .2s;
}

.donut-center {
    position: absolute;
    inset: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    pointer-events: none;
}

.donut-center .big {
    font-size: 22px;
    font-weight: 700;
}

.donut-center .small {
    font-size: 11px;
}

.legend-col {
    flex: 1;
    min-width: 150px;
    display: flex;
    flex-direction: column;
    gap: 6px;
}

.lg-row {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 12px;
}

.lg-row .nm {
    color: var(--text-dim);
}

.lg-row .num {
    margin-left: auto;
}

.dot {
    display: inline-block;
    width: 9px;
    height: 9px;
    border-radius: 50%;
}

.sub-title {
    margin: 16px 0 10px;
    font-size: 13px;
    color: var(--text);
    border-left: 3px solid var(--accent);
    padding-left: 8px;
    font-weight: 600;
}

.intent-list {
    display: flex;
    flex-direction: column;
    gap: 9px;
}

.intent-row .line {
    display: flex;
    justify-content: space-between;
    font-size: 12px;
    margin-bottom: 4px;
}

.intent-row .line .nm {
    display: flex;
    align-items: center;
    gap: 6px;
    color: var(--text-dim);
}

.small {
    font-size: 11px;
}

/* 视图切换按钮组 */
.view-switch {
    display: flex;
    background: var(--panel-3, #1e2433);
    padding: 2px;
    border-radius: 6px;
    gap: 2px;
}

.view-switch button {
    background: transparent;
    border: none;
    color: var(--text-dim, #8b9bc0);
    padding: 3px 8px;
    font-size: 11px;
    border-radius: 4px;
    cursor: pointer;
    transition: all .2s;
}

.view-switch button.active {
    background: var(--accent, #6366f1);
    color: #fff;
    font-weight: 600;
}

/* 顶部关键指标栏 */
.kpi-strip {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 8px;
    margin-bottom: 12px;
}

.kpi-item {
    background: rgba(255, 255, 255, 0.03);
    border: 1px solid rgba(255, 255, 255, 0.06);
    border-radius: 6px;
    padding: 6px 10px;
    display: flex;
    flex-direction: column;
}

.kpi-label {
    font-size: 10px;
    color: var(--text-dim, #8b9bc0);
}

.kpi-val {
    font-size: 14px;
    font-weight: 700;
    margin-top: 2px;
}

.cyan { color: #5eead4; }
.warn { color: #ff8787; }
.pos { color: #7ee0a3; }

/* 漏斗卡片列表 */
.funnel-list {
    display: flex;
    flex-direction: column;
    gap: 10px;
}

.funnel-card {
    background: rgba(255, 255, 255, 0.02);
    border: 1px solid rgba(255, 255, 255, 0.06);
    border-radius: 8px;
    padding: 8px 10px;
}

.card-top {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 8px;
}

.seg-title {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    font-weight: 600;
}

.attr-badge {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 11px;
}

.attr-badge .score {
    font-weight: 700;
}

.rate-pill {
    border: 1px solid;
    padding: 1px 6px;
    border-radius: 10px;
    font-size: 10px;
    font-weight: 600;
    background: rgba(0, 0, 0, 0.2);
}

/* 漏斗步骤条 */
.funnel-bars {
    display: flex;
    align-items: center;
    gap: 6px;
}

.f-step {
    flex: 1;
}

.step-lbl {
    font-size: 10px;
    display: flex;
    justify-content: space-between;
    margin-bottom: 3px;
}

.warn-text {
    color: #ff8787;
    font-size: 9px;
}

.step-bar {
    height: 5px;
    background: rgba(255, 255, 255, 0.06);
    border-radius: 3px;
    overflow: hidden;
}

.step-bar .fill {
    display: block;
    height: 100%;
    border-radius: 3px;
    transition: width .3s ease;
}

.step-bar .fill.potential {
    background: #64748b;
}

.step-bar .fill.served {
    background: #7ee0a3;
}

.funnel-arrow {
    color: rgba(255, 255, 255, 0.2);
    font-size: 10px;
    user-select: none;
}
</style>
