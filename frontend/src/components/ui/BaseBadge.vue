<script setup lang="ts">
withDefaults(defineProps<{ tone?: 'neutral' | 'accent' | 'success' | 'danger' }>(), {
  tone: 'neutral',
})

const tones = {
  neutral: 'border-line-soft text-ink-muted',
  accent: 'border-accent text-accent',
  success: 'border-success text-success',
  danger: 'border-danger text-danger',
}
</script>

<template>
  <span class="label-caps inline-flex items-center border px-2.5 py-0.5 text-[0.6875rem]" :class="tones[tone]">
    <slot />
  </span>
</template>
