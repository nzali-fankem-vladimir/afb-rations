import { useEffect, useState } from 'react'
import { CheckCircle2, Send } from 'lucide-react'

import { AffichageErreur } from '../../components/communs/AffichageErreur'
import { Alert, AlertDescription } from '../../components/communs/Alert'
import { Button } from '../../components/communs/Button'
import { Modale } from '../../components/communs/Modale'
import { Tableau } from '../../components/communs/Tableau'
import type { Colonne } from '../../components/communs/Tableau'
import type { ApiErrorResponse } from '../../api/apiClient'
import type { EtatProcessusResponse, JourneeConsolidee, SoumissionResponse } from '../../api/processusApi'
import { consulterEtatProcessus, soumettreProcessus } from '../../api/processusApi'
import { formatDateJJMMAAAA, formatMontantFcfa } from '../../utils/formatters'

export interface ConsultationEtatTabProps {
  idProcessus: number
  /** EN_COURS_SAISIE ou RETOURNE : sinon la soumission est deja acquise ou hors de portee. */
  modifiable: boolean
  /** Le processus parent doit relire son statut : la clotures depend du montant, pas seulement du geste. */
  onSoumissionReussie: () => void
}

const COLONNES: Colonne<JourneeConsolidee>[] = [
  { cle: 'dateJour', entete: 'Journée', rendu: (journee) => formatDateJJMMAAAA(journee.dateJour) },
  { cle: 'nombreLignes', entete: 'Lignes' },
  {
    cle: 'sousTotalFcfa',
    entete: 'Sous-total',
    className: 'tabular-nums',
    rendu: (journee) => formatMontantFcfa(journee.sousTotalFcfa),
  },
]

/**
 * Consultation de l'etat consolide de la periode, et soumission (guide 7F.4,
 * etape 6). Le detail par journee et le total viennent tels quels de
 * GET /processus/{id}/etat -- aucune somme n'est refaite ici (RG-06, un seul
 * chemin de calcul, cote service Saisie).
 */
export function ConsultationEtatTab({ idProcessus, modifiable, onSoumissionReussie }: ConsultationEtatTabProps) {
  const [etat, setEtat] = useState<EtatProcessusResponse | null>(null)
  const [chargement, setChargement] = useState(true)
  const [erreurChargement, setErreurChargement] = useState<ApiErrorResponse | null>(null)
  const [erreurSoumission, setErreurSoumission] = useState<ApiErrorResponse | null>(null)
  const [soumissionReussie, setSoumissionReussie] = useState<SoumissionResponse | null>(null)
  const [confirmationOuverte, setConfirmationOuverte] = useState(false)

  useEffect(() => {
    let annule = false
    consulterEtatProcessus(idProcessus)
      .then((reponse) => {
        if (!annule) setEtat(reponse)
      })
      .catch((erreurApi: ApiErrorResponse) => {
        if (!annule) setErreurChargement(erreurApi)
      })
      .finally(() => {
        if (!annule) setChargement(false)
      })
    return () => {
      annule = true
    }
  }, [idProcessus])

  // Boucle par Modale (guide implicite : desactiver une action impossible vaut
  // mieux que la laisser echouer -- ici, un clic de trop sur "Soumettre").
  // L'etat de chargement du bouton de confirmation est gere par Modale
  // elle-meme (prop `enCours` interne), rien a dupliquer ici.
  const soumettre = async () => {
    setErreurSoumission(null)
    try {
      const reponse = await soumettreProcessus(idProcessus)
      setSoumissionReussie(reponse)
      setConfirmationOuverte(false)
      onSoumissionReussie()
      // L'ecriture est fermee cote Saisie apres soumission : le total recalcule
      // ne peut plus differer du total desormais porte par le processus.
      const etatMisAJour = await consulterEtatProcessus(idProcessus)
      setEtat(etatMisAJour)
    } catch (erreurApi) {
      setErreurSoumission(erreurApi as ApiErrorResponse)
    }
  }

  if (chargement) {
    return <div aria-busy="true" className="p-4 text-sm text-neutral-600">Chargement de l'état…</div>
  }

  if (erreurChargement || !etat) {
    return erreurChargement ? <AffichageErreur erreur={erreurChargement} /> : null
  }

  return (
    <div className="flex flex-col gap-6">
      {soumissionReussie && (
        <Alert variant="default">
          <CheckCircle2 className="h-4 w-4" aria-hidden="true" />
          <AlertDescription>
            État soumis avec succès. Le document a été généré et signé
            {soumissionReussie.pieceJointe.nombreSignatures > 0 ? ` (${soumissionReussie.pieceJointe.nombreSignatures} signature(s))` : ''}.
          </AlertDescription>
        </Alert>
      )}

      <Tableau
        colonnes={COLONNES}
        donnees={etat.journees}
        cleLigne={(journee) => journee.idFicheJournaliere}
        messageVide="Aucune journée saisie pour le moment."
      />

      <div className="flex items-center justify-between rounded-lg border border-neutral-200 bg-white p-4">
        <span className="text-sm font-medium text-neutral-900">Total de la période</span>
        <span className="text-lg font-semibold tabular-nums text-neutral-900">
          {etat.montantTotalFcfa === null ? '—' : formatMontantFcfa(etat.montantTotalFcfa)}
        </span>
      </div>

      <div className="flex justify-end">
        <Button
          onClick={() => {
            setErreurSoumission(null)
            setConfirmationOuverte(true)
          }}
          disabled={!modifiable || soumissionReussie !== null}
        >
          <Send className="h-4 w-4" aria-hidden="true" />
          Soumettre l'état
        </Button>
      </div>

      {confirmationOuverte && (
        <Modale
          titre="Confirmer la soumission"
          libelleConfirmer="Soumettre"
          variantConfirmer="default"
          onAnnuler={() => setConfirmationOuverte(false)}
          onConfirmer={soumettre}
          contenu={
            <div className="flex flex-col gap-4">
              <p className="text-sm text-neutral-700">
                Le total de la période ({etat.montantTotalFcfa === null ? '—' : formatMontantFcfa(etat.montantTotalFcfa)})
                sera transmis pour validation. Une fois soumis, l'état n'est plus modifiable — seul un retour du
                chef d'unité ou du directeur réseau permettrait de le corriger.
              </p>
              {erreurSoumission && <AffichageErreur erreur={erreurSoumission} />}
            </div>
          }
        />
      )}
    </div>
  )
}
