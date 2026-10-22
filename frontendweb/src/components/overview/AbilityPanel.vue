<script setup>
import { computed } from 'vue';
import { store } from '../../services/store.js';
import { fmtInt, fmtPercent } from '../../utils/format.js';

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
    <div class="panel">
        <div class="panel-head">
            <span class="title">人力供需</span>
            <span class="extra faint">供给不足仅降低相关建筑舒适度</span>
        </div>
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
</template>