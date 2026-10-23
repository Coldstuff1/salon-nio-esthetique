<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { api, type Schemas } from '@/api/http'
import { problemMessage } from '@/api/problem'
import CancelBookingDialog from '@/components/booking/CancelBookingDialog.vue'
import PageSection from '@/components/layout/PageSection.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseSelect from '@/components/ui/BaseSelect.vue'
import BookingStatusBadge from '@/components/ui/BookingStatusBadge.vue'
import RequestState from '@/components/ui/RequestState.vue'
import { useRequest } from '@/composables/useRequest'
import { useLocale } from '@/i18n'
import { addDays, isoWeekday, salonDay, salonInstant, salonToday } from '@/utils/dates'
import { formatDate, formatDay, formatPrice, formatTime } from '@/utils/format'

/**
 * Every appointment in the salon for one week (`?week=` any day in it, `?employee=` to narrow), with what
 * the week adds up to. Admins can cancel any appointment, even one that has started.
 */
type Booking = Schemas['BookingView']

const { t } = useI18n()
const { locale, localize } = useLocale()
const route = useRoute()
const router = useRouter()

const param = (key: string) => (typeof route.query[key] === 'string' ? (route.query[key] as string) : '')
const today = salonToday()
const anchor = computed(() => (/^\d{4}-\d{2}-\d{2}$/.test(param('week')) ? param('week') : today))
const weekStart = computed(() => addDays(anchor.value, 1 - isoWeekday(anchor.value)))
const employeeId = computed(() => param('employee'))

function navigate(changes: { week?: string; employee?: string }) {
  const next = { week: param('week'), employee: employeeId.value, ...changes }
  void router.replace({
    query: Object.fromEntries(Object.entries(next).filter(([, value]) => value)),
  })
}

const { data: employees } = useRequest(() => api.GET('/api/admin/employees'))
const employeeOptions = computed(() => [
  { value: '', label: t('admin.bookings.allEmployees') },
  ...(employees.value ?? [])
    .slice()
    .sort((a, b) => a.displayName.localeCompare(b.displayName))
    .map((e) => ({ value: e.id, label: e.displayName })),
])

const bookings = shallowRef<Booking[]>()
const loading = ref(false)
const error = ref<string>()
async function load() {
  const key = `${weekStart.value}|${employeeId.value}`
  loading.value = true
  error.value = undefined
  try {
    const {
      data,
      error: problem,
      response,
    } = await api.GET('/api/admin/bookings', {
      params: {
        query: {
          from: salonInstant(weekStart.value, '00:00'),
          to: salonInstant(addDays(weekStart.value, 7), '00:00'),
          ...(employeeId.value ? { employeeId: employeeId.value } : {}),
        },
      },
    })
    if (key !== `${weekStart.value}|${employeeId.value}`) return
    bookings.value = data
    if (!data) error.value = problemMessage(problem, response)
  } catch (e) {
    error.value = problemMessage(e)
  } finally {
    loading.value = false
  }
}
watch([weekStart, employeeId], load, { immediate: true })

const days = computed(() =>
  Array.from({ length: 7 }, (_, i) => addDays(weekStart.value, i))
    .map((day) => ({ day, bookings: (bookings.value ?? []).filter((b) => salonDay(b.startsAt) === day) }))
    .filter((group) => group.bookings.length),
)

const totals = computed(() => {
  const list = bookings.value ?? []
  const of = (status: Booking['status']) => list.filter((b) => b.status === status)
  const sum = (items: Booking[]) => items.reduce((total, b) => total + b.subtotalCents, 0)
  return [
    { label: 'admin.bookings.upcoming', count: of('RESERVED').length, cents: sum(of('RESERVED')) },
    { label: 'admin.bookings.completed', count: of('COMPLETED').length, cents: sum(of('COMPLETED')) },
    { label: 'admin.bookings.noShows', count: of('NO_SHOW').length, cents: sum(of('NO_SHOW')) },
    { label: 'admin.bookings.cancelled', count: of('CANCELLED').length },
  ]
})

const cancelling = ref<Booking>()
const dialogOpen = ref(false)
function replace(updated: Booking) {
  bookings.value = bookings.value?.map((b) => (b.id === updated.id ? updated : b))
}
</script>

<template>
  <PageSection>
    <div class="flex flex-col gap-8">
      <div class="flex flex-wrap items-end justify-between gap-4">
        <div class="flex flex-wrap items-center gap-2">
          <BaseButton variant="ghost" size="sm" @click="navigate({ week: addDays(weekStart, -7) })">
            ← {{ t('booking.previousWeek') }}
          </BaseButton>
          <BaseButton
            v-if="weekStart !== addDays(today, 1 - isoWeekday(today))"
            variant="outline"
            size="sm"
            @click="navigate({ week: '' })"
          >
            {{ t('admin.bookings.thisWeek') }}
          </BaseButton>
          <BaseButton variant="ghost" size="sm" @click="navigate({ week: addDays(weekStart, 7) })">
            {{ t('booking.nextWeek') }} →
          </BaseButton>
        </div>
        <BaseSelect
          :model-value="employeeId"
          :options="employeeOptions"
          :label="t('admin.bookings.employee')"
          class="sm:w-64"
          @update:model-value="navigate({ employee: String($event ?? '') })"
        />
      </div>

      <h2 class="display-caps text-2xl">
        {{
          t('admin.bookings.week', {
            from: formatDay(weekStart, locale),
            to: formatDay(addDays(weekStart, 6), locale),
          })
        }}
      </h2>

      <dl class="grid grid-cols-2 gap-px border border-line-soft bg-line-soft lg:grid-cols-4">
        <div v-for="total in totals" :key="total.label" class="flex flex-col gap-1 bg-surface px-5 py-4">
          <dt class="label-caps text-ink-muted">{{ t(total.label) }}</dt>
          <dd class="text-3xl font-medium tabular-nums">{{ loading ? '…' : total.count }}</dd>
          <dd v-if="total.cents !== undefined" class="text-sm text-ink-muted tabular-nums">
            {{ loading ? '' : formatPrice(total.cents, locale) }}
          </dd>
        </div>
      </dl>

      <RequestState
        :loading="loading"
        :error="error"
        :empty="days.length === 0"
        :empty-text="t('admin.bookings.empty')"
        @retry="load"
      >
        <div class="flex flex-col gap-10">
          <section v-for="group in days" :key="group.day" class="flex flex-col gap-3">
            <h3 class="label-caps text-accent">{{ formatDate(salonInstant(group.day, '12:00'), locale) }}</h3>
            <div class="relative overflow-x-auto border border-line-soft bg-surface">
              <table class="w-full min-w-[48rem] text-left text-sm">
                <thead class="label-caps sr-only">
                  <tr>
                    <th>{{ t('booking.when') }}</th>
                    <th>{{ t('admin.bookings.employee') }}</th>
                    <th>{{ t('admin.bookings.client') }}</th>
                    <th>{{ t('booking.steps.services') }}</th>
                    <th>{{ t('booking.subtotal') }}</th>
                    <th>{{ t('admin.status') }}</th>
                    <th>{{ t('admin.actions') }}</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-line-soft">
                  <tr
                    v-for="booking in group.bookings"
                    :key="booking.id"
                    :class="{ 'text-ink-muted': booking.status === 'CANCELLED' }"
                  >
                    <td class="px-4 py-3 whitespace-nowrap tabular-nums">
                      {{ formatTime(booking.startsAt, locale) }} – {{ formatTime(booking.endsAt, locale) }}
                    </td>
                    <td class="px-4 py-3">{{ booking.employeeName }}</td>
                    <td class="px-4 py-3">
                      <p>{{ booking.customerName }}</p>
                      <a
                        v-if="booking.customerPhone"
                        :href="`tel:${booking.customerPhone}`"
                        class="text-ink-muted hover:text-accent"
                      >
                        {{ booking.customerPhone }}
                      </a>
                    </td>
                    <td class="px-4 py-3">
                      {{ booking.services.map((s) => localize(s.nameSnapshot)).join(', ') }}
                      <span v-if="booking.products.length" class="block text-ink-muted">
                        +
                        {{
                          booking.products
                            .map((p) => `${localize(p.nameSnapshot)} × ${p.quantity}`)
                            .join(', ')
                        }}
                      </span>
                    </td>
                    <td class="px-4 py-3 text-right tabular-nums">
                      {{ formatPrice(booking.subtotalCents, locale) }}
                    </td>
                    <td class="px-4 py-3"><BookingStatusBadge :status="booking.status" /></td>
                    <td class="px-4 py-3 text-right">
                      <BaseButton
                        v-if="booking.status === 'RESERVED'"
                        variant="ghost"
                        size="sm"
                        @click="((cancelling = booking), (dialogOpen = true))"
                      >
                        {{ t('common.cancel') }}
                      </BaseButton>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
        </div>
      </RequestState>
    </div>

    <CancelBookingDialog
      v-model:open="dialogOpen"
      :booking="cancelling"
      perspective="staff"
      @cancelled="replace"
    />
  </PageSection>
</template>
