<script setup>
import { computed } from 'vue';

const props = defineProps({
    ratio: { type: Number, default: 0 },   // 0 ~ 1
    color: { type: String, default: '' },   // ''(accent) | gold | pos | neg
});
const width = computed(() => Math.max(0, Math.min(100, (props.ratio || 0) * 100)));
</script>

<template>
    <div class="pbar">
        <span :class="color" :style="{ width: width + '%' }"></span>
    </div>
</template>