<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useAuth } from '@/auth/keycloak'
import ScriptTitle from '@/components/brand/ScriptTitle.vue'
import PageSection from '@/components/layout/PageSection.vue'
import BaseButton from '@/components/ui/BaseButton.vue'

/** Where the router sends signed-in people who lack the role a page needs (`?page=` is where they were going). */
const { username, logout } = useAuth()
const { t } = useI18n()
</script>

<template>
  <PageSection narrow>
    <div class="flex flex-col items-center gap-7 text-center">
      <p class="label-caps text-accent">403</p>
      <ScriptTitle as="h1" :title="t('errors.forbiddenTitle')" />
      <p class="max-w-md text-ink-muted">{{ t('errors.forbiddenText', { username }) }}</p>
      <div class="flex flex-wrap justify-center gap-3">
        <BaseButton :to="{ name: 'home' }">{{ t('errors.backHome') }}</BaseButton>
        <BaseButton variant="outline" @click="logout">{{ t('errors.switchAccount') }}</BaseButton>
      </div>
    </div>
  </PageSection>
</template>
