import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import './index.css'
import App from './App.tsx'
import { AuthProvider } from './contexts/AuthContext'
import { FonctionnalitesProvider } from './contexts/FonctionnalitesProvider'
import { ToastProvider } from './contexts/ToastProvider'

// FonctionnalitesProvider est SOUS AuthProvider, jamais a cote : il lit le
// drapeau une fois la session resolue, avec le jeton (Sprint 7F.7, etape 5).
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <FonctionnalitesProvider>
          <ToastProvider>
            <App />
          </ToastProvider>
        </FonctionnalitesProvider>
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
)
