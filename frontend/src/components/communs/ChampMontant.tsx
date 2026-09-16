import { ChampTexte } from './ChampTexte'

export interface ChampMontantProps {
  id: string
  label: string
  /** Montant resolu par la grille active (RG-03). Nul tant qu'aucun montant n'a ete resolu. */
  montant: number | null
  className?: string
}

// Aucune prop `readOnly` exposee, deliberement : ce champ ne doit JAMAIS devenir
// modifiable, meme par erreur d'un appelant futur. Le montant vient toujours de
// la grille active (RG-03) -- un champ editable ici contredirait la regle cote
// interface, meme si le backend ignore de toute facon la valeur saisie.
export function ChampMontant({ id, label, montant, className }: ChampMontantProps) {
  const valeurAffichee = montant === null ? '' : `${montant.toLocaleString('fr-FR')} FCFA`

  return (
    <ChampTexte
      id={id}
      label={label}
      value={valeurAffichee}
      readOnly
      tabIndex={-1}
      placeholder="Resolu automatiquement depuis la grille active"
      className={className}
    />
  )
}
