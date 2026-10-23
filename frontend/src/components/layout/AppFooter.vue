<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import BrandLogo from '@/components/brand/BrandLogo.vue'
import SocialHandle from '@/components/brand/SocialHandle.vue'

const { t } = useI18n()
const year = new Date().getFullYear()
</script>

<template>
  <footer class="mt-auto border-t border-line-soft bg-surface-soft">
    <div class="page-container flex flex-col items-center gap-5 py-12 text-center">
      <BrandLogo class="w-24" />
      <p class="label-caps text-ink-muted">{{ t('footer.follow') }}</p>
      <SocialHandle />
      <p class="text-xs text-ink-muted">© {{ year }} {{ t('brand.name') }}. {{ t('footer.rights') }}</p>
    </div>
  </footer>
</template>
