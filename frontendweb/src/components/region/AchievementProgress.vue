<script setup>
import { computed } from 'vue';
import { store } from '../../services/store.js';
import ProgressBar from '../common/ProgressBar.vue';
import { fmtInt } from '../../utils/format.js';

const CAT = { FINANCE: '财务', BUILDING: '建筑', GUEST: '客流', STAFF: '雇佣' };

const unlockedSet = computed(() => new Set(store.state?.unlockedAchievements || []));

const groups = computed(() => {
    if (!store.config) return [];
    const list = store.config.achievements || [];
    const out = [];
    for (const [key, name] of Object.entries(CAT)) {
        const items = list.filter((a) => a.category === key);
        if (items.length) out.push({ key, name, items });
    }
    return out;
});

function currentValue(a) {
    const st = store.state;
    if (!st) return 0;
    const stats = st.stats || {};
    switch (a.type) {
        case 'TOTAL_ASSETS': return stats.totalAssets || 0;
        case 'CUMULATIVE_INCOME': return stats.cumulativeIncome || 0;
        case 'BUILDING_COUNT': return (st.buildings || []).filter((b) => b.alive).length;
        case 'FORM_LEVEL_COUNT': return (stats.formLevelCounts || {})[a.param] || 0;
        case 'CUMULATIVE_GUESTS': return stats.totalGuests || 0;
        case 'SEGMENT_GUESTS': return (stats.segmentGuests || {})[a.param] || 0;
        case 'STAFF_COUNT': return (st.staffs || []).filter((s) => s.alive).length;
        case 'POSITION_LEVEL_COUNT': return (stats.positionLevelCounts || {})[a.param] || 0;
        default: return 0;
    }
}
</script>

<template>
    <div class="panel">
        <div class="panel-head">
            <span class="title">成就进度</span>
            <span class="extra faint">全局跨区域累计，新区域不重置</span>
        </div>
        <div class="panel-body">
            <div v-if="!groups.length" class="empty faint">暂无成就</div>
            <template v-for="g in groups" :key="g.key">
                <div class="staff-group"><div class="grp-title">{{ g.name }}</div></div>
                <div class="achi-list">
                    <div v-for="a in g.items" :key="a.id" class="achi-item" :class="{ unlocked: unlockedSet.has(a.id) }">
                        <span class="m">
                            <div class="nm">{{ a.displayName }}</div>
                            <div class="pct">{{ unlockedSet.has(a.id) ? '已达成' : (fmtInt(currentValue(a)) + ' / ' + fmtInt(a.target)) }}</div>
                        </span>
                        <div style="width: 34%">
                            <ProgressBar :ratio="Math.min(1, currentValue(a) / (a.target || 1))" :color="'pos'" />
                        </div>
                    </div>
                </div>
            </template>
        </div>
    </div>
</template>

<style scoped>
.achi-item.unlocked .nm { color: var(--pos); }
</style>