import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'

import { AffichageErreur } from '../../components/communs/AffichageErreur'
import { Alert, AlertDescription } from '../../components/communs/Alert'
import { Badge, BadgeStatutProcessus } from '../../components/communs/Badge'
import { Button } from '../../components/communs/Button'
import { Card, CardContent, CardHeader, CardTitle } from '../../components/communs/Card'
import { Tableau } from '../../components/communs/Tableau'
import type { Colonne } from '../../components/communs/Tableau'
import { PageHeader } from '../../components/layout/PageHeader'
import type { ApiErrorResponse } from '../../api/apiClient'
import type {
  EtatProcessusResponse,
  JourneeConsolidee,
  ProcessusResponse,
  RetourResponse,
  ValidationResponse,
} from '../../api/processusApi'
import { consulterEtatProcessus, consulterProcessus, validerProcessus } from '../../api/processusApi'
import type { EtapeHistoriqueResponse, HistoriqueResponse } from '../../api/reportingApi'
import { consulterHistorique } from '../../api/reportingApi'
import type { NomEtapeEnum } from '../../types/enums'
import { formatDateHeure, formatDateJJMMAAAA, formatMontantFcfa, formatPeriode } from '../../utils/formatters'
import { statutAttendPourRole } from '../../utils/statutProcessus'
import { useAuth } from '../../hooks/useAuth'
import { RetourModale } from './RetourModale'
import { ResultatValidation } from './ResultatValidation'

const COLONNES_JOURNEES: Colonne<JourneeConsolidee>[] = [
  { cle: 'dateJour', entete: 'Journée', rendu: (journee) => formatDateJJMMAAAA(journee.dateJour) },
  { cle: 'nombreLignes', entete: 'Lignes' },
  {
    cle: 'sousTotalFcfa',
    entete: 'Sous-total',
    className: 'tabular-nums',
    rendu: (journee) => formatMontantFcfa(journee.sousTotalFcfa),
  },
]

const LIBELLE_ETAPE: Record<NomEtapeEnum, string> = {
  SOUMISSION_AGENT: 'Soumission',
  VALIDATION_DA: "Validation — Chef d'Unité",
  VALIDATION_DR: 'Validation — Directeur Réseau',
}

function libelleActeur(etape: EtapeHistoriqueResponse): string {
  return etape.loginActeur ?? etape.nomActeur ?? (etape.idActeur !== null ? `acteur #${etape.idActeur}` : 'inconnu')
}

/**
 * Ecran d'examen d'un dossier, commun aux deux niveaux de validation (guide
 * 7F.5, etapes 3 et 4) : l'endpoint de validation est unique, le niveau se
 * lisant sur le statut du dossier -- l'ecran n'a donc pas a distinguer les
 * deux roles dans son fonctionnement.
 *
 * Aucun lien vers le PDF signe (manque backend n°3, tranche a l'ouverture de
 * session : reporte, voir docs/points-en-attente.md). Les signatures deja
 * apposees restent visibles via l'historique.
 */
export function ExamenProcessusPage() {
  const { idProcessus } = useParams<{ idProcessus: string }>()
  const id = Number(idProcessus)
  const { role } = useAuth()

  const [processus, setProcessus] = useState<ProcessusResponse | null>(null)
  const [etat, setEtat] = useState<EtatProcessusResponse | null>(null)
  const [historique, setHistorique] = useState<HistoriqueResponse | null>(null)
  const [chargement, setChargement] = useState(true)
  const [erreurChargement, setErreurChargement] = useState<ApiErrorResponse | null>(null)

  const [validationEnCours, setValidationEnCours] = useState(false)
  const [erreurValidation, setErreurValidation] = useState<ApiErrorResponse | null>(null)
  const [resultatValidation, setResultatValidation] = useState<ValidationResponse | null>(null)

  const [retourModaleOuverte, setRetourModaleOuverte] = useState(false)
  const [resultatRetour, setResultatRetour] = useState<RetourResponse | null>(null)

  useEffect(() => {
    let annule = false
    Promise.all([consulterProcessus(id), consulterEtatProcessus(id), consulterHistorique(id)])
      .then(([reponseProcessus, reponseEtat, reponseHistorique]) => {
        if (annule) return
        setProcessus(reponseProcessus)
        setEtat(reponseEtat)
        setHistorique(reponseHistorique)
        setErreurChargement(null)
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
  }, [id])

  const valider = async () => {
    setErreurValidation(null)
    setValidationEnCours(true)
    try {
      const reponse = await validerProcessus(id)
      setResultatValidation(reponse)
    } catch (erreurApi) {
      setErreurValidation(erreurApi as ApiErrorResponse)
    } finally {
      setValidationEnCours(false)
    }
  }

  if (chargement) {
    return (
      <>
        <PageHeader surTitre="Validation" titre="Chargement…" />
        <div className="p-8" aria-busy="true" />
      </>
    )
  }

  if (erreurChargement || !processus || !etat) {
    return (
      <>
        <PageHeader surTitre="Validation" titre="Dossier introuvable" />
        <div className="p-8">{erreurChargement && <AffichageErreur erreur={erreurChargement} />}</div>
      </>
    )
  }

  const etapeSoumission = historique?.etapes.find((etape) => etape.nomEtape === 'SOUMISSION_AGENT')
  const etapesSignees = historique?.etapes.filter((etape) => etape.signee) ?? []

  const decisionDejaPrise = resultatValidation !== null || resultatRetour !== null
  // Griser plutot que laisser echouer (doctrine du Sprint 7F.4) : un Chef
  // d'Unite qui ouvrirait par URL directe un dossier EN_ATTENTE_DR ne verrait
  // pas de boutons -- le backend refuserait de toute facon (403 ACCES_REFUSE,
  // RG-07), mais autant eviter l'aller-retour pour rien.
  const peutDecider = statutAttendPourRole(processus.statut, role) && !decisionDejaPrise

  return (
    <>
      <PageHeader surTitre={`Unité ${processus.codeUnite}`} titre={formatPeriode(processus.dateDebut, processus.dateFin)} />
      <div className="flex flex-col gap-6 p-8">
        <div className="flex items-center gap-3">
          <BadgeStatutProcessus statut={processus.statut} />
          {processus.typeProcessus === 'COMPLEMENTAIRE' && <Badge variant="attente">Complémentaire</Badge>}
        </div>

        {processus.typeProcessus === 'COMPLEMENTAIRE' && (
          <Alert variant="warning">
            <AlertDescription>
              <p className="font-medium">État complémentaire (régularisation)</p>
              <p>
                Il monte toujours au Directeur Réseau, quel que soit son montant : le seuil
                d'aiguillage n'est pas lu pour ce type d'état (RG-08, Sprint 6bis.1).
                {processus.motifOuverture && <> Motif d'ouverture : {processus.motifOuverture}</>}
              </p>
            </AlertDescription>
          </Alert>
        )}

        {etapeSoumission && (
          <p className="text-sm text-neutral-600">
            Soumis le {formatDateHeure(etapeSoumission.dateAction)} par {libelleActeur(etapeSoumission)}.
          </p>
        )}

        {processus.statut === 'RETOURNE' && processus.motifRetour && (
          <Alert variant="warning">
            <AlertDescription>
              <p className="font-medium">Ce dossier a été retourné à l'agent d'unité.</p>
              <p>Motif : {processus.motifRetour}</p>
            </AlertDescription>
          </Alert>
        )}

        <Tableau
          colonnes={COLONNES_JOURNEES}
          donnees={etat.journees}
          cleLigne={(journee) => journee.idFicheJournaliere}
          messageVide="Aucune journée saisie."
        />

        <div className="flex items-center justify-between rounded-lg border border-neutral-200 bg-white p-4">
          <span className="text-sm font-medium text-neutral-900">Total de la période</span>
          <span className="text-lg font-semibold tabular-nums text-neutral-900">
            {etat.montantTotalFcfa === null ? '—' : formatMontantFcfa(etat.montantTotalFcfa)}
          </span>
        </div>

        <Card>
          <CardHeader>
            <CardTitle>Signatures apposées</CardTitle>
          </CardHeader>
          <CardContent>
            {etapesSignees.length === 0 ? (
              <p className="text-sm text-neutral-600">Aucune signature apposée pour le moment.</p>
            ) : (
              <ul className="flex flex-col gap-2">
                {etapesSignees.map((etape) => (
                  <li
                    key={etape.ordreEtape}
                    className="flex items-center justify-between border-b border-neutral-100 pb-2 text-sm last:border-0 last:pb-0"
                  >
                    <span className="text-neutral-900">
                      {LIBELLE_ETAPE[etape.nomEtape] ?? etape.nomEtape} — {libelleActeur(etape)}
                    </span>
                    <span className="text-neutral-600">{formatDateHeure(etape.dateAction)}</span>
                  </li>
                ))}
              </ul>
            )}
          </CardContent>
        </Card>

        {resultatValidation && <ResultatValidation resultat={resultatValidation} />}
        {erreurValidation && <AffichageErreur erreur={erreurValidation} />}

        {resultatRetour && (
          <Alert variant="warning">
            <AlertDescription>
              <p className="font-medium">
                Dossier retourné à l'agent d'unité
                {resultatRetour.niveauOrigine === 'DIRECTEUR_RESEAU'
                  ? ' — directement, pas au chef d\'unité (RG-11)'
                  : ''}
                .
              </p>
              <p>Motif transmis à l'agent : {resultatRetour.etape.motifRetour}</p>
            </AlertDescription>
          </Alert>
        )}

        {peutDecider && (
          <div className="flex justify-end gap-3">
            <Button variant="outline" onClick={() => setRetourModaleOuverte(true)} disabled={validationEnCours}>
              Retourner
            </Button>
            <Button onClick={valider} isLoading={validationEnCours}>
              Valider
            </Button>
          </div>
        )}
      </div>

      {retourModaleOuverte && (
        <RetourModale
          idProcessus={id}
          onFerme={() => setRetourModaleOuverte(false)}
          onSucces={(reponse) => {
            setResultatRetour(reponse)
            setRetourModaleOuverte(false)
          }}
        />
      )}
    </>
  )
}
