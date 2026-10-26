<script setup>
import { ref, computed, onMounted } from 'vue';
import { store, connect } from './services/store.js';

import StatusBar from './components/overview/StatusBar.vue';
import OverviewBuildingList from './components/overview/OverviewBuildingList.vue';
import DayPreview from './components/overview/DayPreview.vue';
import GuestMixPanel from './components/overview/GuestMixPanel.vue';
import AbilityPanel from './components/overview/AbilityPanel.vue';
import RegionPanel from './components/region/RegionPanel.vue';
import AchievementProgress from './components/region/AchievementProgress.vue';
import PlotMapPanel from './components/building/PlotMapPanel.vue';
import StaffPanel from './components/staff/StaffPanel.vue';
import SkillTreePanel from './components/skill/SkillTreePanel.vue';
import BuildModal from './components/building/BuildModal.vue';
import BuildingDetailModal from './components/building/BuildingDetailModal.vue';
import SettlementLogModal from './components/log/SettlementLogModal.vue';
import AdminConfigModal from './components/admin/AdminConfigModal.vue';
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
const buildTarget = ref(null);
const detailBuildingId = ref(null);
const showLog = ref(false);
const showAdmin = ref(false);

// 详情弹窗按 ID 从最新状态解析，保证次日生效后（内装 / 形态 / 收益变化）弹窗内容实时刷新
const detailBuilding = computed(
    () => (store.state?.buildings || []).find((b) => b.id === detailBuildingId.value) || null
);

function openBuild(payload) { buildTarget.value = payload; }
function openBuilding(b) { detailBuildingId.value = b.id; }
</script>

<template>
    <div class="shell">
        <StatusBar @open-admin="showAdmin = true" />

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
                尚未开启对局，可点击右上角「管理员模式」自定义配置，或点击「开始对局」直接启动。
            </div>

            <template v-else>
                <div v-if="activeTab === 'overview'" class="grid two">
                    <DayPreview />
                    <GuestMixPanel />
                    <AbilityPanel />
                    <OverviewBuildingList @open-building="openBuilding" />
                </div>

                <div v-else-if="activeTab === 'region'" class="grid">
                    <RegionPanel />
                    <AchievementProgress />
                </div>

                <div v-else-if="activeTab === 'building'">
                    <PlotMapPanel @open-building="openBuilding" @open-build="openBuild" />
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

        <BuildModal v-if="buildTarget" :plot="buildTarget.plot" :cellX="buildTarget.cellX" :cellY="buildTarget.cellY" @close="buildTarget = null" />
        <BuildingDetailModal v-if="detailBuilding" :building="detailBuilding" @close="detailBuildingId = null" />
        <SettlementLogModal v-if="showLog" @close="showLog = false" />
        <AdminConfigModal v-if="showAdmin" @close="showAdmin = false" />

        <Toast />
    </div>
</template>