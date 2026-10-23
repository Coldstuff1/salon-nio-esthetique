<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import BaseAlert from './BaseAlert.vue'
import BaseButton from './BaseButton.vue'

/**
 * Stand-in while a `useRequest` call is loading, failed, or came back empty. Renders the default slot only
 * once there's something to show.
 */
defineProps<{
  loading: boolean
  error?: string
  empty?: boolean
  emptyText?: string
}>()
defineEmits<{ retry: [] }>()

const { t } = useI18n()
</script>

<template>
  <p v-if="loading" class="py-12 text-center text-ink-muted" role="status">
    {{ t('common.loading') }}
  </p>
  <BaseAlert v-else-if="error" tone="danger" class="mx-auto flex max-w-xl items-center justify-between gap-4">
    <span>{{ error }}</span>
    <BaseButton variant="outline" size="sm" @click="$emit('retry')">{{ t('common.retry') }}</BaseButton>
  </BaseAlert>
  <p v-else-if="empty" class="py-12 text-center text-ink-muted">
    {{ emptyText }}
  </p>
  <slot v-else />
</template>
