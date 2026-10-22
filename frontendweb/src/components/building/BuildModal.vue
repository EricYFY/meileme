<script setup>
import { ref, computed } from 'vue';
import { store, api } from '../../services/store.js';
import { fmtMoney } from '../../utils/format.js';
import Modal from '../common/Modal.vue';

const props = defineProps({
    plot: { type: Object, required: true },
    cellX: { type: Number, default: 0 },
    cellY: { type: Number, default: 0 },
});
const emit = defineEmits(['close']);

const buildable = computed(() => {
    const whitelist = [...(props.plot.accommodationWhitelist || []), ...(props.plot.supportWhitelist || [])];
    return whitelist
        .map((mid) => store.config?.mothersById?.[mid])
        .filter((m) => m && m.phase1Buildable);
});

const selectedId = ref(null);
const activeId = computed(() => selectedId.value || buildable.value[0]?.id || null);
const baseForm = computed(() => store.config?.formsById?.[activeId.value]);
const funds = computed(() => store.state?.funds ?? 0);

const cost = computed(() => baseForm.value?.buildCost || 0);
const canAfford = computed(() => funds.value >= cost.value);
const days = computed(() => store.config?.mothersById?.[activeId.value]?.buildDays || 0);
const isSupport = computed(() => store.config?.mothersById?.[activeId.value]?.category === 'SUPPORT');

function confirm() {
    if (!activeId.value) return;
    api.build(activeId.value, props.plot.id, props.cellX, props.cellY);
    emit('close');
}
</script>

<template>
    <Modal :title="'新建建筑 @ ' + plot.displayName + '（格 ' + plot.id + ' ' + cellX + ',' + cellY + '）'" @close="emit('close')">
        <div v-if="!buildable.length" class="empty faint">当前地块无可建造母本（第一期仅开放标准客房）</div>

        <div v-for="m in buildable" :key="m.id" class="bld-row" :class="{ active: m.id === activeId }" @click="selectedId = m.id">
            <div class="main">
                <div class="title">{{ m.displayName }}</div>
                <div class="sub">{{ m.category === 'ACCOMMODATION' ? '住宿' : '配套' }}</div>
            </div>
            <span class="tag base">造价 {{ fmtMoney(store.config?.formsById?.[m.id]?.buildCost || 0) }}</span>
        </div>

        <div v-if="baseForm" class="panel" style="margin-top: 14px">
            <div class="panel-body">
                <template v-if="isSupport">
                    <div class="kv"><span class="k">每日服务容量</span><span class="v num">{{ baseForm.serviceCapacity || 0 }} 人次</span></div>
                    <div class="kv"><span class="k">单人收益（Lv1）</span><span class="v num">{{ fmtMoney(baseForm.unitPriceByLevel?.[0] || 0) }}</span></div>
                </template>
                <template v-else>
                    <div class="kv"><span class="k">每间接待人数</span><span class="v num">{{ baseForm.capacityPerRoom }} 人</span></div>
                    <div class="kv"><span class="k">基础房屋收益（Lv1）</span><span class="v num">{{ fmtMoney(baseForm.pricePerRoomByLevel?.[0] || 0) }}</span></div>
                </template>
                <div class="kv total">
                    <span class="k">一次性建造花费</span>
                    <span class="v num" :class="canAfford ? 'pos' : 'neg'">{{ fmtMoney(cost) }}</span>
                </div>
                <div class="kv"><span class="k">建造工期</span><span class="v num">{{ days }} 天（自次日起累计）</span></div>
                <div class="kv"><span class="k">当前资金</span><span class="v num gold">{{ fmtMoney(funds) }}</span></div>
            </div>
        </div>

        <template #footer>
            <button class="ghost" @click="emit('close')">取消</button>
            <button class="primary" :disabled="!canAfford || !activeId" @click="confirm">
                开始建造（{{ days }} 天）
            </button>
        </template>
    </Modal>
</template>

<style scoped>
.bld-row.active { border-color: var(--accent); }
</style>