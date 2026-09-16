import { Button } from './Button'

export interface MessageListeVideProps {
  message: string
  onEffacerFiltres?: () => void
}

// "il n'y a rien" et "il n'y a rien QUI CORRESPONDE" sont deux situations
// differentes. Les confondre derriere un meme "Aucun resultat." laisse croire
// que la base est vide alors qu'un filtre est actif, et n'offre aucune sortie.
// Ce composant porte le second cas, avec le moyen d'en sortir ; le premier cas
// reste une simple chaine, propre a chaque ecran.
export function MessageListeVide({ message, onEffacerFiltres }: MessageListeVideProps) {
  return (
    <span className="flex flex-col items-center gap-3">
      <span>{message}</span>
      {onEffacerFiltres && (
        <Button variant="outline" size="sm" onClick={onEffacerFiltres}>
          Effacer les filtres
        </Button>
      )}
    </span>
  )
}
