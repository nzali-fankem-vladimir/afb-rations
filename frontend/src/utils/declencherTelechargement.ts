/**
 * Declenche le telechargement d'un fichier deja en memoire (Blob), sans passer
 * par une nouvelle requete reseau. Utilise pour le document PDF signe (guide
 * 7F.8) et reutilisable pour tout futur export binaire (rapports, Sprint 7F.7).
 *
 * L'URL objet est revoquee juste apres le clic simule : elle n'a besoin de
 * vivre que l'instant du declenchement, et la laisser vivre fuirait de la
 * memoire a chaque telechargement sur une session longue.
 */
export function declencherTelechargement(contenu: Blob, nomFichier: string): void {
  const url = URL.createObjectURL(contenu)
  const lien = document.createElement('a')
  lien.href = url
  lien.download = nomFichier
  document.body.appendChild(lien)
  lien.click()
  document.body.removeChild(lien)
  URL.revokeObjectURL(url)
}
