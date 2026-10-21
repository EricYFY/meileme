<script setup>
import { computed } from 'vue';
import { store, api } from '../../services/store.js';
import { fmtMoney, shortId } from '../../utils/format.js';
import Modal from '../common/Modal.vue';

const props = defineProps({
    building: { type: Object, required: true },
});
const emit = defineEmits(['close']);

const LINE_LABEL = { BASE: '基础形态', FAMILY: '家庭亲子线', BUSINESS: '商务行政线', MERGE: '合并产物' };
const LINE_CLASS = { BASE: 'base', FAMILY: 'family', BUSINESS: 'business', MERGE: 'merge' };

const form = computed(() => props.building.form || {});
const level = computed(() => props.building.formLevel || 1);
const maxLevel = computed(() => store.config?.formMaxLevel || 3);
const funds = computed(() => store.state?.funds ?? 0);
const plot = computed(() => store.config?.plotsById?.[props.building.plotId]);

const lineLabel = computed(() => LINE_LABEL[form.value.line] || form.value.line || '');
const lineClass = computed(() => LINE_CLASS[form.value.line] || 'base');
const pricePerRoom = computed(() => form.value.pricePerRoomByLevel?.[level.value - 1] ?? 0);
const capacity = computed(() => form.value.capacityPerRoom ?? 0);
const baseComfort = computed(() => form.value.baseComfort ?? 0);

const interiorRows = computed(() => {
    const cap = form.value.maxInteriorLevel?.[level.value - 1] ?? 3;
    return (store.config?.interiors || []).map((it) => {
        const inst = (props.building.interiors || []).find((x) => x.interiorType === it.id);
        const cur = inst?.level || 0;
        const rem = Math.max(0, cap - cur);
        const cost = rem > 0 ? it.upgradeCostByLevel?.[cur] : 0;
        return { ...it, cur, cap, rem, cost, canUp: rem > 0 && funds.value >= cost };
    });
});

const levelUpCost = computed(() => form.value.formLevelUpCost?.[level.value - 1] ?? 0);
const canLevelUp = computed(() => level.value < maxLevel.value && funds.value >= levelUpCost.value);

const branchTargets = computed(() => {
    if (level.value < maxLevel.value) return [];
    return (store.config?.buildingForms || []).filter((f) => f.evolveFrom?.id === form.value.id);
});

function branchMeta(t) {
    const cost = t.evolveCost || 0;
    return { cost, can: funds.value >= cost };
}
</script>

<template>
    <Modal :title="form.displayName || building.mother + ' 详情'" @close="emit('close')">
        <div class="bld-head">
            <span class="tag" :class="lineClass">{{ lineLabel }}</span>
            <span class="tag base">Lv{{ level }}</span>
            <span class="dim" v-if="plot">{{ plot.displayName }} · {{ shortId(building.id) }}</span>
        </div>

        <div class="panel" style="margin-top: 12px">
            <div class="panel-body">
                <div class="kv"><span class="k">每间接待人数</span><span class="v num">{{ capacity }} 人</span></div>
                <div class="kv"><span class="k">当前房屋收益（每间）</span><span class="v num gold">{{ fmtMoney(pricePerRoom) }}</span></div>
                <div class="kv"><span class="k">基础舒适度</span><span class="v num">{{ baseComfort }}</span></div>
            </div>
        </div>

        <h4 class="sec">形态升级</h4>
        <div class="bld-row">
            <div class="main">
                <div class="sub" v-if="level >= maxLevel">形态已满级 Lv{{ maxLevel }}，可进行分支进化</div>
                <div class="sub" v-else>升级至 Lv{{ level + 1 }}，花费 {{ fmtMoney(levelUpCost) }}</div>
            </div>
            <button class="primary" :disabled="!canLevelUp" @click="api.formLevelUp(building.id)">升级</button>
        </div>

        <h4 class="sec">分支进化 <span class="faint" style="font-weight:400">（形态满级后可选，选定不可切换）</span></h4>
        <div v-if="level < maxLevel" class="faint">需先将形态升到 Lv{{ maxLevel }}</div>
        <div v-else-if="!branchTargets.length" class="faint">该形态无可分支的下一阶段</div>
        <div v-for="t in branchTargets" :key="t.id" class="bld-row">
            <div class="main">
                <div class="title">{{ t.displayName }}</div>
                <div class="sub">每间 {{ t.capacityPerRoom }} 人 · 房屋收益 Lv1 {{ fmtMoney(t.pricePerRoomByLevel?.[0] || 0) }}</div>
            </div>
            <button class="gold" :disabled="!branchMeta(t).can" @click="api.branchEvolve(building.id, t.id)">
                进化 {{ fmtMoney(branchMeta(t).cost) }}
            </button>
        </div>

        <h4 class="sec">内装升级</h4>
        <div v-if="!interiorRows.length" class="faint">暂无可升级内装</div>
        <div v-for="it in interiorRows" :key="it.id" class="bld-row">
            <div class="main">
                <div class="title">{{ it.displayName }}</div>
                <div class="sub">当前 Lv{{ it.cur }} / {{ it.cap }}（{{ it.categoryDisplayName }} · 舒适 +{{ it.comfortPerLevel }}/级）</div>
            </div>
            <button class="primary" :disabled="!it.canUp" @click="api.interiorUpgrade(building.id, it.id)">
                {{ it.rem > 0 ? '升级 ' + fmtMoney(it.cost) : '已满' }}
            </button>
        </div>

        <template #footer>
            <button class="ghost" @click="emit('close')">关闭</button>
        </template>
    </Modal>
</template>

<style scoped>
.bld-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.sec { margin: 18px 0 8px; font-size: 14px; color: var(--text); border-left: 3px solid var(--accent); padding-left: 8px; }
.bld-row { margin-bottom: 8px; }
</style>