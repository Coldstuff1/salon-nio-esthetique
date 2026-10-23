# frontend

Vue 3 + Vite SPA for Salon Nio Esthétique, styled with Tailwind CSS v4.

## Design system

The look comes from the salon's Instagram posts: blush paper, hairline frames, letter-spaced serif caps,
one plum script word. Browse every token and component at `http://localhost:5173/#styleguide` (dev only).

**Tokens** live in `src/assets/main.css` as CSS variables, redefined under `[data-theme='dark']`, and are exposed
to Tailwind as `paper`, `surface`, `surface-soft`, `ink`, `ink-muted`, `line`, `line-soft`, `accent`, `blush`,
`danger`, `success` (e.g. `bg-paper text-ink border-line`). Components never need `dark:` variants for color.

| Font | Tailwind | Use |
|------|----------|-----|
| Cormorant Garamond | `font-display`, `.display-caps` | titles, section headings, BEFORE/AFTER labels |
| Allura | `font-script` | a single accent word over a title (`ScriptTitle`) |
| Jost | `font-sans`, `.label-caps` | body, buttons, nav, price items, form labels |

Rules:

- Use the semantic color classes, never raw hex values.
- `blush` is decorative only (brushstrokes, focus halo, selection), never text.
- Script is one word at most, always in `accent`.
- Square corners and 1px hairline borders; the logo arch is the only curve.
- User-facing text goes through `vue-i18n` (`src/i18n/fr.json`, `en.json`; French is the fallback).
  Escape `@` as `{'@'}` in messages, since vue-i18n treats it as linked-message syntax.
- Prices arrive in cents: format them with `formatPrice` / `formatPriceRange` from `src/utils/format.ts`.

Components: `src/components/brand/` (logo, `ScriptTitle`, `PriceList`, `PhotoFrame`, `BeforeAfter`, ticker),
`src/components/ui/` (buttons, form fields, card, badge, alert, dialog) and `src/components/layout/`
(header, footer, `PageSection`, theme and language switches).

## Recommended IDE Setup

[VS Code](https://code.visualstudio.com/) + [Vue (Official)](https://marketplace.visualstudio.com/items?itemName=Vue.volar) (and disable Vetur).

## Recommended Browser Setup

- Chromium-based browsers (Chrome, Edge, Brave, etc.):
  - [Vue.js devtools](https://chromewebstore.google.com/detail/vuejs-devtools/nhdogjmejiglipccpnnnanhbledajbpd)
  - [Turn on Custom Object Formatter in Chrome DevTools](http://bit.ly/object-formatters)
- Firefox:
  - [Vue.js devtools](https://addons.mozilla.org/en-US/firefox/addon/vue-js-devtools/)
  - [Turn on Custom Object Formatter in Firefox DevTools](https://fxdx.dev/firefox-devtools-custom-object-formatters/)

## Type Support for `.vue` Imports in TS

TypeScript cannot handle type information for `.vue` imports by default, so we replace the `tsc` CLI with `vue-tsc` for type checking. In editors, we need [Volar](https://marketplace.visualstudio.com/items?itemName=Vue.volar) to make the TypeScript language service aware of `.vue` types.

## Customize configuration

See [Vite Configuration Reference](https://vite.dev/config/).

## Project Setup

```sh
npm install
```

### Compile and Hot-Reload for Development

```sh
npm run dev
```

### Type-Check, Compile and Minify for Production

```sh
npm run build
```
