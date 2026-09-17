import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'

import { AffichageErreur } from '../../components/communs/AffichageErreur'
import { Alert, AlertDescription } from '../../components/communs/Alert'
import { BadgeStatutProcessus } from '../../components/communs/Badge'
import { PageHeader } from '../../components/layout/PageHeader'
import { cn } from '../../utils/cn'
import type { ApiErrorResponse } from '../../api/apiClient'
import type { ProcessusResponse } from '../../api/processusApi'
import { consulterProcessus } from '../../api/processusApi'
import type { EtapeHistoriqueResponse } from '../../api/reportingApi'
import { consulterHistorique } from '../../api/reportingApi'
import { formatDateHeure, formatPeriode } from '../../utils/formatters'
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
  const [etapeRetour, setEtapeRetour] = useState<EtapeHistoriqueResponse | null>(null)

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

  // Auteur et date du retour (guide 7F.5, etape 6, US-11) : absents de
  // ProcessusResponse.motifRetour, qui ne porte que le texte -- un seul appel
  // supplementaire, uniquement quand l'etat est effectivement RETOURNE. Ne
  // reinitialise jamais etapeRetour a null hors de ce cas : le rendu ne le lit
  // que sous la garde `processus.statut === 'RETOURNE'`, une valeur restee en
  // memoire d'un statut anterieur n'est donc jamais affichee a tort.
  useEffect(() => {
    if (processus?.statut !== 'RETOURNE') {
      return
    }
    let annule = false
    consulterHistorique(idProcessusNumerique)
      .then((historique) => {
        if (annule) return
        const derniereEtapeRetournee = [...historique.etapes]
          .filter((etape) => etape.statutEtape === 'RETOURNEE')
          .sort((a, b) => b.ordreEtape - a.ordreEtape)[0]
        setEtapeRetour(derniereEtapeRetournee ?? null)
      })
      .catch(() => {
        // L'affichage du motif reste possible via processus.motifRetour meme si
        // l'historique est indisponible : l'auteur et la date restent alors tus,
        // mais l'agent n'est jamais prive du texte du retour lui-meme.
      })
    return () => {
      annule = true
    }
  }, [idProcessusNumerique, processus?.statut])

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

        {processus.statut === 'RETOURNE' && processus.motifRetour && (
          <Alert variant="warning">
            <AlertDescription>
              <p className="font-medium">
                Ce dossier a été retourné{etapeRetour ? ` par ${etapeRetour.loginActeur ?? etapeRetour.nomActeur ?? 'un valideur'}` : ''}
                {etapeRetour ? ` le ${formatDateHeure(etapeRetour.dateAction)}` : ''}.
              </p>
              <p>Motif : {processus.motifRetour}</p>
              <p>Corrigez vos lignes puis soumettez à nouveau depuis l'onglet « Consultation &amp; soumission ».</p>
            </AlertDescription>
          </Alert>
        )}

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
