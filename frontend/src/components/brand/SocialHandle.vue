<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<template>
  <a
    href="https://www.instagram.com/salonnioesthetique/"
    target="_blank"
    rel="noopener"
    class="display-caps inline-block text-lg tracking-[0.18em] text-ink transition-colors hover:text-accent"
  >
    {{ t('brand.handle') }}
  </a>
</template>
