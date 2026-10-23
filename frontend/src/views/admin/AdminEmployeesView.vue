<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { api, type Schemas } from '@/api/http'
import { problemCode, problemFields, problemMessage } from '@/api/problem'
import EmployeePortrait from '@/components/brand/EmployeePortrait.vue'
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

/**
 * Everyone on staff, including people who left. Accounts and the `employee` role live in Keycloak; here an
 * account becomes a team member (by its Keycloak id), and members are edited, hidden or brought back.
 */
type Employee = Schemas['AdminEmployee']

const { t } = useI18n()

const { data: employees, loading, error, reload } = useRequest(() => api.GET('/api/admin/employees'))
const sorted = computed(() =>
  [...(employees.value ?? [])].sort(
    (a, b) => Number(b.active) - Number(a.active) || a.displayName.localeCompare(b.displayName),
  ),
)

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

const dialogOpen = ref(false)
const editing = ref<Employee>()
const form = ref({ id: '', displayName: '', bio: '', active: true })
const invalid = ref<string[]>([])
const formError = ref<string>()
const saving = ref(false)
const idLooksWrong = computed(
  () => !editing.value && !!form.value.id.trim() && !UUID.test(form.value.id.trim()),
)

function open(employee?: Employee) {
  editing.value = employee
  form.value = {
    id: employee?.id ?? '',
    displayName: employee?.displayName ?? '',
    bio: employee?.bio ?? '',
    active: employee?.active ?? true,
  }
  invalid.value = []
  formError.value = undefined
  dialogOpen.value = true
}

async function save() {
  saving.value = true
  invalid.value = []
  formError.value = undefined
  const details = { displayName: form.value.displayName.trim(), bio: form.value.bio.trim() || null }
  try {
    const {
      data,
      error: problem,
      response,
    } = editing.value
      ? await api.PUT('/api/admin/employees/{id}', {
          params: { path: { id: editing.value.id } },
          body: { ...details, active: form.value.active },
        })
      : await api.POST('/api/admin/employees', { body: { id: form.value.id.trim(), ...details } })
    if (data) {
      dialogOpen.value = false
      await reload()
    } else if (problemCode(problem) === 'employee-exists') {
      invalid.value = ['id']
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
async function toggle(employee: Employee) {
  toggling.value = employee.id
  listError.value = undefined
  try {
    const { error: problem, response } = employee.active
      ? await api.DELETE('/api/admin/employees/{id}', { params: { path: { id: employee.id } } })
      : await api.PUT('/api/admin/employees/{id}', {
          params: { path: { id: employee.id } },
          body: { displayName: employee.displayName, bio: employee.bio ?? null, active: true },
        })
    if (response.ok) await reload()
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
        <p class="max-w-2xl text-ink-muted">{{ t('admin.employees.intro') }}</p>
        <BaseButton @click="open()">+ {{ t('admin.employees.add') }}</BaseButton>
      </div>
      <BaseAlert v-if="listError" tone="danger">{{ listError }}</BaseAlert>

      <RequestState
        :loading="loading && !employees"
        :error="error"
        :empty="employees?.length === 0"
        :empty-text="t('admin.employees.empty')"
        @retry="reload"
      >
        <ul class="flex flex-col divide-y divide-line-soft border-y border-line-soft">
          <li
            v-for="employee in sorted"
            :key="employee.id"
            class="grid items-center gap-4 py-4 sm:grid-cols-[4.5rem_1fr_auto]"
            :class="{ 'text-ink-muted': !employee.active }"
          >
            <EmployeePortrait
              :name="employee.displayName"
              :photo-url="employee.photoUrl"
              compact
              class="hidden w-18 sm:block"
            />
            <div class="flex flex-col gap-1">
              <div class="flex flex-wrap items-center gap-3">
                <span class="display-caps text-lg">{{ employee.displayName }}</span>
                <BaseBadge :tone="employee.active ? 'success' : 'neutral'">
                  {{ employee.active ? t('admin.active') : t('admin.inactive') }}
                </BaseBadge>
              </div>
              <p v-if="employee.bio" class="line-clamp-2 text-sm text-ink-muted">{{ employee.bio }}</p>
              <p class="font-mono text-xs text-ink-muted">{{ employee.id }}</p>
            </div>
            <div class="flex flex-wrap gap-2 sm:justify-end">
              <BaseButton
                v-if="employee.active"
                :to="{ name: 'employee', params: { employeeId: employee.id } }"
                variant="ghost"
                size="sm"
              >
                {{ t('admin.employees.publicPage') }}
              </BaseButton>
              <BaseButton variant="outline" size="sm" @click="open(employee)">{{
                t('admin.edit')
              }}</BaseButton>
              <BaseButton
                variant="ghost"
                size="sm"
                :loading="toggling === employee.id"
                @click="toggle(employee)"
              >
                {{ employee.active ? t('admin.deactivate') : t('admin.reactivate') }}
              </BaseButton>
            </div>
          </li>
        </ul>
      </RequestState>
    </div>

    <BaseDialog
      v-model:open="dialogOpen"
      :title="editing ? t('admin.employees.edit') : t('admin.employees.add')"
    >
      <form id="employee-form" class="flex flex-col gap-5" @submit.prevent="save">
        <BaseInput
          v-if="!editing"
          v-model="form.id"
          :label="t('admin.employees.keycloakId')"
          :hint="t('admin.employees.keycloakIdHint')"
          :error="
            invalid.includes('id')
              ? t('errors.codes.employee-exists')
              : idLooksWrong
                ? t('admin.employees.keycloakIdInvalid')
                : undefined
          "
          placeholder="00000000-0000-0000-0000-000000000000"
          class="font-mono"
          required
        />
        <BaseInput
          v-model="form.displayName"
          :label="t('myProfile.name')"
          :error="invalid.includes('displayName') ? t('myProfile.nameInvalid') : undefined"
          required
          maxlength="120"
        />
        <BaseTextarea
          v-model="form.bio"
          :label="t('staff.profile.bio')"
          :hint="t('admin.employees.bioHint')"
        />
        <BaseCheckbox v-if="editing" v-model="form.active" :label="t('admin.employees.activeLabel')" />
        <BaseAlert v-if="formError" tone="danger">{{ formError }}</BaseAlert>
      </form>
      <template #actions>
        <BaseButton variant="outline" @click="dialogOpen = false">{{ t('common.cancel') }}</BaseButton>
        <BaseButton
          type="submit"
          form="employee-form"
          :loading="saving"
          :disabled="!form.displayName.trim() || (!editing && (!form.id.trim() || idLooksWrong))"
        >
          {{ t('common.save') }}
        </BaseButton>
      </template>
    </BaseDialog>
  </PageSection>
</template>
