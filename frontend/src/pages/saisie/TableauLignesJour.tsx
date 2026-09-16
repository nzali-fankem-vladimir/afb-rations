import { Pencil, Trash2 } from 'lucide-react'

import { Button } from '../../components/communs/Button'
import { Tableau } from '../../components/communs/Tableau'
import type { Colonne } from '../../components/communs/Tableau'
import type { LigneResponse } from '../../api/saisieApi'
import { formatMontantFcfa } from '../../utils/formatters'

const LIBELLE_NATURE: Record<string, string> = { RATION: 'Ration', TRANSPORT: 'Transport' }
const LIBELLE_SESSION: Record<string, string> = { JOUR: 'Jour', SOIR: 'Soir' }

export interface TableauLignesJourProps {
  lignes: LigneResponse[]
  chargement: boolean
  sousTotalFcfa: number
  /** Actions desactivees plutot que masquees : l'agent comprend avant de cliquer (guide 7F.4, etape 5). */
  modifiable: boolean
  onDemanderModification: (ligne: LigneResponse) => void
  onDemanderSuppression: (ligne: LigneResponse) => void
}

export function TableauLignesJour({
  lignes,
  chargement,
  sousTotalFcfa,
  modifiable,
  onDemanderModification,
  onDemanderSuppression,
}: TableauLignesJourProps) {
  const colonnes: Colonne<LigneResponse>[] = [
    {
      cle: 'beneficiaire',
      entete: 'Bénéficiaire',
      rendu: (ligne) => `${ligne.beneficiaire.nom} ${ligne.beneficiaire.prenom}`,
    },
    { cle: 'nature', entete: 'Nature', rendu: (ligne) => LIBELLE_NATURE[ligne.nature] ?? ligne.nature },
    { cle: 'session', entete: 'Session', rendu: (ligne) => LIBELLE_SESSION[ligne.session] ?? ligne.session },
    {
      cle: 'montantApplique',
      entete: 'Montant',
      className: 'tabular-nums',
      rendu: (ligne) => (ligne.montantApplique === null ? '—' : formatMontantFcfa(ligne.montantApplique)),
    },
  ]

  return (
    <div className="flex flex-col gap-2">
      <Tableau
        colonnes={colonnes}
        donnees={lignes}
        cleLigne={(ligne) => ligne.id}
        chargement={chargement}
        messageVide="Aucune ligne saisie pour cette journée."
        actions={(ligne) => (
          <div className="flex justify-end gap-2">
            <Button
              variant="ghost"
              size="icon"
              aria-label={`Modifier la ligne de ${ligne.beneficiaire.nom} ${ligne.beneficiaire.prenom}`}
              disabled={!modifiable}
              onClick={() => onDemanderModification(ligne)}
            >
              <Pencil className="h-4 w-4" aria-hidden="true" />
            </Button>
            <Button
              variant="ghost"
              size="icon"
              aria-label={`Supprimer la ligne de ${ligne.beneficiaire.nom} ${ligne.beneficiaire.prenom}`}
              disabled={!modifiable}
              onClick={() => onDemanderSuppression(ligne)}
            >
              <Trash2 className="h-4 w-4" aria-hidden="true" />
            </Button>
          </div>
        )}
      />
      {lignes.length > 0 && (
        <p className="text-right text-sm font-medium text-neutral-900">
          Sous-total du jour : {formatMontantFcfa(sousTotalFcfa)}
        </p>
      )}
    </div>
  )
}
