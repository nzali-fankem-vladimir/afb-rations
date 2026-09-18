import { Badge } from '../../components/communs/Badge'
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

/**
 * Lignes de la journee (guide 7F.4, etape 3 ; colonnes enrichies au Sprint
 * 7F.6, proposition n°5).
 *
 * Le compte courant et le code agence sont affiches : ce sont eux qui decident
 * qui est paye et sur quelle agence, et l'agent doit pouvoir les relire sans
 * ouvrir la modification. CODE AGENCE n'est pas CODE UNITE (CLAUDE.md §4) :
 * c'est l'agence de domiciliation du compte du beneficiaire.
 */
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
      className: 'font-medium text-neutral-900',
      rendu: (ligne) => `${ligne.beneficiaire.nom} ${ligne.beneficiaire.prenom}`,
    },
    {
      cle: 'numCompteCourant',
      entete: 'N° compte',
      className: 'tabular-nums',
      rendu: (ligne) => ligne.beneficiaire.numCompteCourant,
    },
    {
      cle: 'codeAgence',
      entete: 'Agence',
      className: 'tabular-nums',
      rendu: (ligne) => ligne.beneficiaire.codeAgence,
    },
    {
      cle: 'nature',
      entete: 'Nature',
      rendu: (ligne) => (
        <Badge variant="neutre">{LIBELLE_NATURE[ligne.nature] ?? ligne.nature}</Badge>
      ),
    },
    { cle: 'session', entete: 'Session', rendu: (ligne) => LIBELLE_SESSION[ligne.session] ?? ligne.session },
    {
      cle: 'montantApplique',
      entete: 'Montant',
      className: 'tabular-nums font-medium text-neutral-900',
      rendu: (ligne) => (ligne.montantApplique === null ? 'Indisponible' : formatMontantFcfa(ligne.montantApplique)),
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
          <div className="flex justify-end gap-1">
            <Button
              variant="ghost"
              size="sm"
              disabled={!modifiable}
              onClick={() => onDemanderModification(ligne)}
            >
              Modifier
            </Button>
            <Button
              variant="ghost"
              size="sm"
              className="text-primary-700 hover:bg-primary-50 hover:text-primary-700"
              disabled={!modifiable}
              onClick={() => onDemanderSuppression(ligne)}
            >
              Supprimer
            </Button>
          </div>
        )}
      />
      {lignes.length > 0 && (
        <div className="flex items-baseline justify-end gap-3 rounded-lg border border-neutral-200 bg-white px-4 py-2.5">
          <span className="text-sm text-neutral-700">
            Sous-total du jour · {lignes.length} ligne{lignes.length > 1 ? 's' : ''}
          </span>
          <span className="text-lg font-semibold tabular-nums text-neutral-900">
            {formatMontantFcfa(sousTotalFcfa)}
          </span>
        </div>
      )}
    </div>
  )
}
