import { useAuth } from './hooks/useAuth'

/**
 * Ecran d'accueil du module.
 *
 * Il n'y a volontairement aucun formulaire de connexion : le bouton declenche une
 * redirection vers Keycloak, qui verifie les identifiants aupres de l'annuaire.
 * L'application ne voit jamais de mot de passe (CLAUDE.md section 10).
 */
function App() {
  const { etat, utilisateur, connecter, deconnecter } = useAuth()

  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-6 bg-white px-6 text-noir-charte">
      <h1 className="text-center text-2xl font-medium">
        Module Rations et Transport Garde Armée
      </h1>

      {etat === 'CHARGEMENT' && <p className="text-sm text-noir-charte/60">Ouverture de la session…</p>}

      {etat === 'DECONNECTE' && (
        <button
          type="button"
          onClick={() => void connecter()}
          className="rounded bg-rouge-principal px-6 py-2 text-white transition-colors hover:bg-rouge-secondaire"
        >
          Se connecter
        </button>
      )}

      {etat === 'NON_HABILITE' && (
        <div className="max-w-md text-center">
          <p className="text-sm">
            Votre compte est reconnu, mais aucun profil ne vous a été ouvert dans ce module.
            Rapprochez-vous de l'administrateur.
          </p>
          <button
            type="button"
            onClick={() => void deconnecter()}
            className="mt-4 text-sm underline underline-offset-4"
          >
            Se déconnecter
          </button>
        </div>
      )}

      {etat === 'ERREUR' && (
        <div className="max-w-md text-center">
          <p className="text-sm">
            Le service d'identité est injoignable. Réessayez dans un instant ou signalez-le au support.
          </p>
          <button
            type="button"
            onClick={() => window.location.reload()}
            className="mt-4 text-sm underline underline-offset-4"
          >
            Réessayer
          </button>
        </div>
      )}

      {etat === 'CONNECTE' && utilisateur && (
        <div className="text-center">
          <p className="text-lg">
            {utilisateur.prenom} {utilisateur.nom}
          </p>
          <p className="mt-1 text-sm text-noir-charte/70">
            {utilisateur.role} — unité {utilisateur.codeUnite}
          </p>
          <button
            type="button"
            onClick={() => void deconnecter()}
            className="mt-4 text-sm underline underline-offset-4"
          >
            Se déconnecter
          </button>
        </div>
      )}
    </div>
  )
}

export default App
