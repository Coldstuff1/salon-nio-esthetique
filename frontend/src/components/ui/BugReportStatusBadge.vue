<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { Schemas } from '@/api/http'
import BaseBadge from './BaseBadge.vue'

defineProps<{ status: Schemas['AdminBugReport']['status'] }>()

const { t } = useI18n()

const tones = {
  NEW: 'danger',
  IN_PROGRESS: 'accent',
  RESOLVED: 'success',
  DISMISSED: 'neutral',
} as const
</script>

<template>
  <BaseBadge :tone="tones[status]">{{ t(`bugReport.status.${status}`) }}</BaseBadge>
</template>
