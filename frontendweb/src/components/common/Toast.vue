<script setup>
import { store } from '../../services/store.js';
</script>

<template>
    <div class="toast-layer">
        <div v-for="t in store.toasts" :key="t.id" class="toast" :class="t.kind">{{ t.text }}</div>
    </div>
</template>