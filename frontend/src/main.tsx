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
// ToastProvider est AU-DESSUS d'AuthProvider : la connexion et la deconnexion
// notifient elles aussi. Il ne depend de rien, aucune raison de le loger plus bas.
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <ToastProvider>
        <AuthProvider>
          <FonctionnalitesProvider>
            <App />
          </FonctionnalitesProvider>
        </AuthProvider>
      </ToastProvider>
    </BrowserRouter>
  </StrictMode>,
)
