<script setup lang="ts">
/** Standard vertical rhythm and width for a page band; `tone="soft"` alternates the background. */
withDefaults(defineProps<{ tone?: 'paper' | 'soft' | 'surface'; narrow?: boolean }>(), {
  tone: 'paper',
})

const tones = { paper: 'bg-paper', soft: 'bg-surface-soft', surface: 'bg-surface' }
</script>

<template>
  <section class="overflow-x-clip py-16 md:py-24" :class="tones[tone]">
    <div class="page-container" :class="{ 'max-w-3xl': narrow }">
      <slot />
    </div>
  </section>
</template>
