<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { useNotifications } from '@/composables/useNotifications'

/** Link to the notifications page, with the unread count; only for signed-in visitors. */
const { t } = useI18n()
const { unread } = useNotifications()

const label = computed(() =>
  unread.value ? t('notifications.bellUnread', { n: unread.value }) : t('notifications.bell'),
)
</script>

<template>
  <RouterLink
    :to="{ name: 'notifications' }"
    class="relative p-2 text-ink transition-colors hover:text-accent"
    :aria-label="label"
    :title="label"
  >
    <svg viewBox="0 0 20 20" class="size-5" aria-hidden="true">
      <path
        d="M10 3a4.5 4.5 0 0 0-4.5 4.5v3L4 13.5h12l-1.5-3v-3A4.5 4.5 0 0 0 10 3ZM8.25 16a1.75 1.75 0 0 0 3.5 0"
        fill="none"
        stroke="currentColor"
        stroke-width="1.25"
        stroke-linecap="round"
        stroke-linejoin="round"
      />
    </svg>
    <span
      v-if="unread"
      class="absolute top-0.5 right-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-accent px-1 text-[0.625rem] leading-none font-medium text-accent-ink"
      aria-hidden="true"
    >
      {{ unread > 9 ? '9+' : unread }}
    </span>
  </RouterLink>
</template>
