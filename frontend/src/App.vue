<script setup lang="ts">
import { watchEffect } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterView, useRoute } from 'vue-router'
import AppFooter from './components/layout/AppFooter.vue'
import AppHeader from './components/layout/AppHeader.vue'
import { pageTitle } from './composables/usePageTitle'

const route = useRoute()
const { t } = useI18n()

// "Soins · Salon Nio Esthétique", following language changes too
watchEffect(() => {
  const title = pageTitle.value ?? (route.meta.title && t(route.meta.title))
  document.title = title ? `${title} · ${t('brand.name')}` : t('brand.name')
})
</script>

<template>
  <div class="flex min-h-screen flex-col">
    <AppHeader />

    <main class="flex-1">
      <RouterView />
    </main>

    <AppFooter />
  </div>
</template>
