<script setup>
defineProps({
    title: { type: String, default: '' },
    wide: { type: Boolean, default: false },
});
const emit = defineEmits(['close']);
</script>

<template>
    <div class="modal-mask" @click.self="emit('close')">
        <div class="modal" :class="{ wide }">
            <div class="modal-head">
                <span>{{ title }}</span>
                <button class="ghost x" @click="emit('close')">关闭</button>
            </div>
            <div class="modal-body">
                <slot />
            </div>
            <div v-if="$slots.footer" class="modal-foot">
                <slot name="footer" />
            </div>
        </div>
    </div>
</template>