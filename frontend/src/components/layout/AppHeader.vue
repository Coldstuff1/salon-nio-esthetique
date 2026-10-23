<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useAuth } from '@/auth/keycloak'
import BrandLogo from '@/components/brand/BrandLogo.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import LocaleSwitch from './LocaleSwitch.vue'
import ThemeToggle from './ThemeToggle.vue'

const { authenticated, username, login, logout } = useAuth()
const { t } = useI18n()
</script>

<template>
  <header class="border-b border-line-soft bg-paper/90 backdrop-blur-sm sticky top-0 z-20">
    <div class="page-container flex items-center justify-between gap-4 py-4">
      <a href="/" class="shrink-0" :aria-label="t('nav.home')">
        <BrandLogo variant="wordmark" />
      </a>

      <nav v-if="$slots.default" class="label-caps hidden items-center gap-6 md:flex">
        <slot />
      </nav>

      <div class="flex items-center gap-2 sm:gap-3">
        <LocaleSwitch />
        <ThemeToggle />
        <template v-if="authenticated">
          <span class="hidden text-sm text-ink-muted sm:inline">{{ username }}</span>
          <BaseButton variant="outline" size="sm" @click="logout">{{ t('auth.logout') }}</BaseButton>
        </template>
        <BaseButton v-else size="sm" @click="login">{{ t('auth.login') }}</BaseButton>
      </div>
    </div>
  </header>
</template>
