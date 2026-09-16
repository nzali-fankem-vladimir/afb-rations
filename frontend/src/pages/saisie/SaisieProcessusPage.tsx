import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'

import { AffichageErreur } from '../../components/communs/AffichageErreur'
import { BadgeStatutProcessus } from '../../components/communs/Badge'
import { PageHeader } from '../../components/layout/PageHeader'
import { cn } from '../../utils/cn'
import type { ApiErrorResponse } from '../../api/apiClient'
import type { ProcessusResponse } from '../../api/processusApi'
import { consulterProcessus } from '../../api/processusApi'
import { formatPeriode } from '../../utils/formatters'
import { estStatutModifiable } from '../../utils/statutProcessus'
import { ConsultationEtatTab } from './ConsultationEtatTab'
import { SaisieJournaliereTab } from './SaisieJournaliereTab'

type Onglet = 'SAISIE' | 'CONSULTATION'

/**
 * Ecran de saisie d'un processus (guide 7F.4, route de detail sans lien propre
 * dans la sidebar -- declaree sous le meme ProtectedRoute que /saisie, decision
 * Sprint 7F.3). Deux onglets, une seule page : l'agent bascule entre saisir et
 * verifier avant de soumettre, sans changer de dossier.
 */
export function SaisieProcessusPage() {
  const { idProcessus } = useParams<{ idProcessus: string }>()
  const idProcessusNumerique = Number(idProcessus)

  const [processus, setProcessus] = useState<ProcessusResponse | null>(null)
  const [chargement, setChargement] = useState(true)
  const [erreur, setErreur] = useState<ApiErrorResponse | null>(null)
  const [onglet, setOnglet] = useState<Onglet>('SAISIE')

  useEffect(() => {
    let annule = false
    consulterProcessus(idProcessusNumerique)
      .then((reponse) => {
        if (!annule) setProcessus(reponse)
      })
      .catch((erreurApi: ApiErrorResponse) => {
        if (!annule) setErreur(erreurApi)
      })
      .finally(() => {
        if (!annule) setChargement(false)
      })
    return () => {
      annule = true
    }
  }, [idProcessusNumerique])

  if (chargement) {
    return (
      <>
        <PageHeader surTitre="Saisie" titre="Chargement…" />
        <div className="p-8" aria-busy="true" />
      </>
    )
  }

  if (erreur || !processus) {
    return (
      <>
        <PageHeader surTitre="Saisie" titre="Processus introuvable" />
        <div className="p-8">
          {erreur && <AffichageErreur erreur={erreur} />}
        </div>
      </>
    )
  }

  const modifiable = estStatutModifiable(processus.statut)

  return (
    <>
      <PageHeader
        surTitre={`Unité ${processus.codeUnite}`}
        titre={formatPeriode(processus.dateDebut, processus.dateFin)}
      />
      <div className="flex flex-col gap-6 p-8">
        <div className="flex items-center justify-between">
          <div role="tablist" aria-label="Sections" className="flex gap-2">
            <BoutonOnglet actif={onglet === 'SAISIE'} onClick={() => setOnglet('SAISIE')}>
              Saisie journalière
            </BoutonOnglet>
            <BoutonOnglet actif={onglet === 'CONSULTATION'} onClick={() => setOnglet('CONSULTATION')}>
              Consultation &amp; soumission
            </BoutonOnglet>
          </div>
          <BadgeStatutProcessus statut={processus.statut} />
        </div>

        {onglet === 'SAISIE' ? (
          <SaisieJournaliereTab
            idProcessus={processus.idProcessus}
            dateDebut={processus.dateDebut}
            dateFin={processus.dateFin}
            modifiable={modifiable}
          />
        ) : (
          <ConsultationEtatTab
            idProcessus={processus.idProcessus}
            modifiable={modifiable}
            onSoumissionReussie={() => {
              // L'etat n'est plus modifiable une fois soumis : la relecture
              // reflete immediatement le nouveau statut (guide 7F.4, etape 6).
              void consulterProcessus(idProcessusNumerique).then(setProcessus)
            }}
          />
        )}
      </div>
    </>
  )
}

function BoutonOnglet({
  actif,
  onClick,
  children,
}: {
  actif: boolean
  onClick: () => void
  children: string
}) {
  return (
    <button
      type="button"
      role="tab"
      aria-selected={actif}
      onClick={onClick}
      className={cn(
        'rounded-full px-4 py-1.5 text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-500',
        actif ? 'bg-primary-500 text-white' : 'bg-neutral-100 text-neutral-700 hover:bg-neutral-200',
      )}
    >
      {children}
    </button>
  )
}
