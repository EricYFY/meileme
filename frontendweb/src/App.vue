<script setup>
import { ref, onMounted, computed } from 'vue';
import { store, connect, api } from './services/store.js';
import { fmtMoney } from './utils/format.js';

import StatusBar from './components/overview/StatusBar.vue';
import PlotMapView from './components/overview/PlotMapView.vue';
import DayPreview from './components/overview/DayPreview.vue';
import RegionPanel from './components/region/RegionPanel.vue';
import AchievementProgress from './components/region/AchievementProgress.vue';
import BuildingListPanel from './components/building/BuildingListPanel.vue';
import StaffPanel from './components/staff/StaffPanel.vue';
import SkillTreePanel from './components/skill/SkillTreePanel.vue';
import BuildModal from './components/building/BuildModal.vue';
import BuildingDetailModal from './components/building/BuildingDetailModal.vue';
import SettlementLogModal from './components/log/SettlementLogModal.vue';
import Toast from './components/common/Toast.vue';

onMounted(() => connect());

const TABS = [
    { key: 'overview', label: '总览' },
    { key: 'region', label: '区块' },
    { key: 'building', label: '建筑' },
    { key: 'staff', label: '员工' },
    { key: 'skill', label: '技能' },
    { key: 'achievement', label: '成就' },
];

const activeTab = ref('overview');
const buildPlot = ref(null);
const detailBuilding = ref(null);
const showLog = ref(false);

function openBuild(plot) { buildPlot.value = plot; }
function openBuilding(b) { detailBuilding.value = b; }

const gameOver = computed(() => store.gameOver);

function restart() {
    store.gameOver = null;
    api.startGame();
}
</script>

<template>
    <div class="shell">
        <StatusBar />

        <div class="nav">
            <button v-for="t in TABS" :key="t.key" class="tab" :class="{ active: activeTab === t.key }"
                    @click="activeTab = t.key">{{ t.label }}</button>
            <div class="spacer" style="flex: 1"></div>
            <button class="tab" @click="showLog = true">结算日志</button>
        </div>

        <div class="main">
            <div v-if="store.booting" class="empty">
                <span class="big">·</span>正在连接服务…
            </div>

            <div v-else-if="!store.state" class="empty">
                <span class="big">·</span>
                尚未开启对局，点击右上角「开始对局」启动。
            </div>

            <template v-else>
                <div v-if="activeTab === 'overview'" class="grid two">
                    <DayPreview />
                    <PlotMapView @open-building="openBuilding" @open-build="openBuild" />
                </div>

                <div v-else-if="activeTab === 'region'" class="grid">
                    <RegionPanel />
                    <AchievementProgress />
                </div>

                <div v-else-if="activeTab === 'building'">
                    <BuildingListPanel @open-building="openBuilding" />
                </div>

                <div v-else-if="activeTab === 'staff'">
                    <StaffPanel />
                </div>

                <div v-else-if="activeTab === 'skill'">
                    <SkillTreePanel />
                </div>

                <div v-else-if="activeTab === 'achievement'">
                    <AchievementProgress />
                </div>
            </template>
        </div>

        <BuildModal v-if="buildPlot" :plot="buildPlot" @close="buildPlot = null" />
        <BuildingDetailModal v-if="detailBuilding" :building="detailBuilding" @close="detailBuilding = null" />
        <SettlementLogModal v-if="showLog" @close="showLog = false" />

        <div v-if="gameOver" class="over-mask">
            <div class="panel" style="max-width: 480px; width: 100%">
                <div class="panel-head"><span class="title neg">对局结束</span></div>
                <div class="panel-body">
                    <p>{{ gameOver.reason }}</p>
                    <p class="dim">结束于第 {{ gameOver.day }} 天</p>
                    <div v-if="gameOver.lastSettle">
                        <div class="kv"><span class="k">住宿收入</span><span class="v num pos">+{{ fmtMoney(gameOver.lastSettle.accommodationRevenue) }}</span></div>
                        <div class="kv"><span class="k">配套收入</span><span class="v num pos">+{{ fmtMoney(gameOver.lastSettle.supportRevenue) }}</span></div>
                        <div class="kv"><span class="k">建筑维护费</span><span class="v num neg">-{{ fmtMoney(gameOver.lastSettle.maintenanceCost) }}</span></div>
                        <div class="kv"><span class="k">员工薪资</span><span class="v num neg">-{{ fmtMoney(gameOver.lastSettle.salaryCost) }}</span></div>
                        <div class="kv total"><span class="k">当日净利润</span><span class="v num" :class="gameOver.lastSettle.netProfit >= 0 ? 'pos' : 'neg'">{{ fmtMoney(gameOver.lastSettle.netProfit) }}</span></div>
                    </div>
                </div>
                <div class="panel-body" style="padding-top: 0; display: flex; gap: 10px; justify-content: flex-end">
                    <button class="primary" @click="restart">重开一局</button>
                </div>
            </div>
        </div>

        <Toast />
    </div>
</template>