<script setup lang="ts">
defineProps<{ title: string }>()
</script>

<template>
  <section>
    <h3
      class="mx-auto mb-5 max-w-sm text-center font-display text-2xl tracking-[0.04em] uppercase underline decoration-1 underline-offset-[6px]"
    >
      {{ title }}
    </h3>
    <dl class="flex flex-col gap-3.5">
      <slot />
    </dl>
  </section>
</template>
