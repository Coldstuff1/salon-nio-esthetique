<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useLocale } from '@/i18n'
import { formatPrice, formatPriceRange } from '@/utils/format'
import BeforeAfter from '@/components/brand/BeforeAfter.vue'
import BrandLogo from '@/components/brand/BrandLogo.vue'
import MarqueeTicker from '@/components/brand/MarqueeTicker.vue'
import PriceList from '@/components/brand/PriceList.vue'
import PriceRow from '@/components/brand/PriceRow.vue'
import PriceSection from '@/components/brand/PriceSection.vue'
import ScriptTitle from '@/components/brand/ScriptTitle.vue'
import SocialHandle from '@/components/brand/SocialHandle.vue'
import PageSection from '@/components/layout/PageSection.vue'
import BaseAlert from '@/components/ui/BaseAlert.vue'
import BaseBadge from '@/components/ui/BaseBadge.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseCard from '@/components/ui/BaseCard.vue'
import BaseCheckbox from '@/components/ui/BaseCheckbox.vue'
import BaseDialog from '@/components/ui/BaseDialog.vue'
import BaseInput from '@/components/ui/BaseInput.vue'
import BaseSelect from '@/components/ui/BaseSelect.vue'
import BaseTextarea from '@/components/ui/BaseTextarea.vue'
import BookingStatusBadge from '@/components/ui/BookingStatusBadge.vue'
import SectionDivider from '@/components/ui/SectionDivider.vue'

/** Dev-only catalogue of the design system (open `/#styleguide`). */
const { t } = useI18n()
const { locale } = useLocale()

const price = (cents: number) => formatPrice(cents, locale.value)
const range = (min: number, max: number) => formatPriceRange(min, max, locale.value)

const tokens = [
  'paper',
  'surface',
  'surface-soft',
  'ink',
  'ink-muted',
  'line',
  'line-soft',
  'accent',
  'blush',
  'danger',
  'success',
]

// stand-in photos until real treatment pictures are uploaded
const placeholder = (from: string, to: string) =>
  `data:image/svg+xml,${encodeURIComponent(
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 400 300"><defs><linearGradient id="g" x2="1" y2="1"><stop stop-color="${from}"/><stop offset="1" stop-color="${to}"/></linearGradient></defs><rect width="400" height="300" fill="url(#g)"/></svg>`,
  )}`

const name = ref('')
const service = ref('')
const notes = ref('')
const reminders = ref(true)
const dialogOpen = ref(false)
const loading = ref(false)
</script>

<template>
  <div>
    <PageSection>
      <div class="flex flex-col items-center gap-10 text-center">
        <BrandLogo class="w-48" />
        <ScriptTitle as="h1" title="Eyebrows" script="treatment" />
        <SocialHandle />
      </div>
    </PageSection>

    <PageSection tone="soft">
      <h2 class="display-caps mb-8 text-3xl">Tokens</h2>
      <div class="grid grid-cols-2 gap-4 sm:grid-cols-4 lg:grid-cols-6">
        <div v-for="token in tokens" :key="token" class="flex flex-col gap-2">
          <div class="h-16 border border-line-soft" :style="{ background: `var(--c-${token})` }" />
          <code class="text-xs text-ink-muted">{{ token }}</code>
        </div>
      </div>

      <h2 class="display-caps mt-16 mb-8 text-3xl">Typography</h2>
      <div class="flex flex-col gap-6">
        <p class="display-caps text-display">Display serif</p>
        <h2 class="display-caps text-4xl">Heading two</h2>
        <h3 class="text-2xl">Heading three, sentence case</h3>
        <p class="font-script text-5xl text-accent">Script accent</p>
        <p class="label-caps">Label caps — sans, letter-spaced</p>
        <p class="max-w-prose">
          Body text in Jost. Le salon offre des soins du visage, l'épilation à la cire et la mise en
          forme des sourcils dans une ambiance douce et chaleureuse.
          <a href="#styleguide" class="link">Un lien</a>.
        </p>
        <p class="text-sm text-ink-muted">Muted secondary text.</p>
      </div>
    </PageSection>

    <section class="bg-paper py-10">
      <MarqueeTicker :items="[t('prices.ticker'), t('prices.ticker'), 'Salon Nio']" />
      <div class="page-container py-10">
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

    <PageSection tone="soft">
      <div class="mb-14 text-center">
        <ScriptTitle title="Eyebrows" script="treatment" />
      </div>
      <BeforeAfter
        :before="placeholder('#c98a7a', '#7a3a3a')"
        :after="placeholder('#d9a58e', '#5a2a2e')"
        alt="Eyebrows treatment"
      />
    </PageSection>

    <PageSection>
      <h2 class="display-caps mb-8 text-3xl">Components</h2>

      <div class="flex flex-wrap items-center gap-3">
        <BaseButton>Primary</BaseButton>
        <BaseButton variant="outline">Outline</BaseButton>
        <BaseButton variant="ghost">Ghost</BaseButton>
        <BaseButton size="sm">Small</BaseButton>
        <BaseButton :loading="loading" @click="loading = !loading">{{ t('common.loading') }}</BaseButton>
        <BaseButton disabled>Disabled</BaseButton>
      </div>

      <div class="mt-8 flex flex-wrap gap-3">
        <BaseBadge>Neutral</BaseBadge>
        <BookingStatusBadge status="RESERVED" />
        <BookingStatusBadge status="COMPLETED" />
        <BookingStatusBadge status="CANCELLED" />
        <BookingStatusBadge status="NO_SHOW" />
      </div>

      <SectionDivider class="my-12" />

      <div class="grid gap-8 md:grid-cols-2">
        <BaseCard>
          <form class="flex flex-col gap-5" @submit.prevent>
            <BaseInput v-model="name" label="Nom" placeholder="Marie Tremblay" hint="Tel qu'affiché sur votre reçu" />
            <BaseInput model-value="pas-un-courriel" label="Courriel" type="email" error="Adresse invalide" />
            <BaseSelect
              v-model="service"
              label="Soin"
              placeholder="Choisir un soin"
              :options="[
                { value: 'basic', label: t('prices.basicFacial') },
                { value: 'sculpt', label: t('prices.sculptingFacial') },
              ]"
            />
            <BaseTextarea v-model="notes" label="Notes" />
            <BaseCheckbox v-model="reminders" label="Rappel par courriel" />
            <BaseButton type="submit">{{ t('nav.booking') }}</BaseButton>
          </form>
        </BaseCard>

        <div class="flex flex-col gap-4">
          <BaseAlert>Votre rendez-vous est confirmé pour le 24 octobre.</BaseAlert>
          <BaseAlert tone="success">Profil enregistré.</BaseAlert>
          <BaseAlert tone="danger">Ce créneau n'est plus disponible.</BaseAlert>
          <BaseButton variant="outline" @click="dialogOpen = true">Open dialog</BaseButton>
        </div>
      </div>

      <BaseDialog v-model:open="dialogOpen" title="Annuler le rendez-vous">
        <p>Voulez-vous vraiment annuler votre soin du visage du 24 octobre ?</p>
        <template #actions>
          <BaseButton variant="ghost" @click="dialogOpen = false">{{ t('common.close') }}</BaseButton>
          <BaseButton @click="dialogOpen = false">{{ t('common.cancel') }}</BaseButton>
        </template>
      </BaseDialog>
    </PageSection>
  </div>
</template>
