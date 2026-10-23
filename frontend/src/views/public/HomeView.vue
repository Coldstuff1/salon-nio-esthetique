<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { api } from '@/api/http'
import { useLocale } from '@/i18n'
import { groupByCategory } from '@/utils/catalog'
import { formatPriceRange } from '@/utils/format'
import BrandLogo from '@/components/brand/BrandLogo.vue'
import MarqueeTicker from '@/components/brand/MarqueeTicker.vue'
import PriceList from '@/components/brand/PriceList.vue'
import PriceRow from '@/components/brand/PriceRow.vue'
import PriceSection from '@/components/brand/PriceSection.vue'
import ScriptTitle from '@/components/brand/ScriptTitle.vue'
import PageSection from '@/components/layout/PageSection.vue'
import EmployeeCard from '@/components/public/EmployeeCard.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseCard from '@/components/ui/BaseCard.vue'
import { useRequest } from '@/composables/useRequest'

const { t } = useI18n()
const { locale } = useLocale()

// the treatment families the salon presents itself with; the live list and prices are below and on /services
const highlights = ['facial', 'waxing', 'brows'] as const

const { data: services } = useRequest(() => api.GET('/api/public/services'))
const { data: employees } = useRequest(() => api.GET('/api/public/employees'))

// only what can actually be booked makes the price list
const priceGroups = computed(() =>
  groupByCategory((services.value ?? []).filter((s) => s.minPriceCents != null && s.maxPriceCents != null)),
)
const teaser = computed(() => (employees.value ?? []).slice(0, 3))

function scrollToPrices() {
  document.getElementById('prix')?.scrollIntoView({ behavior: 'smooth' })
}
</script>

<template>
  <div>
    <!-- hero -->
    <PageSection>
      <div class="grid items-center gap-12 md:grid-cols-[1.2fr_1fr] md:gap-16">
        <div class="flex flex-col items-center gap-7 text-center md:items-start md:text-left">
          <p class="label-caps text-accent">{{ t('welcome.eyebrow') }}</p>
          <ScriptTitle as="h1" :title="t('welcome.title')" :script="t('welcome.script')" />
          <p class="max-w-md text-lg text-ink-muted">
            {{ t('welcome.intro') }}
          </p>
          <div class="flex flex-wrap justify-center gap-3 md:justify-start">
            <BaseButton :to="{ name: 'booking' }">{{ t('welcome.book') }}</BaseButton>
            <BaseButton v-if="priceGroups.length" variant="outline" @click="scrollToPrices">
              {{ t('welcome.seePrices') }}
            </BaseButton>
          </div>
        </div>
        <BrandLogo class="mx-auto w-56 sm:w-64 md:w-72" />
      </div>
    </PageSection>

    <!-- treatments -->
    <PageSection tone="soft">
      <div class="mb-14 text-center">
        <ScriptTitle :title="t('welcome.servicesTitle')" :script="t('welcome.servicesScript')" />
      </div>
      <div class="grid gap-6 md:grid-cols-3">
        <BaseCard v-for="(key, index) in highlights" :key="key" class="flex flex-col gap-4">
          <span class="font-display text-3xl text-blush">0{{ index + 1 }}</span>
          <h3 class="display-caps text-2xl">{{ t(`welcome.${key}.title`) }}</h3>
          <p class="flex-1 text-ink-muted">{{ t(`welcome.${key}.text`) }}</p>
        </BaseCard>
      </div>
      <div class="mt-12 text-center">
        <BaseButton :to="{ name: 'services' }" variant="outline">{{ t('welcome.allServices') }}</BaseButton>
      </div>
    </PageSection>

    <!-- price list, framed by the ticker like the Instagram post -->
    <section v-if="priceGroups.length" id="prix" class="scroll-mt-20 overflow-x-clip bg-paper py-10">
      <MarqueeTicker :items="[t('prices.ticker'), t('prices.ticker'), 'Salon Nio']" />
      <div class="page-container py-12">
        <PriceList>
          <PriceSection
            v-for="group in priceGroups"
            :key="group.category ?? ''"
            :title="group.category ?? t('services.otherCategory')"
          >
            <PriceRow
              v-for="service in group.items"
              :key="service.serviceTypeId"
              :name="service.name"
              :price="formatPriceRange(service.minPriceCents!, service.maxPriceCents!, locale)"
            />
          </PriceSection>
        </PriceList>
      </div>
      <MarqueeTicker :items="[t('prices.ticker'), t('prices.ticker'), 'Salon Nio']" />
    </section>

    <!-- team -->
    <PageSection v-if="teaser.length" tone="soft">
      <div class="mb-14 text-center">
        <ScriptTitle :title="t('team.title')" :script="t('team.script')" />
      </div>
      <div class="grid grid-cols-[repeat(auto-fit,minmax(15rem,20rem))] justify-center gap-x-8 gap-y-14">
        <EmployeeCard v-for="employee in teaser" :key="employee.id" :employee="employee" />
      </div>
      <div v-if="(employees?.length ?? 0) > teaser.length" class="mt-12 text-center">
        <BaseButton :to="{ name: 'team' }" variant="outline">{{ t('welcome.allTeam') }}</BaseButton>
      </div>
    </PageSection>

    <!-- closing call to action -->
    <PageSection :tone="teaser.length ? 'paper' : 'soft'" narrow>
      <div class="flex flex-col items-center gap-6 text-center">
        <p class="font-script text-5xl text-accent md:text-6xl">
          {{ t('welcome.closingScript') }}
        </p>
        <p class="text-lg text-ink-muted">{{ t('welcome.closingText') }}</p>
        <BaseButton :to="{ name: 'booking' }">{{ t('welcome.book') }}</BaseButton>
      </div>
    </PageSection>
  </div>
</template>
