<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { api } from '@/api/http'
import { problemMessage } from '@/api/problem'
import PageSection from '@/components/layout/PageSection.vue'
import BaseAlert from '@/components/ui/BaseAlert.vue'
import BaseButton from '@/components/ui/BaseButton.vue'

/**
 * Where an email's "stop these emails" link lands: no sign-in, one button. It asks first rather than acting on
 * load, since mail scanners open links on their own. The token reads `<person>.<TYPE>.<signature>`.
 */
const { t, te } = useI18n()
const route = useRoute()

const token = computed(() => (typeof route.query.token === 'string' ? route.query.token : ''))
const typeLabel = computed(() => {
  const key = `notifications.settings.types.${token.value.split('.')[1]}`
  return te(key) ? t(key) : undefined
})
const busy = ref(false)
const done = ref(false)
const error = ref<string>()

async function unsubscribe() {
  error.value = undefined
  busy.value = true
  try {
    const {
      data,
      error: problem,
      response,
    } = await api.POST('/api/public/unsubscribe', {
      params: { query: { token: token.value } },
    })
    if (data) done.value = true
    else error.value = problemMessage(problem, response)
  } catch (e) {
    error.value = problemMessage(e)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <PageSection narrow>
    <div class="flex flex-col items-start gap-6">
      <h1 class="display-caps text-3xl">{{ t('pages.unsubscribe') }}</h1>

      <template v-if="!typeLabel">
        <p>{{ t('notifications.unsubscribe.invalid') }}</p>
      </template>
      <template v-else-if="done">
        <BaseAlert tone="success">{{ t('notifications.unsubscribe.done', { type: typeLabel }) }}</BaseAlert>
      </template>
      <template v-else>
        <div class="flex flex-col gap-2">
          <p class="text-lg">{{ t('notifications.unsubscribe.question', { type: typeLabel }) }}</p>
          <p class="text-ink-muted">{{ t('notifications.unsubscribe.explanation') }}</p>
        </div>
        <BaseAlert v-if="error" tone="danger">{{ error }}</BaseAlert>
        <BaseButton :loading="busy" @click="unsubscribe">{{
          t('notifications.unsubscribe.confirm')
        }}</BaseButton>
      </template>

      <BaseButton variant="outline" size="sm" :to="{ name: 'notification-settings' }">
        {{ t('notifications.unsubscribe.manage') }}
      </BaseButton>
    </div>
  </PageSection>
</template>
