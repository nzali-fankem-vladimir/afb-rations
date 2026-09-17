/** Un montant FCFA, jamais un flottant, jamais recalcule (CLAUDE.md section 4 et 15). */
export function formatMontantFcfa(montant: number): string {
  return `${montant.toLocaleString('fr-FR')} FCFA`
}

/** Une date ISO (AAAA-MM-JJ) affichee au format francais JJ/MM/AAAA. */
export function formatDateJJMMAAAA(dateIso: string): string {
  const [annee, mois, jour] = dateIso.split('-')
  return `${jour}/${mois}/${annee}`
}

/**
 * Une periode par ses DEUX bornes, jamais sous forme de mois : une semaine a
 * cheval sur deux mois n'a pas de mois (Maille 1, point M-04). `dateFin` est
 * incluse -- l'affichage ne l'ajoute ni ne la retranche.
 */
export function formatPeriode(dateDebut: string, dateFin: string): string {
  return `du ${formatDateJJMMAAAA(dateDebut)} au ${formatDateJJMMAAAA(dateFin)}`
}

/** Jour de semaine abrege ("lun.", "mar.", ...), pour le selecteur de jour. */
export function formatJourSemaineCourt(dateIso: string): string {
  return new Date(`${dateIso}T00:00:00`).toLocaleDateString('fr-FR', { weekday: 'short' })
}

/**
 * Une date-heure ISO (LocalDateTime backend, ex. "2026-09-16T10:23:45") affichee
 * au format francais JJ/MM/AAAA HH:mm. Utilise pour les signatures et les etapes
 * de l'historique (guide 7F.5, etape 3) -- jamais la seule date, l'heure
 * distingue deux passages du meme jour au meme niveau apres un retour.
 */
export function formatDateHeure(dateTimeIso: string): string {
  return new Date(dateTimeIso).toLocaleString('fr-FR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

/**
 * Toutes les dates entre dateDebut et dateFin, BORNES INCLUSES (Maille 1) :
 * dateFin est le dernier jour de la periode, jamais le premier de la suivante.
 * Utilise pour borner le calendrier de saisie a la periode reelle de l'etat
 * (guide 7F.4, etape 3) -- jamais un calendrier libre.
 */
const UN_JOUR_MS = 24 * 60 * 60 * 1000

export function enumererJours(dateDebut: string, dateFin: string): string[] {
  // Arithmetique entierement en UTC (Date.UTC, pas new Date(iso)) : un
  // aller-retour par le fuseau local pourrait decaler la date d'un jour au
  // moment de la reserialiser en ISO, ce qui bornerait le calendrier de saisie
  // sur une journee fausse.
  const [anneeDebut, moisDebut, jourDebut] = dateDebut.split('-').map(Number)
  const [anneeFin, moisFin, jourFin] = dateFin.split('-').map(Number)
  const debutMs = Date.UTC(anneeDebut, moisDebut - 1, jourDebut)
  const finMs = Date.UTC(anneeFin, moisFin - 1, jourFin)

  const jours: string[] = []
  for (let curseurMs = debutMs; curseurMs <= finMs; curseurMs += UN_JOUR_MS) {
    jours.push(new Date(curseurMs).toISOString().slice(0, 10))
  }
  return jours
}
