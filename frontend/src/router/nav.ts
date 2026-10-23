import { computed } from 'vue'
import type { RouteLocationRaw } from 'vue-router'
import { useAuth } from '@/auth/keycloak'

export interface NavLink {
  to: RouteLocationRaw
  /** i18n key */
  label: string
  /** shown as the main call to action */
  primary?: boolean
}

/** Header links for the current visitor; people with several roles get every area they can open. */
export function useNavLinks() {
  const { roles } = useAuth()
  return computed<NavLink[]>(() => {
    // areas link to their root so the link stays highlighted on every page inside
    const links: NavLink[] = [
      { to: { name: 'services' }, label: 'nav.services' },
      { to: { name: 'team' }, label: 'nav.team' },
      { to: { name: 'products' }, label: 'nav.products' },
    ]
    if (roles.value.includes('client')) links.push({ to: '/account', label: 'nav.account' })
    if (roles.value.includes('employee')) links.push({ to: '/staff', label: 'nav.staff' })
    if (roles.value.includes('admin')) links.push({ to: '/admin', label: 'nav.admin' })
    links.push({ to: { name: 'booking' }, label: 'nav.booking', primary: true })
    return links
  })
}
