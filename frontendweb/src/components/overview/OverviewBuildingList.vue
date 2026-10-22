<script setup>
import { computed } from 'vue';
import { store } from '../../services/store.js';
import BuildingIcon from '../overview/BuildingIcon.vue';

const emit = defineEmits(['open-building']);

const groups = computed(() => {
    const plots = store.config?.plots || [];
    const buildings = (store.state?.buildings || []).filter((b) => b.alive);
    const projects = (store.state?.projects || []).filter((p) => p.type === 'BUILD');
    const out = [];
    for (const plot of plots) {
        const list = buildings.filter((b) => b.plotId === plot.id);
        const pending = projects.filter((p) => p.plotId === plot.id);
        if (list.length || pending.length) out.push({ plot, list, pending });
    }
    return out;
});

function catLabel(b) {
    return store.config?.mothersById?.[b.mother]?.category === 'ACCOMMODATION' ? '住宿' : '配套';
}
function motherName(mid) {
    return store.config?.mothersById?.[mid]?.displayName || '建筑';
}
</script>

<template>
    <div class="panel">
        <div class="panel-head">
            <span class="title">建筑总览</span>
            <span class="extra faint">点击建筑查看详情（升级 / 进化 / 内装）；新建请在「建筑」页签的格子上进行</span>
        </div>
        <div class="panel-body">
            <div v-if="!groups.length" class="empty faint">暂无建筑，请前往「建筑」页签在地块格子上新建</div>

            <div v-for="g in groups" :key="g.plot.id" class="plot-group">
                <div class="group-head">
                    {{ g.plot.displayName }}
                    <span class="faint" style="font-weight: 400; font-size: 12px">
                        建筑 {{ g.list.length }}<template v-if="g.pending.length"> · 在建 {{ g.pending.length }}</template>
                    </span>
                </div>

                <div class="row-list">
                    <div v-for="p in g.pending" :key="p.id" class="bld-row project-row">
                        <span class="tag gold">建造中</span>
                        <div class="main">
                            <div class="title">{{ motherName(p.mother) }}</div>
                            <div class="sub">剩 {{ p.remainingDays }} / {{ p.totalDays }} 天 · 格 ({{ p.cellX }}, {{ p.cellY }})</div>
                        </div>
                    </div>

                    <div v-for="b in g.list" :key="b.id" class="bld-row" @click="emit('open-building', b)">
                        <BuildingIcon :building="b" />
                        <div class="main">
                            <div class="title">{{ b.form.displayName || b.mother }}</div>
                            <div class="sub">{{ catLabel(b) }} · 格 ({{ b.cellX }}, {{ b.cellY }})</div>
                        </div>
                        <span class="tag" :class="(b.form.line || 'base').toLowerCase()">{{ b.form.line || 'BASE' }}</span>
                        <span class="num dim">Lv{{ b.formLevel }}</span>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<style scoped>
.plot-group { margin-bottom: 16px; }
.plot-group:last-child { margin-bottom: 0; }
.group-head { font-size: 14px; font-weight: 700; margin-bottom: 8px; }
.project-row { cursor: default; border-color: rgba(242, 181, 60, .45); }
.project-row .tag.gold { background: rgba(242, 181, 60, .18); color: var(--gold); }
</style>