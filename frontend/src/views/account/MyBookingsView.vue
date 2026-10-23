<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { api, type Schemas } from '@/api/http'
import BookingCard from '@/components/booking/BookingCard.vue'
import CancelBookingDialog from '@/components/booking/CancelBookingDialog.vue'
import PageSection from '@/components/layout/PageSection.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import RequestState from '@/components/ui/RequestState.vue'
import { useRequest } from '@/composables/useRequest'
import { bookAgainLink, isUpcoming } from '@/utils/bookings'

const { t } = useI18n()

const { data: bookings, loading, error, reload } = useRequest(() => api.GET('/api/bookings/mine'))

// the API lists newest first; upcoming ones read better soonest first
const upcoming = computed(() => (bookings.value ?? []).filter((b) => isUpcoming(b)).reverse())
const past = computed(() => (bookings.value ?? []).filter((b) => !isUpcoming(b)))

const cancelling = ref<Schemas['BookingView']>()
const dialogOpen = ref(false)
function askCancel(booking: Schemas['BookingView']) {
  cancelling.value = booking
  dialogOpen.value = true
}
function replace(updated: Schemas['BookingView']) {
  bookings.value = bookings.value?.map((b) => (b.id === updated.id ? updated : b))
}
</script>

<template>
  <PageSection>
    <RequestState
      :loading="loading"
      :error="error"
      :empty="bookings?.length === 0"
      :empty-text="t('myBookings.empty')"
      @retry="reload"
    >
      <div class="flex flex-col gap-14">
        <section class="flex flex-col gap-6">
          <div class="flex flex-wrap items-center justify-between gap-4">
            <h2 class="display-caps text-3xl">{{ t('myBookings.upcoming') }}</h2>
            <BaseButton :to="{ name: 'booking' }" size="sm">{{ t('myBookings.bookNew') }}</BaseButton>
          </div>
          <p v-if="!upcoming.length" class="text-ink-muted">{{ t('myBookings.noUpcoming') }}</p>
          <BookingCard v-for="booking in upcoming" :key="booking.id" :booking="booking">
            <BaseButton
              :to="{ name: 'my-booking', params: { bookingId: booking.id } }"
              variant="outline"
              size="sm"
            >
              {{ t('myBookings.details') }}
            </BaseButton>
            <BaseButton variant="ghost" size="sm" @click="askCancel(booking)">
              {{ t('myBookings.cancel') }}
            </BaseButton>
          </BookingCard>
        </section>

        <section v-if="past.length" class="flex flex-col gap-6">
          <h2 class="display-caps text-3xl">{{ t('myBookings.past') }}</h2>
          <BookingCard v-for="booking in past" :key="booking.id" :booking="booking">
            <BaseButton :to="bookAgainLink(booking)" variant="outline" size="sm">
              {{ t('myBookings.bookAgain') }}
            </BaseButton>
          </BookingCard>
        </section>
      </div>
    </RequestState>

    <CancelBookingDialog v-model:open="dialogOpen" :booking="cancelling" @cancelled="replace" />
  </PageSection>
</template>
