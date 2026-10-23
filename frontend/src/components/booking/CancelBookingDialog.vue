<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { api, type Schemas } from '@/api/http'
import { problemMessage } from '@/api/problem'
import BaseAlert from '@/components/ui/BaseAlert.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseDialog from '@/components/ui/BaseDialog.vue'
import { useLocale } from '@/i18n'
import { formatDate, formatTime } from '@/utils/format'

/** Asks before cancelling; emits the cancelled booking so the page can show its new status. */
const props = withDefaults(
  defineProps<{ booking?: Schemas['BookingView']; perspective?: 'client' | 'staff' }>(),
  { perspective: 'client' },
)
const open = defineModel<boolean>('open', { default: false })
const emit = defineEmits<{ cancelled: [booking: Schemas['BookingView']] }>()

const { t } = useI18n()
const { locale } = useLocale()
const cancelling = ref(false)
const error = ref<string>()

watch(open, () => (error.value = undefined))

async function cancel() {
  if (!props.booking) return
  cancelling.value = true
  error.value = undefined
  try {
    const {
      data,
      error: problem,
      response,
    } = await api.POST('/api/bookings/{id}/cancel', {
      params: { path: { id: props.booking.id } },
    })
    if (data) {
      emit('cancelled', data)
      open.value = false
    } else {
      error.value = problemMessage(problem, response)
    }
  } catch (e) {
    error.value = problemMessage(e)
  } finally {
    cancelling.value = false
  }
}
</script>

<template>
  <BaseDialog v-model:open="open" :title="t('myBookings.cancelTitle')">
    <p v-if="booking" class="text-ink-muted">
      {{
        t(perspective === 'staff' ? 'staff.cancelText' : 'myBookings.cancelText', {
          date: formatDate(booking.startsAt, locale),
          time: formatTime(booking.startsAt, locale),
          name: perspective === 'staff' ? booking.customerName : booking.employeeName,
        })
      }}
    </p>
    <BaseAlert v-if="error" tone="danger" class="mt-4">{{ error }}</BaseAlert>
    <template #actions>
      <BaseButton variant="outline" @click="open = false">{{ t('myBookings.keep') }}</BaseButton>
      <BaseButton :loading="cancelling" @click="cancel">{{ t('myBookings.cancelConfirm') }}</BaseButton>
    </template>
  </BaseDialog>
</template>
