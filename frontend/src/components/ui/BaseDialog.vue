<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

/** Native `<dialog>` (focus trap, Esc, backdrop) styled as a framed card. */
defineProps<{ title: string }>()
const open = defineModel<boolean>('open', { default: false })

const dialog = ref<HTMLDialogElement>()
const { t } = useI18n()

watch([open, dialog], ([isOpen, element]) => {
  if (!element) return
  if (isOpen && !element.open) element.showModal()
  if (!isOpen && element.open) element.close()
})
</script>

<template>
  <dialog
    ref="dialog"
    class="m-auto w-[min(32rem,calc(100vw-2rem))] border border-line bg-surface p-0 text-ink backdrop:bg-ink/40 backdrop:backdrop-blur-[2px]"
    @close="open = false"
    @click.self="open = false"
  >
    <div class="p-6 sm:p-8">
      <div class="mb-5 flex items-start justify-between gap-4">
        <h2 class="display-caps text-2xl">{{ title }}</h2>
        <button
          type="button"
          class="-mt-1 -mr-2 p-2 text-ink-muted transition-colors hover:text-accent"
          :aria-label="t('common.close')"
          @click="open = false"
        >
          <svg viewBox="0 0 16 16" class="size-4" aria-hidden="true">
            <path d="M3 3l10 10M13 3 3 13" stroke="currentColor" stroke-width="1.25" />
          </svg>
        </button>
      </div>
      <slot />
      <div v-if="$slots.actions" class="mt-8 flex flex-wrap justify-end gap-3">
        <slot name="actions" />
      </div>
    </div>
  </dialog>
</template>
