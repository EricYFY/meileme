<script setup>
defineProps({
    buildable: { type: Boolean, default: false },
});
const emit = defineEmits(['click']);
</script>

<template>
    <div class="slot" :class="{ buildable }" @click="buildable && emit('click')">
        <span v-if="buildable" class="plus">+</span>
        <span v-else class="dim">空</span>
    </div>
</template>