<script setup>
import { computed } from 'vue';
import { store } from '../../services/store.js';
import { fmtMoney, fmtPercent, fmtInt, moneyClass } from '../../utils/format.js';

const p = computed(() => store.state?.todayPreview || null);
</script>

<template>
    <div class="panel">
        <div class="panel-head">
            <span class="title">今日预计</span>
            <span class="extra faint">次日 0 点结算生效</span>
        </div>
        <div class="panel-body" v-if="p">
            <div class="kv"><span class="k">预计接待人数</span><span class="v num">{{ fmtInt(p.expectedGuests) }} 人</span></div>
            <div class="kv"><span class="k">售出 / 总间数</span><span class="v num">{{ p.occupiedRooms }} / {{ p.totalRooms }}</span></div>
            <div class="kv"><span class="k">入住率</span><span class="v num">{{ fmtPercent(p.occupancyRate) }}</span></div>
            <div class="kv"><span class="k">住宿收入</span><span class="v num pos">+{{ fmtMoney(p.accommodationRevenue) }}</span></div>
            <div class="kv"><span class="k">配套收入</span><span class="v num pos">+{{ fmtMoney(p.supportRevenue) }}</span></div>
            <div class="kv"><span class="k">建筑维护费</span><span class="v num neg">-{{ fmtMoney(p.maintenance) }}</span></div>
            <div class="kv"><span class="k">员工薪资</span><span class="v num neg">-{{ fmtMoney(p.salary) }}</span></div>
            <div class="kv total"><span class="k">预计净利润</span><span class="v num" :class="moneyClass(p.netProfit)">{{ fmtMoney(p.netProfit) }}</span></div>
        </div>
        <div class="panel-body empty" v-else>
            <span class="big">·</span>暂无预计数据
        </div>
    </div>
</template>