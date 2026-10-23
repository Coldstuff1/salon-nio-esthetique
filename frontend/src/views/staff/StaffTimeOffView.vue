<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { api, type Schemas } from '@/api/http'
import { problemMessage } from '@/api/problem'
import PageSection from '@/components/layout/PageSection.vue'
import BaseAlert from '@/components/ui/BaseAlert.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseCard from '@/components/ui/BaseCard.vue'
import BaseCheckbox from '@/components/ui/BaseCheckbox.vue'
import BaseInput from '@/components/ui/BaseInput.vue'
import RequestState from '@/components/ui/RequestState.vue'
import { useRequest } from '@/composables/useRequest'
import { useLocale } from '@/i18n'
import { addDays, salonDay, salonInstant, salonTimeOfDay, salonToday } from '@/utils/dates'
import { formatDay, formatTime } from '@/utils/format'

/**
 * Days or hours the employee is away. Clients can't book into them; appointments already booked stay, so
 * adding time off warns about any that fall inside it.
 */
const { t } = useI18n()
const { locale } = useLocale()

const { data: profile, loading, error, reload } = useRequest(() => api.GET('/api/staff/me'))

// past periods no longer matter to anyone
const upcoming = computed(() =>
  (profile.value?.timeOff ?? [])
    .filter((p) => Date.parse(p.endsAt) > Date.now())
    .sort((a, b) => a.startsAt.localeCompare(b.startsAt)),
)

/** "Mon, Nov 2 – Wed, Nov 4" for whole days, "Mon, Nov 2, 1:00 p.m. – 3:00 p.m." otherwise. */
function describe(period: Schemas['TimeOff']) {
  const wholeDays = salonTimeOfDay(period.startsAt) === '00:00' && salonTimeOfDay(period.endsAt) === '00:00'
  const first = salonDay(period.startsAt)
  if (wholeDays) {
    const last = addDays(salonDay(period.endsAt), -1)
    return first === last
      ? formatDay(first, locale.value)
      : `${formatDay(first, locale.value)} – ${formatDay(last, locale.value)}`
  }
  const end = salonDay(period.endsAt)
  return end === first
    ? `${formatDay(first, locale.value)}, ${formatTime(period.startsAt, locale.value)} – ${formatTime(period.endsAt, locale.value)}`
    : `${formatDay(first, locale.value)}, ${formatTime(period.startsAt, locale.value)} – ${formatDay(end, locale.value)}, ${formatTime(period.endsAt, locale.value)}`
}

const today = salonToday()
const startDay = ref(today)
const endDay = ref(today)
const allDay = ref(true)
const startTime = ref('12:00')
const endTime = ref('17:00')
const reason = ref('')

// whole days run from the first day's midnight to the midnight after the last day
const range = computed(() => ({
  startsAt: salonInstant(startDay.value, allDay.value ? '00:00' : startTime.value),
  endsAt: allDay.value
    ? salonInstant(addDays(endDay.value, 1), '00:00')
    : salonInstant(endDay.value, endTime.value),
}))
const formError = computed(() => {
  if (!startDay.value || !endDay.value || (!allDay.value && (!startTime.value || !endTime.value))) {
    return t('staff.hours.missingTime')
  }
  return range.value.startsAt < range.value.endsAt ? undefined : t('staff.hours.endBeforeStart')
})

const adding = ref(false)
const notice = ref<{ tone: 'success' | 'danger'; text: string }>()
/** Appointments still booked inside the period just added. */
const clashes = ref<Schemas['BookingView'][]>([])

async function add() {
  adding.value = true
  notice.value = undefined
  clashes.value = []
  try {
    const body = { ...range.value, reason: reason.value.trim() || null }
    const { data, error: problem, response } = await api.POST('/api/staff/me/time-off', { body })
    if (!data) {
      notice.value = { tone: 'danger', text: problemMessage(problem, response) }
      return
    }
    reason.value = ''
    notice.value = { tone: 'success', text: t('staff.timeOff.added') }
    await reload()
    const schedule = await api.GET('/api/staff/me/schedule', {
      params: { query: { from: data.startsAt, to: data.endsAt } },
    })
    clashes.value = (schedule.data ?? []).filter((b) => b.status === 'RESERVED')
  } catch (e) {
    notice.value = { tone: 'danger', text: problemMessage(e) }
  } finally {
    adding.value = false
  }
}

const removing = ref<string>()
async function remove(period: Schemas['TimeOff']) {
  removing.value = period.id
  notice.value = undefined
  try {
    const { error: problem, response } = await api.DELETE('/api/staff/me/time-off/{timeOffId}', {
      params: { path: { timeOffId: period.id } },
    })
    if (response.ok) {
      clashes.value = []
      await reload()
    } else {
      notice.value = { tone: 'danger', text: problemMessage(problem, response) }
    }
  } catch (e) {
    notice.value = { tone: 'danger', text: problemMessage(e) }
  } finally {
    removing.value = undefined
  }
}
</script>

<template>
  <PageSection>
    <div class="grid items-start gap-12 lg:grid-cols-[1fr_24rem]">
      <section class="flex flex-col gap-6">
        <h2 class="display-caps text-2xl">{{ t('staff.timeOff.upcoming') }}</h2>
        <RequestState
          :loading="loading && !profile"
          :error="error"
          :empty="upcoming.length === 0"
          :empty-text="t('staff.timeOff.none')"
          @retry="reload"
        >
          <ul class="flex flex-col divide-y divide-line-soft border-y border-line-soft">
            <li
              v-for="period in upcoming"
              :key="period.id"
              class="flex items-center justify-between gap-4 py-4"
            >
              <div class="flex flex-col gap-0.5">
                <span>{{ describe(period) }}</span>
                <span v-if="period.reason" class="text-sm text-ink-muted">{{ period.reason }}</span>
              </div>
              <BaseButton variant="ghost" size="sm" :loading="removing === period.id" @click="remove(period)">
                {{ t('staff.hours.remove') }}
              </BaseButton>
            </li>
          </ul>
        </RequestState>
      </section>

      <BaseCard as="form" class="flex flex-col gap-5" @submit.prevent="add">
        <h2 class="display-caps text-xl">{{ t('staff.timeOff.add') }}</h2>
        <div class="grid grid-cols-2 gap-4">
          <BaseInput v-model="startDay" type="date" :min="today" :label="t('staff.timeOff.from')" required />
          <BaseInput v-model="endDay" type="date" :min="startDay" :label="t('staff.timeOff.to')" required />
        </div>
        <BaseCheckbox v-model="allDay" :label="t('staff.timeOff.allDay')" />
        <div v-if="!allDay" class="grid grid-cols-2 gap-4">
          <BaseInput
            v-model="startTime"
            type="time"
            step="900"
            :label="t('staff.timeOff.startTime')"
            required
          />
          <BaseInput v-model="endTime" type="time" step="900" :label="t('staff.timeOff.endTime')" required />
        </div>
        <BaseInput
          v-model="reason"
          :label="t('staff.timeOff.reason')"
          :hint="t('staff.timeOff.reasonHint')"
          maxlength="255"
        />
        <p v-if="formError" class="text-sm text-danger">{{ formError }}</p>
        <BaseButton type="submit" :loading="adding" :disabled="!!formError">{{
          t('staff.timeOff.add')
        }}</BaseButton>
      </BaseCard>
    </div>

    <div class="mt-8 flex flex-col gap-4">
      <BaseAlert v-if="notice" :tone="notice.tone">{{ notice.text }}</BaseAlert>
      <BaseAlert v-if="clashes.length" tone="danger" class="flex flex-col gap-2">
        <p>{{ t('staff.timeOff.clashes', clashes.length) }}</p>
        <ul class="list-inside list-disc">
          <li v-for="booking in clashes" :key="booking.id">
            <RouterLink
              :to="{ name: 'staff-schedule', query: { day: salonDay(booking.startsAt) } }"
              class="link"
            >
              {{ formatDay(salonDay(booking.startsAt), locale) }},
              {{ formatTime(booking.startsAt, locale) }} —
              {{ booking.customerName }}
            </RouterLink>
          </li>
        </ul>
      </BaseAlert>
    </div>
  </PageSection>
</template>
