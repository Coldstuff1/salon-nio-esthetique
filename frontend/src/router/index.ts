import { createRouter, createWebHistory } from 'vue-router'
import { useAuth } from '@/auth/keycloak'
import AreaLayout from '@/layouts/AreaLayout.vue'
import HomeView from '@/views/public/HomeView.vue'

export type Role = 'client' | 'employee' | 'admin'

declare module 'vue-router' {
  interface RouteMeta {
    /** i18n key of the page title, shown in the tab */
    title?: string
    /** realm role needed to open the page; children inherit it */
    role?: Role
    /** the page is for any signed-in visitor, whatever their role */
    signedIn?: boolean
    /** route name of the `AreaLayout` tab to highlight, for pages under a tab (e.g. one booking) */
    tab?: string
  }
}

/** A link in an area's sub-navigation (`AreaLayout`). */
export interface AreaLink {
  to: { name: string }
  label: string
}

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView,
      meta: { title: 'nav.home' },
    },
    {
      path: '/services',
      name: 'services',
      component: () => import('@/views/public/ServicesView.vue'),
      meta: { title: 'nav.services' },
    },
    {
      path: '/team',
      name: 'team',
      component: () => import('@/views/public/TeamView.vue'),
      meta: { title: 'nav.team' },
    },
    {
      path: '/team/:employeeId',
      name: 'employee',
      component: () => import('@/views/public/EmployeeView.vue'),
      meta: { title: 'pages.employee' },
    },
    {
      path: '/products',
      name: 'products',
      component: () => import('@/views/public/ProductsView.vue'),
      meta: { title: 'nav.products' },
    },
    {
      path: '/book',
      name: 'booking',
      component: () => import('@/views/public/BookView.vue'),
      meta: { title: 'nav.booking' },
    },

    {
      path: '/account',
      component: AreaLayout,
      props: {
        title: 'nav.account',
        links: [
          { to: { name: 'my-bookings' }, label: 'pages.myBookings' },
          { to: { name: 'my-profile' }, label: 'pages.myProfile' },
        ] satisfies AreaLink[],
      },
      meta: { role: 'client' },
      children: [
        { path: '', redirect: { name: 'my-bookings' } },
        {
          path: 'bookings',
          name: 'my-bookings',
          component: () => import('@/views/account/MyBookingsView.vue'),
          meta: { title: 'pages.myBookings' },
        },
        {
          path: 'bookings/:bookingId',
          name: 'my-booking',
          component: () => import('@/views/account/MyBookingView.vue'),
          meta: { title: 'pages.myBooking', tab: 'my-bookings' },
        },
        {
          path: 'profile',
          name: 'my-profile',
          component: () => import('@/views/account/MyProfileView.vue'),
          meta: { title: 'pages.myProfile' },
        },
      ],
    },

    {
      path: '/staff',
      component: AreaLayout,
      props: {
        title: 'nav.staff',
        links: [
          { to: { name: 'staff-schedule' }, label: 'pages.staffSchedule' },
          { to: { name: 'staff-profile' }, label: 'pages.staffProfile' },
          { to: { name: 'staff-hours' }, label: 'pages.staffHours' },
          { to: { name: 'staff-time-off' }, label: 'pages.staffTimeOff' },
          { to: { name: 'staff-services' }, label: 'pages.staffServices' },
          { to: { name: 'staff-products' }, label: 'pages.staffProducts' },
        ] satisfies AreaLink[],
      },
      meta: { role: 'employee' },
      children: [
        {
          path: '',
          name: 'staff-schedule',
          component: () => import('@/views/staff/StaffScheduleView.vue'),
          meta: { title: 'pages.staffSchedule' },
        },
        {
          path: 'profile',
          name: 'staff-profile',
          component: () => import('@/views/staff/StaffProfileView.vue'),
          meta: { title: 'pages.staffProfile' },
        },
        {
          path: 'hours',
          name: 'staff-hours',
          component: () => import('@/views/staff/StaffHoursView.vue'),
          meta: { title: 'pages.staffHours' },
        },
        {
          path: 'time-off',
          name: 'staff-time-off',
          component: () => import('@/views/staff/StaffTimeOffView.vue'),
          meta: { title: 'pages.staffTimeOff' },
        },
        {
          path: 'services',
          name: 'staff-services',
          component: () => import('@/views/staff/StaffServicesView.vue'),
          meta: { title: 'pages.staffServices' },
        },
        {
          path: 'products',
          name: 'staff-products',
          component: () => import('@/views/staff/StaffProductsView.vue'),
          meta: { title: 'pages.staffProducts' },
        },
      ],
    },

    {
      path: '/admin',
      component: AreaLayout,
      props: {
        title: 'nav.admin',
        links: [
          { to: { name: 'admin-bookings' }, label: 'pages.adminBookings' },
          { to: { name: 'admin-services' }, label: 'pages.adminServices' },
          { to: { name: 'admin-products' }, label: 'pages.adminProducts' },
          { to: { name: 'admin-employees' }, label: 'pages.adminEmployees' },
          { to: { name: 'admin-bug-reports' }, label: 'pages.adminBugReports' },
        ] satisfies AreaLink[],
      },
      meta: { role: 'admin' },
      children: [
        { path: '', redirect: { name: 'admin-bookings' } },
        {
          path: 'bookings',
          name: 'admin-bookings',
          component: () => import('@/views/admin/AdminBookingsView.vue'),
          meta: { title: 'pages.adminBookings' },
        },
        {
          path: 'services',
          name: 'admin-services',
          component: () => import('@/views/admin/AdminServicesView.vue'),
          meta: { title: 'pages.adminServices' },
        },
        {
          path: 'products',
          name: 'admin-products',
          component: () => import('@/views/admin/AdminProductsView.vue'),
          meta: { title: 'pages.adminProducts' },
        },
        {
          path: 'employees',
          name: 'admin-employees',
          component: () => import('@/views/admin/AdminEmployeesView.vue'),
          meta: { title: 'pages.adminEmployees' },
        },
        {
          path: 'bug-reports',
          name: 'admin-bug-reports',
          component: () => import('@/views/admin/AdminBugReportsView.vue'),
          meta: { title: 'pages.adminBugReports' },
        },
        {
          path: 'bug-reports/:reportId',
          name: 'admin-bug-report',
          component: () => import('@/views/admin/AdminBugReportView.vue'),
          meta: { title: 'pages.adminBugReport', tab: 'admin-bug-reports' },
        },
      ],
    },

    {
      path: '/notifications',
      name: 'notifications',
      component: () => import('@/views/NotificationsView.vue'),
      meta: { title: 'pages.notifications', signedIn: true },
    },

    {
      path: '/forbidden',
      name: 'forbidden',
      component: () => import('@/views/ForbiddenView.vue'),
      meta: { title: 'errors.forbiddenTitle' },
    },
    // dev-only design system catalogue
    ...(import.meta.env.DEV
      ? [
          {
            path: '/styleguide',
            name: 'styleguide',
            component: () => import('@/views/StyleGuide.vue'),
          },
        ]
      : []),
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFoundView.vue'),
      meta: { title: 'errors.notFoundTitle' },
    },
  ],
  scrollBehavior(to, _from, savedPosition) {
    if (savedPosition) return savedPosition
    // leave room for the sticky header
    if (to.hash) return { el: to.hash, behavior: 'smooth', top: 80 }
    return { top: 0 }
  },
})

router.beforeEach((to) => {
  const { role, signedIn } = to.meta
  if (!role && !signedIn) return true

  const { authenticated, hasRole, login } = useAuth()
  if (!authenticated.value) {
    // Keycloak brings the visitor back to this page once they're logged in
    void login(to.fullPath)
    return false
  }
  if (role && !hasRole(role)) {
    return { name: 'forbidden', query: { page: to.fullPath }, replace: true }
  }
  return true
})
