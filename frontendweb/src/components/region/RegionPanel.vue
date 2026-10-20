<script setup>
import { computed } from 'vue';
import { store } from '../../services/store.js';

const REGION_NAMES = { VILLAGE_A: 'A村', TOWN_B: 'B镇' };
const REGION_ORDER = ['VILLAGE_A', 'TOWN_B'];
const REGION_GATE_ACH = 'ACH_A_TOTAL';

const regionGroups = computed(() => {
    if (!store.config) return [];
    const out = [];
    for (const rid of REGION_ORDER) {
        const plots = (store.config.plots || []).filter((p) => p.region === rid).sort((a, b) => a.orderInRegion - b.orderInRegion);
        if (plots.length) out.push({ region: rid, name: REGION_NAMES[rid] || rid, plots });
    }
    return out;
});

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

function lockReasons(plot) {
    const reasons = [];
    if (!regionUnlocked(plot.region)) {
        reasons.push('需先解锁' + (REGION_NAMES[plot.region] || plot.region));
    }
    for (const aid of plot.unlockAchievementIds || []) {
        if (!unlockedAch.value.has(aid)) {
            const a = store.config?.achievementsById?.[aid];
            reasons.push('达成「' + (a?.displayName || aid) + '」');
        }
    }
    return reasons;
}

function regionGateText(rid) {
    if (rid === 'TOWN_B') {
        const a = store.config?.achievementsById?.[REGION_GATE_ACH];
        return '达成 A 村总成就「' + (a?.displayName || REGION_GATE_ACH) + '」后解锁';
    }
    return '';
}
</script>

<template>
    <div class="panel">
        <div class="panel-head">
            <span class="title">区块地图</span>
            <span class="extra faint">大区域 → 地块，技能点随地块解锁发放</span>
        </div>
        <div class="panel-body">
            <div class="plot-grid">
                <div v-for="g in regionGroups" :key="g.region" class="region-group">
                    <div class="region-head">
                        {{ g.name }}
                        <span v-if="!regionUnlocked(g.region)" class="faint" style="font-weight: 400; font-size: 12px">{{ regionGateText(g.region) }}</span>
                    </div>
                    <div class="plot-cards">
                        <div v-for="plot in g.plots" :key="plot.id" class="plot-card" :class="{ locked: !unlockedPlots.has(plot.id) }">
                            <div class="plot-name">{{ plot.displayName }}</div>
                            <div class="plot-meta">建筑 {{ plotCount(plot) }} / {{ plot.buildLimit }}</div>
                            <div class="plot-meta">
                                住宿：{{ (plot.accommodationWhitelist || []).map((m) => motherName(m)).join(' / ') || '—' }}
                            </div>
                            <div v-if="!unlockedPlots.has(plot.id)" class="lock-msg">
                                <div v-for="(r, i) in lockReasons(plot)" :key="i">· {{ r }}</div>
                            </div>
                            <div v-else class="lock-msg pos">已解锁</div>
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