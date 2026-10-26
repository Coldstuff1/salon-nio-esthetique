<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { api, type Schemas } from '@/api/http'
import { problemMessage } from '@/api/problem'
import PageSection from '@/components/layout/PageSection.vue'
import PushDeviceCard from '@/components/notifications/PushDeviceCard.vue'
import BaseAlert from '@/components/ui/BaseAlert.vue'
import BaseCheckbox from '@/components/ui/BaseCheckbox.vue'
import RequestState from '@/components/ui/RequestState.vue'
import { useRequest } from '@/composables/useRequest'

/**
 * Which notifications the signed-in person also gets by email and on their devices, for the types their roles
 * get; the in-app list always has everything. Each change is saved at once. Emails' "manage my notifications"
 * links lead here.
 */
type Setting = Schemas['ChannelSetting']
type Channel = Setting['channel']

const { t } = useI18n()

const { data: settings, loading, error, reload } = useRequest(() => api.GET('/api/me/notification-settings'))
const saveError = ref<string>()
const saving = ref<string>()

/** One row per type, in the backend's order. */
const rows = computed(() => {
  const byType = new Map<Setting['type'], Partial<Record<Channel, boolean>>>()
  for (const setting of settings.value ?? []) {
    byType.set(setting.type, { ...byType.get(setting.type), [setting.channel]: setting.enabled })
  }
  return [...byType].map(([type, channels]) => ({ type, channels }))
})

async function change(type: Setting['type'], channel: Channel, enabled: boolean) {
  saveError.value = undefined
  saving.value = type
  try {
    const { data, error, response } = await api.PUT('/api/me/notification-settings', {
      body: [{ type, channel, enabled }],
    })
    if (data) settings.value = data
    else saveError.value = problemMessage(error, response)
  } catch (e) {
    saveError.value = problemMessage(e)
  } finally {
    saving.value = undefined
  }
}
</script>

<template>
  <PageSection narrow>
    <div class="flex flex-col gap-8">
      <div class="flex flex-col gap-3">
        <h1 class="display-caps text-3xl">{{ t('pages.notificationSettings') }}</h1>
        <p class="text-ink-muted">{{ t('notifications.settings.intro') }}</p>
      </div>

      <PushDeviceCard />

      <BaseAlert v-if="saveError" tone="danger">{{ saveError }}</BaseAlert>

      <RequestState :loading="loading && !settings" :error="error" @retry="reload">
        <div class="flex flex-col gap-3">
          <ul class="divide-y divide-line-soft border border-line-soft bg-surface">
            <li
              v-for="row in rows"
              :key="row.type"
              class="flex flex-col gap-3 px-5 py-4 sm:flex-row sm:items-center sm:justify-between"
            >
              <span>{{ t(`notifications.settings.types.${row.type}`) }}</span>
              <span class="flex shrink-0 gap-6 text-sm">
                <BaseCheckbox
                  :label="t('notifications.settings.email')"
                  :model-value="row.channels.EMAIL"
                  :disabled="saving === row.type"
                  @update:model-value="change(row.type, 'EMAIL', !!$event)"
                />
                <BaseCheckbox
                  :label="t('notifications.settings.push')"
                  :model-value="row.channels.PUSH"
                  :disabled="saving === row.type"
                  @update:model-value="change(row.type, 'PUSH', !!$event)"
                />
              </span>
            </li>
          </ul>
          <p class="text-sm text-ink-muted">{{ t('notifications.settings.pushHint') }}</p>
        </div>
      </RequestState>
    </div>
  </PageSection>
</template>
