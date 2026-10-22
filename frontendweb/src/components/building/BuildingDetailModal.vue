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

// 配套建筑（餐厅 / 温泉 / 观景台等）：展示服务容量与单人收益，而非「每间房价」
const isSupport = computed(() => store.config?.mothersById?.[props.building.mother]?.category === 'SUPPORT');
const serviceCapacity = computed(() => form.value.serviceCapacity ?? 0);
const unitPrice = computed(() => form.value.unitPriceByLevel?.[level.value - 1] ?? 0);

// 后端下发的「单间实际房屋收益」构成（与日结同一套乘数），无数据时退回形态基础值
const revenue = computed(() => store.state?.roomRevenue?.[props.building.id] || null);
const effectivePrice = computed(() => revenue.value?.final ?? pricePerRoom.value);
const hasSkillBonus = computed(() => {
    const r = revenue.value;
    if (!r) return false;
    return (r.priceMult || 1) > 1.0001 || (r.mergeBonus || 0) > 0;
});

// 该建筑进行中的工程（作为主目标或合并源）
const activeProject = computed(() => (store.state?.projects || []).find(
    (p) => p.buildingId === props.building.id || p.secondBuildingId === props.building.id
) || null);

function interiorProject(typeId) {
    return (store.state?.projects || []).find(
        (p) => p.type === 'INTERIOR_UPGRADE' && p.buildingId === props.building.id && p.interiorType === typeId
    ) || null;
}

const levelUpDays = computed(() => form.value.formLevelUpDays?.[level.value - 1] ?? 0);

const interiorRows = computed(() => {
    const cap = form.value.maxInteriorLevel?.[level.value - 1] ?? 3;
    const mother = props.building.mother;
    return (store.config?.interiors || [])
        .filter((it) => !it.applicableMothers || it.applicableMothers.includes(mother))
        .map((it) => {
            const inst = (props.building.interiors || []).find((x) => x.interiorType === it.id);
            const cur = inst?.level || 0;
            const rem = Math.max(0, cap - cur);
            const cost = rem > 0 ? it.upgradeCostByLevel?.[cur] : 0;
            const days = rem > 0 ? (it.upgradeDaysByLevel?.[cur] ?? 0) : 0;
            const proj = interiorProject(it.id);
            return { ...it, cur, cap, rem, cost, days, proj, canUp: !proj && rem > 0 && funds.value >= cost };
        });
});

const levelUpCost = computed(() => form.value.formLevelUpCost?.[level.value - 1] ?? 0);
const canLevelUp = computed(
    () => !activeProject.value && level.value < maxLevel.value && funds.value >= levelUpCost.value
);

const branchTargets = computed(() => {
    if (level.value < maxLevel.value) return [];
    return (store.config?.buildingForms || []).filter((f) => f.evolveFrom?.id === form.value.id);
});

function branchMeta(t) {
    const cost = t.evolveCost || 0;
    return { cost, days: t.evolveDays || 0, can: funds.value >= cost };
}

/** 百分比格式化：0.02 → 2%，0.015 → 1.5% */
function pct(v) {
    const n = Math.round((v || 0) * 1000) / 10;
    return `${n}%`;
}

/** 内装的客群吸引力加成描述（仅列出有加成的客群） */
function affinityText(it) {
    const map = it.segmentAffinityPerLevel || {};
    return Object.entries(map)
        .filter(([, v]) => v > 0)
        .map(([k, v]) => `${store.config?.segmentsById?.[k]?.displayName || k} +${pct(v)}`)
        .join('、');
}
</script>

<template>
    <Modal :title="form.displayName || building.mother + ' 详情'" @close="emit('close')">
        <div class="bld-head">
            <span class="tag" :class="lineClass">{{ lineLabel }}</span>
            <span class="tag base">Lv{{ level }}</span>
            <span class="dim" v-if="plot">{{ plot.displayName }} · {{ shortId(building.id) }}</span>
        </div>

        <div v-if="activeProject" class="project-banner">
            <span class="tag gold">{{ activeProject.typeDisplayName }}</span>
            <div class="main">
                <div class="title">{{ activeProject.displayName }}</div>
                <div class="sub faint">
                    剩余 {{ activeProject.remainingDays }} / {{ activeProject.totalDays }} 天 · 预计第 {{ activeProject.finishDay }} 天完成
                    <br>施工期间沿用当前形态 / 内装计算收益、舒适度与吸引力
                </div>
            </div>
        </div>

        <div class="panel" style="margin-top: 12px">
            <div class="panel-body" v-if="isSupport">
                <div class="kv"><span class="k">每日服务容量</span><span class="v num">{{ serviceCapacity }} 人次</span></div>
                <div class="kv"><span class="k">单人收益（Lv{{ level }}）</span><span class="v num">{{ fmtMoney(unitPrice) }}</span></div>
                <div class="kv total">
                    <span class="k">满员日收益上限</span>
                    <span class="v num gold">{{ fmtMoney(serviceCapacity * unitPrice) }}</span>
                </div>
                <div class="kv"><span class="k">基础舒适度</span><span class="v num">{{ baseComfort }}</span></div>
            </div>
            <div class="panel-body" v-else>
                <div class="kv"><span class="k">每间接待人数</span><span class="v num">{{ capacity }} 人</span></div>
                <div class="kv">
                    <span class="k">基础房屋收益（每间）</span>
                    <span class="v num">{{ fmtMoney(revenue?.base ?? pricePerRoom) }}</span>
                </div>
                <div class="kv" v-if="revenue && revenue.interiorBonus > 0">
                    <span class="k">内装收益加成</span>
                    <span class="v num pos">+{{ pct(revenue.interiorBonus) }}</span>
                </div>
                <div class="kv" v-if="hasSkillBonus">
                    <span class="k">技能 / 合并加成</span>
                    <span class="v num pos">×{{ (revenue.priceMult || 1).toFixed(2) }}<template
                        v-if="revenue.mergeBonus > 0"> · 合并 +{{ pct(revenue.mergeBonus) }}</template></span>
                </div>
                <div class="kv total">
                    <span class="k">当前房屋收益（每间）</span>
                    <span class="v num gold">{{ fmtMoney(effectivePrice) }}</span>
                </div>
                <div class="kv"><span class="k">基础舒适度</span><span class="v num">{{ baseComfort }}</span></div>
            </div>
        </div>

        <h4 class="sec">形态升级</h4>
        <div class="bld-row">
            <div class="main">
                <div class="sub" v-if="level >= maxLevel">形态已满级 Lv{{ maxLevel }}，可进行分支进化</div>
                <div class="sub" v-else>升级至 Lv{{ level + 1 }}，花费 {{ fmtMoney(levelUpCost) }} · 工期 {{ levelUpDays }} 天</div>
            </div>
            <button class="primary" :disabled="!canLevelUp" @click="api.formLevelUp(building.id)">
                升级（{{ levelUpDays }} 天）
            </button>
        </div>

        <template v-if="!isSupport">
            <h4 class="sec">分支进化 <span class="faint" style="font-weight:400">（形态满级后可选，选定不可切换）</span></h4>
            <div v-if="level < maxLevel" class="faint">需先将形态升到 Lv{{ maxLevel }}</div>
            <div v-else-if="!branchTargets.length" class="faint">该形态无可分支的下一阶段</div>
            <div v-for="t in branchTargets" :key="t.id" class="bld-row">
                <div class="main">
                    <div class="title">{{ t.displayName }}</div>
                    <div class="sub">每间 {{ t.capacityPerRoom }} 人 · 房屋收益 Lv1 {{ fmtMoney(t.pricePerRoomByLevel?.[0] || 0) }}</div>
                </div>
                <button class="gold" :disabled="!branchMeta(t).can || !!activeProject"
                        @click="api.branchEvolve(building.id, t.id)">
                    进化 {{ fmtMoney(branchMeta(t).cost) }}（{{ branchMeta(t).days }} 天）
                </button>
            </div>
        </template>

        <h4 class="sec">内装升级</h4>
        <div v-if="!interiorRows.length" class="faint">暂无可升级内装</div>
        <div v-for="it in interiorRows" :key="it.id" class="bld-row">
            <div class="main">
                <div class="title">{{ it.displayName }}</div>
                <div class="sub">
                    当前 Lv{{ it.cur }} / {{ it.cap }}（{{ it.categoryDisplayName }} · 舒适 +{{ it.comfortPerLevel }}/级<template
                        v-if="it.capacityPerLevel > 0"> · 容量 +{{ it.capacityPerLevel }}/级</template>）
                </div>
                <div class="sub faint">客群吸引力：{{ affinityText(it) }}</div>
                <div class="sub faint">房屋收益：+{{ pct(it.revenueBonusPerLevel) }}/级</div>
                <div class="sub gold" v-if="it.proj">
                    内装升级中 · 剩余 {{ it.proj.remainingDays }} / {{ it.proj.totalDays }} 天（目标 Lv{{ it.proj.targetLevel }}）
                </div>
            </div>
            <button class="primary" :disabled="!it.canUp" @click="api.interiorUpgrade(building.id, it.id)">
                <template v-if="it.proj">升级中</template>
                <template v-else-if="it.rem > 0">升级 {{ fmtMoney(it.cost) }}（{{ it.days }} 天）</template>
                <template v-else>已满</template>
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
.project-banner {
    display: flex; align-items: flex-start; gap: 10px; margin-top: 12px; padding: 10px 12px;
    border: 1px solid rgba(242, 181, 60, .45); border-radius: var(--radius-sm); background: rgba(242, 181, 60, .08);
}
.project-banner .tag.gold { background: rgba(242, 181, 60, .18); color: var(--gold); }
.project-banner .main { flex: 1; }
.project-banner .title { font-weight: 600; }
.project-banner .sub { font-size: 12px; margin-top: 2px; line-height: 1.6; }
</style>