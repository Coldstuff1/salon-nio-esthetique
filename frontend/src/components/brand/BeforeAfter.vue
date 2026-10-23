<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import PhotoFrame from './PhotoFrame.vue'

/** Two framed photos staggered left/right with BEFORE/AFTER labels. */
defineProps<{ before: string; after: string; alt: string }>()

const { t } = useI18n()
</script>

<template>
  <!-- the label rules run past the page edge; the surrounding PageSection clips them -->
  <div class="flex flex-col gap-10 sm:gap-14">
    <PhotoFrame
      :src="before"
      :alt="`${alt} — ${t('results.before')}`"
      :label="t('results.before')"
      label-side="right"
      class="sm:w-3/5"
    />
    <PhotoFrame
      :src="after"
      :alt="`${alt} — ${t('results.after')}`"
      :label="t('results.after')"
      label-side="left"
      class="sm:ml-auto sm:w-3/5"
    />
  </div>
</template>
