<script setup lang="ts">
import { ref } from 'vue'
import { apiFetch } from './api/http'
import { useAuth } from './auth/keycloak'

const { authenticated, username, roles, login, logout } = useAuth()

const me = ref<unknown>()
const error = ref<string>()

async function whoAmI() {
  error.value = undefined
  const response = await apiFetch('/api/me')
  if (response.ok) {
    me.value = await response.json()
  } else {
    me.value = undefined
    error.value = `${response.status} ${response.statusText}`
  }
}
</script>

<template>
  <header>
    <h1>Nio Esthétique</h1>

    <div v-if="authenticated" class="auth">
      <span>{{ username }} <small>({{ roles.join(', ') }})</small></span>
      <button @click="logout">Log out</button>
    </div>
    <div v-else class="auth">
      <button @click="login">Log in</button>
    </div>
  </header>

  <main v-if="authenticated">
    <button @click="whoAmI">Who am I?</button>
    <pre v-if="me">{{ me }}</pre>
    <p v-if="error" class="error">{{ error }}</p>
  </main>
</template>

<style scoped>
header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
  margin-bottom: 2rem;
}

.auth {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.error {
  color: crimson;
}
</style>
