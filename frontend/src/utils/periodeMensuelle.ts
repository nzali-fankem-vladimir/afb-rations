/**
 * Choix d'une période par mois et année, plutôt que par deux dates exactes
 * (retour utilisateur, rattrapage post-7F.7 : exiger de connaître les bornes
 * précises d'une période hebdomadaire oblige à les retenir par cœur, alors
 * qu'un mois se retrouve facilement parmi ses quatre semaines).
 *
 * Traduit en bornes ISO couvrant tout le mois, consommées par les filtres
 * `dateDebut`/`dateFin` déjà exposés par le backend -- aucun paramètre
 * d'API n'est ajouté. Le filtre serveur est un CHEVAUCHEMENT, pas une
 * égalité (vérifié dans `ProcessusSpecifications.java`, service Workflow) :
 * un état à cheval sur deux mois apparaît dans les deux, jamais invisible
 * dans les deux -- la dégradation va toujours du bon côté.
 *
 * Partagé entre l'écran de suivi (`SuiviPage`) et le choix de l'état
 * d'origine à régulariser (`OuvertureComplementairePage`), qui posent tous
 * deux le même problème : choisir une période parmi celles qui existent,
 * pas la ressaisir.
 */

export const OPTIONS_MOIS = [
  { valeur: '1', libelle: 'Janvier' },
  { valeur: '2', libelle: 'Février' },
  { valeur: '3', libelle: 'Mars' },
  { valeur: '4', libelle: 'Avril' },
  { valeur: '5', libelle: 'Mai' },
  { valeur: '6', libelle: 'Juin' },
  { valeur: '7', libelle: 'Juillet' },
  { valeur: '8', libelle: 'Août' },
  { valeur: '9', libelle: 'Septembre' },
  { valeur: '10', libelle: 'Octobre' },
  { valeur: '11', libelle: 'Novembre' },
  { valeur: '12', libelle: 'Décembre' },
]

/**
 * Les trois dernieres annees, la plus recente en tete.
 *
 * ATTENTION -- fonction, pas constante figee au chargement du module : elle
 * doit rendre 2027 en 2027 sans qu'aucun fichier ne soit modifie. Un tableau
 * calcule une seule fois au chargement resterait bloque sur l'annee de ce
 * chargement si l'onglet restait ouvert sans jamais recharger la page a
 * travers un changement d'annee -- rare, mais un poste de controle interne
 * qui reste ouvert des jours durant n'est pas un cas a exclure.
 */
export function optionsAnnee(): { valeur: string; libelle: string }[] {
  const anneeCourante = new Date().getFullYear()
  return Array.from({ length: 3 }, (_, index) => {
    const annee = anneeCourante - index
    return { valeur: String(annee), libelle: String(annee) }
  })
}

export interface MoisAnnee {
  /** 1 à 12. */
  mois: string
  annee: string
}

export function moisEtAnneeCourants(): MoisAnnee {
  const maintenant = new Date()
  return { mois: String(maintenant.getMonth() + 1), annee: String(maintenant.getFullYear()) }
}

export function moisEtAnneePrecedents(): MoisAnnee {
  const maintenant = new Date()
  const moisCourant = maintenant.getMonth() + 1
  const anneeCourante = maintenant.getFullYear()
  return moisCourant === 1
    ? { mois: '12', annee: String(anneeCourante - 1) }
    : { mois: String(moisCourant - 1), annee: String(anneeCourante) }
}

/**
 * Bornes ISO du mois donné (1-indexé), toutes deux incluses. Calcul en
 * arithmétique UTC pour ne jamais glisser d'un jour (même précaution
 * qu'`enumererJours`, ce même fichier utilitaire).
 */
export function bornesDuMois(annee: number, mois: number): { dateDebut: string; dateFin: string } {
  const dateDebut = new Date(Date.UTC(annee, mois - 1, 1)).toISOString().slice(0, 10)
  const dateFin = new Date(Date.UTC(annee, mois, 0)).toISOString().slice(0, 10)
  return { dateDebut, dateFin }
}
