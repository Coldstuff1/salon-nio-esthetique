<script setup lang="ts">
defineOptions({ inheritAttrs: false })
defineProps<{ label: string }>()
const model = defineModel<boolean>()
</script>

<template>
  <label class="inline-flex cursor-pointer items-center gap-3">
    <span class="relative inline-flex">
      <input
        v-model="model"
        type="checkbox"
        v-bind="$attrs"
        class="peer size-4 cursor-pointer appearance-none border border-line bg-surface transition-colors checked:border-accent checked:bg-accent"
      />
      <svg
        viewBox="0 0 16 16"
        class="pointer-events-none absolute inset-0 hidden size-4 text-accent-ink peer-checked:block"
        aria-hidden="true"
      >
        <path d="M3.5 8.5l3 3 6-7" fill="none" stroke="currentColor" stroke-width="1.75" />
      </svg>
    </span>
    <span>{{ label }}</span>
  </label>
</template>
