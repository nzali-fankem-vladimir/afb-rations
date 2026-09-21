/**
 * Marqueur d'un changement de session en cours, connexion ou deconnexion.
 *
 * <p><b>Pourquoi il existe.</b> Se connecter et se deconnecter font toutes deux
 * QUITTER la page : le navigateur part chez le fournisseur d'identite, puis
 * revient. Une notification affichee avant le depart disparaitrait avec la page.
 * Elle doit donc s'afficher au RETOUR, et il faut alors savoir que ce retour
 * suit un geste volontaire.
 *
 * <p>Sans ce marqueur, la notification de connexion s'afficherait aussi a chaque
 * rechargement de la page : {@code check-sso} restaure alors une session
 * existante sans que l'utilisateur ait rien fait, et une « connexion reussie »
 * a chaque F5 serait du bruit, pas une information.
 *
 * <p><b>Ce que ce n'est pas.</b> Un jeton. Le marqueur ne porte qu'un mot et une
 * heure : la regle du module, « le jeton ne va jamais dans un stockage
 * persistant » (CLAUDE.md section 10), est intacte. Il est pose dans
 * {@code sessionStorage} (propre a l'onglet, efface a sa fermeture), valable deux
 * minutes : un depart qui n'aboutit pas ne doit pas declencher une notification
 * perimee au prochain chargement.
 *
 * <p>Stockage indisponible (navigation privee, politique du navigateur) : aucune
 * notification, jamais une erreur. La connexion elle-meme n'en depend pas.
 */

export type EvenementSession = 'CONNEXION' | 'DECONNEXION'

const CLE = 'rations-evenement-session'
const VALIDITE_MS = 2 * 60 * 1000

interface Marqueur {
  evenement: EvenementSession
  pose: number
}

/** A appeler juste avant la redirection vers le fournisseur d'identite. */
export function poserMarqueur(evenement: EvenementSession): void {
  try {
    const marqueur: Marqueur = { evenement, pose: Date.now() }
    sessionStorage.setItem(CLE, JSON.stringify(marqueur))
  } catch {
    // Pas de notification, mais la connexion ou la deconnexion se poursuit.
  }
}

/**
 * Rend vrai UNE SEULE fois, au retour d'un geste du type attendu.
 *
 * <p>Le marqueur est retire dans tous les cas ou il est lu, y compris perime :
 * une seconde lecture (double montage des effets en mode strict de React) ne
 * doit pas afficher la notification deux fois.
 */
export function consommerMarqueur(attendu: EvenementSession): boolean {
  try {
    const brut = sessionStorage.getItem(CLE)
    if (brut === null) return false
    const marqueur = JSON.parse(brut) as Partial<Marqueur>
    if (marqueur.evenement !== attendu) return false
    sessionStorage.removeItem(CLE)
    return typeof marqueur.pose === 'number' && Date.now() - marqueur.pose < VALIDITE_MS
  } catch {
    return false
  }
}
