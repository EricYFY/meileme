<script setup>
import { computed } from 'vue';
import { store, api } from '../../services/store.js';
import { fmtMoney, fmtInt, shortId } from '../../utils/format.js';

const positions = computed(() => store.config?.positions || []);
const staffs = computed(() => (store.state?.staffs || []).filter((s) => s.alive));
const funds = computed(() => store.state?.funds || 0);

function abilityNames(p) {
    return (p.abilities || []).map((a) => store.config?.abilitiesById?.[a]?.displayName || a).join('、');
}

function compensation(staffId) {
    return (store.state?.staffCompensations || {})[staffId] ?? 0;
}

/** 后端下发的培训状态：在岗天数 / 达标所需天数 / 培训费 / 是否达标 / 是否满级 */
function training(staffId) {
    return (store.state?.staffTraining || {})[staffId] || null;
}

function trainDays(s) { return training(s.id)?.daysAtCurrentLevel ?? s.daysAtCurrentLevel ?? 0; }
function trainRequired(s) { return training(s.id)?.requiredDays ?? 0; }
function trainCost(s) { return training(s.id)?.trainCost ?? 0; }
function atMaxLevel(s) { return !!training(s.id)?.maxLevel || (training(s.id) ? false : s.level >= (store.config?.staffMaxLevel || 3)); }
function readyToTrain(s) { return !!training(s.id)?.ready; }

function isStaffTraining(s) {
    return !!(training(s.id)?.training || s.training);
}

function trainingRemaining(s) {
    return training(s.id)?.trainingDaysRemaining ?? s.trainingDaysRemaining ?? 0;
}

function trainingTotal(s) {
    return training(s.id)?.trainingTotalDays ?? s.trainingTotalDays ?? (training(s.id)?.trainingDays || 1);
}

function trainingDaysCfg(s) {
    return training(s.id)?.trainingDays ?? 1;
}

function upgradeDaysStr(p) {
    if (p?.upgradeDaysByLevel && p.upgradeDaysByLevel.length) {
        return p.upgradeDaysByLevel.join(' / ') + ' 天';
    }
    return '10 / 20 天';
}

function trainDaysStr(p) {
    if (p?.trainDaysByLevel && p.trainDaysByLevel.length) {
        return p.trainDaysByLevel.join(' / ') + ' 天';
    }
    return '1 / 2 天';
}

function trainProgress(s) {
    const req = trainRequired(s);
    if (!req) return 0;
    return Math.min(100, Math.round((trainDays(s) / req) * 100));
}

function canTrain(s) {
    return readyToTrain(s) && !isStaffTraining(s) && !atMaxLevel(s) && funds.value >= trainCost(s);
}

function positionAbilityVals(p) {
    const vals = p?.abilityValueByLevel || p?.abilityValues;
    if (vals && vals.length) return vals;
    if (p?.abilityOutputs) {
        const first = Object.values(p.abilityOutputs)[0];
        if (first && first.length) return first;
    }
    return [30, 55, 85];
}

function abilityValue(s) {
    const p = store.config?.positionsById?.[s.position];
    const vals = positionAbilityVals(p);
    return vals[s.level - 1] ?? vals[vals.length - 1] ?? (s.level === 1 ? 30 : (s.level === 2 ? 55 : 85));
}

function groupByPosition() {
    const out = [];
    for (const p of positions.value) {
        const list = staffs.value.filter((s) => s.position === p.id);
        if (list.length) out.push({ position: p, list });
    }
    return out;
}
</script>

<template>
    <div class="grid two">
        <div class="panel">
            <div class="panel-head">
                <span class="title">招聘</span>
                <span class="extra faint">仅可招聘 Lv1 · 次日生效 · 无冷却</span>
            </div>
            <div class="panel-body">
                <div class="staff-grid">
                    <div v-for="p in positions" :key="p.id" class="staff-card">
                        <div class="title">{{ p.displayName }}</div>
                        <div class="sub">{{ abilityNames(p) }}</div>
                        <div class="row"><span class="dim">能力值 Lv1/2/3</span><span class="num">{{ fmtInt(positionAbilityVals(p)[0]) }} / {{ fmtInt(positionAbilityVals(p)[1]) }} / {{ fmtInt(positionAbilityVals(p)[2]) }}</span></div>
                        <div class="row"><span class="dim">晋升在岗要求</span><span class="num">{{ upgradeDaysStr(p) }}</span></div>
                        <div class="row"><span class="dim">培训升级工期</span><span class="num">{{ trainDaysStr(p) }}</span></div>
                        <button class="primary" style="width: 100%" @click="api.hire(p.id)">
                            招聘 Lv1 · {{ fmtMoney(p.hireCost) }}
                        </button>
                    </div>
                </div>
            </div>
        </div>

        <div class="panel">
            <div class="panel-head">
                <span class="title">在册员工</span>
                <span class="extra faint">在岗历练达标后开启培训晋升 · 培训期间原等级能力正常提供</span>
            </div>
            <div class="panel-body">
                <div v-if="!staffs.length" class="empty faint">暂无在册员工</div>
                <template v-for="g in groupByPosition()" :key="g.position.id">
                    <div class="staff-group"><div class="grp-title">{{ g.position.displayName }} × {{ g.list.length }}</div></div>
                    <div class="staff-grid">
                        <div v-for="s in g.list" :key="s.id" class="staff-card">
                            <div class="row"><span class="dim">ID</span><span class="num">{{ shortId(s.id) }}</span></div>
                            <div class="row"><span class="dim">等级</span><span class="num">Lv{{ s.level }}<span class="faint"> · 能力 {{ fmtInt(abilityValue(s)) }}</span></span></div>
                            <div class="row"><span class="dim">在岗累计</span><span class="num">{{ s.workDays }} 天</span></div>
                            <div v-if="!atMaxLevel(s)" class="train-block">
                                <template v-if="isStaffTraining(s)">
                                    <div class="row">
                                        <span class="dim">晋升培训中</span>
                                        <span class="num pos">剩余 {{ trainingRemaining(s) }} 天（共 {{ trainingTotal(s) }} 天）</span>
                                    </div>
                                    <div class="pbar">
                                        <span class="pos" :style="{ width: Math.round(((trainingTotal(s) - trainingRemaining(s)) / Math.max(1, trainingTotal(s))) * 100) + '%' }"></span>
                                    </div>
                                    <div class="faint" style="margin-top: 4px; font-size: 11px;">
                                        ★ 培训期间 Lv{{ s.level }} 能力持续正常生效
                                    </div>
                                    <button class="gold" style="width: 100%; margin-top: 8px" disabled>
                                        培训晋升 Lv{{ (training(s.id)?.trainingTargetLevel || s.level + 1) }} 进行中…
                                    </button>
                                </template>
                                <template v-else>
                                    <div class="row">
                                        <span class="dim">本级在岗历练</span>
                                        <span class="num" :class="readyToTrain(s) ? 'pos' : ''">{{ trainDays(s) }} / {{ trainRequired(s) }} 天</span>
                                    </div>
                                    <div class="pbar"><span :class="readyToTrain(s) ? 'pos' : ''" :style="{ width: trainProgress(s) + '%' }"></span></div>
                                    <button class="gold" style="width: 100%; margin-top: 8px"
                                            :disabled="!canTrain(s)"
                                            @click="api.train(s.id)">
                                        <template v-if="readyToTrain(s)">
                                            开启培训晋升 Lv{{ s.level + 1 }} · {{ fmtMoney(trainCost(s)) }} (工期 {{ trainingDaysCfg(s) }} 天)
                                        </template>
                                        <template v-else>
                                            历练中 · 需在岗满 {{ trainRequired(s) }} 天 (差 {{ Math.max(0, trainRequired(s) - trainDays(s)) }} 天)
                                        </template>
                                    </button>
                                </template>
                            </div>
                            <div v-else class="row"><span class="dim">成长</span><span class="num gold">已满级</span></div>
                            <div class="row"><span class="dim">解雇补偿</span><span class="num neg">{{ fmtMoney(compensation(s.id)) }}</span></div>
                            <button class="danger" style="width: 100%" @click="api.fire(s.id)">解雇</button>
                        </div>
                    </div>
                </template>
            </div>
        </div>
    </div>
</template>

<style scoped>
.train-block { margin-top: 6px; }
.train-block .pbar { margin-top: 2px; }
</style>
