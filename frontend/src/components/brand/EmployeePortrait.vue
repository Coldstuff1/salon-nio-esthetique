<script setup lang="ts">
import { computed } from 'vue'

/**
 * An employee's photo in the brand's matted frame, or their initials when they haven't added one.
 * `compact` is the thumbnail for lists.
 */
const props = defineProps<{ name: string; photoUrl?: string | null; compact?: boolean }>()

const initials = computed(() =>
  props.name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join(''),
)
</script>

<template>
  <div class="border border-line bg-surface" :class="compact ? 'p-1' : 'p-2.5 sm:p-3'">
    <img
      v-if="photoUrl"
      :src="photoUrl"
      :alt="name"
      class="block aspect-[4/5] w-full object-cover"
      loading="lazy"
    />
    <div
      v-else
      class="flex aspect-[4/5] w-full items-center justify-center bg-surface-soft font-display tracking-[0.08em] text-blush"
      :class="compact ? 'text-xl' : 'text-6xl'"
      aria-hidden="true"
    >
      {{ initials }}
    </div>
  </div>
</template>
