<script setup lang="ts">
withDefaults(defineProps<{ as?: string; padded?: boolean }>(), { as: 'div', padded: true })
</script>

<template>
  <component :is="as" class="border border-line bg-surface" :class="{ 'p-6 sm:p-8': padded }">
    <slot />
  </component>
</template>
