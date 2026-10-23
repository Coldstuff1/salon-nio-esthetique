<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { api, type Schemas } from '@/api/http'
import { problemCode, problemFields, problemMessage } from '@/api/problem'
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
import { formatPrice } from '@/utils/format'

/**
 * The salon's retail products, and for each the employees selling it with what a unit cost them, their price
 * and their stock (costs are never shown publicly). Deactivating hides a product but keeps it on past
 * appointments.
 */
type Product = Schemas['AdminProduct']

const { t } = useI18n()
const { locale, localize } = useLocale()

const { data: products, loading, error, reload } = useRequest(() => api.GET('/api/admin/products'))

const sorted = computed(() =>
  [...(products.value ?? [])].sort(
    (a, b) => Number(b.active) - Number(a.active) || localize(a.name)!.localeCompare(localize(b.name)!),
  ),
)
// who actually sells it now; offerings the employee switched off, or of people who left, don't count
const sellers = (product: Product) => product.offerings.filter((o) => o.active && o.employeeActive)

const dialogOpen = ref(false)
const editing = ref<Product>()
const form = ref({ name: toInputs(), sku: '', description: toInputs(), active: true })
const invalid = ref<string[]>([])
const formError = ref<string>()
const saving = ref(false)

function open(product?: Product) {
  editing.value = product
  form.value = {
    name: toInputs(product?.name),
    sku: product?.sku ?? '',
    description: toInputs(product?.description),
    active: product?.active ?? true,
  }
  invalid.value = []
  formError.value = undefined
  dialogOpen.value = true
}

const body = (values: typeof form.value) => ({
  name: fromInputs(values.name) ?? { en: '' },
  sku: values.sku.trim(),
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
      ? await api.PUT('/api/admin/products/{id}', {
          params: { path: { id: editing.value.id } },
          body: body(form.value),
        })
      : await api.POST('/api/admin/products', { body: body(form.value) })
    if (data) {
      dialogOpen.value = false
      await reload()
    } else if (problemCode(problem) === 'sku-taken') {
      invalid.value = ['sku']
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
async function toggle(product: Product) {
  toggling.value = product.id
  listError.value = undefined
  try {
    const {
      data,
      error: problem,
      response,
    } = await api.PUT('/api/admin/products/{id}', {
      params: { path: { id: product.id } },
      body: {
        name: product.name,
        sku: product.sku,
        description: product.description,
        active: !product.active,
      },
    })
    if (data) await reload()
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
        <p class="max-w-2xl text-ink-muted">{{ t('admin.products.intro') }}</p>
        <BaseButton @click="open()">+ {{ t('admin.products.add') }}</BaseButton>
      </div>
      <BaseAlert v-if="listError" tone="danger">{{ listError }}</BaseAlert>

      <RequestState
        :loading="loading && !products"
        :error="error"
        :empty="products?.length === 0"
        :empty-text="t('products.empty')"
        @retry="reload"
      >
        <div class="relative overflow-x-auto border border-line-soft bg-surface">
          <table class="w-full min-w-[48rem] text-left text-sm">
            <thead class="label-caps border-b border-line-soft text-ink-muted">
              <tr>
                <th class="px-4 py-3 font-medium">{{ t('admin.products.name') }}</th>
                <th class="px-4 py-3 font-medium">{{ t('admin.products.sku') }}</th>
                <th class="px-4 py-3 font-medium">{{ t('admin.products.soldBy') }}</th>
                <th class="px-4 py-3 font-medium">{{ t('admin.status') }}</th>
                <th class="px-4 py-3">
                  <span class="sr-only">{{ t('admin.actions') }}</span>
                </th>
              </tr>
            </thead>
            <tbody class="divide-y divide-line-soft">
              <tr v-for="product in sorted" :key="product.id" :class="{ 'text-ink-muted': !product.active }">
                <td class="px-4 py-3 align-top">
                  <p class="font-medium">{{ localize(product.name) }}</p>
                  <p v-if="product.description" class="line-clamp-1 text-ink-muted">
                    {{ localize(product.description) }}
                  </p>
                </td>
                <td class="px-4 py-3 align-top font-mono text-xs">{{ product.sku }}</td>
                <td class="px-4 py-3 align-top">
                  <ul v-if="sellers(product).length" class="flex flex-col gap-1">
                    <li v-for="offering in sellers(product)" :key="offering.employeeId" class="tabular-nums">
                      <span class="font-medium">{{ offering.employeeName }}</span>
                      <span class="text-ink-muted">
                        ·
                        {{
                          t('admin.products.terms', {
                            cost: formatPrice(offering.costCents, locale),
                            price: formatPrice(offering.priceCents, locale),
                            stock: offering.quantityOnHand,
                          })
                        }}
                      </span>
                    </li>
                  </ul>
                  <span v-else class="text-ink-muted">{{ t('admin.products.notSold') }}</span>
                </td>
                <td class="px-4 py-3 align-top">
                  <BaseBadge :tone="product.active ? 'success' : 'neutral'">
                    {{ product.active ? t('admin.active') : t('admin.inactive') }}
                  </BaseBadge>
                </td>
                <td class="px-4 py-3 align-top">
                  <div class="flex justify-end gap-2">
                    <BaseButton variant="outline" size="sm" @click="open(product)">{{
                      t('admin.edit')
                    }}</BaseButton>
                    <BaseButton
                      variant="ghost"
                      size="sm"
                      :loading="toggling === product.id"
                      @click="toggle(product)"
                    >
                      {{ product.active ? t('admin.deactivate') : t('admin.reactivate') }}
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
      :title="editing ? t('admin.products.edit') : t('admin.products.add')"
    >
      <form id="product-form" class="flex flex-col gap-5" @submit.prevent="save">
        <BaseInput
          v-model="form.name.en"
          :label="t('admin.inEnglish', { label: t('admin.products.name') })"
          :error="invalid.includes('name.en') ? t('admin.nameInvalid') : undefined"
          required
          maxlength="120"
        />
        <BaseInput
          v-model="form.name.fr"
          :label="t('admin.inFrench', { label: t('admin.products.name') })"
          :hint="t('admin.frenchHint')"
          :error="invalid.includes('name.fr') ? t('admin.nameInvalid') : undefined"
          maxlength="120"
        />
        <BaseInput
          v-model="form.sku"
          :label="t('admin.products.sku')"
          :error="
            invalid.includes('sku')
              ? t(form.sku.trim() ? 'errors.codes.sku-taken' : 'admin.products.skuRequired')
              : undefined
          "
          required
          maxlength="60"
        />
        <BaseTextarea
          v-model="form.description.en"
          :label="t('admin.inEnglish', { label: t('admin.description') })"
          :hint="t('admin.products.descriptionHint')"
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
        <BaseButton
          type="submit"
          form="product-form"
          :loading="saving"
          :disabled="!form.name.en.trim() || !form.sku.trim()"
        >
          {{ t('common.save') }}
        </BaseButton>
      </template>
    </BaseDialog>
  </PageSection>
</template>
