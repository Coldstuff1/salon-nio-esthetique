<script setup lang="ts">
import { onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { api } from '@/api/http'
import { ACCEPTED_IMAGE_TYPES, MAX_IMAGE_MB, uploadMedia } from '@/api/media'
import { problemFields, problemMessage } from '@/api/problem'
import BaseAlert from '@/components/ui/BaseAlert.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseDialog from '@/components/ui/BaseDialog.vue'
import BaseInput from '@/components/ui/BaseInput.vue'
import BaseTextarea from '@/components/ui/BaseTextarea.vue'
import { useLocale } from '@/i18n'

/**
 * Lets anyone signed in report a problem, with an optional screenshot (picked or pasted). The page they're on,
 * their screen size and language go along with it. Closing keeps the draft; once sent, the next opening is blank.
 */
const open = defineModel<boolean>('open', { default: false })

const { t } = useI18n()
const { locale } = useLocale()
const route = useRoute()

const title = ref('')
const description = ref('')
const screenshot = ref<File>()
const preview = ref<string>()
// already in storage, so sending again after a failure doesn't upload it twice
let uploaded: { file: File; mediaId: string } | undefined
const input = ref<HTMLInputElement>()

const sending = ref(false)
const sent = ref(false)
const error = ref<string>()
const invalid = ref<string[]>([])

watch(open, (isOpen) => {
  if (!isOpen) return
  if (sent.value) {
    title.value = ''
    description.value = ''
    setScreenshot(undefined)
    uploaded = undefined
    sent.value = false
  }
  error.value = undefined
  invalid.value = []
})

function setScreenshot(file: File | undefined) {
  if (preview.value) URL.revokeObjectURL(preview.value)
  screenshot.value = file
  preview.value = file && URL.createObjectURL(file)
}
onUnmounted(() => setScreenshot(undefined))

function pick(file: File) {
  if (!ACCEPTED_IMAGE_TYPES.includes(file.type)) {
    error.value = t('media.unsupportedType')
    return
  }
  if (file.size > MAX_IMAGE_MB * 1024 * 1024) {
    error.value = t('media.tooLarge', { max: MAX_IMAGE_MB })
    return
  }
  error.value = undefined
  setScreenshot(file)
}

function onChange() {
  const file = input.value?.files?.[0]
  // let the same file be picked again after removing it
  if (input.value) input.value.value = ''
  if (file) pick(file)
}

function onPaste(event: ClipboardEvent) {
  const file = [...(event.clipboardData?.files ?? [])].find((f) => f.type.startsWith('image/'))
  if (!file) return
  event.preventDefault()
  pick(file)
}

async function screenshotId(): Promise<string | null> {
  const file = screenshot.value
  if (!file) return null
  if (uploaded?.file !== file) uploaded = { file, mediaId: await uploadMedia(file) }
  return uploaded.mediaId
}

async function send() {
  sending.value = true
  error.value = undefined
  invalid.value = []
  try {
    let mediaId: string | null
    try {
      mediaId = await screenshotId()
    } catch (e) {
      // uploadMedia already words its errors
      error.value = e instanceof Error ? e.message : problemMessage(e)
      return
    }
    const {
      data,
      error: problem,
      response,
    } = await api.POST('/api/bug-reports', {
      body: {
        title: title.value.trim(),
        description: description.value.trim(),
        pagePath: route.fullPath.slice(0, 2048),
        locale: locale.value,
        viewport: `${window.innerWidth}x${window.innerHeight}`,
        appVersion: import.meta.env.VITE_APP_VERSION ?? null,
        screenshotId: mediaId,
      },
    })
    if (data) {
      sent.value = true
    } else {
      invalid.value = problemFields(problem)
      if (!invalid.value.length) error.value = problemMessage(problem, response)
    }
  } catch (e) {
    error.value = problemMessage(e)
  } finally {
    sending.value = false
  }
}
</script>

<template>
  <BaseDialog v-model:open="open" :title="t('bugReport.open')">
    <BaseAlert v-if="sent" tone="success">
      <p class="font-medium">{{ t('bugReport.sentTitle') }}</p>
      <p class="text-ink-muted">{{ t('bugReport.sentText') }}</p>
    </BaseAlert>
    <form v-else id="bug-report-form" class="flex flex-col gap-5" @submit.prevent="send" @paste="onPaste">
      <p class="text-ink-muted">{{ t('bugReport.intro') }}</p>
      <BaseInput
        v-model="title"
        :label="t('bugReport.titleLabel')"
        :error="invalid.includes('title') ? t('bugReport.titleInvalid') : undefined"
        required
        maxlength="120"
      />
      <BaseTextarea
        v-model="description"
        :label="t('bugReport.description')"
        :hint="t('bugReport.descriptionHint')"
        :error="invalid.includes('description') ? t('bugReport.descriptionInvalid') : undefined"
        required
        maxlength="5000"
        rows="5"
      />
      <div class="flex flex-col gap-1.5">
        <p class="label-caps text-ink-muted">{{ t('bugReport.screenshot') }}</p>
        <div v-if="preview" class="flex flex-wrap items-start gap-3">
          <img
            :src="preview"
            :alt="t('bugReport.screenshot')"
            class="max-h-32 max-w-48 border border-line-soft object-contain"
          />
          <BaseButton variant="ghost" size="sm" @click="setScreenshot(undefined)">
            {{ t('bugReport.removeScreenshot') }}
          </BaseButton>
        </div>
        <BaseButton v-else variant="outline" size="sm" class="self-start" @click="input?.click()">
          {{ t('bugReport.addScreenshot') }}
        </BaseButton>
        <input
          ref="input"
          type="file"
          class="sr-only"
          :accept="ACCEPTED_IMAGE_TYPES.join(',')"
          tabindex="-1"
          aria-hidden="true"
          @change="onChange"
        />
        <p class="text-sm text-ink-muted">{{ t('bugReport.screenshotHint', { max: MAX_IMAGE_MB }) }}</p>
      </div>
      <p class="text-sm text-ink-muted">{{ t('bugReport.contextNote') }}</p>
      <BaseAlert v-if="error" tone="danger">{{ error }}</BaseAlert>
    </form>
    <template #actions>
      <BaseButton v-if="sent" @click="open = false">{{ t('common.close') }}</BaseButton>
      <template v-else>
        <BaseButton variant="outline" @click="open = false">{{ t('common.cancel') }}</BaseButton>
        <BaseButton
          type="submit"
          form="bug-report-form"
          :loading="sending"
          :disabled="!title.trim() || !description.trim()"
        >
          {{ t('bugReport.send') }}
        </BaseButton>
      </template>
    </template>
  </BaseDialog>
</template>
