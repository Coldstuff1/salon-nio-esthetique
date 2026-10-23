<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { Schemas } from '@/api/http'
import BaseCard from '@/components/ui/BaseCard.vue'
import BookingStatusBadge from '@/components/ui/BookingStatusBadge.vue'
import { useLocale } from '@/i18n'
import { formatDate, formatPrice, formatTime } from '@/utils/format'

/**
 * One appointment: when, with whom, what, and its status. Clients see their aesthetician; staff see the client
 * and how to reach them. Actions go in the default slot.
 */
withDefaults(defineProps<{ booking: Schemas['BookingView']; perspective?: 'client' | 'staff' }>(), {
  perspective: 'client',
})

const { t } = useI18n()
const { locale } = useLocale()
</script>

<template>
  <BaseCard class="flex flex-col gap-5">
    <div class="flex flex-wrap items-start justify-between gap-3">
      <!-- staff look at one day at a time: lead with the time and who's coming -->
      <div v-if="perspective === 'staff'" class="flex flex-col gap-1">
        <p class="display-caps text-xl tabular-nums">
          {{ formatTime(booking.startsAt, locale) }} – {{ formatTime(booking.endsAt, locale) }}
        </p>
        <p class="font-medium">{{ booking.customerName }}</p>
        <p class="flex flex-wrap gap-x-4 text-sm text-ink-muted">
          <a v-if="booking.customerPhone" :href="`tel:${booking.customerPhone}`" class="hover:text-accent">
            {{ booking.customerPhone }}
          </a>
          <a v-if="booking.customerEmail" :href="`mailto:${booking.customerEmail}`" class="hover:text-accent">
            {{ booking.customerEmail }}
          </a>
        </p>
      </div>
      <div v-else class="flex flex-col gap-1">
        <p class="display-caps text-xl">{{ formatDate(booking.startsAt, locale) }}</p>
        <p class="text-ink-muted">
          {{ formatTime(booking.startsAt, locale) }} – {{ formatTime(booking.endsAt, locale) }} ·
          {{ t('booking.withName', { name: booking.employeeName }) }}
        </p>
      </div>
      <BookingStatusBadge :status="booking.status" />
    </div>

    <ul class="flex flex-col gap-1.5 border-t border-line-soft pt-4 text-sm">
      <li v-for="line in booking.services" :key="line.serviceTypeId" class="flex justify-between gap-4">
        <span>{{ line.nameSnapshot }}</span>
        <span class="tabular-nums">{{ formatPrice(line.priceCents, locale) }}</span>
      </li>
      <li v-for="line in booking.products" :key="line.productId" class="flex justify-between gap-4">
        <span>{{ line.nameSnapshot }} × {{ line.quantity }}</span>
        <span class="tabular-nums">{{ formatPrice(line.unitPriceCents * line.quantity, locale) }}</span>
      </li>
      <li class="mt-1 flex justify-between gap-4 font-medium">
        <span class="label-caps">{{ t('booking.subtotal') }}</span>
        <span class="tabular-nums">{{ formatPrice(booking.subtotalCents, locale) }}</span>
      </li>
    </ul>

    <slot name="details" />

    <div v-if="$slots.default" class="flex flex-wrap gap-3">
      <slot />
    </div>
  </BaseCard>
</template>
