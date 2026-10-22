<script setup>
import { ref, computed } from 'vue';
import { store, api } from '../../services/store.js';
import { fmtMoney } from '../../utils/format.js';
import BuildingIcon from '../overview/BuildingIcon.vue';

const emit = defineEmits(['open-building']);

const FILTERS = [
    { key: 'ALL', label: '全部' },
    { key: 'ACCOMMODATION', label: '住宿' },
    { key: 'SUPPORT', label: '配套' },
];

const filter = ref('ALL');
const mergeMode = ref(false);
const mergeSel = ref([]);

const buildings = computed(() => (store.state?.buildings || []).filter((b) => b.alive));
const projects = computed(() => store.state?.projects || []);

function projectOf(buildingId) {
    return projects.value.find((p) => p.buildingId === buildingId || p.secondBuildingId === buildingId) || null;
}

const filtered = computed(() => {
    const list = buildings.value;
    if (filter.value === 'ALL') return list;
    return list.filter((b) => {
        const cat = store.config?.mothersById?.[b.mother]?.category;
        return cat === filter.value;
    });
});

function plotName(plotId) {
    return store.config?.plotsById?.[plotId]?.displayName || plotId;
}

function catLabel(b) {
    return store.config?.mothersById?.[b.mother]?.category === 'ACCOMMODATION' ? '住宿' : '配套';
}

function toggleMergeSelect(b) {
    if (projectOf(b.id)) return;
    const idx = mergeSel.value.indexOf(b.id);
    if (idx >= 0) {
        mergeSel.value.splice(idx, 1);
    } else {
        if (mergeSel.value.length >= 2) mergeSel.value.shift();
        mergeSel.value.push(b.id);
    }
}

const selA = computed(() => buildings.value.find((b) => b.id === mergeSel.value[0]));
const selB = computed(() => buildings.value.find((b) => b.id === mergeSel.value[1]));

function matchRecipe(a, b) {
    if (!a || !b) return null;
    return (store.config?.mergeRecipes || []).find((r) =>
        (r.sourceFormA?.id === a.form.id && r.sourceLevelA === a.formLevel && r.sourceFormB?.id === b.form.id && r.sourceLevelB === b.formLevel) ||
        (r.sourceFormA?.id === b.form.id && r.sourceLevelA === b.formLevel && r.sourceFormB?.id === a.form.id && r.sourceLevelB === a.formLevel)
    );
}

const matched = computed(() => matchRecipe(selA.value, selB.value));
const canMerge = computed(() => !!matched.value && (store.state?.funds ?? 0) >= (matched.value.mergeCost || 0));

function confirmMerge() {
    if (!selA.value || !selB.value || !matched.value) return;
    api.mergeEvolve(selA.value.id, selB.value.id);
    mergeSel.value = [];
    mergeMode.value = false;
}
</script>

<template>
    <div class="panel">
        <div class="panel-head">
            <span class="title">建筑总览</span>
            <span class="extra">
                <button v-for="f in FILTERS" :key="f.key" class="ghost" :class="{ primary: filter === f.key }"
                        @click="filter = f.key">{{ f.label }}</button>
                <button class="gold" :class="{ danger: mergeMode }" @click="mergeMode = !mergeMode; mergeSel = []">
                    {{ mergeMode ? '退出合并' : '合并进化' }}
                </button>
            </span>
        </div>

        <div class="panel-body">
            <div v-if="mergeMode" class="muted-note" style="margin-bottom: 10px">
                合并模式：点击选择两个源建筑（消耗两个、新建一个）。已选 {{ mergeSel.length }} / 2
            </div>

            <div v-if="projects.length" class="project-list">
                <div v-for="p in projects" :key="p.id" class="bld-row project-row">
                    <span class="tag base">{{ p.typeDisplayName }}</span>
                    <div class="main">
                        <div class="title">{{ p.displayName }}</div>
                        <div class="sub">
                            {{ plotName(p.plotId) }} · 剩余 {{ p.remainingDays }} / {{ p.totalDays }} 天 · 第 {{ p.finishDay }} 天完成
                        </div>
                    </div>
                    <span class="faint">施工期间沿用当前状态</span>
                </div>
            </div>

            <div v-if="!filtered.length" class="empty faint">暂无建筑</div>

            <div class="row-list">
                <div v-for="b in filtered" :key="b.id" class="bld-row"
                     :class="{ active: mergeMode && mergeSel.includes(b.id) }"
                     @click="mergeMode ? toggleMergeSelect(b) : emit('open-building', b)">
                    <BuildingIcon :building="b" />
                    <div class="main">
                        <div class="title">{{ b.form.displayName || b.mother }}</div>
                        <div class="sub">{{ plotName(b.plotId) }} · {{ catLabel(b) }}</div>
                    </div>
                    <span v-if="projectOf(b.id)" class="tag gold">{{ projectOf(b.id).typeDisplayName }} · 剩 {{ projectOf(b.id).remainingDays }} 天</span>
                    <span class="tag" :class="(b.form.line || 'base').toLowerCase()">{{ b.form.line || 'BASE' }}</span>
                    <span class="num dim">Lv{{ b.formLevel }}</span>
                </div>
            </div>

            <div v-if="mergeMode && mergeSel.length === 2" class="panel" style="margin-top: 16px">
                <div class="panel-body">
                    <div v-if="matched">
                        <div class="kv"><span class="k">匹配配方</span><span class="v">{{ matched.displayName }}</span></div>
                        <div class="kv"><span class="k">产物形态</span><span class="v">{{ matched.resultForm?.displayName }}</span></div>
                        <div class="kv"><span class="k">合并花费</span><span class="v num" :class="canMerge ? 'pos' : 'neg'">{{ fmtMoney(matched.mergeCost) }}</span></div>
                    </div>
                    <div v-else class="empty faint">所选两建筑不满足任何合并配方（需形态与等级匹配）</div>
                </div>
                <div class="panel-body" style="padding-top: 0">
                    <button class="primary" :disabled="!canMerge" @click="confirmMerge">
                        开始合并（{{ matched.mergeDays || 0 }} 天）
                    </button>
                </div>
            </div>
        </div>
    </div>
</template>

<style scoped>
.bld-row.active { border-color: var(--gold); background: rgba(242, 181, 60, .1); }
.project-list { display: flex; flex-direction: column; gap: 8px; margin-bottom: 12px; }
.project-row { cursor: default; border-color: rgba(242, 181, 60, .45); }
.project-row .tag.gold { background: rgba(242, 181, 60, .18); color: var(--gold); }
</style>