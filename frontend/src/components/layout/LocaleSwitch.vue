<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { locales, useLocale } from '@/i18n'

const { locale, setLocale } = useLocale()
const { t } = useI18n()
</script>

<template>
  <div class="label-caps flex items-center" role="group" :aria-label="t('locale.label')">
    <template v-for="(code, index) in locales" :key="code">
      <span v-if="index > 0" class="text-ink-muted" aria-hidden="true">/</span>
      <button
        type="button"
        class="px-1.5 py-1 transition-colors"
        :class="code === locale ? 'text-accent' : 'text-ink-muted hover:text-ink'"
        :aria-pressed="code === locale"
        :lang="code"
        @click="setLocale(code)"
      >
        {{ code }}
      </button>
    </template>
  </div>
</template>
