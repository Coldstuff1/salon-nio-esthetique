<script setup lang="ts">
import { RouterLink, type RouteLocationRaw } from 'vue-router'

/** A button, or with `to` a router link styled as one. */
withDefaults(
  defineProps<{
    variant?: 'primary' | 'outline' | 'ghost'
    size?: 'sm' | 'md'
    type?: 'button' | 'submit' | 'reset'
    to?: RouteLocationRaw
    loading?: boolean
    disabled?: boolean
  }>(),
  { variant: 'primary', size: 'md', type: 'button' },
)

const variants = {
  primary: 'bg-accent text-accent-ink border-accent hover:bg-accent-hover hover:border-accent-hover',
  outline: 'bg-transparent text-ink border-line hover:bg-surface-soft',
  ghost: 'bg-transparent text-ink border-transparent hover:text-accent',
}
const sizes = { sm: 'px-4 py-2 text-xs', md: 'px-6 py-3' }
const base =
  'label-caps inline-flex items-center justify-center gap-2 border transition-colors disabled:cursor-not-allowed disabled:opacity-50'
</script>

<template>
  <RouterLink v-if="to" :to="to" :class="[base, variants[variant], sizes[size]]">
    <slot />
  </RouterLink>
  <button
    v-else
    :type="type"
    :disabled="disabled || loading"
    :aria-busy="loading || undefined"
    :class="[base, variants[variant], sizes[size]]"
  >
    <span
      v-if="loading"
      class="size-3.5 animate-spin rounded-full border-[1.5px] border-current border-r-transparent"
      aria-hidden="true"
    />
    <slot />
  </button>
</template>
