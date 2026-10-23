<script setup>
import { ref, computed, watch } from 'vue';
import { store, api } from '../../services/store.js';
import { fmtMoney } from '../../utils/format.js';

const emit = defineEmits(['open-building', 'open-build']);

const selectedPlotId = ref(null);
const mergeMode = ref(false);
const mergeSel = ref([]);

const unlockedPlots = computed(() =>
    (store.config?.plots || []).filter((p) => (store.state?.unlockedPlots || []).includes(p.id))
);

watch(unlockedPlots, (list) => {
    if (!selectedPlotId.value || !list.some((p) => p.id === selectedPlotId.value)) {
        selectedPlotId.value = list[0]?.id || null;
    }
}, { immediate: true });

const currentPlot = computed(() => store.config?.plotsById?.[selectedPlotId.value] || null);
const map = computed(() => store.config?.plotMapsById?.[selectedPlotId.value] || null);

const coords = computed(() => {
    const w = map.value?.width || 0;
    const h = map.value?.height || 0;
    const arr = [];
    for (let y = 0; y < h; y++) {
        for (let x = 0; x < w; x++) arr.push({ x, y });
    }
    return arr;
});

const gridStyle = computed(() => ({ gridTemplateColumns: `repeat(${map.value?.width || 1}, minmax(0, 1fr))` }));

const cellIndex = computed(() => {
    const m = {};
    for (const c of map.value?.cells || []) m[c.x + ',' + c.y] = c;
    return m;
});

const buildingAt = computed(() => {
    const m = {};
    for (const b of store.state?.buildings || []) {
        if (b.alive && b.plotId === selectedPlotId.value) m[b.cellX + ',' + b.cellY] = b;
    }
    return m;
});

const projectAt = computed(() => {
    const m = {};
    for (const p of store.state?.projects || []) {
        if (((p.projectType || p.type) === 'BUILD') && p.plotId === selectedPlotId.value) m[p.cellX + ',' + p.cellY] = p;
    }
    return m;
});

const cellsView = computed(() => coords.value.map((c) => {
    const key = c.x + ',' + c.y;
    const cell = cellIndex.value[key];
    const b = buildingAt.value[key];
    const p = projectAt.value[key];
    let kind;
    let data = null;
    if (b) { kind = 'building'; data = b; }
    else if (p) { kind = 'project'; data = p; }
    else if (cell && cell.buildable) { kind = 'buildable'; data = cell; }
    else { kind = 'terrain'; data = cell; }
    return { ...c, key, kind, data, selected: b ? mergeSel.value.includes(b.id) : false };
}));

// 地形展示（后端序列化为枚举名：TOWN / PLAIN / MOUNTAIN / LAKE）
const TERRAIN_META = {
    TOWN: { label: '城', name: '城镇', cls: 'town' },
    PLAIN: { label: '原', name: '平原', cls: 'plain' },
    MOUNTAIN: { label: '山', name: '山地', cls: 'mountain' },
    LAKE: { label: '湖', name: '湖区', cls: 'lake' },
};
function terrainMeta(t) {
    const key = typeof t === 'string' ? t : (t?.name || '');
    return TERRAIN_META[key] || { label: '?', name: '地形', cls: 'town' };
}

const LINE_LABEL = { BASE: '基', FAMILY: '家', BUSINESS: '商', MERGE: '合' };
const LINE_CLASS = { BASE: 'base', FAMILY: 'family', BUSINESS: 'business', MERGE: 'merge' };
function lineBadge(b) { return LINE_LABEL[b.form?.line] || '基'; }
function lineCls(b) { return LINE_CLASS[b.form?.line] || 'base'; }

function onCellClick(v) {
    if (v.kind === 'building') {
        if (mergeMode.value) toggleSelect(v.data);
        else emit('open-building', v.data);
        return;
    }
    if (!mergeMode.value && v.kind === 'buildable') {
        emit('open-build', { plot: currentPlot.value, cellX: v.x, cellY: v.y });
    }
}

// —— 合并进化（相邻多选） ——
function projectOf(id) {
    return (store.state?.projects || []).find((p) => p.buildingId === id || p.secondBuildingId === id) || null;
}
function toggleSelect(b) {
    if (projectOf(b.id)) return;
    const idx = mergeSel.value.indexOf(b.id);
    if (idx >= 0) mergeSel.value.splice(idx, 1);
    else {
        if (mergeSel.value.length >= 2) mergeSel.value.shift();
        mergeSel.value.push(b.id);
    }
}
const selA = computed(() => (store.state?.buildings || []).find((b) => b.id === mergeSel.value[0]));
const selB = computed(() => (store.state?.buildings || []).find((b) => b.id === mergeSel.value[1]));

function matchRecipe(a, b) {
    if (!a || !b) return null;
    return (store.config?.mergeRecipes || []).find((r) =>
        (r.sourceFormA?.id === a.form.id && r.sourceLevelA === a.formLevel && r.sourceFormB?.id === b.form.id && r.sourceLevelB === b.formLevel) ||
        (r.sourceFormA?.id === b.form.id && r.sourceLevelA === b.formLevel && r.sourceFormB?.id === a.form.id && r.sourceLevelB === a.formLevel)
    );
}
const matched = computed(() => matchRecipe(selA.value, selB.value));
const isAdjacent = computed(() => {
    const a = selA.value;
    const b = selB.value;
    if (!a || !b) return false;
    if (a.cellX < 0 || a.cellY < 0 || b.cellX < 0 || b.cellY < 0) return false;
    return Math.abs(a.cellX - b.cellX) + Math.abs(a.cellY - b.cellY) === 1;
});
const canMerge = computed(() =>
    !!matched.value && isAdjacent.value && (store.state?.funds ?? 0) >= (matched.value.mergeCost || 0)
);

function confirmMerge() {
    if (!selA.value || !selB.value || !matched.value) return;
    api.mergeEvolve(selA.value.id, selB.value.id);
    mergeSel.value = [];
    mergeMode.value = false;
}
</script>

<template>
    <div class="plot-map-panel panel">
        <div class="panel-head">
            <span class="title">地块地图</span>
            <span class="extra">
                <button v-for="p in unlockedPlots" :key="p.id" class="ghost" :class="{ primary: p.id === selectedPlotId }"
                        @click="selectedPlotId = p.id">{{ p.displayName }}</button>
                <button class="gold" :class="{ danger: mergeMode }" @click="mergeMode = !mergeMode; mergeSel = []">
                    {{ mergeMode ? '退出合并' : '合并进化' }}
                </button>
            </span>
        </div>

        <div class="panel-body">
            <div v-if="!unlockedPlots.length" class="empty faint">尚未解锁任何地块</div>
            <div v-else-if="!map" class="empty faint">地块地图缺失</div>
            <template v-else>
                <div class="map-toolbar">
                    <span v-if="mergeMode" class="gold">合并模式：点击选择两座相邻建筑（消耗两个、新建一个）。已选 {{ mergeSel.length }} / 2</span>
                    <span v-else class="faint">点击空可建造格新建建筑 · 点击建筑查看详情（升级 / 进化 / 内装）</span>
                </div>

                <div class="map-grid" :style="gridStyle">
                    <div v-for="v in cellsView" :key="v.key"
                         class="map-cell"
                         :class="[v.kind, { selected: v.selected }]"
                         :title="v.kind === 'terrain' ? terrainMeta(v.data?.terrain).name : (v.kind === 'buildable' ? '可建造空地' : '')"
                         @click="onCellClick(v)">
                        <template v-if="v.kind === 'building'">
                            <span class="cb-badge" :class="lineCls(v.data)">{{ lineBadge(v.data) }}</span>
                            <span class="cb-name">{{ v.data.form?.displayName || v.data.mother }}</span>
                            <span class="cb-lv">Lv{{ v.data.formLevel }}</span>
                        </template>
                        <template v-else-if="v.kind === 'project'">
                            <span class="ghost-dot">建</span>
                            <span class="cb-name">建造中</span>
                        </template>
                        <template v-else-if="v.kind === 'buildable'">
                            <span class="plus">+</span>
                        </template>
                        <template v-else>
                            <span class="terr" :class="terrainMeta(v.data?.terrain).cls">{{ terrainMeta(v.data?.terrain).label }}</span>
                        </template>
                    </div>
                </div>

                <div class="legend">
                    <span class="faint">地形：</span>
                    <span class="lg"><i class="dot town"></i>城镇</span>
                    <span class="lg"><i class="dot plain"></i>平原</span>
                    <span class="lg"><i class="dot mountain"></i>山地</span>
                    <span class="lg"><i class="dot lake"></i>湖区</span>
                    <span class="lg"><i class="dot buildable"></i>可建造空地</span>
                </div>
            </template>

            <div v-if="mergeMode && mergeSel.length === 2" class="panel" style="margin-top: 16px">
                <div class="panel-body">
                    <div v-if="matched">
                        <div class="kv"><span class="k">匹配配方</span><span class="v">{{ matched.displayName }}</span></div>
                        <div class="kv"><span class="k">产物形态</span><span class="v">{{ matched.resultForm?.displayName }}</span></div>
                        <div class="kv">
                            <span class="k">合并花费</span>
                            <span class="v num" :class="canMerge ? 'pos' : 'neg'">{{ fmtMoney(matched.mergeCost) }}</span>
                        </div>
                    </div>
                    <div v-else class="empty faint">所选两建筑不满足任何合并配方（需形态与等级匹配）</div>
                    <div v-if="matched && !isAdjacent" class="faint" style="margin-top: 6px">
                        ⚠ 两座建筑不相邻，合并需上下左右相邻
                    </div>
                </div>
                <div class="panel-body" style="padding-top: 0">
                    <button class="primary" :disabled="!canMerge" @click="confirmMerge">
                        开始合并（{{ matched?.mergeDays || 0 }} 天）
                    </button>
                </div>
            </div>
        </div>
    </div>
</template>

<style scoped>
.map-toolbar { margin-bottom: 12px; font-size: 13px; }
.map-grid {
    display: grid;
    gap: 3px;
    width: 100%;
    max-width: 720px;
}
.map-cell {
    aspect-ratio: 1;
    min-width: 0;
    border-radius: 5px;
    border: 1px solid var(--border-soft);
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 1px;
    font-size: 10px;
    overflow: hidden;
    background: var(--bg-soft);
}
.map-cell.buildable {
    border-style: dashed;
    border-color: var(--accent);
    color: var(--accent);
    cursor: pointer;
}
.map-cell.buildable:hover { background: var(--accent-soft); }
.map-cell.building { cursor: pointer; border-style: solid; }
.map-cell.building:hover { border-color: var(--accent); }
.map-cell.building.selected { border-color: var(--gold); background: rgba(242, 181, 60, .12); }
.map-cell.project { border-color: var(--gold); background: rgba(242, 181, 60, .08); color: var(--gold); }
.map-cell .plus { font-size: 16px; }

.cb-badge {
    width: 18px; height: 18px; border-radius: 4px;
    display: flex; align-items: center; justify-content: center;
    font-size: 11px; font-weight: 700; color: #fff; flex: none;
}
.cb-badge.base { background: #333d55; }
.cb-badge.family { background: #2c4a3c; }
.cb-badge.business { background: #3d3a6b; }
.cb-badge.merge { background: #5c4227; }
.cb-name {
    color: var(--text-dim); max-width: 100%; padding: 0 2px;
    overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 9px;
}
.cb-lv { color: var(--text-faint); font-size: 9px; }
.ghost-dot {
    width: 18px; height: 18px; border-radius: 4px; background: rgba(242, 181, 60, .2);
    display: flex; align-items: center; justify-content: center; font-size: 11px;
}

.terr { font-size: 11px; color: #fff; }
.terr.town { color: #e0b98a; }
.terr.plain { color: #8fd0a0; }
.terr.mountain { color: #9aa4b8; }
.terr.lake { color: #7fc4e8; }

.legend { margin-top: 12px; display: flex; flex-wrap: wrap; gap: 10px; align-items: center; font-size: 12px; }
.lg { display: inline-flex; align-items: center; gap: 4px; color: var(--text-dim); }
.dot { width: 10px; height: 10px; border-radius: 3px; display: inline-block; }
.dot.town { background: #6b4f2f; }
.dot.plain { background: #2c4a36; }
.dot.mountain { background: #4a4f58; }
.dot.lake { background: #21435c; }
.dot.buildable { background: transparent; border: 1px dashed var(--accent); }
</style>