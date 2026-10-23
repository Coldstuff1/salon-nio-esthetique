<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useLocale } from '@/i18n'
import { formatPrice, formatPriceRange } from '@/utils/format'
import BrandLogo from '@/components/brand/BrandLogo.vue'
import MarqueeTicker from '@/components/brand/MarqueeTicker.vue'
import PriceList from '@/components/brand/PriceList.vue'
import PriceRow from '@/components/brand/PriceRow.vue'
import PriceSection from '@/components/brand/PriceSection.vue'
import ScriptTitle from '@/components/brand/ScriptTitle.vue'
import PageSection from '@/components/layout/PageSection.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseCard from '@/components/ui/BaseCard.vue'

const { t } = useI18n()
const { locale } = useLocale()

const price = (cents: number) => formatPrice(cents, locale.value)
const range = (min: number, max: number) => formatPriceRange(min, max, locale.value)

// static until the page reads /api/public/service-types; prices from the salon's price-list post
const services = [
  { key: 'facial', fromCents: 6000 },
  { key: 'waxing', fromCents: 1000 },
  { key: 'brows', fromCents: undefined },
] as const

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
          <p class="max-w-md text-lg text-ink-muted">{{ t('welcome.intro') }}</p>
          <div class="flex flex-wrap justify-center gap-3 md:justify-start">
            <BaseButton>{{ t('welcome.book') }}</BaseButton>
            <BaseButton variant="outline" @click="scrollToPrices">{{ t('welcome.seePrices') }}</BaseButton>
          </div>
        </div>
        <BrandLogo class="mx-auto w-56 sm:w-64 md:w-72" />
      </div>
    </PageSection>

    <!-- services -->
    <PageSection tone="soft">
      <div class="mb-14 text-center">
        <ScriptTitle :title="t('welcome.servicesTitle')" :script="t('welcome.servicesScript')" />
      </div>
      <div class="grid gap-6 md:grid-cols-3">
        <BaseCard v-for="(service, index) in services" :key="service.key" class="flex flex-col gap-4">
          <span class="font-display text-3xl text-blush">0{{ index + 1 }}</span>
          <h3 class="display-caps text-2xl">{{ t(`welcome.${service.key}.title`) }}</h3>
          <p class="flex-1 text-ink-muted">{{ t(`welcome.${service.key}.text`) }}</p>
          <p v-if="service.fromCents" class="label-caps text-accent">
            {{ t('welcome.from', { price: price(service.fromCents) }) }}
          </p>
        </BaseCard>
      </div>
    </PageSection>

    <!-- price list, framed by the ticker like the Instagram post -->
    <section id="prix" class="scroll-mt-20 overflow-x-clip bg-paper py-10">
      <MarqueeTicker :items="[t('prices.ticker'), t('prices.ticker'), 'Salon Nio']" />
      <div class="page-container py-12">
        <PriceList>
          <PriceSection :title="t('prices.facial')">
            <PriceRow :name="t('prices.basicFacial')" :price="price(6000)" />
            <PriceRow :name="t('prices.sculptingFacial')" :price="price(8000)" />
            <PriceRow :name="t('prices.dermaplaning')" :price="price(2000)" />
            <PriceRow :name="t('prices.vagacial')" :price="price(5000)" :note="t('prices.vagacialNote')" />
          </PriceSection>
          <PriceSection :title="t('prices.waxing')">
            <PriceRow :name="t('prices.brazilian')" :price="range(1500, 4000)" />
            <PriceRow :name="t('prices.underarms')" :price="price(1000)" />
            <PriceRow :name="t('prices.legs')" :price="range(3500, 6000)" />
            <PriceRow :name="t('prices.arms')" :price="range(2000, 4000)" />
          </PriceSection>
        </PriceList>
      </div>
      <MarqueeTicker :items="[t('prices.ticker'), t('prices.ticker'), 'Salon Nio']" />
    </section>

    <!-- closing call to action -->
    <PageSection tone="soft" narrow>
      <div class="flex flex-col items-center gap-6 text-center">
        <p class="font-script text-5xl text-accent md:text-6xl">{{ t('welcome.closingScript') }}</p>
        <p class="text-lg text-ink-muted">{{ t('welcome.closingText') }}</p>
        <BaseButton>{{ t('welcome.book') }}</BaseButton>
      </div>
    </PageSection>
  </div>
</template>
