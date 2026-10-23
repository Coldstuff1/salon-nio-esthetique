<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { api } from '@/api/http'
import ScriptTitle from '@/components/brand/ScriptTitle.vue'
import PageSection from '@/components/layout/PageSection.vue'
import EmployeeCard from '@/components/public/EmployeeCard.vue'
import RequestState from '@/components/ui/RequestState.vue'
import { useRequest } from '@/composables/useRequest'

const { t } = useI18n()

const { data: employees, loading, error, reload } = useRequest(() => api.GET('/api/public/employees'))
</script>

<template>
  <div>
    <PageSection narrow>
      <div class="flex flex-col items-center gap-6 text-center">
        <ScriptTitle as="h1" :title="t('team.title')" :script="t('team.script')" />
        <p class="max-w-lg text-lg text-ink-muted">{{ t('team.intro') }}</p>
      </div>
    </PageSection>

    <PageSection tone="soft">
      <RequestState
        :loading="loading"
        :error="error"
        :empty="employees?.length === 0"
        :empty-text="t('team.empty')"
        @retry="reload"
      >
        <div class="grid grid-cols-[repeat(auto-fit,minmax(15rem,20rem))] justify-center gap-x-8 gap-y-14">
          <EmployeeCard v-for="employee in employees" :key="employee.id" :employee="employee" />
        </div>
      </RequestState>
    </PageSection>
  </div>
</template>
