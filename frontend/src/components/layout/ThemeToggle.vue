<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useTheme } from '@/composables/useTheme'

const { resolved, setTheme } = useTheme()
const { t } = useI18n()
</script>

<template>
  <button
    type="button"
    class="p-2 text-ink transition-colors hover:text-accent"
    :aria-label="`${t('theme.label')}: ${resolved === 'dark' ? t('theme.dark') : t('theme.light')}`"
    :aria-pressed="resolved === 'dark'"
    @click="setTheme(resolved === 'dark' ? 'light' : 'dark')"
  >
    <!-- moon in light mode, sun in dark mode -->
    <svg v-if="resolved === 'light'" viewBox="0 0 20 20" class="size-5" aria-hidden="true">
      <path
        d="M16.5 12.5A7 7 0 0 1 7.5 3.5a7 7 0 1 0 9 9Z"
        fill="none"
        stroke="currentColor"
        stroke-width="1.25"
        stroke-linejoin="round"
      />
    </svg>
    <svg v-else viewBox="0 0 20 20" class="size-5" aria-hidden="true">
      <circle cx="10" cy="10" r="3.5" fill="none" stroke="currentColor" stroke-width="1.25" />
      <path
        d="M10 1.5v2M10 16.5v2M1.5 10h2M16.5 10h2M4 4l1.4 1.4M14.6 14.6 16 16M4 16l1.4-1.4M14.6 5.4 16 4"
        stroke="currentColor"
        stroke-width="1.25"
        stroke-linecap="round"
      />
    </svg>
  </button>
</template>
