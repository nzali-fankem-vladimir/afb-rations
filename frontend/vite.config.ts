import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    // Le port est fige : il figure dans les URI de redirection du realm Keycloak
    // et dans les origines CORS des services. Un glissement silencieux vers un
    // autre port ferait echouer la connexion sans message explicite.
    port: 5173,
    strictPort: true,
  },
})
