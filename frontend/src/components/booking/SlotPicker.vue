<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { api } from '@/api/http'
import { problemMessage } from '@/api/problem'
import BaseAlert from '@/components/ui/BaseAlert.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import { useLocale } from '@/i18n'
import { addDays, salonToday } from '@/utils/dates'
import { formatDay, formatTime } from '@/utils/format'

/**
 * Step 3: a week of days (each with how many start times are free) and the free times of the chosen day.
 * Days and times are the salon's.
 */
const props = defineProps<{ employeeId: string; serviceIds: string[]; day?: string; startsAt?: string }>()
const emit = defineEmits<{ 'update:day': [day: string]; 'update:startsAt': [startsAt: string] }>()

const { t } = useI18n()
const { locale } = useLocale()

const DAYS = 7
const MAX_WEEKS_AHEAD = 12
const today = salonToday()
const daysFromToday = (day: string) =>
  Math.round((Date.parse(`${day}T00:00:00Z`) - Date.parse(`${today}T00:00:00Z`)) / 86_400_000)

const week = ref(props.day ? Math.max(0, Math.floor(daysFromToday(props.day) / DAYS)) : 0)
const days = computed(() => Array.from({ length: DAYS }, (_, i) => addDays(today, week.value * DAYS + i)))

const slots = ref<Record<string, string[]>>({})
const loading = ref(false)
const error = ref<string>()

async function load() {
  loading.value = true
  error.value = undefined
  const requested = days.value
  try {
    const results = await Promise.all(
      requested.map((date) =>
        api.GET('/api/public/employees/{id}/slots', {
          params: { path: { id: props.employeeId }, query: { date, serviceIds: props.serviceIds } },
        }),
      ),
    )
    if (requested !== days.value) return // the week changed while loading
    const failed = results.find((r) => !r.data)
    if (failed) {
      error.value = problemMessage(failed.error, failed.response)
      return
    }
    slots.value = Object.fromEntries(requested.map((date, i) => [date, results[i]!.data!]))
    // open on the first day with room when none is picked in this week yet
    if (!props.day || !requested.includes(props.day)) {
      const first = requested.find((date) => slots.value[date]?.length)
      if (first) emit('update:day', first)
    }
  } catch (e) {
    error.value = problemMessage(e)
  } finally {
    loading.value = false
  }
}

watch([days, () => props.employeeId, () => props.serviceIds.join()], load, { immediate: true })

const selectedSlots = computed(() => (props.day ? (slots.value[props.day] ?? []) : []))
const weekIsEmpty = computed(() => !loading.value && days.value.every((d) => !slots.value[d]?.length))
</script>

<template>
  <div class="flex flex-col gap-8">
    <div class="flex items-center justify-between gap-3">
      <BaseButton variant="ghost" size="sm" :disabled="week === 0 || loading" @click="week--">
        ← {{ t('booking.previousWeek') }}
      </BaseButton>
      <BaseButton variant="ghost" size="sm" :disabled="week >= MAX_WEEKS_AHEAD || loading" @click="week++">
        {{ t('booking.nextWeek') }} →
      </BaseButton>
    </div>

    <BaseAlert v-if="error" tone="danger" class="flex items-center justify-between gap-4">
      <span>{{ error }}</span>
      <BaseButton variant="outline" size="sm" @click="load">{{ t('common.retry') }}</BaseButton>
    </BaseAlert>

    <template v-else>
      <div class="grid grid-cols-4 gap-2 sm:grid-cols-7" role="group" :aria-label="t('booking.chooseDay')">
        <button
          v-for="date in days"
          :key="date"
          type="button"
          class="flex flex-col items-center gap-1 border px-1 py-3 text-center transition-colors disabled:cursor-not-allowed disabled:opacity-40"
          :class="
            date === day
              ? 'border-accent bg-accent text-accent-ink'
              : 'border-line-soft bg-surface hover:border-line'
          "
          :disabled="loading || !slots[date]?.length"
          :aria-pressed="date === day"
          @click="emit('update:day', date)"
        >
          <span class="label-caps text-[0.6875rem]">{{ formatDay(date, locale) }}</span>
          <span class="text-xs" :class="date === day ? 'text-accent-ink/80' : 'text-ink-muted'">
            {{ loading ? '…' : t('booking.openings', slots[date]?.length ?? 0) }}
          </span>
        </button>
      </div>

      <p v-if="loading" class="text-center text-ink-muted" role="status">{{ t('common.loading') }}</p>
      <p v-else-if="weekIsEmpty" class="text-center text-ink-muted">{{ t('booking.noOpeningsThisWeek') }}</p>
      <div
        v-else-if="selectedSlots.length"
        class="grid grid-cols-[repeat(auto-fill,minmax(5.5rem,1fr))] gap-2"
        role="group"
        :aria-label="t('booking.chooseTime')"
      >
        <button
          v-for="slot in selectedSlots"
          :key="slot"
          type="button"
          class="border px-2 py-2.5 text-center tabular-nums transition-colors"
          :class="
            slot === startsAt
              ? 'border-accent bg-accent text-accent-ink'
              : 'border-line-soft bg-surface hover:border-line'
          "
          :aria-pressed="slot === startsAt"
          @click="emit('update:startsAt', slot)"
        >
          {{ formatTime(slot, locale) }}
        </button>
      </div>
    </template>
  </div>
</template>
