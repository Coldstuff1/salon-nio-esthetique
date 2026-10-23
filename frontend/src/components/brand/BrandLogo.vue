<script setup lang="ts">
import { useId } from 'vue'

/**
 * The salon's arch badge redrawn as SVG so it follows the theme tokens.
 * `badge` is the full logo (hero, footer); `wordmark` is the compact header version.
 */
withDefaults(defineProps<{ variant?: 'badge' | 'wordmark' }>(), { variant: 'badge' })

const arcId = useId()
</script>

<template>
  <svg
    v-if="variant === 'badge'"
    viewBox="0 0 200 300"
    role="img"
    aria-label="Salon Nio Esthétique — Aesthetician services"
    class="text-ink"
  >
    <path d="M14 292 V100 A86 86 0 0 1 186 100 V292 Z" class="fill-surface-soft" />
    <path
      d="M22 284 V100 A78 78 0 0 1 178 100 V284 Z"
      fill="none"
      stroke="currentColor"
      stroke-width="1"
    />
    <!-- watercolor brushstroke -->
    <path
      d="M88 92 C112 112, 92 150, 112 176 C126 196, 104 222, 120 248 C104 228, 88 200, 98 172 C84 146, 104 118, 88 92 Z"
      class="fill-blush"
      opacity="0.85"
    />
    <path
      d="M96 104 C116 130, 98 160, 118 196"
      fill="none"
      class="stroke-blush"
      stroke-width="6"
      stroke-linecap="round"
      opacity="0.6"
    />
    <path :id="arcId" d="M32 152 A68 68 0 0 1 168 152" fill="none" />
    <text class="font-display" font-size="14" letter-spacing="1.1" fill="currentColor">
      <textPath :href="`#${arcId}`" startOffset="50%" text-anchor="middle">
        SALON NIO ESTHÉTIQUE
      </textPath>
    </text>
    <line x1="52" y1="250" x2="148" y2="250" stroke="currentColor" stroke-width="0.75" />
    <text
      x="100"
      y="264"
      text-anchor="middle"
      class="font-sans"
      font-size="8.5"
      letter-spacing="1.8"
      fill="currentColor"
    >
      AESTHETICIAN SERVICES
    </text>
    <line x1="52" y1="272" x2="148" y2="272" stroke="currentColor" stroke-width="0.75" />
  </svg>

  <span v-else class="inline-flex items-baseline gap-1.5 leading-none text-ink">
    <span class="display-caps text-lg font-semibold whitespace-nowrap sm:text-xl">Salon Nio</span>
    <!-- dropped on phones so the header actions fit -->
    <span class="hidden font-script text-2xl text-accent sm:inline">Esthétique</span>
  </span>
</template>
