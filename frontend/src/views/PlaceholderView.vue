<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import ScriptTitle from '@/components/brand/ScriptTitle.vue'
import PageSection from '@/components/layout/PageSection.vue'

/** Stands in for a planned page until it's built, so navigation and access rules can be tried end to end. */
const route = useRoute()
const { t } = useI18n()
</script>

<template>
  <PageSection narrow>
    <div class="flex flex-col items-center gap-6 text-center">
      <ScriptTitle as="h1" :title="route.meta.title ? t(route.meta.title) : ''" />
      <p class="text-ink-muted">{{ t('pages.comingSoon') }}</p>
    </div>
  </PageSection>
</template>
