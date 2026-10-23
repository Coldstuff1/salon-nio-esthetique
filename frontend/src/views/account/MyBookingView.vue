<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { api } from '@/api/http'
import BookingCard from '@/components/booking/BookingCard.vue'
import CancelBookingDialog from '@/components/booking/CancelBookingDialog.vue'
import PageSection from '@/components/layout/PageSection.vue'
import BaseAlert from '@/components/ui/BaseAlert.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import RequestState from '@/components/ui/RequestState.vue'
import { usePageTitle } from '@/composables/usePageTitle'
import { useRequest } from '@/composables/useRequest'
import { useLocale } from '@/i18n'
import NotFoundView from '@/views/NotFoundView.vue'
import { bookAgainLink, isUpcoming } from '@/utils/bookings'
import { formatDate } from '@/utils/format'

/** One appointment; also where the booking page lands (`?confirmed=1`). */
const { t } = useI18n()
const { locale } = useLocale()
const route = useRoute()

// there's no single-booking endpoint; a client's own list is short
const { data: bookings, loading, error, reload } = useRequest(() => api.GET('/api/bookings/mine'))
const booking = computed(() => bookings.value?.find((b) => b.id === route.params.bookingId))
const confirmed = computed(() => route.query.confirmed === '1')

usePageTitle(() => booking.value && formatDate(booking.value.startsAt, locale.value))

const dialogOpen = ref(false)
</script>

<template>
  <NotFoundView v-if="bookings && !booking" />
  <PageSection v-else narrow>
    <RequestState :loading="loading" :error="error" @retry="reload">
      <div v-if="booking" class="flex flex-col gap-6">
        <BaseAlert v-if="confirmed && booking.status === 'RESERVED'" tone="success">
          <p class="font-medium">{{ t('myBookings.confirmedTitle') }}</p>
          <p class="text-ink-muted">{{ t('myBookings.confirmedText') }}</p>
        </BaseAlert>

        <BookingCard :booking="booking">
          <template #details>
            <div v-if="booking.notes" class="flex flex-col gap-1 border-t border-line-soft pt-4 text-sm">
              <p class="label-caps text-ink-muted">{{ t('booking.notes') }}</p>
              <p class="whitespace-pre-line">{{ booking.notes }}</p>
            </div>
          </template>
          <BaseButton v-if="isUpcoming(booking)" variant="outline" size="sm" @click="dialogOpen = true">
            {{ t('myBookings.cancel') }}
          </BaseButton>
          <BaseButton v-else :to="bookAgainLink(booking)" variant="outline" size="sm">
            {{ t('myBookings.bookAgain') }}
          </BaseButton>
        </BookingCard>

        <BaseButton :to="{ name: 'my-bookings' }" variant="ghost" size="sm" class="self-start">
          ← {{ t('myBookings.backToList') }}
        </BaseButton>
      </div>
    </RequestState>

    <CancelBookingDialog
      v-model:open="dialogOpen"
      :booking="booking"
      @cancelled="(updated) => (bookings = bookings?.map((b) => (b.id === updated.id ? updated : b)))"
    />
  </PageSection>
</template>
