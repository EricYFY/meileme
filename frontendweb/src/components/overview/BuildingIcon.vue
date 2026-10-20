<script setup>
import { computed } from 'vue';

const props = defineProps({
    building: { type: Object, required: true },
});

const LINE_LABEL = { BASE: '基', FAMILY: '家', BUSINESS: '商', MERGE: '合' };
const LINE_CLASS = { BASE: 'base', FAMILY: 'family', BUSINESS: 'business', MERGE: 'merge' };

const form = computed(() => props.building.form || {});
const line = computed(() => form.value.line || 'BASE');
const badge = computed(() => LINE_LABEL[line.value] || '基');
const lineClass = computed(() => LINE_CLASS[line.value] || 'base');
const name = computed(() => form.value.displayName || props.building.mother || '');
const level = computed(() => props.building.formLevel || 1);
</script>

<template>
    <div class="building-tile">
        <span class="badge" :class="lineClass">{{ badge }}</span>
        <span class="nm">{{ name }}</span>
        <span class="nm faint">Lv{{ level }}</span>
    </div>
</template>

<style scoped>
.badge {
    width: 26px;
    height: 26px;
    border-radius: 7px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-weight: 700;
    font-size: 13px;
    color: #fff;
}
.badge.base { background: #333d55; }
.badge.family { background: #2c4a3c; }
.badge.business { background: #3d3a6b; }
.badge.merge { background: #5c4227; }
</style>