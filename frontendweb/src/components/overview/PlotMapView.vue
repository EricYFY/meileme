<script setup>
import { computed } from 'vue';
import { store } from '../../services/store.js';
import { buildGuestDots } from '../../utils/guestAnimation.js';
import { fmtInt, fmtPercent } from '../../utils/format.js';
import BuildingIcon from './BuildingIcon.vue';
import BuildSlot from './BuildSlot.vue';

const emit = defineEmits(['open-building', 'open-build']);

const guestCount = computed(() => Math.round(store.state?.todayPreview?.expectedGuests || 0) || 0);
const dots = computed(() => buildGuestDots(guestCount.value, 50));

const regionGroups = computed(() => {
    const st = store.state;
    if (!st || !store.config) return [];
    const unlockedRegions = st.unlockedRegions || [];
    const unlockedPlots = new Set(st.unlockedPlots || []);
    const plots = (store.config.plots || []).filter((p) => unlockedPlots.has(p.id));
    const out = [];
    for (const r of store.config.regions || []) {
        if (!unlockedRegions.includes(r.id)) continue;
        const list = plots.filter((p) => p.region === r.id).sort((a, b) => a.orderInRegion - b.orderInRegion);
        if (list.length) out.push({ region: r.id, name: r.displayName, plots: list });
    }
    return out;
});

const buildingsByPlot = computed(() => {
    const m = {};
    for (const b of store.state?.buildings || []) {
        if (!m[b.plotId]) m[b.plotId] = [];
        m[b.plotId].push(b);
    }
    return m;
});

function slots(plot) {
    const n = plot.buildLimit || 0;
    const arr = [];
    const list = buildingsByPlot.value[plot.id] || [];
    for (let i = 0; i < n; i++) arr.push(list[i] || null);
    return arr;
}

function plotBuildable(plot) {
    const whitelist = [...(plot.accommodationWhitelist || []), ...(plot.supportWhitelist || [])];
    return whitelist.some((mid) => store.config?.mothersById?.[mid]?.phase1Buildable);
}

const abilityRows = computed(() => {
    const st = store.state;
    if (!st || !store.config) return [];
    return (store.config.abilities || [])
        .map((a) => {
            const d = (st.abilityDemand || {})[a.id] || 0;
            const s = (st.abilitySupply || {})[a.id] || 0;
            const r = (st.abilityRatios || {})[a.id];
            return { id: a.id, name: a.displayName, demand: d, supply: s, ratio: r == null ? 1 : r };
        })
        .filter((r) => r.demand > 0);
});
</script>

<template>
    <div class="overview">
        <div class="guest-field">
            <div class="guest-hint">今日预计接待 {{ fmtInt(guestCount) }} 人</div>
            <span v-for="d in dots" :key="d.id" class="guest-dot"
                  :style="{ left: d.left + '%', top: d.top + '%', width: d.size + 'px', height: d.size + 'px', background: d.color, animationDuration: d.duration + 's', animationDelay: d.delay + 's' }"></span>
        </div>

        <div class="panel">
            <div class="panel-head"><span class="title">人力供需</span><span class="extra faint">供给不足仅降低相关建筑舒适度</span></div>
            <div class="panel-body">
                <div v-if="!abilityRows.length" class="empty faint">暂无能力需求</div>
                <div v-for="r in abilityRows" :key="r.id" class="kv">
                    <span class="k">{{ r.name }}</span>
                    <span class="v num" :class="r.ratio < 1 ? 'neg' : 'pos'">
                        {{ fmtInt(r.supply) }} / {{ fmtInt(r.demand) }}（{{ fmtPercent(r.ratio) }}）
                    </span>
                </div>
            </div>
        </div>

        <div class="plot-grid">
            <div v-for="g in regionGroups" :key="g.region" class="region-group">
                <div class="region-head">{{ g.name }}</div>
                <div class="plot-cards">
                    <div v-for="plot in g.plots" :key="plot.id" class="plot-card">
                        <div class="plot-name">{{ plot.displayName }}</div>
                        <div class="plot-meta">已建 {{ (buildingsByPlot[plot.id] || []).length }} / {{ plot.buildLimit }}</div>
                        <div class="slots">
                            <template v-for="(b, i) in slots(plot)" :key="plot.id + '-' + i">
                                <div v-if="b" class="slot filled" @click="emit('open-building', b)">
                                    <BuildingIcon :building="b" />
                                </div>
                                <BuildSlot v-else :buildable="plotBuildable(plot)" @click="emit('open-build', plot)" />
                            </template>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<style scoped>
.overview { display: flex; flex-direction: column; gap: 16px; }
.guest-hint {
    position: absolute; top: 10px; left: 12px; font-size: 12px; color: var(--text-dim);
    background: rgba(20, 30, 55, .8); padding: 3px 10px; border-radius: 20px; z-index: 2;
}
</style>