<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { api } from '@/api/http'
import { problemMessage } from '@/api/problem'
import PageSection from '@/components/layout/PageSection.vue'
import BaseAlert from '@/components/ui/BaseAlert.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import RequestState from '@/components/ui/RequestState.vue'
import { fieldClasses } from '@/components/ui/fieldClasses'
import { useRequest } from '@/composables/useRequest'
import { useLocale } from '@/i18n'
import { formatWeekday } from '@/utils/format'

/**
 * The employee's usual week: one or more time ranges per day (e.g. around a lunch break), saved all at once.
 * Clients can only book inside these hours; time off removes days from them.
 */
interface Window {
  key: number
  startTime: string
  endTime: string
}

const { t } = useI18n()
const { locale } = useLocale()

const { data: profile, loading, error, reload } = useRequest(() => api.GET('/api/staff/me'))

const days = [1, 2, 3, 4, 5, 6, 7]
let nextKey = 0
const week = ref<Record<number, Window[]>>({})
const saved = ref('')

const snapshot = () =>
  JSON.stringify(days.map((d) => (week.value[d] ?? []).map((w) => [w.startTime, w.endTime])))

watch(profile, (p) => {
  const next: Record<number, Window[]> = {}
  for (const window of p?.availability ?? []) {
    ;(next[window.dayOfWeek] ??= []).push({
      key: nextKey++,
      startTime: window.startTime.slice(0, 5),
      endTime: window.endTime.slice(0, 5),
    })
  }
  week.value = next
  saved.value = snapshot()
})

function add(day: number) {
  const windows = (week.value[day] ??= [])
  const last = windows.at(-1)
  // a new range starts where the last one ended, or a typical morning
  windows.push({ key: nextKey++, startTime: last?.endTime ?? '09:00', endTime: last ? '' : '17:00' })
}
function remove(day: number, key: number) {
  week.value[day] = (week.value[day] ?? []).filter((w) => w.key !== key)
}
function copyToWeekdays(from: number) {
  for (const day of [1, 2, 3, 4, 5]) {
    if (day !== from) week.value[day] = (week.value[from] ?? []).map((w) => ({ ...w, key: nextKey++ }))
  }
}

/** Per range: why it can't be saved, if anything. */
const problems = computed(() => {
  const found: Record<number, string> = {}
  for (const day of days) {
    const windows = [...(week.value[day] ?? [])].sort((a, b) => a.startTime.localeCompare(b.startTime))
    windows.forEach((w, i) => {
      if (!w.startTime || !w.endTime) found[w.key] = t('staff.hours.missingTime')
      else if (w.startTime >= w.endTime) found[w.key] = t('staff.hours.endBeforeStart')
      else if (i > 0 && w.startTime < windows[i - 1]!.endTime) found[w.key] = t('staff.hours.overlap')
    })
  }
  return found
})
const dirty = computed(() => snapshot() !== saved.value)

const saving = ref(false)
const notice = ref<{ tone: 'success' | 'danger'; text: string }>()
async function save() {
  saving.value = true
  notice.value = undefined
  try {
    const body = days.flatMap((dayOfWeek) =>
      (week.value[dayOfWeek] ?? []).map((w) => ({ dayOfWeek, startTime: w.startTime, endTime: w.endTime })),
    )
    const { data, error: problem, response } = await api.PUT('/api/staff/me/availability', { body })
    if (data) {
      profile.value = data
      notice.value = { tone: 'success', text: t('staff.hours.saved') }
    } else {
      notice.value = { tone: 'danger', text: problemMessage(problem, response) }
    }
  } catch (e) {
    notice.value = { tone: 'danger', text: problemMessage(e) }
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <PageSection>
    <RequestState :loading="loading" :error="error" @retry="reload">
      <div class="flex max-w-3xl flex-col gap-8">
        <p class="text-ink-muted">{{ t('staff.hours.intro') }}</p>

        <ul class="flex flex-col divide-y divide-line-soft border-y border-line-soft">
          <li v-for="day in days" :key="day" class="grid gap-3 py-5 sm:grid-cols-[9rem_1fr]">
            <div class="flex items-baseline justify-between gap-3 sm:flex-col sm:justify-start">
              <span class="display-caps text-lg">{{ formatWeekday(day, locale) }}</span>
              <span v-if="!week[day]?.length" class="text-sm text-ink-muted">{{
                t('staff.hours.closed')
              }}</span>
            </div>
            <div class="flex flex-col gap-3">
              <div v-for="window in week[day]" :key="window.key" class="flex flex-col gap-1">
                <div class="flex flex-wrap items-center gap-2">
                  <input
                    v-model="window.startTime"
                    type="time"
                    step="900"
                    :aria-label="t('staff.hours.start', { day: formatWeekday(day, locale) })"
                    :class="[fieldClasses(problems[window.key]), '!w-40']"
                  />
                  <span class="text-ink-muted">–</span>
                  <input
                    v-model="window.endTime"
                    type="time"
                    step="900"
                    :aria-label="t('staff.hours.end', { day: formatWeekday(day, locale) })"
                    :class="[fieldClasses(problems[window.key]), '!w-40']"
                  />
                  <BaseButton variant="ghost" size="sm" @click="remove(day, window.key)">
                    {{ t('staff.hours.remove') }}
                  </BaseButton>
                </div>
                <p v-if="problems[window.key]" class="text-sm text-danger">{{ problems[window.key] }}</p>
              </div>
              <div class="flex flex-wrap gap-2">
                <BaseButton variant="outline" size="sm" @click="add(day)"
                  >+ {{ t('staff.hours.add') }}</BaseButton
                >
                <BaseButton
                  v-if="day <= 5 && week[day]?.length"
                  variant="ghost"
                  size="sm"
                  @click="copyToWeekdays(day)"
                >
                  {{ t('staff.hours.copyToWeekdays') }}
                </BaseButton>
              </div>
            </div>
          </li>
        </ul>

        <BaseAlert v-if="notice" :tone="notice.tone">{{ notice.text }}</BaseAlert>
        <div class="flex items-center gap-4">
          <BaseButton :loading="saving" :disabled="!dirty || Object.keys(problems).length > 0" @click="save">
            {{ t('common.save') }}
          </BaseButton>
          <span v-if="dirty" class="text-sm text-ink-muted">{{ t('staff.unsaved') }}</span>
        </div>
      </div>
    </RequestState>
  </PageSection>
</template>
