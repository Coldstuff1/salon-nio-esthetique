<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import type { Schemas } from '@/api/http'
import EmployeePortrait from '@/components/brand/EmployeePortrait.vue'

/** A team member on the team page and home teaser; the whole card opens their profile. */
defineProps<{ employee: Schemas['EmployeeSummary'] }>()

const { t } = useI18n()
</script>

<template>
  <RouterLink
    :to="{ name: 'employee', params: { employeeId: employee.id } }"
    class="group flex flex-col gap-5 text-center"
  >
    <EmployeePortrait
      :name="employee.displayName"
      :photo-url="employee.photoUrl"
      class="transition-colors group-hover:border-accent"
    />
    <div class="flex flex-col gap-2">
      <h3 class="display-caps text-2xl transition-colors group-hover:text-accent">
        {{ employee.displayName }}
      </h3>
      <p v-if="employee.bio" class="line-clamp-3 text-ink-muted">
        {{ employee.bio }}
      </p>
      <p class="label-caps text-accent">{{ t('team.viewProfile') }}</p>
    </div>
  </RouterLink>
</template>
