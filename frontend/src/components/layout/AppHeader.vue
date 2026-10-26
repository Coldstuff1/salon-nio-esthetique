<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute } from 'vue-router'
import { useAuth } from '@/auth/keycloak'
import BrandLogo from '@/components/brand/BrandLogo.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import { useNavLinks } from '@/router/nav'
import LocaleSwitch from './LocaleSwitch.vue'
import NotificationBell from './NotificationBell.vue'
import ReportBugDialog from './ReportBugDialog.vue'
import ThemeToggle from './ThemeToggle.vue'

const { authenticated, username, login, logout } = useAuth()
const { t } = useI18n()
const links = useNavLinks()

const menuOpen = ref(false)
const route = useRoute()
watch(() => route.fullPath, () => (menuOpen.value = false))

const reportOpen = ref(false)
function openReport() {
  menuOpen.value = false
  reportOpen.value = true
}
</script>

<template>
  <header class="sticky top-0 z-20 border-b border-line-soft bg-paper/90 backdrop-blur-sm">
    <div class="page-container flex items-center justify-between gap-3 py-4">
      <RouterLink :to="{ name: 'home' }" class="shrink-0" :aria-label="t('nav.home')">
        <BrandLogo variant="wordmark" />
      </RouterLink>

      <nav class="label-caps hidden items-center gap-5 whitespace-nowrap xl:flex" :aria-label="t('nav.menu')">
        <template v-for="link in links" :key="link.label">
          <BaseButton v-if="link.primary" :to="link.to" size="sm">{{ t(link.label) }}</BaseButton>
          <RouterLink v-else :to="link.to" class="nav-link">{{ t(link.label) }}</RouterLink>
        </template>
      </nav>

      <div class="flex items-center gap-1 sm:gap-3">
        <NotificationBell v-if="authenticated" />
        <LocaleSwitch />
        <ThemeToggle />
        <!-- the drawer has these on phones -->
        <div class="hidden items-center gap-3 sm:flex">
          <template v-if="authenticated">
            <span class="hidden text-sm text-ink-muted 2xl:inline">{{ username }}</span>
            <BaseButton
              variant="ghost"
              size="sm"
              :aria-label="t('bugReport.open')"
              :title="t('bugReport.open')"
              @click="openReport"
            >
              <svg viewBox="0 0 20 20" class="size-4" aria-hidden="true">
                <path
                  d="M5 17V3.5h9.5l-2 3.5 2 3.5H5"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="1.25"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
              <span class="hidden 2xl:inline">{{ t('bugReport.open') }}</span>
            </BaseButton>
            <BaseButton variant="outline" size="sm" @click="logout">{{ t('auth.logout') }}</BaseButton>
          </template>
          <BaseButton v-else size="sm" variant="outline" @click="login()">{{ t('auth.login') }}</BaseButton>
        </div>
        <button
          type="button"
          class="p-2 text-ink transition-colors hover:text-accent xl:hidden"
          :aria-label="t('nav.menu')"
          :aria-expanded="menuOpen"
          aria-controls="mobile-menu"
          @click="menuOpen = !menuOpen"
        >
          <svg viewBox="0 0 20 20" class="size-5" aria-hidden="true">
            <path
              :d="menuOpen ? 'M5 5l10 10M15 5 5 15' : 'M3 6h14M3 10h14M3 14h14'"
              stroke="currentColor"
              stroke-width="1.25"
              stroke-linecap="round"
            />
          </svg>
        </button>
      </div>
    </div>

    <nav
      v-show="menuOpen"
      id="mobile-menu"
      class="label-caps border-t border-line-soft bg-paper xl:hidden"
      :aria-label="t('nav.menu')"
    >
      <div class="page-container flex flex-col gap-1 py-4">
        <template v-for="link in links" :key="link.label">
          <BaseButton v-if="link.primary" :to="link.to" class="mt-3">{{ t(link.label) }}</BaseButton>
          <RouterLink v-else :to="link.to" class="nav-link py-3">{{ t(link.label) }}</RouterLink>
        </template>
        <BaseButton v-if="authenticated" variant="ghost" class="mt-2 sm:hidden" @click="openReport">
          {{ t('bugReport.open') }}
        </BaseButton>
        <BaseButton v-if="authenticated" variant="outline" class="mt-2 sm:hidden" @click="logout">
          {{ t('auth.logout') }}
        </BaseButton>
        <BaseButton v-else variant="outline" class="mt-2 sm:hidden" @click="login()">
          {{ t('auth.login') }}
        </BaseButton>
      </div>
    </nav>

    <ReportBugDialog v-if="authenticated" v-model:open="reportOpen" />
  </header>
</template>

<style scoped>
@reference '@/assets/main.css';

.nav-link {
  @apply text-ink-muted transition-colors hover:text-ink;
}

/* the current page, or any page inside the current area */
.nav-link.router-link-active {
  @apply text-accent;
}
</style>
