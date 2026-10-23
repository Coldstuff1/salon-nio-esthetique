<script setup lang="ts">
import { defineAsyncComponent, onBeforeUnmount, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { api, type Schemas } from './api/http'
import { useAuth } from './auth/keycloak'
import AppFooter from './components/layout/AppFooter.vue'
import AppHeader from './components/layout/AppHeader.vue'
import PageSection from './components/layout/PageSection.vue'
import BaseAlert from './components/ui/BaseAlert.vue'
import BaseButton from './components/ui/BaseButton.vue'
import BaseCard from './components/ui/BaseCard.vue'
import WelcomeView from './views/WelcomeView.vue'

// dev-only design system catalogue at /#styleguide, until there's a router
const StyleGuide = import.meta.env.DEV
  ? defineAsyncComponent(() => import('./views/StyleGuide.vue'))
  : undefined
const hash = ref(location.hash)
const onHashChange = () => (hash.value = location.hash)
window.addEventListener('hashchange', onHashChange)
onBeforeUnmount(() => window.removeEventListener('hashchange', onHashChange))

const { authenticated, roles } = useAuth()
const { t } = useI18n()

const me = ref<Schemas['Me']>()
const error = ref<string>()

async function whoAmI() {
  error.value = undefined
  const { data, response } = await api.GET('/api/me')
  if (data) {
    me.value = data
  } else {
    me.value = undefined
    error.value = `${response.status} ${response.statusText}`
  }
}
</script>

<template>
  <div class="flex min-h-screen flex-col">
    <AppHeader />

    <main class="flex-1">
      <component :is="StyleGuide" v-if="StyleGuide && hash === '#styleguide'" />

      <WelcomeView v-else />

      <PageSection v-if="authenticated" narrow>
        <BaseCard class="flex flex-col items-start gap-4">
          <p class="text-sm text-ink-muted">{{ roles.join(', ') }}</p>
          <BaseButton variant="outline" @click="whoAmI">{{ t('auth.whoAmI') }}</BaseButton>
          <pre v-if="me" class="w-full overflow-x-auto bg-surface-soft p-4 text-sm">{{ me }}</pre>
          <BaseAlert v-if="error" tone="danger" class="w-full">{{ error }}</BaseAlert>
        </BaseCard>
      </PageSection>
    </main>

    <AppFooter />
  </div>
</template>
