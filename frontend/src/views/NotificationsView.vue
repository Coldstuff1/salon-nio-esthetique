<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { api, type Schemas } from '@/api/http'
import { problemMessage } from '@/api/problem'
import PageSection from '@/components/layout/PageSection.vue'
import BaseAlert from '@/components/ui/BaseAlert.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import RequestState from '@/components/ui/RequestState.vue'
import { refreshUnread } from '@/composables/useNotifications'
import { useRequest } from '@/composables/useRequest'
import { useLocale } from '@/i18n'
import { salonDay } from '@/utils/dates'
import { formatDate, formatDay, formatTime } from '@/utils/format'

/**
 * The signed-in person's latest notifications, whatever their role. Opening one marks it read and goes to
 * the page it's about, if there is one.
 */
type Notification = Schemas['NotificationView']

const { t } = useI18n()
const { locale } = useLocale()
const router = useRouter()

const { data: notifications, loading, error, reload } = useRequest(() => api.GET('/api/me/notifications'))
const hasUnread = computed(() => (notifications.value ?? []).some((n) => !n.readAt))
const actionError = ref<string>()
const markingAll = ref(false)

/** Title and text in the reader's language, from the type and the params the backend stored. */
function text(notification: Notification) {
  const { type, params } = notification
  const values: Record<string, string> = { ...params }
  if (params.startsAt) {
    values.date = formatDate(params.startsAt, locale.value)
    values.time = formatTime(params.startsAt, locale.value)
  }
  const body =
    type === 'BUG_REPORT_CLOSED'
      ? `notifications.types.${type}.body.${params.status}`
      : `notifications.types.${type}.body`
  return { title: t(`notifications.types.${type}.title`), body: t(body, values) }
}

function markedRead(ids: string[]) {
  const now = new Date().toISOString()
  notifications.value = notifications.value?.map((n) =>
    ids.includes(n.id) && !n.readAt ? { ...n, readAt: now } : n,
  )
}

async function open(notification: Notification) {
  actionError.value = undefined
  if (!notification.readAt) {
    const { error, response } = await api.POST('/api/me/notifications/{id}/read', {
      params: { path: { id: notification.id } },
    })
    if (error) {
      actionError.value = problemMessage(error, response)
      return
    }
    markedRead([notification.id])
    void refreshUnread()
  }
  if (notification.link) await router.push(notification.link)
}

async function markAllRead() {
  actionError.value = undefined
  markingAll.value = true
  try {
    const { error, response } = await api.POST('/api/me/notifications/read-all')
    if (error) {
      actionError.value = problemMessage(error, response)
      return
    }
    markedRead((notifications.value ?? []).map((n) => n.id))
    void refreshUnread()
  } catch (e) {
    actionError.value = problemMessage(e)
  } finally {
    markingAll.value = false
  }
}
</script>

<template>
  <PageSection narrow>
    <div class="flex flex-col gap-8">
      <div class="flex flex-wrap items-end justify-between gap-4">
        <h1 class="display-caps text-3xl">{{ t('pages.notifications') }}</h1>
        <BaseButton v-if="hasUnread" variant="outline" size="sm" :loading="markingAll" @click="markAllRead">
          {{ t('notifications.markAllRead') }}
        </BaseButton>
      </div>

      <BaseAlert v-if="actionError" tone="danger">{{ actionError }}</BaseAlert>

      <RequestState
        :loading="loading && !notifications"
        :error="error"
        :empty="notifications?.length === 0"
        :empty-text="t('notifications.empty')"
        @retry="reload"
      >
        <ul class="divide-y divide-line-soft border border-line-soft bg-surface">
          <li v-for="notification in notifications" :key="notification.id">
            <button
              type="button"
              class="flex w-full items-start gap-4 px-5 py-4 text-left transition-colors hover:bg-surface-soft"
              :class="{ 'cursor-default': notification.readAt && !notification.link }"
              @click="open(notification)"
            >
              <span
                class="mt-2 size-2 shrink-0 rounded-full"
                :class="notification.readAt ? 'bg-transparent' : 'bg-accent'"
                aria-hidden="true"
              />
              <span class="flex min-w-0 flex-1 flex-col gap-1">
                <span class="flex flex-wrap items-baseline justify-between gap-x-4">
                  <span :class="notification.readAt ? 'text-ink-muted' : 'font-medium'">
                    {{ text(notification).title }}
                    <span v-if="!notification.readAt" class="sr-only">({{ t('notifications.unread') }})</span>
                  </span>
                  <span class="text-xs whitespace-nowrap text-ink-muted">
                    {{ formatDay(salonDay(notification.createdAt), locale) }}
                    · {{ formatTime(notification.createdAt, locale) }}
                  </span>
                </span>
                <span class="text-sm break-words text-ink-muted">{{ text(notification).body }}</span>
              </span>
            </button>
          </li>
        </ul>
      </RequestState>
    </div>
  </PageSection>
</template>
