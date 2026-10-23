<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute } from 'vue-router'
import { api, type Schemas } from '@/api/http'
import { problemMessage } from '@/api/problem'
import PageSection from '@/components/layout/PageSection.vue'
import BaseAlert from '@/components/ui/BaseAlert.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseSelect from '@/components/ui/BaseSelect.vue'
import BaseTextarea from '@/components/ui/BaseTextarea.vue'
import BugReportStatusBadge from '@/components/ui/BugReportStatusBadge.vue'
import RequestState from '@/components/ui/RequestState.vue'
import { usePageTitle } from '@/composables/usePageTitle'
import { useRequest } from '@/composables/useRequest'
import { useLocale } from '@/i18n'
import NotFoundView from '@/views/NotFoundView.vue'
import { formatDate, formatTime } from '@/utils/format'

/** One reported problem with everything sent along with it, and where an admin moves it along. */
type Status = Schemas['AdminBugReportDetail']['status']

const STATUSES: Status[] = ['NEW', 'IN_PROGRESS', 'RESOLVED', 'DISMISSED']

const { t } = useI18n()
const { locale } = useLocale()
const route = useRoute()

const reportId = computed(() => String(route.params.reportId))
const {
  data: report,
  loading,
  error,
  status: responseStatus,
  reload,
} = useRequest(() => api.GET('/api/admin/bug-reports/{id}', { params: { path: { id: reportId.value } } }))
watch(reportId, reload)

// unknown report; a malformed id in the URL comes back as 400
const notFound = computed(() => responseStatus.value === 404 || responseStatus.value === 400)
usePageTitle(() => (notFound.value ? t('errors.notFoundTitle') : report.value?.title))

const when = (iso: string) => `${formatDate(iso, locale.value)}, ${formatTime(iso, locale.value)}`
const statusOptions = computed(() =>
  STATUSES.map((value) => ({ value, label: t(`bugReport.status.${value}`) })),
)

// follow-up form
const status = ref<Status>('NEW')
const note = ref('')
watch(report, (r) => {
  status.value = r?.status ?? 'NEW'
  note.value = r?.adminNote ?? ''
})
const saving = ref(false)
const notice = ref<{ tone: 'success' | 'danger'; text: string }>()

async function save() {
  saving.value = true
  notice.value = undefined
  try {
    const {
      data,
      error: problem,
      response,
    } = await api.PUT('/api/admin/bug-reports/{id}', {
      params: { path: { id: reportId.value } },
      body: { status: status.value, adminNote: note.value.trim() || null },
    })
    if (data) {
      report.value = data
      notice.value = { tone: 'success', text: t('admin.bugReports.saved') }
    } else {
      notice.value = { tone: 'danger', text: problemMessage(problem, response) }
    }
  } catch (e) {
    notice.value = { tone: 'danger', text: problemMessage(e) }
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <NotFoundView v-if="notFound" />
  <PageSection v-else>
    <RequestState :loading="loading && !report" :error="error" @retry="reload">
      <div v-if="report" class="flex flex-col gap-8">
        <BaseButton :to="{ name: 'admin-bug-reports' }" variant="ghost" size="sm" class="self-start">
          ← {{ t('admin.bugReports.back') }}
        </BaseButton>

        <div class="flex flex-col gap-3">
          <div class="flex flex-wrap items-center gap-3">
            <h1 class="display-caps text-2xl">{{ report.title }}</h1>
            <BugReportStatusBadge :status="report.status" />
          </div>
          <p class="text-sm text-ink-muted">
            {{ t('admin.bugReports.received') }} · {{ when(report.createdAt) }}
          </p>
        </div>

        <div class="grid gap-8 lg:grid-cols-[1.4fr_1fr]">
          <div class="flex flex-col gap-8">
            <p class="whitespace-pre-wrap">{{ report.description }}</p>

            <a
              v-if="report.screenshotUrl"
              :href="report.screenshotUrl"
              target="_blank"
              rel="noopener"
              class="self-start"
            >
              <img
                :src="report.screenshotUrl"
                :alt="t('bugReport.screenshot')"
                class="max-h-[32rem] border border-line-soft object-contain"
              />
            </a>
          </div>

          <div class="flex flex-col gap-8">
            <section class="flex flex-col gap-3 border border-line-soft bg-surface p-5 text-sm">
              <h2 class="label-caps text-accent">{{ t('admin.bugReports.reporter') }}</h2>
              <div>
                <p class="font-medium">{{ report.reporterName }}</p>
                <p class="text-ink-muted">{{ report.reporterUsername }}</p>
                <a
                  v-if="report.reporterEmail"
                  :href="`mailto:${report.reporterEmail}`"
                  class="text-accent hover:underline"
                >
                  {{ report.reporterEmail }}
                </a>
                <p class="text-ink-muted">
                  {{ report.reporterRoles.map((role) => t(`bugReport.roles.${role}`)).join(', ') }}
                </p>
              </div>

              <h2 class="label-caps mt-2 text-accent">{{ t('admin.bugReports.context') }}</h2>
              <dl class="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1.5">
                <dt class="text-ink-muted">{{ t('admin.bugReports.page') }}</dt>
                <dd class="break-all">
                  <RouterLink :to="report.pagePath" class="text-accent hover:underline">
                    {{ report.pagePath }}
                  </RouterLink>
                </dd>
                <template v-if="report.locale">
                  <dt class="text-ink-muted">{{ t('admin.bugReports.locale') }}</dt>
                  <dd>{{ report.locale }}</dd>
                </template>
                <template v-if="report.viewport">
                  <dt class="text-ink-muted">{{ t('admin.bugReports.viewport') }}</dt>
                  <dd>{{ report.viewport }}</dd>
                </template>
                <template v-if="report.userAgent">
                  <dt class="text-ink-muted">{{ t('admin.bugReports.userAgent') }}</dt>
                  <dd class="break-words">{{ report.userAgent }}</dd>
                </template>
                <template v-if="report.appVersion">
                  <dt class="text-ink-muted">{{ t('admin.bugReports.version') }}</dt>
                  <dd>{{ report.appVersion }}</dd>
                </template>
              </dl>
            </section>

            <form class="flex flex-col gap-5" @submit.prevent="save">
              <h2 class="label-caps text-accent">{{ t('admin.bugReports.triage') }}</h2>
              <BaseSelect v-model="status" :options="statusOptions" :label="t('admin.status')" />
              <BaseTextarea
                v-model="note"
                :label="t('admin.bugReports.note')"
                :hint="t('admin.bugReports.noteHint')"
                maxlength="5000"
              />
              <BaseAlert v-if="notice" :tone="notice.tone">{{ notice.text }}</BaseAlert>
              <div class="flex flex-wrap items-center gap-4">
                <BaseButton type="submit" :loading="saving">{{ t('common.save') }}</BaseButton>
                <p v-if="report.updatedAt" class="text-sm text-ink-muted">
                  {{
                    t('admin.bugReports.updatedAt', {
                      date: formatDate(report.updatedAt, locale),
                      time: formatTime(report.updatedAt, locale),
                    })
                  }}
                </p>
              </div>
            </form>
          </div>
        </div>
      </div>
    </RequestState>
  </PageSection>
</template>
