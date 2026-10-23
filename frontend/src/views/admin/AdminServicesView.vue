<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { api, type Schemas } from '@/api/http'
import { problemFields, problemMessage } from '@/api/problem'
import PageSection from '@/components/layout/PageSection.vue'
import BaseAlert from '@/components/ui/BaseAlert.vue'
import BaseBadge from '@/components/ui/BaseBadge.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseCheckbox from '@/components/ui/BaseCheckbox.vue'
import BaseDialog from '@/components/ui/BaseDialog.vue'
import BaseInput from '@/components/ui/BaseInput.vue'
import BaseTextarea from '@/components/ui/BaseTextarea.vue'
import RequestState from '@/components/ui/RequestState.vue'
import { useRequest } from '@/composables/useRequest'
import { useLocale } from '@/i18n'
import { fromInputs, toInputs } from '@/utils/catalog'

/**
 * The salon's treatment catalog. Employees then choose which they offer and at what price; deactivating hides
 * a treatment everywhere but keeps it on past appointments.
 */
type ServiceType = Schemas['ServiceType']

const { t } = useI18n()
const { localize } = useLocale()

const { data: types, loading, error, reload } = useRequest(() => api.GET('/api/admin/service-types'))
// how many active employees offer each one
const { data: summaries, reload: reloadSummaries } = useRequest(() => api.GET('/api/public/services'))
const offeredBy = (id: string) =>
  summaries.value?.find((s) => s.serviceTypeId === id)?.employeeIds.length ?? 0

const sorted = computed(() =>
  [...(types.value ?? [])].sort(
    (a, b) =>
      Number(b.active) - Number(a.active) ||
      (localize(a.category) ?? '￿').localeCompare(localize(b.category) ?? '￿') ||
      localize(a.name)!.localeCompare(localize(b.name)!),
  ),
)
// existing categories to pick from, per language
const categories = (side: 'en' | 'fr') =>
  [...new Set((types.value ?? []).map((s) => s.category?.[side]?.trim()).filter(Boolean))] as string[]

// add / edit dialog
const dialogOpen = ref(false)
const editing = ref<ServiceType>()
const form = ref({ name: toInputs(), category: toInputs(), description: toInputs(), active: true })
const invalid = ref<string[]>([])
const formError = ref<string>()
const saving = ref(false)

function open(type?: ServiceType) {
  editing.value = type
  form.value = {
    name: toInputs(type?.name),
    category: toInputs(type?.category),
    description: toInputs(type?.description),
    active: type?.active ?? true,
  }
  invalid.value = []
  formError.value = undefined
  dialogOpen.value = true
}

const body = (values: typeof form.value) => ({
  name: fromInputs(values.name) ?? { en: '' },
  category: fromInputs(values.category),
  description: fromInputs(values.description),
  active: values.active,
})

async function save() {
  saving.value = true
  invalid.value = []
  formError.value = undefined
  try {
    const {
      data,
      error: problem,
      response,
    } = editing.value
      ? await api.PUT('/api/admin/service-types/{id}', {
          params: { path: { id: editing.value.id } },
          body: body(form.value),
        })
      : await api.POST('/api/admin/service-types', { body: body(form.value) })
    if (data) {
      dialogOpen.value = false
      await Promise.all([reload(), reloadSummaries()])
    } else {
      invalid.value = problemFields(problem)
      if (!invalid.value.length) formError.value = problemMessage(problem, response)
    }
  } catch (e) {
    formError.value = problemMessage(e)
  } finally {
    saving.value = false
  }
}

const toggling = ref<string>()
const listError = ref<string>()
async function toggle(type: ServiceType) {
  toggling.value = type.id
  listError.value = undefined
  try {
    const {
      data,
      error: problem,
      response,
    } = await api.PUT('/api/admin/service-types/{id}', {
      params: { path: { id: type.id } },
      body: {
        name: type.name,
        category: type.category,
        description: type.description,
        active: !type.active,
      },
    })
    if (data) await Promise.all([reload(), reloadSummaries()])
    else listError.value = problemMessage(problem, response)
  } catch (e) {
    listError.value = problemMessage(e)
  } finally {
    toggling.value = undefined
  }
}
</script>

<template>
  <PageSection>
    <div class="flex flex-col gap-8">
      <div class="flex flex-wrap items-start justify-between gap-4">
        <p class="max-w-2xl text-ink-muted">{{ t('admin.services.intro') }}</p>
        <BaseButton @click="open()">+ {{ t('admin.services.add') }}</BaseButton>
      </div>
      <BaseAlert v-if="listError" tone="danger">{{ listError }}</BaseAlert>

      <RequestState
        :loading="loading && !types"
        :error="error"
        :empty="types?.length === 0"
        :empty-text="t('admin.services.empty')"
        @retry="reload"
      >
        <div class="relative overflow-x-auto border border-line-soft bg-surface">
          <table class="w-full min-w-[40rem] text-left text-sm">
            <thead class="label-caps border-b border-line-soft text-ink-muted">
              <tr>
                <th class="px-4 py-3 font-medium">{{ t('admin.services.name') }}</th>
                <th class="px-4 py-3 font-medium">{{ t('admin.services.category') }}</th>
                <th class="px-4 py-3 font-medium">{{ t('admin.services.offeredBy') }}</th>
                <th class="px-4 py-3 font-medium">{{ t('admin.status') }}</th>
                <th class="px-4 py-3">
                  <span class="sr-only">{{ t('admin.actions') }}</span>
                </th>
              </tr>
            </thead>
            <tbody class="divide-y divide-line-soft">
              <tr v-for="type in sorted" :key="type.id" :class="{ 'text-ink-muted': !type.active }">
                <td class="px-4 py-3">
                  <p class="font-medium">{{ localize(type.name) }}</p>
                  <p v-if="type.description" class="line-clamp-1 text-ink-muted">
                    {{ localize(type.description) }}
                  </p>
                </td>
                <td class="px-4 py-3">{{ localize(type.category) ?? '—' }}</td>
                <td class="px-4 py-3">
                  <span v-if="!type.active">—</span>
                  <span v-else :class="{ 'text-danger': offeredBy(type.id) === 0 }">
                    {{ t('admin.services.employees', offeredBy(type.id)) }}
                  </span>
                </td>
                <td class="px-4 py-3">
                  <BaseBadge :tone="type.active ? 'success' : 'neutral'">
                    {{ type.active ? t('admin.active') : t('admin.inactive') }}
                  </BaseBadge>
                </td>
                <td class="px-4 py-3">
                  <div class="flex justify-end gap-2">
                    <BaseButton variant="outline" size="sm" @click="open(type)">{{
                      t('admin.edit')
                    }}</BaseButton>
                    <BaseButton
                      variant="ghost"
                      size="sm"
                      :loading="toggling === type.id"
                      @click="toggle(type)"
                    >
                      {{ type.active ? t('admin.deactivate') : t('admin.reactivate') }}
                    </BaseButton>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </RequestState>
    </div>

    <BaseDialog
      v-model:open="dialogOpen"
      :title="editing ? t('admin.services.edit') : t('admin.services.add')"
    >
      <form id="service-form" class="flex flex-col gap-5" @submit.prevent="save">
        <BaseInput
          v-model="form.name.en"
          :label="t('admin.inEnglish', { label: t('admin.services.name') })"
          :error="invalid.includes('name.en') ? t('admin.nameInvalid') : undefined"
          required
          maxlength="120"
        />
        <BaseInput
          v-model="form.name.fr"
          :label="t('admin.inFrench', { label: t('admin.services.name') })"
          :hint="t('admin.frenchHint')"
          :error="invalid.includes('name.fr') ? t('admin.nameInvalid') : undefined"
          maxlength="120"
        />
        <BaseInput
          v-model="form.category.en"
          :label="t('admin.inEnglish', { label: t('admin.services.category') })"
          :hint="t('admin.services.categoryHint')"
          :error="
            invalid.includes('category.en')
              ? t(form.category.en.trim() ? 'admin.services.categoryInvalid' : 'admin.englishRequired')
              : undefined
          "
          list="service-categories-en"
          maxlength="60"
        />
        <BaseInput
          v-model="form.category.fr"
          :label="t('admin.inFrench', { label: t('admin.services.category') })"
          :error="invalid.includes('category.fr') ? t('admin.services.categoryInvalid') : undefined"
          list="service-categories-fr"
          maxlength="60"
        />
        <datalist v-for="side in ['en', 'fr'] as const" :id="`service-categories-${side}`" :key="side">
          <option v-for="category in categories(side)" :key="category" :value="category" />
        </datalist>
        <BaseTextarea
          v-model="form.description.en"
          :label="t('admin.inEnglish', { label: t('admin.description') })"
          :hint="t('admin.services.descriptionHint')"
          :error="invalid.includes('description.en') ? t('admin.englishRequired') : undefined"
        />
        <BaseTextarea
          v-model="form.description.fr"
          :label="t('admin.inFrench', { label: t('admin.description') })"
        />
        <BaseCheckbox v-if="editing" v-model="form.active" :label="t('admin.activeLabel')" />
        <BaseAlert v-if="formError" tone="danger">{{ formError }}</BaseAlert>
      </form>
      <template #actions>
        <BaseButton variant="outline" @click="dialogOpen = false">{{ t('common.cancel') }}</BaseButton>
        <BaseButton type="submit" form="service-form" :loading="saving" :disabled="!form.name.en.trim()">
          {{ t('common.save') }}
        </BaseButton>
      </template>
    </BaseDialog>
  </PageSection>
</template>
