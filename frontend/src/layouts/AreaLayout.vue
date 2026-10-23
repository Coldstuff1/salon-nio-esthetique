<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { RouterLink, RouterView } from 'vue-router'
import type { AreaLink } from '@/router'

/** Frame for a signed-in area (account, staff, admin): its title, a tab bar of its pages, then the page. */
defineProps<{ title: string; links: AreaLink[] }>()

const { t } = useI18n()
</script>

<template>
  <div>
    <div class="border-b border-line-soft bg-surface-soft">
      <div class="page-container pt-10">
        <p class="label-caps text-accent">{{ t(title) }}</p>
        <nav class="label-caps -mb-px mt-6 flex gap-6 overflow-x-auto" :aria-label="t(title)">
          <RouterLink
            v-for="link in links"
            :key="link.label"
            :to="link.to"
            class="shrink-0 border-b-2 border-transparent pb-3 text-ink-muted transition-colors hover:text-ink"
            exact-active-class="!border-accent !text-ink"
          >
            {{ t(link.label) }}
          </RouterLink>
        </nav>
      </div>
    </div>
    <RouterView />
  </div>
</template>
