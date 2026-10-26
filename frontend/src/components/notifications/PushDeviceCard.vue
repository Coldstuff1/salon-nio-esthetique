<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { problemMessage } from '@/api/problem'
import BaseAlert from '@/components/ui/BaseAlert.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseCard from '@/components/ui/BaseCard.vue'
import { usePushSubscription } from '@/composables/usePushSubscription'

/**
 * Turns push notifications on or off in this browser, or says why it can't (blocked, iPhone not added to the
 * Home Screen, no support). The browser's permission prompt only ever opens from the button.
 */
const { t } = useI18n()
const { state, enable, disable, refresh } = usePushSubscription()

const explanation = computed(
  () =>
    state.value &&
    {
      on: t('notifications.push.on'),
      off: t('notifications.push.off'),
      blocked: t('notifications.push.blocked'),
      'install-first': t('notifications.push.installFirst'),
      unsupported: t('notifications.push.unsupported'),
    }[state.value],
)
const busy = ref(false)
const error = ref<string>()

// the person may have changed the permission in the browser's settings since the last look
onMounted(() => void refresh())

async function toggle() {
  error.value = undefined
  busy.value = true
  try {
    await (state.value === 'on' ? disable() : enable())
  } catch (e) {
    error.value = problemMessage(e)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <BaseCard v-if="state" class="flex flex-col gap-4">
    <div class="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
      <div class="flex flex-col gap-1">
        <h2 class="label-caps text-ink-muted">{{ t('notifications.push.title') }}</h2>
        <p class="text-sm">{{ explanation }}</p>
      </div>
      <BaseButton
        v-if="state === 'on' || state === 'off'"
        :variant="state === 'on' ? 'outline' : 'primary'"
        size="sm"
        class="shrink-0"
        :loading="busy"
        @click="toggle"
      >
        {{ state === 'on' ? t('notifications.push.disable') : t('notifications.push.enable') }}
      </BaseButton>
    </div>
    <BaseAlert v-if="error" tone="danger">{{ error }}</BaseAlert>
  </BaseCard>
</template>
