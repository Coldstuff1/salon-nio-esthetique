<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import ScriptTitle from '@/components/brand/ScriptTitle.vue'
import PageSection from '@/components/layout/PageSection.vue'
import BaseButton from '@/components/ui/BaseButton.vue'

const { t } = useI18n()
</script>

<template>
  <PageSection narrow>
    <div class="flex flex-col items-center gap-7 text-center">
      <p class="label-caps text-accent">404</p>
      <ScriptTitle as="h1" :title="t('errors.notFoundTitle')" :script="t('errors.notFoundScript')" />
      <p class="max-w-md text-ink-muted">{{ t('errors.notFoundText') }}</p>
      <div class="flex flex-wrap justify-center gap-3">
        <BaseButton :to="{ name: 'home' }">{{ t('errors.backHome') }}</BaseButton>
        <BaseButton :to="{ name: 'services' }" variant="outline">{{ t('nav.services') }}</BaseButton>
      </div>
    </div>
  </PageSection>
</template>
