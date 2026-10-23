<script setup lang="ts">
/** Serif caps title with one script word swept across its lower right, as in the "Eyebrows / Treatment" post. */
withDefaults(
  defineProps<{
    title: string
    script?: string
    as?: 'h1' | 'h2' | 'h3'
  }>(),
  { as: 'h2' },
)

const sizes = { h1: 'text-display', h2: 'text-4xl md:text-5xl', h3: 'text-2xl' }
</script>

<template>
  <component :is="as" class="relative inline-block pb-[0.45em] leading-none" :class="sizes[as]">
    <span class="display-caps block">{{ title }}</span>
    <span
      v-if="script"
      class="absolute -right-[0.4em] bottom-0 font-script text-[0.9em] whitespace-nowrap text-accent normal-case"
    >
      {{ script }}
    </span>
  </component>
</template>
