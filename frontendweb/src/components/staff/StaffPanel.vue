<script setup>
import { reactive, computed } from 'vue';
import { store, api } from '../../services/store.js';
import { fmtMoney, fmtInt, shortId } from '../../utils/format.js';

const positions = computed(() => store.config?.positions || []);
const staffs = computed(() => (store.state?.staffs || []).filter((s) => s.alive));

const hireLevel = reactive({});
function levelFor(posId) { return hireLevel[posId] || 1; }
function hireCost(p) { return (p.hireCost || 0) * levelFor(p.id); }

function abilityNames(p) {
    return (p.abilities || []).map((a) => store.config?.abilitiesById?.[a]?.displayName || a).join('、');
}

function compensation(staffId) {
    return (store.state?.staffCompensations || {})[staffId] ?? 0;
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
                <span class="extra faint">次日生效 · 招聘必定成功 · 无冷却</span>
            </div>
            <div class="panel-body">
                <div class="staff-grid">
                    <div v-for="p in positions" :key="p.id" class="staff-card">
                        <div class="title">{{ p.displayName }}</div>
                        <div class="sub">{{ abilityNames(p) }}</div>
                        <div class="row"><span class="dim">日薪 Lv1/2/3</span><span class="num">{{ fmtMoney(p.salaryByLevel?.[0]) }} / {{ fmtMoney(p.salaryByLevel?.[1]) }} / {{ fmtMoney(p.salaryByLevel?.[2]) }}</span></div>
                        <div class="row"><span class="dim">能力值 Lv1/2/3</span><span class="num">{{ fmtInt(p.abilityValueByLevel?.[0]) }} / {{ fmtInt(p.abilityValueByLevel?.[1]) }} / {{ fmtInt(p.abilityValueByLevel?.[2]) }}</span></div>
                        <div class="row" style="align-items: center">
                            <span class="dim">等级</span>
                            <span style="display: flex; gap: 4px">
                                <button v-for="lv in [1, 2, 3]" :key="lv" class="ghost"
                                        :class="{ primary: levelFor(p.id) === lv }"
                                        @click="hireLevel[p.id] = lv">Lv{{ lv }}</button>
                            </span>
                        </div>
                        <button class="primary" style="width: 100%" @click="api.hire(p.id, levelFor(p.id))">
                            招聘 · {{ fmtMoney(hireCost(p)) }}
                        </button>
                    </div>
                </div>
            </div>
        </div>

        <div class="panel">
            <div class="panel-head">
                <span class="title">在册员工</span>
                <span class="extra faint">在岗累计游戏日达标自动升级</span>
            </div>
            <div class="panel-body">
                <div v-if="!staffs.length" class="empty faint">暂无在册员工</div>
                <template v-for="g in groupByPosition()" :key="g.position.id">
                    <div class="staff-group"><div class="grp-title">{{ g.position.displayName }} × {{ g.list.length }}</div></div>
                    <div class="staff-grid">
                        <div v-for="s in g.list" :key="s.id" class="staff-card">
                            <div class="row"><span class="dim">ID</span><span class="num">{{ shortId(s.id) }}</span></div>
                            <div class="row"><span class="dim">等级</span><span class="num">Lv{{ s.level }}</span></div>
                            <div class="row"><span class="dim">在岗累计</span><span class="num">{{ s.workDays }} 天</span></div>
                            <div class="row"><span class="dim">解雇补偿</span><span class="num neg">{{ fmtMoney(compensation(s.id)) }}</span></div>
                            <button class="danger" style="width: 100%" @click="api.fire(s.id)">解雇</button>
                        </div>
                    </div>
                </template>
            </div>
        </div>
    </div>
</template>