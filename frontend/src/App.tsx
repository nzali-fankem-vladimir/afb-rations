import { AppRouter } from './router/AppRouter'

/**
 * Racine du module. Toute la logique d'acces vit dans ProtectedRoute : l'ecran de
 * session (chargement, connexion, non habilite, erreur) y est rendu tant que la
 * session n'est pas ouverte. Aucun formulaire de connexion n'existe (CLAUDE.md
 * section 10).
 */
function App() {
  return <AppRouter />
}

export default App
