<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { api, type Schemas } from '@/api/http'
import { problemMessage } from '@/api/problem'
import BookingCard from '@/components/booking/BookingCard.vue'
import CancelBookingDialog from '@/components/booking/CancelBookingDialog.vue'
import PageSection from '@/components/layout/PageSection.vue'
import BaseAlert from '@/components/ui/BaseAlert.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseCard from '@/components/ui/BaseCard.vue'
import RequestState from '@/components/ui/RequestState.vue'
import { useRequest } from '@/composables/useRequest'
import { useLocale } from '@/i18n'
import { addDays, isoWeekday, salonDay, salonInstant, salonToday } from '@/utils/dates'
import { formatDate, formatDay } from '@/utils/format'

/** The employee's week at a glance and the chosen day's appointments (`?day=YYYY-MM-DD`, salon time). */
const { t } = useI18n()
const { locale } = useLocale()
const route = useRoute()
const router = useRouter()

const today = salonToday()
const day = computed(() => {
  const value = route.query.day
  return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value) ? value : today
})
const pickDay = (value: string) => router.replace({ query: value === today ? {} : { day: value } })

const weekStart = computed(() => addDays(day.value, 1 - isoWeekday(day.value)))
const weekDays = computed(() => Array.from({ length: 7 }, (_, i) => addDays(weekStart.value, i)))

const bookings = shallowRef<Schemas['BookingView'][]>()
const loading = ref(false)
const error = ref<string>()
async function load() {
  const start = weekStart.value
  loading.value = true
  error.value = undefined
  try {
    const {
      data,
      error: problem,
      response,
    } = await api.GET('/api/staff/me/schedule', {
      params: { query: { from: salonInstant(start, '00:00'), to: salonInstant(addDays(start, 7), '00:00') } },
    })
    if (start !== weekStart.value) return
    bookings.value = data
    if (!data) error.value = problemMessage(problem, response)
  } catch (e) {
    error.value = problemMessage(e)
  } finally {
    loading.value = false
  }
}
watch(weekStart, load, { immediate: true })

const byDay = computed(() => {
  const groups: Record<string, Schemas['BookingView'][]> = {}
  for (const booking of bookings.value ?? []) (groups[salonDay(booking.startsAt)] ??= []).push(booking)
  return groups
})
const active = (list?: Schemas['BookingView'][]) => (list ?? []).filter((b) => b.status !== 'CANCELLED')
// cancelled ones stay visible, after the rest
const dayBookings = computed(() => {
  const list = byDay.value[day.value] ?? []
  return [...active(list), ...list.filter((b) => b.status === 'CANCELLED')]
})

// what's missing before clients can book this employee at all
const { data: profile } = useRequest(() => api.GET('/api/staff/me'))
const setup = computed(() => {
  const p = profile.value
  if (!p) return []
  return [
    { done: !!p.photoUrl, label: 'staff.setup.photo', to: { name: 'staff-profile' } },
    { done: p.availability.length > 0, label: 'staff.setup.hours', to: { name: 'staff-hours' } },
    {
      done: p.serviceOfferings.some((o) => o.active),
      label: 'staff.setup.services',
      to: { name: 'staff-services' },
    },
  ]
})

const busy = ref<string>()
const actionError = ref<string>()
async function close(booking: Schemas['BookingView'], outcome: 'complete' | 'no-show') {
  busy.value = booking.id
  actionError.value = undefined
  try {
    const call =
      outcome === 'complete'
        ? api.POST('/api/bookings/{id}/complete', { params: { path: { id: booking.id } } })
        : api.POST('/api/bookings/{id}/no-show', { params: { path: { id: booking.id } } })
    const { data, error: problem, response } = await call
    if (data) replace(data)
    else actionError.value = problemMessage(problem, response)
  } catch (e) {
    actionError.value = problemMessage(e)
  } finally {
    busy.value = undefined
  }
}
function replace(updated: Schemas['BookingView']) {
  bookings.value = bookings.value?.map((b) => (b.id === updated.id ? updated : b))
}

const started = (booking: Schemas['BookingView']) => Date.parse(booking.startsAt) <= Date.now()
const cancelling = ref<Schemas['BookingView']>()
const dialogOpen = ref(false)
</script>

<template>
  <PageSection>
    <div class="flex flex-col gap-10">
      <BaseCard v-if="setup.some((s) => !s.done)" class="flex flex-col gap-4">
        <h2 class="display-caps text-xl">{{ t('staff.setup.title') }}</h2>
        <p class="text-ink-muted">{{ t('staff.setup.intro') }}</p>
        <ul class="flex flex-col gap-2">
          <li v-for="item in setup" :key="item.label" class="flex items-center gap-3">
            <span :class="item.done ? 'text-success' : 'text-ink-muted'" aria-hidden="true">
              {{ item.done ? '✓' : '○' }}
            </span>
            <RouterLink v-if="!item.done" :to="item.to" class="link">{{ t(item.label) }}</RouterLink>
            <span v-else class="text-ink-muted line-through">{{ t(item.label) }}</span>
          </li>
        </ul>
      </BaseCard>

      <div class="flex flex-col gap-4">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <BaseButton variant="ghost" size="sm" @click="pickDay(addDays(day, -7))">
            ← {{ t('booking.previousWeek') }}
          </BaseButton>
          <BaseButton v-if="day !== today" variant="outline" size="sm" @click="pickDay(today)">
            {{ t('staff.today') }}
          </BaseButton>
          <BaseButton variant="ghost" size="sm" @click="pickDay(addDays(day, 7))">
            {{ t('booking.nextWeek') }} →
          </BaseButton>
        </div>
        <div class="grid grid-cols-4 gap-2 sm:grid-cols-7" role="group" :aria-label="t('booking.chooseDay')">
          <button
            v-for="date in weekDays"
            :key="date"
            type="button"
            class="flex flex-col items-center gap-1 border px-1 py-3 text-center transition-colors"
            :class="
              date === day
                ? 'border-accent bg-accent text-accent-ink'
                : 'border-line-soft bg-surface hover:border-line'
            "
            :aria-pressed="date === day"
            @click="pickDay(date)"
          >
            <span class="label-caps text-[0.6875rem]">{{ formatDay(date, locale) }}</span>
            <span class="text-xs" :class="date === day ? 'text-accent-ink/80' : 'text-ink-muted'">
              {{ loading ? '…' : t('staff.appointments', active(byDay[date]).length) }}
            </span>
            <span v-if="date === today" class="sr-only">{{ t('staff.today') }}</span>
          </button>
        </div>
      </div>

      <section class="flex flex-col gap-6">
        <h2 class="display-caps text-2xl">{{ formatDate(salonInstant(day, '12:00'), locale) }}</h2>
        <BaseAlert v-if="actionError" tone="danger">{{ actionError }}</BaseAlert>
        <RequestState
          :loading="loading"
          :error="error"
          :empty="dayBookings.length === 0"
          :empty-text="t('staff.noAppointments')"
          @retry="load"
        >
          <div class="flex flex-col gap-4">
            <BookingCard
              v-for="booking in dayBookings"
              :key="booking.id"
              :booking="booking"
              perspective="staff"
              :class="{ 'opacity-60': booking.status === 'CANCELLED' }"
            >
              <template #details>
                <div v-if="booking.notes" class="flex flex-col gap-1 border-t border-line-soft pt-4 text-sm">
                  <p class="label-caps text-ink-muted">{{ t('booking.notes') }}</p>
                  <p class="whitespace-pre-line">{{ booking.notes }}</p>
                </div>
              </template>
              <template v-if="booking.status === 'RESERVED'">
                <!-- outcomes only once the appointment has begun -->
                <template v-if="started(booking)">
                  <BaseButton size="sm" :loading="busy === booking.id" @click="close(booking, 'complete')">
                    {{ t('staff.complete') }}
                  </BaseButton>
                  <BaseButton
                    variant="outline"
                    size="sm"
                    :disabled="busy === booking.id"
                    @click="close(booking, 'no-show')"
                  >
                    {{ t('staff.noShow') }}
                  </BaseButton>
                </template>
                <BaseButton
                  variant="ghost"
                  size="sm"
                  :disabled="busy === booking.id"
                  @click="((cancelling = booking), (dialogOpen = true))"
                >
                  {{ t('myBookings.cancel') }}
                </BaseButton>
              </template>
            </BookingCard>
          </div>
        </RequestState>
      </section>
    </div>

    <CancelBookingDialog
      v-model:open="dialogOpen"
      :booking="cancelling"
      perspective="staff"
      @cancelled="replace"
    />
  </PageSection>
</template>
