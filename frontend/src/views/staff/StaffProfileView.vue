<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { api } from '@/api/http'
import { ACCEPTED_IMAGE_TYPES, MAX_IMAGE_MB, uploadMedia } from '@/api/media'
import { problemFields, problemMessage } from '@/api/problem'
import ScriptTitle from '@/components/brand/ScriptTitle.vue'
import PageSection from '@/components/layout/PageSection.vue'
import PhotoUploader from '@/components/staff/PhotoUploader.vue'
import BaseAlert from '@/components/ui/BaseAlert.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseInput from '@/components/ui/BaseInput.vue'
import BaseTextarea from '@/components/ui/BaseTextarea.vue'
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

// the name and bio clients read on the team page
const displayName = ref('')
const bio = ref('')
watch(profile, (p) => {
  displayName.value = p?.displayName ?? ''
  bio.value = p?.bio ?? ''
})
const detailsSaving = ref(false)
const detailsNotice = ref<{ tone: 'success' | 'danger'; text: string }>()
const invalid = ref<string[]>([])

async function saveDetails() {
  detailsSaving.value = true
  detailsNotice.value = undefined
  invalid.value = []
  try {
    const { data, error, response } = await api.PUT('/api/staff/me', {
      body: { displayName: displayName.value.trim(), bio: bio.value.trim() || null },
    })
    if (data) {
      profile.value = data
      detailsNotice.value = { tone: 'success', text: t('myProfile.saved') }
    } else {
      invalid.value = problemFields(error)
      if (!invalid.value.length)
        detailsNotice.value = { tone: 'danger', text: problemMessage(error, response) }
    }
  } catch (e) {
    detailsNotice.value = { tone: 'danger', text: problemMessage(e) }
  } finally {
    detailsSaving.value = false
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

        <form class="flex w-full max-w-md flex-col gap-6 text-left" @submit.prevent="saveDetails">
          <h2 class="label-caps text-center text-accent">{{ t('staff.profile.detailsTitle') }}</h2>
          <BaseInput
            v-model="displayName"
            :label="t('myProfile.name')"
            :error="invalid.includes('displayName') ? t('myProfile.nameInvalid') : undefined"
            required
            maxlength="120"
          />
          <BaseTextarea
            v-model="bio"
            :label="t('staff.profile.bio')"
            :hint="t('staff.profile.bioHint')"
            rows="6"
          />
          <BaseAlert v-if="detailsNotice" :tone="detailsNotice.tone">{{ detailsNotice.text }}</BaseAlert>
          <div class="flex flex-wrap items-center gap-3">
            <BaseButton type="submit" :loading="detailsSaving" :disabled="!displayName.trim()">
              {{ t('common.save') }}
            </BaseButton>
            <BaseButton
              v-if="profile.active"
              :to="{ name: 'employee', params: { employeeId: profile.id } }"
              variant="ghost"
            >
              {{ t('staff.profile.viewPublic') }}
            </BaseButton>
          </div>
        </form>
      </div>
    </RequestState>
  </PageSection>
</template>
