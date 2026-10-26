import { defineConfig, minimal2023Preset } from '@vite-pwa/assets-generator/config'

// The installed app's icons, generated next to their source (public/icon.svg) by `npm run gen:icons`, which
// `dev` and `build` run first: the PNGs aren't committed (the repository ignores images). The site keeps its
// own favicon.ico.
export default defineConfig({
  preset: {
    ...minimal2023Preset,
    transparent: { ...minimal2023Preset.transparent, favicons: [] },
    maskable: { ...minimal2023Preset.maskable, resizeOptions: { background: '#fbf4f0' } },
    apple: { ...minimal2023Preset.apple, resizeOptions: { background: '#fbf4f0' } },
  },
  images: ['public/icon.svg'],
})
