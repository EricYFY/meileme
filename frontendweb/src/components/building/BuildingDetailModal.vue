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
const isSupport = computed(() => {
    if (form.value?.category) return form.value.category === 'SUPPORT';
    return store.config?.mothersById?.[props.building.mother]?.category === 'SUPPORT';
});
const serviceCapacity = computed(() => form.value.serviceCapacity ?? 0);
const unitPrice = computed(() => form.value.unitPriceByLevel?.[level.value - 1] ?? 0);

// 承载能力对应内装与容量分解
const capacityInteriorId = computed(() => {
    if (form.value?.capacityInterior !== undefined) {
        return form.value.capacityInterior || null;
    }
    return isSupport.value ? (form.value?.id?.includes('RESTAURANT') ? 'DINING_TABLE' : null) : 'BED';
});
const capacityInteriorDef = computed(() => store.config?.interiorsById?.[capacityInteriorId.value] || null);
const baseCapacity = computed(() => isSupport.value ? serviceCapacity.value : (form.value.capacityPerRoom ?? 0));
const interiorCapacityBonus = computed(() => {
    if (comfortData.value?.interiorCapacity !== undefined) {
        return Number(comfortData.value.interiorCapacity);
    }
    const b = props.building;
    const target = capacityInteriorId.value;
    if (!target) return 0;
    const it = (b.interiors || []).find((x) => x.interiorType === target);
    if (!it) return 0;
    const capPerLvl = Number(capacityInteriorDef.value?.capacityPerLevel ?? (target === 'BED' ? 1 : 0));
    return capPerLvl * (it.level || 0);
});
const totalCapacity = computed(() => {
    if (comfortData.value?.totalCapacity !== undefined) {
        return Number(comfortData.value.totalCapacity);
    }
    return baseCapacity.value + interiorCapacityBonus.value;
});

// 后端下发的「单间实际房屋收益」构成（与日结同一套乘数），无数据时退回形态基础值
const revenue = computed(() => store.state?.roomRevenue?.[props.building.id] || null);
const effectivePrice = computed(() => revenue.value?.final ?? pricePerRoom.value);
const hasSkillBonus = computed(() => {
    const r = revenue.value;
    if (!r) return false;
    return (r.priceMult || 1) > 1.0001 || (r.mergeBonus || 0) > 0;
});

// 单建筑舒适度分解与人力保障
const comfortData = computed(() => store.state?.buildingComfort?.[props.building.id] || null);
const totalComfort = computed(() => comfortData.value?.totalComfort ?? baseComfort.value);
const interiorComfort = computed(() => comfortData.value?.interiorComfort ?? 0);
const staffFactor = computed(() => comfortData.value?.staffFactor ?? 1.0);
const shortages = computed(() => comfortData.value?.shortages ?? []);
const formLevelComfortStep = computed(() => store.config?.formLevelComfortStep ?? 0.15);
const levelComfortRatio = computed(() => {
    if (comfortData.value?.levelComfortRatio !== undefined) {
        return Number(comfortData.value.levelComfortRatio);
    }
    return Math.max(0, (level.value - 1) * formLevelComfortStep.value);
});
const effectiveBaseComfort = computed(() => {
    if (comfortData.value?.baseComfort !== undefined) {
        return Number(comfortData.value.baseComfort);
    }
    return baseComfort.value * (1 + levelComfortRatio.value);
});

// 客群相性与吸引力贡献
const SEGMENT_COLORS = {
    FAMILY: '#7ee0a3',
    BUSINESS: '#8ab4ff',
    ROMANTIC: '#f4a5d0',
    WELLNESS: '#ffd37a',
};
const SEGMENT_ICONS = {
    FAMILY: '👨👩👧',
    BUSINESS: '💼',
    ROMANTIC: '👩❤️👨',
    WELLNESS: '🧘',
};

const segmentAffinities = computed(() => {
    const f = form.value;
    const b = props.building;
    const segAttractions = comfortData.value?.segmentAttraction || {};
    const formAffinityMap = f.segmentAffinity || f.affinities || store.config?.buildingFormsById?.[f.id]?.affinities || store.config?.buildingFormsById?.[f.id]?.segmentAffinity || {};
    return (store.config?.segments || []).map((seg) => {
        const baseAff = Number(formAffinityMap[seg.id] ?? store.config?.defaultSegmentAffinity ?? 0.5);
        let interiorAff = 0;
        for (const it of (b.interiors || [])) {
            const def = store.config?.interiorsById?.[it.interiorType];
            const defAff = Number(def?.segmentAffinityPerLevel?.[seg.id] ?? (def?.affinitySegment === seg.id ? def?.affinityPerLevel : 0) ?? 0);
            interiorAff += defAff * (it.level || 0);
        }
        const totalAff = baseAff + interiorAff;
        const attrScore = Number(segAttractions[seg.id] ?? 0);
        let grade = 'C';
        if (totalAff >= 1.2) grade = 'S';
        else if (totalAff >= 0.8) grade = 'A';
        else if (totalAff >= 0.4) grade = 'B';
        else if (totalAff <= 0) grade = '—';
        return {
            id: seg.id,
            name: seg.displayName,
            icon: seg.icon || SEGMENT_ICONS[seg.id] || '👥',
            color: seg.color || SEGMENT_COLORS[seg.id] || '#8b9bc0',
            baseAff,
            interiorAff,
            totalAff,
            attrScore,
            grade,
        };
    });
});

// 该建筑进行中的工程（作为主目标或合并源）
const activeProject = computed(() => (store.state?.projects || []).find(
    (p) => p.buildingId === props.building.id || p.secondBuildingId === props.building.id
) || null);

function interiorProject(typeId) {
    return (store.state?.projects || []).find(
        (p) => (p.projectType || p.type) === 'INTERIOR_UPGRADE' && p.buildingId === props.building.id && p.interiorType === typeId
    ) || null;
}

const levelUpDays = computed(() => form.value.formLevelUpDays?.[level.value - 1] ?? 0);

function discount(key) {
    let m = 1.0;
    const unlocked = store.state?.unlockedSkills || [];
    for (const sid of unlocked) {
        const node = store.config?.skillsById?.[sid];
        if (node && node.effectType === 'COST_DISCOUNT' && node.effectKey === key) {
            m *= Math.max(0, 1 - (node.value || 0));
        }
    }
    return m;
}

const interiorDiscount = computed(() => discount('INTERIOR_UPGRADE_COST_DISCOUNT'));
const evolveDiscount = computed(() => discount('EVOLVE_COST_DISCOUNT'));

const interiorRows = computed(() => {
    const cap = form.value.maxInteriorLevel?.[level.value - 1] ?? 3;
    const mother = props.building.mother;
    const allowedList = form.value.availableInteriors;
    return (store.config?.interiors || [])
        .filter((it) => {
            if (Array.isArray(allowedList) && allowedList.length > 0) {
                return allowedList.includes(it.id);
            }
            if (mother && it.applicableMothers && it.applicableMothers.length > 0) {
                return it.applicableMothers.includes(mother);
            }
            if (!isSupport.value) {
                return it.applicableMothers?.includes('STANDARD_ROOM');
            }
            return true;
        })
        .map((it) => {
            const inst = (props.building.interiors || []).find((x) => x.interiorType === it.id);
            const cur = inst?.level || 0;
            const rem = Math.max(0, cap - cur);
            const rawCost = rem > 0 ? (it.upgradeCostByLevel?.[cur] ?? 0) : 0;
            const cost = Math.round(rawCost * interiorDiscount.value);
            const days = rem > 0 ? (it.upgradeDaysByLevel?.[cur] ?? 0) : 0;
            const proj = interiorProject(it.id);
            return { ...it, cur, cap, rem, cost, days, proj, canUp: !proj && rem > 0 && funds.value >= cost };
        });
});

const levelUpCost = computed(() => Math.round((form.value.formLevelUpCost?.[level.value - 1] ?? 0) * evolveDiscount.value));
const canLevelUp = computed(
    () => !activeProject.value && level.value < maxLevel.value && funds.value >= levelUpCost.value
);

const branchTargets = computed(() => {
    const lines = store.config?.evolutionLines || [];
    const myLines = lines.filter((l) => l.sourceFormId === form.value?.id);
    return myLines.map((line) => {
        const targetForm = store.config?.formsById?.[line.targetFormId] || 
                           (store.config?.buildingForms || []).find((f) => f.id === line.targetFormId);
        if (!targetForm) return null;
        const reqLevel = line.requiredLevel ?? maxLevel.value;
        const cost = (line.cost && line.cost > 0) ? line.cost : (targetForm.evolveCost || 0);
        const days = (line.days && line.days > 0) ? line.days : (targetForm.evolveDays || 0);
        return {
            ...targetForm,
            lineConfig: line,
            evolveCost: cost,
            evolveDays: days,
            requiredLevel: reqLevel,
        };
    }).filter(Boolean);
});

function branchMeta(t) {
    const cost = Math.round((t.evolveCost || 0) * evolveDiscount.value);
    const tierOk = !plot.value || (t.tier || 0) <= (plot.value.evolveTierCap ?? 3);
    const reqLevelOk = level.value >= (t.requiredLevel || maxLevel.value);
    const fundsOk = funds.value >= cost;
    const noProject = !activeProject.value;

    let reason = '';
    if (!reqLevelOk) reason = `需升至 Lv${t.requiredLevel || maxLevel.value}`;
    else if (!tierOk) reason = `超过地块上限 Tier ${plot.value?.evolveTierCap ?? 3}`;
    else if (!fundsOk) reason = '资金不足';
    else if (!noProject) reason = '已有工程进行中';

    return {
        cost,
        days: t.evolveDays || 0,
        can: reqLevelOk && tierOk && fundsOk && noProject,
        reason,
    };
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
                <div class="kv">
                    <span class="k">每日服务容量</span>
                    <span class="v num">{{ totalCapacity }} 人次
                        <span class="faint" v-if="interiorCapacityBonus > 0">（基础 {{ baseCapacity }} + 内装 {{ interiorCapacityBonus }}）</span>
                    </span>
                </div>
                <div class="kv"><span class="k">单人收益（Lv{{ level }}）</span><span class="v num">{{ fmtMoney(unitPrice) }}</span></div>
                <div class="kv total">
                    <span class="k">满员日收益上限</span>
                    <span class="v num gold">{{ fmtMoney(totalCapacity * unitPrice) }}</span>
                </div>
            </div>
            <div class="panel-body" v-else>
                <div class="kv">
                    <span class="k">每间接待人数</span>
                    <span class="v num">{{ totalCapacity }} 人
                        <span class="faint" v-if="interiorCapacityBonus > 0">（基础 {{ baseCapacity }} + 内装 {{ interiorCapacityBonus }}）</span>
                    </span>
                </div>
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
            </div>
        </div>

        <!-- 综合舒适度分析卡片 -->
        <div class="detail-card">
            <div class="card-head">
                <span class="card-title">综合舒适度剖析</span>
                <span class="score-badge" :class="staffFactor < 0.999 ? 'warn' : 'pos'">
                    ★ {{ totalComfort.toFixed(2) }}
                </span>
            </div>
            <div class="comfort-breakdown">
                <div class="bd-row">
                    <span class="lbl">形态基础舒适度</span>
                    <span class="val">{{ baseComfort.toFixed(2) }}</span>
                </div>
                <div class="bd-row">
                    <span class="lbl">该等级形态舒适度提升系数 (Lv{{ level }})</span>
                    <span class="val" :class="levelComfortRatio > 0 ? 'pos' : 'dim'">
                        +{{ levelComfortRatio.toFixed(2) }}
                        <span class="faint" v-if="levelComfortRatio > 0">（提升后 {{ effectiveBaseComfort.toFixed(2) }}）</span>
                    </span>
                </div>
                <div class="bd-row">
                    <span class="lbl">内装舒适加成</span>
                    <span class="val pos">+{{ interiorComfort.toFixed(2) }}</span>
                </div>
                <div class="bd-row">
                    <span class="lbl">人力供给系数</span>
                    <span class="val" :class="staffFactor < 0.999 ? 'neg' : 'dim'">
                        × {{ staffFactor.toFixed(2) }}
                    </span>
                </div>
            </div>
            <div v-if="staffFactor < 0.999" class="shortage-alert">
                ⚠️ 人力供给不足导致舒适度打折！短缺项：
                <span v-for="s in shortages" :key="s.ability" class="short-pill">
                    {{ s.name }} (供给度 {{ s.ratio.toFixed(2) }})
                </span>
            </div>
        </div>

        <!-- 客群相性与吸引力贡献卡片 -->
        <div class="detail-card">
            <div class="card-head">
                <span class="card-title">客群吸引力与相性贡献</span>
                <span class="faint small">影响每日客流转化分配</span>
            </div>
            <div class="affinity-grid">
                <div v-for="seg in segmentAffinities" :key="seg.id" class="aff-card" :style="{ borderColor: seg.color }">
                    <div class="aff-top">
                        <span class="seg-info">
                            <span class="aff-icon">{{ seg.icon }}</span>
                            <span class="aff-name" :style="{ color: seg.color }">{{ seg.name }}</span>
                        </span>
                        <span class="grade-tag" :style="{ background: seg.color }">{{ seg.grade }}</span>
                    </div>
                    <div class="aff-mid">
                        <div class="aff-line">
                            <span class="faint">形态基础</span>
                            <span class="num">{{ seg.baseAff.toFixed(2) }}</span>
                        </div>
                        <div class="aff-line">
                            <span class="faint">内装加成</span>
                            <span class="num pos">+{{ seg.interiorAff.toFixed(2) }}</span>
                        </div>
                        <div class="aff-line total">
                            <span class="faint">综合相性</span>
                            <span class="num bold" :style="{ color: seg.color }">{{ seg.totalAff.toFixed(2) }}</span>
                        </div>
                    </div>
                    <div class="aff-bot">
                        <span class="faint">吸引力贡献</span>
                        <span class="score gold">{{ seg.attrScore.toFixed(2) }}</span>
                    </div>
                </div>
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
            <div v-if="!branchTargets.length" class="faint">该形态无可分支的下一阶段</div>
            <div v-else>
                <div v-if="level < maxLevel" class="faint" style="margin-bottom: 8px">
                    当前 Lv{{ level }}，需先升到 Lv{{ maxLevel }} 达到进化条件
                </div>
                <div v-for="t in branchTargets" :key="t.id" class="bld-row">
                    <div class="main">
                        <div class="title">{{ t.displayName }} <span class="tag base" style="font-size: 11px">Tier {{ t.tier }}</span></div>
                        <div class="sub">
                            每间 {{ t.capacityPerRoom }} 人 · 房屋收益 Lv1 {{ fmtMoney(t.pricePerRoomByLevel?.[0] || t.pricePerRoom?.[0] || 0) }}
                        </div>
                        <div v-if="!branchMeta(t).can && branchMeta(t).reason" class="sub" style="color: var(--neg); font-size: 11px">
                            不可进化：{{ branchMeta(t).reason }}
                        </div>
                    </div>
                    <button class="gold" :disabled="!branchMeta(t).can"
                            @click="api.branchEvolve(building.id, t.id)">
                        进化 {{ fmtMoney(branchMeta(t).cost) }}（{{ branchMeta(t).days }} 天）
                    </button>
                </div>
            </div>
        </template>

        <h4 class="sec">内装升级</h4>
        <div v-if="!interiorRows.length" class="faint">暂无可升级内装</div>
        <div v-for="it in interiorRows" :key="it.id" class="bld-row">
            <div class="main">
                <div class="title">{{ it.displayName }}</div>
                <div class="sub">
                    当前 Lv{{ it.cur }} / {{ it.cap }}（{{ it.categoryDisplayName }} · 舒适 +{{ it.comfortPerLevel }}/级<template
                        v-if="it.id === capacityInteriorId && it.capacityPerLevel > 0"> · 容量 +{{ it.capacityPerLevel }}/级</template>）
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

/* 舒适度与吸引力详情卡片 */
.detail-card {
    background: rgba(255, 255, 255, 0.02);
    border: 1px solid rgba(255, 255, 255, 0.07);
    border-radius: var(--radius-sm, 8px);
    padding: 10px 12px;
    margin-top: 10px;
}
.card-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 8px;
}
.card-title {
    font-size: 13px;
    font-weight: 600;
    color: var(--text);
}
.score-badge {
    font-size: 14px;
    font-weight: 700;
    padding: 2px 8px;
    border-radius: 12px;
}
.score-badge.pos {
    color: #5eead4;
    background: rgba(94, 234, 212, 0.12);
    border: 1px solid rgba(94, 234, 212, 0.3);
}
.score-badge.warn {
    color: #ff8787;
    background: rgba(255, 135, 135, 0.12);
    border: 1px solid rgba(255, 135, 135, 0.3);
}

.comfort-breakdown {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 8px;
    background: rgba(0, 0, 0, 0.15);
    padding: 8px 10px;
    border-radius: 6px;
}
.bd-row {
    display: flex;
    flex-direction: column;
    font-size: 11px;
}
.bd-row .lbl {
    color: var(--text-dim, #8b9bc0);
    font-size: 10px;
    margin-bottom: 2px;
}
.bd-row .val {
    font-weight: 600;
    font-size: 13px;
}

.shortage-alert {
    margin-top: 8px;
    font-size: 11px;
    color: #ff8787;
    background: rgba(255, 135, 135, 0.08);
    border: 1px solid rgba(255, 135, 135, 0.2);
    border-radius: 4px;
    padding: 6px 8px;
    line-height: 1.5;
}
.short-pill {
    display: inline-block;
    background: rgba(255, 135, 135, 0.2);
    padding: 1px 5px;
    border-radius: 4px;
    margin: 0 2px;
    font-weight: 600;
}

/* 客群相性四宫格 */
.affinity-grid {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 8px;
}
.aff-card {
    background: rgba(0, 0, 0, 0.2);
    border: 1px solid;
    border-radius: 6px;
    padding: 8px 10px;
}
.aff-top {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 6px;
}
.seg-info {
    display: flex;
    align-items: center;
    gap: 5px;
    font-size: 12px;
    font-weight: 600;
}
.grade-tag {
    color: #000;
    font-weight: 800;
    font-size: 10px;
    padding: 1px 6px;
    border-radius: 4px;
}
.aff-mid {
    display: flex;
    flex-direction: column;
    gap: 3px;
    background: rgba(0, 0, 0, 0.2);
    border-radius: 4px;
    padding: 5px 8px;
    margin: 4px 0 6px 0;
}
.aff-line {
    display: flex;
    justify-content: space-between;
    font-size: 11px;
}
.aff-line.total {
    border-top: 1px dashed rgba(255, 255, 255, 0.12);
    padding-top: 3px;
    margin-top: 2px;
}
.aff-bot {
    display: flex;
    justify-content: space-between;
    font-size: 11px;
    padding: 0 2px;
}
.aff-bot .score {
    font-weight: 700;
}
</style>