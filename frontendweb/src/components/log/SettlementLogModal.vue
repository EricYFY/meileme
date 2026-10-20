<script setup>
import { onMounted, computed } from 'vue';
import { store, api } from '../../services/store.js';
import { fmtMoney, fmtPercent, fmtInt, moneyClass } from '../../utils/format.js';
import Modal from '../common/Modal.vue';

const emit = defineEmits(['close']);

onMounted(() => {
    api.requestDailyLog();
    api.requestRangeReport(7);
});

const history = computed(() => {
    const h = store.dailyLog?.history || [];
    return [...h].reverse();
});

const range = computed(() => store.rangeReport);

function requestRange(days) { api.requestRangeReport(days); }
</script>

<template>
    <Modal title="结算日志" :wide="true" @close="emit('close')">
        <div class="panel" style="margin-bottom: 16px">
            <div class="panel-head">
                <span class="title">区间报告</span>
                <span class="extra">
                    <button v-for="d in [1, 7, 30, 365]" :key="d" class="ghost"
                            @click="requestRange(d)">近 {{ d }} 天</button>
                </span>
            </div>
            <div class="panel-body" v-if="range">
                <div class="kv"><span class="k">统计天数</span><span class="v num">{{ range.days }} 天</span></div>
                <div class="kv"><span class="k">总收入</span><span class="v num pos">+{{ fmtMoney(range.totalRevenue) }}</span></div>
                <div class="kv"><span class="k">总支出</span><span class="v num neg">-{{ fmtMoney(range.totalExpense) }}</span></div>
                <div class="kv total"><span class="k">净利润</span><span class="v num" :class="moneyClass(range.netProfit)">{{ fmtMoney(range.netProfit) }}</span></div>
                <div class="kv"><span class="k">累计接待</span><span class="v num">{{ fmtInt(range.totalGuests) }} 人</span></div>
                <div class="kv"><span class="k">溢出流失</span><span class="v num">{{ fmtInt(range.overflowGuests) }} 人</span></div>
                <div class="kv"><span class="k">平均入住率</span><span class="v num">{{ fmtPercent(range.avgOccupancyRate) }}</span></div>
            </div>
        </div>

        <div v-if="!history.length" class="empty faint">暂无结算记录</div>
        <table v-else class="mini">
            <thead>
                <tr>
                    <th>天</th><th>日期</th>
                    <th class="num">收入</th><th class="num">支出</th>
                    <th class="num">净利润</th><th class="num">入住率</th><th class="num">接待</th>
                </tr>
            </thead>
            <tbody>
                <tr v-for="d in history" :key="d.day">
                    <td>{{ d.day }}</td>
                    <td>{{ d.dateDisplay }}</td>
                    <td class="num pos">{{ fmtMoney(d.totalRevenue) }}</td>
                    <td class="num neg">{{ fmtMoney(d.totalExpense) }}</td>
                    <td class="num" :class="moneyClass(d.netProfit)">{{ fmtMoney(d.netProfit) }}</td>
                    <td class="num">{{ fmtPercent(d.occupancyRate) }}</td>
                    <td class="num">{{ fmtInt(d.totalServed) }}</td>
                </tr>
            </tbody>
        </table>
    </Modal>
</template>