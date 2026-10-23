<script setup lang="ts">
import { computed } from 'vue'

/** "PRICE LIST / SALON NIO / PRICE LIST" strip framing the price-list post. Scrolls slowly unless reduced motion. */
const props = withDefaults(defineProps<{ items: string[]; repeat?: number }>(), { repeat: 3 })

const sequence = computed(() => Array.from({ length: props.repeat }, () => props.items).flat())
</script>

<template>
  <div class="overflow-hidden py-3 select-none" aria-hidden="true">
    <div class="flex w-max animate-[marquee_40s_linear_infinite] motion-reduce:animate-none">
      <!-- two identical halves so the loop is seamless -->
      <div v-for="half in 2" :key="half" class="flex shrink-0">
        <span
          v-for="(item, index) in sequence"
          :key="index"
          class="font-sans text-sm font-semibold tracking-[0.1em] whitespace-nowrap uppercase"
        >
          {{ item }}<span class="px-3 font-normal">/</span>
        </span>
      </div>
    </div>
  </div>
</template>

<style scoped>
@keyframes marquee {
  to {
    transform: translateX(-50%);
  }
}
</style>
