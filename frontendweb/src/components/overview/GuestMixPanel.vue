<script setup>
// 今日客群洞察：各客群意向客流（柱状对比）+ 实际接待客群构成（环形饼图）
import { computed } from 'vue';
import { store } from '../../services/store.js';
import { fmtInt } from '../../utils/format.js';

const SEGMENT_COLORS = {
    FAMILY: '#7ee0a3',
    BUSINESS: '#8ab4ff',
    ROMANTIC: '#f4a5d0',
    WELLNESS: '#ffd37a',
};

// 环形图几何参数
const R = 44;
const CIRC = 2 * Math.PI * R;

const p = computed(() => store.state?.todayPreview || null);

const segments = computed(() => {
    const preview = p.value;
    return (store.config?.segments || []).map((s) => ({
        id: s.id,
        name: s.displayName,
        color: SEGMENT_COLORS[s.id] || '#8b9bc0',
        potential: Number(preview?.potentialBySegment?.[s.id] || 0),
        intent: Number(preview?.intentBySegment?.[s.id] || 0),
        served: Number(preview?.servedBySegment?.[s.id] || 0),
    }));
});

const totalIntent = computed(() => segments.value.reduce((a, s) => a + s.intent, 0));
const totalServed = computed(() => segments.value.reduce((a, s) => a + s.served, 0));
const overflow = computed(() => Math.max(0, totalIntent.value - totalServed.value));
const maxIntent = computed(() => Math.max(1, ...segments.value.map((s) => s.intent)));

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
</script>

<template>
    <div class="panel">
        <div class="panel-head">
            <span class="title">今日客群</span>
            <span class="extra faint">意向客流 → 实际接待</span>
        </div>

        <div class="panel-body" v-if="p">
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
                        <div class="num big">{{ fmtInt(totalServed) }}</div>
                        <div class="faint small">实际接待</div>
                    </div>
                </div>

                <div class="legend-col">
                    <div v-for="s in segments" :key="s.id" class="lg-row">
                        <span class="dot" :style="{ background: s.color }"></span>
                        <span class="nm">{{ s.name }}</span>
                        <span class="num">{{ fmtInt(s.served) }} 人
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
                        <span class="num">{{ fmtInt(s.intent) }} 人
                            <span class="faint">· {{ share(s.intent, totalIntent) }}</span></span>
                    </div>
                    <div class="pbar">
                        <span :style="{ width: (s.intent / maxIntent * 100) + '%', background: s.color }"></span>
                    </div>
                </div>
            </div>

            <div class="muted-note">
                意向客流 = 潜在客流 × 转化率；床位容量不足时超出部分直接流失（今日溢出 {{ fmtInt(overflow) }} 人）。
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
</style>
