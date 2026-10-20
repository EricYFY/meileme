<script setup>
import { computed } from 'vue';
import { store, api } from '../../services/store.js';

const NODE_W = 150;
const NODE_H = 58;
const HGAP = 66;
const VGAP = 32;

const CAT_COLOR = {
    BUILDING: '#5a8cff',
    INTERIOR: '#7ad7e0',
    DEMAND: '#7ee0a3',
    HIRING: '#ffd37a',
    FINANCE: '#f2b53c',
    REGION: '#f4a5d0',
};

const skills = computed(() => store.config?.skills || []);
const skillsById = computed(() => store.config?.skillsById || {});
const unlockedSet = computed(() => new Set(store.state?.unlockedSkills || []));
const skillPoints = computed(() => store.state?.skillPoints || 0);

function computeDepth() {
    const depth = {};
    const ids = skills.value.map((s) => s.id);
    let changed = true;
    let guard = 0;
    while (changed && guard++ < ids.length + 2) {
        changed = false;
        for (const id of ids) {
            const s = skillsById.value[id];
            const prereqs = s.prerequisites || [];
            let d = 0;
            if (prereqs.length) {
                d = Math.max(...prereqs.map((p) => (depth[p] ?? -1) + 1));
            }
            if (depth[id] === undefined || d > depth[id]) {
                depth[id] = d;
                changed = true;
            }
        }
    }
    return depth;
}

const layout = computed(() => {
    const depth = computeDepth();
    const layers = {};
    for (const s of skills.value) {
        const d = depth[s.id] || 0;
        (layers[d] = layers[d] || []).push(s);
    }
    const maxDepth = Math.max(0, ...Object.keys(layers).map(Number));
    const pos = {};
    let maxY = 0;
    for (let d = 0; d <= maxDepth; d++) {
        const list = (layers[d] || []).sort((a, b) => (a.category || '').localeCompare(b.category || '') || a.id.localeCompare(b.id));
        list.forEach((s, i) => {
            const y = i * (NODE_H + VGAP);
            pos[s.id] = { x: d * (NODE_W + HGAP), y };
            maxY = Math.max(maxY, y + NODE_H);
        });
    }
    const edges = [];
    for (const s of skills.value) {
        for (const p of s.prerequisites || []) {
            if (pos[p] && pos[s.id]) {
                edges.push({ id: p + '->' + s.id, x1: pos[p].x + NODE_W, y1: pos[p].y + NODE_H / 2, x2: pos[s.id].x, y2: pos[s.id].y + NODE_H / 2 });
            }
        }
    }
    return { pos, edges, width: Math.max((maxDepth + 1) * (NODE_W + HGAP), 360), height: Math.max(maxY, 180) };
});

function nodeState(s) {
    if (unlockedSet.value.has(s.id)) return 'unlocked';
    const prereqs = s.prerequisites || [];
    const allPrereq = prereqs.every((p) => unlockedSet.value.has(p));
    const enough = skillPoints.value >= 1;
    return allPrereq && enough ? 'available' : 'locked';
}

function nodeFill(state) {
    if (state === 'unlocked') return '#14351f';
    if (state === 'available') return '#22314f';
    return '#1a2438';
}
function nodeStroke(state) {
    if (state === 'unlocked') return '#43d69a';
    if (state === 'available') return '#5a8cff';
    return '#2b3a5c';
}
function onClickNode(s) {
    if (nodeState(s) === 'available') api.unlockSkill(s.id);
}
</script>

<template>
    <div class="panel">
        <div class="panel-head">
            <span class="title">局内技能树</span>
            <span class="extra">
                <span class="gold num">可用技能点：{{ skillPoints }}</span>
            </span>
        </div>
        <div class="panel-body">
            <div class="legend" style="margin-bottom: 10px">
                <span><span class="dot" style="background: #43d69a"></span>已解锁</span>
                <span><span class="dot" style="background: #5a8cff"></span>可解锁</span>
                <span><span class="dot" style="background: #2b3a5c"></span>未满足前置</span>
                <span class="faint">效果仅两类：倍率加成 / 费用减免</span>
            </div>
            <div v-if="!skills.length" class="empty faint">暂无技能</div>
            <div v-else class="skill-tree">
                <svg :width="layout.width" :height="layout.height" :viewBox="'0 0 ' + layout.width + ' ' + layout.height">
                    <path v-for="e in layout.edges" :key="e.id" class="skill-link"
                          :class="{ done: unlockedSet.has(e.id.split('->')[1]) }"
                          :d="'M ' + e.x1 + ' ' + e.y1 + ' C ' + (e.x1 + 34) + ' ' + e.y1 + ', ' + (e.x2 - 34) + ' ' + e.y2 + ', ' + e.x2 + ' ' + e.y2" />
                    <g v-for="s in skills" :key="s.id" class="skill-node" :class="nodeState(s)"
                       :transform="'translate(' + (layout.pos[s.id]?.x || 0) + ',' + (layout.pos[s.id]?.y || 0) + ')'"
                       @click="onClickNode(s)">
                        <rect :width="NODE_W" :height="NODE_H" rx="8"
                              :fill="nodeFill(nodeState(s))" :stroke="nodeStroke(nodeState(s))" :stroke-width="1.5" />
                        <text class="name" x="10" y="22">{{ s.displayName }}</text>
                        <text class="desc" x="10" y="38">{{ (s.description || '').slice(0, 14) }}</text>
                        <text class="desc" x="10" y="51" :fill="CAT_COLOR[s.category] || '#a9b6d4'">{{ s.category }}</text>
                        <title>{{ s.displayName }}：{{ s.description }}</title>
                    </g>
                </svg>
            </div>
        </div>
    </div>
</template>

<style scoped>
.skill-tree { overflow: auto; min-height: 340px; }
.skill-node { cursor: pointer; }
.skill-node .name { font-size: 12px; fill: #fff; font-weight: 600; }
.skill-node .desc { font-size: 10px; fill: #a9b6d4; }
.skill-node.locked { opacity: .5; }
.skill-link { stroke: #2b3a5c; stroke-width: 1.5; fill: none; }
.skill-link.done { stroke: #43d69a; }
</style>