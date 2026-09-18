import { Badge } from '../../components/communs/Badge'
import { Modale } from '../../components/communs/Modale'
import { Tableau } from '../../components/communs/Tableau'
import type { Colonne } from '../../components/communs/Tableau'
import type { JourneeConsolidee, LigneConsolidee } from '../../api/processusApi'
import { formatDateLongue, formatMontantFcfa } from '../../utils/formatters'

const LIBELLE_NATURE: Record<string, string> = { RATION: 'Ration', TRANSPORT: 'Transport' }
const LIBELLE_SESSION: Record<string, string> = { JOUR: 'Jour', SOIR: 'Soir' }

const COLONNES: Colonne<LigneConsolidee>[] = [
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
    rendu: (ligne) => <Badge variant="neutre">{LIBELLE_NATURE[ligne.nature] ?? ligne.nature}</Badge>,
  },
  { cle: 'session', entete: 'Session', rendu: (ligne) => LIBELLE_SESSION[ligne.session] ?? ligne.session },
  {
    cle: 'montantApplique',
    entete: 'Montant',
    className: 'tabular-nums font-medium text-neutral-900',
    rendu: (ligne) => (ligne.montantApplique === null ? 'Indisponible' : formatMontantFcfa(ligne.montantApplique)),
  },
]

export interface DetailJourneeModaleProps {
  journee: JourneeConsolidee
  onFerme: () => void
}

/**
 * Detail complet d'une journee pour le valideur (Chef d'Unite / Directeur
 * Reseau) : retour utilisateur post-refonte -- le tableau "Detail par
 * journee" de ExamenProcessusPage ne portait que le nombre de lignes et le
 * sous-total, sans l'identite des beneficiaires. Cette information existe
 * deja dans EtatProcessusResponse (JourneeConsolidee.lignes, RG-06) : rien
 * n'est redemande au serveur, seule la restitution manquait.
 *
 * Lecture seule : le valideur consulte, il ne modifie rien depuis cet ecran
 * (la modification reste le geste de l'agent d'unite, RG-11).
 */
export function DetailJourneeModale({ journee, onFerme }: DetailJourneeModaleProps) {
  return (
    <Modale
      titre={`Détail du ${formatDateLongue(journee.dateJour)}`}
      largeur="max-w-2xl"
      onAnnuler={onFerme}
      contenu={
        <div className="flex flex-col gap-4">
          <Tableau
            colonnes={COLONNES}
            donnees={journee.lignes}
            cleLigne={(ligne) => ligne.id}
            messageVide="Aucune ligne saisie pour cette journée."
          />
          {journee.lignes.length > 0 && (
            <div className="flex items-baseline justify-end gap-3 rounded-lg border border-neutral-200 bg-white px-4 py-2.5">
              <span className="text-sm text-neutral-700">
                {journee.lignes.length} ligne{journee.lignes.length > 1 ? 's' : ''}
              </span>
              <span className="text-lg font-semibold tabular-nums text-neutral-900">
                {formatMontantFcfa(journee.sousTotalFcfa)}
              </span>
            </div>
          )}
        </div>
      }
    />
  )
}
