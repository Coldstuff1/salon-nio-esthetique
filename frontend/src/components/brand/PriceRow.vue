<script setup lang="ts">
/** One item of a `PriceSection`: caps name on the left, price or range on the right, optional note below. */
defineProps<{ name: string; price: string; note?: string }>()
</script>

<template>
  <div class="grid grid-cols-[1fr_auto] items-baseline gap-x-6">
    <dt class="label-caps text-sm">{{ name }}</dt>
    <dd class="text-right font-sans font-medium whitespace-nowrap tabular-nums">{{ price }}</dd>
    <dd v-if="note" class="col-span-2 text-sm text-ink-muted italic">{{ note }}</dd>
  </div>
</template>
