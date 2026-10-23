<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { computed } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'
import type { AreaLink } from '@/router'

/** Frame for a signed-in area (account, staff, admin): its title, a tab bar of its pages, then the page. */
defineProps<{ title: string; links: AreaLink[] }>()

const { t } = useI18n()
const route = useRoute()
const activeTab = computed(() => route.meta.tab ?? route.name)
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
            class="shrink-0 border-b-2 pb-3 transition-colors"
            :class="
              link.to.name === activeTab
                ? 'border-accent text-ink'
                : 'border-transparent text-ink-muted hover:text-ink'
            "
            :aria-current="link.to.name === activeTab ? 'page' : undefined"
          >
            {{ t(link.label) }}
          </RouterLink>
        </nav>
      </div>
    </div>
    <RouterView />
  </div>
</template>
