<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { api } from '@/api/http'
import { problemFields, problemMessage } from '@/api/problem'
import PageSection from '@/components/layout/PageSection.vue'
import BaseAlert from '@/components/ui/BaseAlert.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseInput from '@/components/ui/BaseInput.vue'
import RequestState from '@/components/ui/RequestState.vue'
import { useRequest } from '@/composables/useRequest'

/** The name and phone number the salon sees on the client's appointments. */
const { t } = useI18n()

const { data: profile, loading, error, reload } = useRequest(() => api.GET('/api/me/profile'))
const displayName = ref('')
const phone = ref('')
watch(profile, (p) => {
  displayName.value = p?.displayName ?? ''
  phone.value = p?.phone ?? ''
})

const saving = ref(false)
const saved = ref(false)
const saveError = ref<string>()
const invalid = ref<string[]>([])

async function save() {
  saving.value = true
  saved.value = false
  saveError.value = undefined
  invalid.value = []
  try {
    const {
      data,
      error: problem,
      response,
    } = await api.PUT('/api/me/profile', {
      body: { displayName: displayName.value.trim(), phone: phone.value.trim() || null },
    })
    if (data) {
      profile.value = data
      saved.value = true
    } else {
      invalid.value = problemFields(problem)
      if (!invalid.value.length) saveError.value = problemMessage(problem, response)
    }
  } catch (e) {
    saveError.value = problemMessage(e)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <PageSection narrow>
    <RequestState :loading="loading" :error="error" @retry="reload">
      <form class="flex max-w-lg flex-col gap-6" @submit.prevent="save">
        <p class="text-ink-muted">{{ t('myProfile.intro') }}</p>
        <BaseInput
          v-model="displayName"
          :label="t('myProfile.name')"
          :error="invalid.includes('displayName') ? t('myProfile.nameInvalid') : undefined"
          autocomplete="name"
          required
          maxlength="120"
        />
        <BaseInput
          v-model="phone"
          type="tel"
          :label="t('booking.phone')"
          :hint="t('booking.phoneHint')"
          :error="invalid.includes('phone') ? t('booking.phoneInvalid') : undefined"
          autocomplete="tel"
          maxlength="30"
        />
        <BaseInput
          :model-value="profile?.email ?? ''"
          :label="t('myProfile.email')"
          :hint="t('myProfile.emailHint')"
          readonly
        />
        <BaseAlert v-if="saved" tone="success">{{ t('myProfile.saved') }}</BaseAlert>
        <BaseAlert v-if="saveError" tone="danger">{{ saveError }}</BaseAlert>
        <div>
          <BaseButton type="submit" :loading="saving" :disabled="!displayName.trim()">
            {{ t('common.save') }}
          </BaseButton>
        </div>
      </form>
    </RequestState>
  </PageSection>
</template>
