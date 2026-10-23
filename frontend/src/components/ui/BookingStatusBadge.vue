<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { Schemas } from '@/api/http'
import BaseBadge from './BaseBadge.vue'

defineProps<{ status: Schemas['BookingView']['status'] }>()

const { t } = useI18n()

const tones = {
  RESERVED: 'accent',
  COMPLETED: 'success',
  CANCELLED: 'neutral',
  NO_SHOW: 'danger',
} as const
</script>

<template>
  <BaseBadge :tone="tones[status]">{{ t(`booking.status.${status}`) }}</BaseBadge>
</template>
