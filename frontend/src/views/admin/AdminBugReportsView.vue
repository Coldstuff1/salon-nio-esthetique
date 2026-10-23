<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { api, type Schemas } from '@/api/http'
import PageSection from '@/components/layout/PageSection.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseSelect from '@/components/ui/BaseSelect.vue'
import BugReportStatusBadge from '@/components/ui/BugReportStatusBadge.vue'
import RequestState from '@/components/ui/RequestState.vue'
import { useRequest } from '@/composables/useRequest'
import { useLocale } from '@/i18n'
import { salonDay } from '@/utils/dates'
import { formatDay, formatTime } from '@/utils/format'

/**
 * Problems people reported from the site, newest first. `?status=` narrows the list: open ones (new or in
 * progress) by default, `ALL`, or a single status.
 */
type Status = Schemas['AdminBugReport']['status']

const STATUSES: Status[] = ['NEW', 'IN_PROGRESS', 'RESOLVED', 'DISMISSED']
const OPEN: Status[] = ['NEW', 'IN_PROGRESS']

const { t } = useI18n()
const { locale } = useLocale()
const route = useRoute()
const router = useRouter()

const { data: reports, loading, error, reload } = useRequest(() => api.GET('/api/admin/bug-reports'))

const filter = computed(() => (typeof route.query.status === 'string' ? route.query.status : ''))
const matches = (status: Status, value: string) =>
  value === 'ALL' || (value ? status === value : OPEN.includes(status))
const count = (value: string) => (reports.value ?? []).filter((r) => matches(r.status, value)).length
const shown = computed(() => (reports.value ?? []).filter((r) => matches(r.status, filter.value)))

const filterOptions = computed(() =>
  [
    { value: '', label: t('admin.bugReports.open') },
    ...STATUSES.map((status) => ({ value: status, label: t(`bugReport.status.${status}`) })),
    { value: 'ALL', label: t('admin.bugReports.all') },
  ].map((option) => ({
    ...option,
    label: t('admin.bugReports.withCount', { label: option.label, n: count(option.value) }),
  })),
)

function navigate(status?: string) {
  void router.replace({ query: status ? { status } : {} })
}
</script>

<template>
  <PageSection>
    <div class="flex flex-col gap-8">
      <div class="flex flex-wrap items-end justify-between gap-4">
        <div class="max-w-2xl text-ink-muted">
          <p>{{ t('admin.bugReports.intro') }}</p>
          <p v-if="reports">{{ t('admin.bugReports.newCount', count('NEW')) }}</p>
        </div>
        <BaseSelect
          :model-value="filter"
          :options="filterOptions"
          :label="t('admin.bugReports.filter')"
          class="min-w-48"
          @update:model-value="navigate"
        />
      </div>

      <RequestState
        :loading="loading && !reports"
        :error="error"
        :empty="shown.length === 0"
        :empty-text="t('admin.bugReports.empty')"
        @retry="reload"
      >
        <div class="relative overflow-x-auto border border-line-soft bg-surface">
          <table class="w-full min-w-[44rem] text-left text-sm">
            <thead class="label-caps border-b border-line-soft text-ink-muted">
              <tr>
                <th class="px-4 py-3 font-medium">{{ t('admin.bugReports.received') }}</th>
                <th class="px-4 py-3 font-medium">{{ t('admin.bugReports.report') }}</th>
                <th class="px-4 py-3 font-medium">{{ t('admin.bugReports.reporter') }}</th>
                <th class="px-4 py-3 font-medium">{{ t('admin.status') }}</th>
                <th class="px-4 py-3">
                  <span class="sr-only">{{ t('admin.actions') }}</span>
                </th>
              </tr>
            </thead>
            <tbody class="divide-y divide-line-soft">
              <tr v-for="report in shown" :key="report.id">
                <td class="px-4 py-3 whitespace-nowrap">
                  <p>{{ formatDay(salonDay(report.createdAt), locale) }}</p>
                  <p class="text-ink-muted">{{ formatTime(report.createdAt, locale) }}</p>
                </td>
                <td class="px-4 py-3">
                  <p class="font-medium">{{ report.title }}</p>
                  <p class="line-clamp-1 break-all text-ink-muted">{{ report.pagePath }}</p>
                </td>
                <td class="px-4 py-3">
                  <p>{{ report.reporterName }}</p>
                  <p class="text-ink-muted">
                    {{ report.reporterRoles.map((role) => t(`bugReport.roles.${role}`)).join(', ') }}
                  </p>
                </td>
                <td class="px-4 py-3">
                  <div class="flex flex-col items-start gap-1">
                    <BugReportStatusBadge :status="report.status" />
                    <span v-if="report.hasScreenshot" class="text-xs text-ink-muted">
                      {{ t('admin.bugReports.screenshotAttached') }}
                    </span>
                  </div>
                </td>
                <td class="px-4 py-3">
                  <div class="flex justify-end">
                    <BaseButton
                      :to="{ name: 'admin-bug-report', params: { reportId: report.id } }"
                      variant="outline"
                      size="sm"
                    >
                      {{ t('admin.bugReports.view') }}
                    </BaseButton>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </RequestState>
    </div>
  </PageSection>
</template>
