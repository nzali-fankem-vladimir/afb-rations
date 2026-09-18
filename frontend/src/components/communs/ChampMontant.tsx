import { ChampTexte } from './ChampTexte'

export interface ChampMontantProps {
  id: string
  label: string
  /** Montant resolu par la grille active (RG-03). Nul tant qu'aucun montant n'est connu. */
  montant: number | null
  /** Texte du champ vide : ce que l'utilisateur doit faire, ou ce qui se passe. */
  placeholder?: string
  /** Precision affichee sous le champ (ex. date du tarif, caractere indicatif). */
  aide?: string
  /** Situation bloquante affichee comme une erreur de champ (ex. aucun tarif). */
  erreur?: string
  className?: string
}

// Aucune prop `readOnly` exposee, deliberement : ce champ ne doit JAMAIS devenir
// modifiable, meme par erreur d'un appelant futur. Le montant vient toujours de
// la grille active (RG-03) -- un champ editable ici contredirait la regle cote
// interface, meme si le backend ignore de toute facon la valeur saisie.
export function ChampMontant({
  id,
  label,
  montant,
  placeholder = 'Résolu automatiquement depuis la grille active',
  aide,
  erreur,
  className,
}: ChampMontantProps) {
  const valeurAffichee = montant === null ? '' : `${montant.toLocaleString('fr-FR')} FCFA`

  return (
    <div className={className}>
      <ChampTexte
        id={id}
        label={label}
        value={valeurAffichee}
        readOnly
        tabIndex={-1}
        placeholder={placeholder}
        erreur={erreur}
        // Passe seulement s'il y a une aide : un aria-describedby explicite a
        // undefined ecraserait celui que ChampTexte pose sur son erreur.
        {...(aide && !erreur ? { 'aria-describedby': `${id}-aide` } : {})}
        className={montant !== null ? '[&_input]:font-semibold [&_input]:tabular-nums' : undefined}
      />
      {aide && !erreur && (
        <p id={`${id}-aide`} className="mt-1 text-xs text-neutral-600">
          {aide}
        </p>
      )}
    </div>
  )
}
