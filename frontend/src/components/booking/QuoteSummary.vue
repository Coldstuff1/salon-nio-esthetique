<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { Schemas } from '@/api/http'
import { useLocale } from '@/i18n'
import { formatDate, formatDuration, formatPrice, formatTime } from '@/utils/format'

/** The booking so far: with whom, when, what, and the price from the live quote. */
defineProps<{
  employeeName?: string
  quote?: Schemas['Quote']
  startsAt?: string
  loading?: boolean
}>()

const { t } = useI18n()
const { locale } = useLocale()
</script>

<template>
  <div class="flex flex-col gap-5 border border-line bg-surface p-6">
    <h2 class="display-caps text-xl">{{ t('booking.summary') }}</h2>

    <dl class="flex flex-col gap-3 text-sm">
      <div v-if="employeeName" class="flex justify-between gap-4">
        <dt class="text-ink-muted">{{ t('booking.with') }}</dt>
        <dd class="text-right">{{ employeeName }}</dd>
      </div>
      <div v-if="startsAt" class="flex justify-between gap-4">
        <dt class="text-ink-muted">{{ t('booking.when') }}</dt>
        <dd class="text-right">
          {{ formatDate(startsAt, locale) }}<br />
          {{ formatTime(startsAt, locale) }}
        </dd>
      </div>
    </dl>

    <template v-if="quote">
      <ul class="flex flex-col gap-2 border-t border-line-soft pt-4 text-sm">
        <li v-for="line in quote.services" :key="line.serviceTypeId" class="flex justify-between gap-4">
          <span>{{ line.nameSnapshot }}</span>
          <span class="tabular-nums">{{ formatPrice(line.priceCents, locale) }}</span>
        </li>
        <li v-for="line in quote.products" :key="line.productId" class="flex justify-between gap-4">
          <span>{{ line.nameSnapshot }} × {{ line.quantity }}</span>
          <span class="tabular-nums">{{ formatPrice(line.unitPriceCents * line.quantity, locale) }}</span>
        </li>
      </ul>
      <div class="flex flex-col gap-1 border-t border-line-soft pt-4">
        <div class="flex items-baseline justify-between gap-4">
          <span class="label-caps">{{ t('booking.subtotal') }}</span>
          <span class="text-lg font-medium tabular-nums">{{ formatPrice(quote.subtotalCents, locale) }}</span>
        </div>
        <p class="text-sm text-ink-muted">
          {{ t('booking.duration', { duration: formatDuration(quote.totalDurationMinutes, locale) }) }}
        </p>
        <p class="text-xs text-ink-muted">{{ t('booking.payOnSite') }}</p>
      </div>
    </template>
    <p v-else-if="loading" class="text-sm text-ink-muted" role="status">{{ t('common.loading') }}</p>
    <p v-else class="text-sm text-ink-muted">{{ t('booking.nothingYet') }}</p>
  </div>
</template>
