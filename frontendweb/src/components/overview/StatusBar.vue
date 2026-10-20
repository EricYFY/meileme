<script setup>
import { computed } from 'vue';
import { store, api } from '../../services/store.js';
import { fmtMoney } from '../../utils/format.js';

const scenicName = computed(() => store.config?.scenic?.name || '山间民宿度假村');
const running = computed(() => store.state?.running === true && !store.state?.gameOver);
const paused = computed(() => store.tick?.paused === true);
const speed = computed(() => store.tick?.speed ?? 1);
const day = computed(() => store.tick.day ?? store.state?.day ?? 1);
const date = computed(() => store.tick.date || store.state?.date || '');
const funds = computed(() => store.tick.funds ?? store.state?.funds ?? 0);

function setSpeed(v) { api.setSpeed(v); }
</script>

<template>
    <div class="statusbar">
        <div class="brand">
            山间民宿度假村
            <span class="sub">挂机经营</span>
        </div>
        <div class="sep"></div>
        <div class="stat">
            <span class="k">第 N 天 / 日期</span>
            <span class="v">{{ day }} 天 · {{ date }}</span>
        </div>
        <div class="sep"></div>
        <div class="stat">
            <span class="k">资金池</span>
            <span class="v money num">{{ fmtMoney(funds) }}</span>
        </div>
        <div class="spacer"></div>

        <span class="dim" v-if="store.connected">已连接</span>
        <span class="neg" v-else>连接中…</span>

        <div class="ctrl" v-if="running">
            <div class="speed-grp">
                <button v-for="o in store.config?.speedOptions || [1]" :key="o"
                        class="opt" :class="{ active: o === speed }"
                        @click="setSpeed(o)">{{ o }}×</button>
            </div>
            <button v-if="!paused" class="ghost" @click="api.pause()">暂停</button>
            <button v-else class="primary" @click="api.resume()">继续</button>
        </div>

        <button v-if="!running && !store.gameOver" class="primary" @click="api.startGame()">开始对局</button>
        <button v-if="running" class="danger" @click="api.stopGame()">结束</button>
    </div>
</template>