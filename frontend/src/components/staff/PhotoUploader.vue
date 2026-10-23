<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ACCEPTED_IMAGE_TYPES, MAX_IMAGE_MB } from '@/api/media'
import EmployeePortrait from '@/components/brand/EmployeePortrait.vue'
import BaseButton from '@/components/ui/BaseButton.vue'

/**
 * An employee's portrait with buttons to pick a new photo or remove it. Previews the picked file while the
 * parent uploads it (`busy`); the parent does the upload and passes back the saved `photoUrl`.
 */
const props = defineProps<{ name: string; photoUrl?: string | null; busy?: boolean }>()
const emit = defineEmits<{ select: [File]; remove: [] }>()

const { t } = useI18n()
const input = ref<HTMLInputElement>()
const preview = ref<string>()

const shown = computed(() => preview.value ?? props.photoUrl)

function clearPreview() {
  if (preview.value) URL.revokeObjectURL(preview.value)
  preview.value = undefined
}

function onChange() {
  const file = input.value?.files?.[0]
  // let the same file be picked again after a failure
  if (input.value) input.value.value = ''
  if (!file) return
  clearPreview()
  preview.value = URL.createObjectURL(file)
  emit('select', file)
}

// the parent is done: show the saved photo (or the old one after a failure)
watch(
  () => props.busy,
  (busy) => {
    if (!busy) clearPreview()
  },
)
onUnmounted(clearPreview)
</script>

<template>
  <div class="flex flex-col items-center gap-5">
    <EmployeePortrait :name="name" :photo-url="shown" class="w-full max-w-xs" :class="{ 'opacity-60': busy }" />
    <input
      ref="input"
      type="file"
      class="sr-only"
      :accept="ACCEPTED_IMAGE_TYPES.join(',')"
      tabindex="-1"
      aria-hidden="true"
      @change="onChange"
    />
    <div class="flex flex-wrap justify-center gap-3">
      <BaseButton :loading="busy" @click="input?.click()">
        {{ photoUrl ? t('media.changePhoto') : t('media.addPhoto') }}
      </BaseButton>
      <BaseButton v-if="photoUrl" variant="ghost" :disabled="busy" @click="emit('remove')">
        {{ t('media.removePhoto') }}
      </BaseButton>
    </div>
    <p class="text-sm text-ink-muted">{{ t('media.photoHint', { max: MAX_IMAGE_MB }) }}</p>
  </div>
</template>
