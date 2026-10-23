<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { api } from '@/api/http'
import { ACCEPTED_IMAGE_TYPES, MAX_IMAGE_MB, uploadMedia } from '@/api/media'
import { problemMessage } from '@/api/problem'
import ScriptTitle from '@/components/brand/ScriptTitle.vue'
import PageSection from '@/components/layout/PageSection.vue'
import PhotoUploader from '@/components/staff/PhotoUploader.vue'
import BaseAlert from '@/components/ui/BaseAlert.vue'
import RequestState from '@/components/ui/RequestState.vue'
import { useRequest } from '@/composables/useRequest'

const { t } = useI18n()
const { data: profile, loading, error, reload } = useRequest(() => api.GET('/api/staff/me'))

const saving = ref(false)
const notice = ref<{ tone: 'success' | 'danger'; text: string }>()

async function onSelect(file: File) {
  if (!ACCEPTED_IMAGE_TYPES.includes(file.type)) {
    notice.value = { tone: 'danger', text: t('media.unsupportedType') }
    return
  }
  if (file.size > MAX_IMAGE_MB * 1024 * 1024) {
    notice.value = { tone: 'danger', text: t('media.tooLarge', { max: MAX_IMAGE_MB }) }
    return
  }
  await save(async () => {
    const mediaId = await uploadMedia(file)
    return api.PUT('/api/staff/me/photo', { body: { mediaId } })
  }, t('media.photoSaved'))
}

async function onRemove() {
  await save(() => api.DELETE('/api/staff/me/photo'), t('media.photoRemoved'))
}

async function save(
  call: () => Promise<{ data?: NonNullable<typeof profile.value>; error?: unknown; response: Response }>,
  done: string,
) {
  saving.value = true
  notice.value = undefined
  try {
    const { data, error, response } = await call()
    if (data) {
      profile.value = data
      notice.value = { tone: 'success', text: done }
    } else {
      notice.value = { tone: 'danger', text: problemMessage(error, response) }
    }
  } catch (e) {
    // uploadMedia already words its errors; anything else is the network
    notice.value = { tone: 'danger', text: e instanceof Error ? e.message : problemMessage(e) }
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <PageSection narrow>
    <RequestState :loading="loading" :error="error" @retry="reload">
      <div v-if="profile" class="flex flex-col items-center gap-8 text-center">
        <ScriptTitle as="h1" :title="profile.displayName" />
        <section class="flex w-full flex-col items-center gap-6">
          <h2 class="label-caps text-accent">{{ t('media.photoTitle') }}</h2>
          <PhotoUploader
            :name="profile.displayName"
            :photo-url="profile.photoUrl"
            :busy="saving"
            @select="onSelect"
            @remove="onRemove"
          />
          <BaseAlert v-if="notice" :tone="notice.tone" class="w-full max-w-md text-left">
            {{ notice.text }}
          </BaseAlert>
        </section>
      </div>
    </RequestState>
  </PageSection>
</template>
