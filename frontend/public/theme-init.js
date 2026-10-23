// Sets the theme before first paint so dark mode doesn't flash; mirrors src/composables/useTheme.ts.
// A file rather than an inline script so the Content-Security-Policy can forbid inline scripts.
;(function () {
  var theme
  try {
    theme = localStorage.getItem('nio.theme')
  } catch (e) {}
  if (theme !== 'light' && theme !== 'dark') {
    theme = matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
  }
  document.documentElement.dataset.theme = theme
})()
