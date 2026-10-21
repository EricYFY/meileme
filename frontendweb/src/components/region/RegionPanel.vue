<script setup>
import { computed } from 'vue';
import { store, api } from '../../services/store.js';
import { fmtMoney } from '../../utils/format.js';

const regionGroups = computed(() => {
    if (!store.config) return [];
    const plots = store.config.plots || [];
    const out = [];
    for (const r of store.config.regions || []) {
        const list = plots.filter((p) => p.region === r.id).sort((a, b) => a.orderInRegion - b.orderInRegion);
        if (list.length) out.push({ region: r.id, name: r.displayName, flow: r.baseDailyFlow, plots: list });
    }
    return out;
});

/** 全局地块顺序（用于「上一地块」锁判定） */
const plotOrder = computed(() => (store.config?.plots || []).map((p) => p.id));

const unlockedPlots = computed(() => new Set(store.state?.unlockedPlots || []));
const unlockedRegions = computed(() => new Set(store.state?.unlockedRegions || []));
const unlockedAch = computed(() => new Set(store.state?.unlockedAchievements || []));

function motherName(mid) {
    return store.config?.mothersById?.[mid]?.displayName || mid;
}

function plotCount(plot) {
    return (store.state?.buildings || []).filter((b) => b.plotId === plot.id && b.alive).length;
}

function regionUnlocked(rid) {
    return unlockedRegions.value.has(rid);
}

function previousPlotId(plot) {
    const idx = plotOrder.value.indexOf(plot.id);
    return idx > 0 ? plotOrder.value[idx - 1] : null;
}

function lockReasons(plot) {
    const reasons = [];
    const prevId = previousPlotId(plot);
    if (prevId && !unlockedPlots.value.has(prevId)) {
        const prev = store.config?.plotsById?.[prevId];
        reasons.push('需先解锁上一地块「' + (prev?.displayName || prevId) + '」');
    }
    for (const aid of plot.unlockAchievementIds || []) {
        if (!unlockedAch.value.has(aid)) {
            const a = store.config?.achievementsById?.[aid];
            reasons.push('达成「' + (a?.displayName || aid) + '」');
        }
    }
    return reasons;
}

/** 前置条件（上一地块 + 成就）已满足，仅剩支付解锁费用 */
function eligible(plot) {
    if (unlockedPlots.value.has(plot.id)) return false;
    return lockReasons(plot).length === 0;
}

function costOf(plot) {
    return plot.unlockCost || 0;
}

function canAfford(plot) {
    return (store.state?.funds || 0) >= costOf(plot);
}
</script>

<template>
    <div class="panel">
        <div class="panel-head">
            <span class="title">区块地图</span>
            <span class="extra faint">地块按顺序链式解锁：先解锁上一地块 → 达成成就 → 支付解锁费用；技能点随地块发放</span>
        </div>
        <div class="panel-body">
            <div class="plot-grid">
                <div v-for="g in regionGroups" :key="g.region" class="region-group">
                    <div class="region-head">
                        {{ g.name }}
                        <span class="faint" style="font-weight: 400; font-size: 12px">每日基础客流 {{ g.flow }} 人</span>
                        <span v-if="!regionUnlocked(g.region)" class="faint" style="font-weight: 400; font-size: 12px">· 未解锁</span>
                    </div>
                    <div class="plot-cards">
                        <div v-for="plot in g.plots" :key="plot.id" class="plot-card" :class="{ locked: !unlockedPlots.has(plot.id) }">
                            <div class="plot-name">{{ plot.displayName }}</div>
                            <div class="plot-meta">建筑 {{ plotCount(plot) }} / {{ plot.buildLimit }}</div>
                            <div class="plot-meta">
                                住宿：{{ (plot.accommodationWhitelist || []).map((m) => motherName(m)).join(' / ') || '—' }}
                            </div>
                            <div v-if="unlockedPlots.has(plot.id)" class="lock-msg pos">已解锁</div>
                            <div v-else-if="eligible(plot)" class="lock-msg">
                                <div class="pos">前置条件已满足</div>
                                <div>解锁费用 {{ fmtMoney(costOf(plot)) }}</div>
                                <button class="primary" style="margin-top: 8px; width: 100%"
                                        :disabled="!canAfford(plot)"
                                        @click="api.unlockPlot(plot.id)">支付并解锁（次日生效）</button>
                            </div>
                            <div v-else class="lock-msg">
                                <div v-for="(r, i) in lockReasons(plot)" :key="i">· {{ r }}</div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<style scoped>
.plot-card { position: relative; }
</style>
