<script setup lang="ts">
withDefaults(defineProps<{ tone?: 'info' | 'success' | 'danger' }>(), { tone: 'info' })

const tones = {
  info: 'border-l-accent',
  success: 'border-l-success',
  danger: 'border-l-danger',
}
</script>

<template>
  <div
    :role="tone === 'danger' ? 'alert' : 'status'"
    class="border border-l-[3px] border-line-soft bg-surface px-4 py-3 text-sm"
    :class="tones[tone]"
  >
    <slot />
  </div>
</template>
