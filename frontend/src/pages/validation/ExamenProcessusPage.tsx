import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { Eye } from 'lucide-react'

import { AffichageErreur } from '../../components/communs/AffichageErreur'
import { Alert, AlertDescription } from '../../components/communs/Alert'
import { BadgeStatutProcessus } from '../../components/communs/Badge'
import { Button } from '../../components/communs/Button'
import { Card, CardContent, CardHeader, CardTitle } from '../../components/communs/Card'
import { Modale } from '../../components/communs/Modale'
import { Recapitulatif } from '../../components/communs/Recapitulatif'
import { StatTile } from '../../components/communs/StatTile'
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
import {
  consulterEtatProcessus,
  consulterProcessus,
  telechargerDocument,
  validerProcessus,
} from '../../api/processusApi'
import type { EtapeHistoriqueResponse, HistoriqueResponse } from '../../api/reportingApi'
import { consulterHistorique } from '../../api/reportingApi'
import type { NomEtapeEnum } from '../../types/enums'
import { declencherTelechargement } from '../../utils/declencherTelechargement'
import { enumererJours, formatDateHeure, formatDateJJMMAAAA, formatMontantFcfa, formatPeriode } from '../../utils/formatters'
import { statutAttendPourRole } from '../../utils/statutProcessus'
import { useAuth } from '../../hooks/useAuth'
import { useToast } from '../../hooks/useToast'
import { DetailJourneeModale } from '../../components/communs/DetailJourneeModale'
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
  VALIDATION_DA: "Validation · Chef d'Unité",
  VALIDATION_DR: 'Validation · Directeur Réseau',
}

/**
 * Ce qui suivra la validation, deduit du STATUT du dossier (RG-07) -- jamais du
 * seuil, que le frontend n'a pas a connaitre (ParametreController, Sprint 6bis.1).
 * Au premier niveau d'un etat NORMAL, l'issue depend du seuil : on le dit sans
 * l'afficher. La comparaison reelle reste unique, cote AiguillageService.
 */
function suiteDeLaValidation(processus: ProcessusResponse): string {
  if (processus.statut === 'EN_ATTENTE_DR') {
    return 'Clôture et envoi à la comptabilité'
  }
  if (processus.typeProcessus === 'COMPLEMENTAIRE') {
    return 'Transmission au Directeur Réseau'
  }
  return "Clôture, ou transmission au Directeur Réseau selon le seuil d'aiguillage"
}

function libelleActeur(etape: EtapeHistoriqueResponse): string {
  return etape.loginActeur ?? etape.nomActeur ?? (etape.idActeur !== null ? `acteur #${etape.idActeur}` : 'inconnu')
}

interface EtapeAffichee {
  cle: NomEtapeEnum
  libelle: string
  detail: string
  fait: boolean
}

/**
 * Le parcours de signature en entier, étapes à venir comprises (retour
 * utilisateur, maquette de refonte du Sprint 7F.6) : "on voit où en est le
 * dossier et ce qui suit", pas seulement ce qui est déjà signé.
 *
 * Le niveau Directeur Réseau reste CONDITIONNEL pour un état NORMAL (RG-08,
 * comparaison au seuil que ce frontend n'a jamais à refaire, AiguillageService
 * est seul juge) : avant que le Chef d'Unité n'ait tranché, son issue n'est
 * pas connue, et l'écran le dit sans l'inventer. Pour un état COMPLEMENTAIRE,
 * ce niveau est TOUJOURS requis, quel que soit le montant (RG-08, Sprint 6bis.1).
 */
function construireEtapesAffichees(
  processus: ProcessusResponse,
  historique: HistoriqueResponse | null,
): EtapeAffichee[] {
  const etapes = historique?.etapes ?? []
  const soumission = etapes.find((etape) => etape.nomEtape === 'SOUMISSION_AGENT')
  const validationDa = etapes.find((etape) => etape.nomEtape === 'VALIDATION_DA' && etape.statutEtape === 'VALIDEE')
  const validationDr = etapes.find((etape) => etape.nomEtape === 'VALIDATION_DR' && etape.statutEtape === 'VALIDEE')

  const detailFait = (etape: EtapeHistoriqueResponse) => `${libelleActeur(etape)} · ${formatDateHeure(etape.dateAction)}`

  const etapeDr: EtapeAffichee = validationDr
    ? { cle: 'VALIDATION_DR', libelle: LIBELLE_ETAPE.VALIDATION_DR, detail: detailFait(validationDr), fait: true }
    : processus.statut === 'CLOTURE'
      ? { cle: 'VALIDATION_DR', libelle: LIBELLE_ETAPE.VALIDATION_DR, detail: 'sans objet, clôturé sous le seuil', fait: false }
      : processus.statut === 'EN_ATTENTE_DR'
        ? { cle: 'VALIDATION_DR', libelle: LIBELLE_ETAPE.VALIDATION_DR, detail: 'en attente', fait: false }
        : {
            cle: 'VALIDATION_DR',
            libelle: LIBELLE_ETAPE.VALIDATION_DR,
            detail:
              processus.typeProcessus === 'COMPLEMENTAIRE'
                ? 'toujours requise (état complémentaire, RG-08)'
                : 'selon le montant, décidé par le Chef d\'Unité',
            fait: false,
          }

  return [
    {
      cle: 'SOUMISSION_AGENT',
      libelle: LIBELLE_ETAPE.SOUMISSION_AGENT,
      detail: soumission ? detailFait(soumission) : 'en attente',
      fait: Boolean(soumission),
    },
    {
      cle: 'VALIDATION_DA',
      libelle: LIBELLE_ETAPE.VALIDATION_DA,
      detail: validationDa ? detailFait(validationDa) : 'en attente',
      fait: Boolean(validationDa),
    },
    etapeDr,
  ]
}

/**
 * Ecran d'examen d'un dossier, commun aux deux niveaux de validation (guide
 * 7F.5, etapes 3 et 4) : l'endpoint de validation est unique, le niveau se
 * lisant sur le statut du dossier -- l'ecran n'a donc pas a distinguer les
 * deux roles dans son fonctionnement.
 *
 * Le PDF signe est telechargeable depuis cet ecran (guide 7F.8) : un chef
 * d'unite ou un directeur reseau peut ainsi relire le document avant de le
 * viser une seconde fois, point ferme au Sprint 7F.5 (voir
 * docs/points-en-attente.md, section « PDF signe »). Les signatures deja
 * apposees restent en outre visibles via l'historique.
 */
export function ExamenProcessusPage() {
  const { idProcessus } = useParams<{ idProcessus: string }>()
  const id = Number(idProcessus)
  const { role } = useAuth()
  const { succes } = useToast()

  const [processus, setProcessus] = useState<ProcessusResponse | null>(null)
  const [etat, setEtat] = useState<EtatProcessusResponse | null>(null)
  const [historique, setHistorique] = useState<HistoriqueResponse | null>(null)
  const [chargement, setChargement] = useState(true)
  const [erreurChargement, setErreurChargement] = useState<ApiErrorResponse | null>(null)

  const [validationEnCours, setValidationEnCours] = useState(false)
  const [erreurValidation, setErreurValidation] = useState<ApiErrorResponse | null>(null)
  const [resultatValidation, setResultatValidation] = useState<ValidationResponse | null>(null)

  const [retourModaleOuverte, setRetourModaleOuverte] = useState(false)
  const [confirmationOuverte, setConfirmationOuverte] = useState(false)
  const [resultatRetour, setResultatRetour] = useState<RetourResponse | null>(null)

  // Journee dont le detail (identite des beneficiaires, guide implicite du
  // retour utilisateur) est affiche dans une modale -- le valideur ne voyait
  // jusque-la que le nombre de lignes et le sous-total, jamais qui est paye.
  const [journeeDetail, setJourneeDetail] = useState<JourneeConsolidee | null>(null)

  const [telechargementEnCours, setTelechargementEnCours] = useState(false)
  const [erreurTelechargement, setErreurTelechargement] = useState<ApiErrorResponse | null>(null)

  const telecharger = async () => {
    setErreurTelechargement(null)
    setTelechargementEnCours(true)
    try {
      const document = await telechargerDocument(id)
      declencherTelechargement(document.contenu, document.nomFichier)
    } catch (erreurApi) {
      setErreurTelechargement(erreurApi as ApiErrorResponse)
    } finally {
      setTelechargementEnCours(false)
    }
  }

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
      const versDirecteurReseau =
        reponse.aiguillage === 'ENVOI_DIRECTEUR_RESEAU' || reponse.aiguillage === 'COMPLEMENTAIRE_ENVOI_DIRECTEUR_RESEAU'
      succes(
        'Dossier validé',
        versDirecteurReseau
          ? 'Transféré au Directeur Réseau'
          : reponse.transmission && !reponse.transmission.transmis
            ? "Clôturé, envoi à la comptabilité à vérifier"
            : 'Clôturé, envoyé à la comptabilité',
      )
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

  const etapesAffichees = construireEtapesAffichees(processus, historique)
  const joursPeriode = enumererJours(processus.dateDebut, processus.dateFin).length

  const decisionDejaPrise = resultatValidation !== null || resultatRetour !== null
  // Griser plutot que laisser echouer (doctrine du Sprint 7F.4) : un Chef
  // d'Unite qui ouvrirait par URL directe un dossier EN_ATTENTE_DR ne verrait
  // pas de boutons -- le backend refuserait de toute facon (403 ACCES_REFUSE,
  // RG-07), mais autant eviter l'aller-retour pour rien.
  const peutDecider = statutAttendPourRole(processus.statut, role) && !decisionDejaPrise

  return (
    <>
      <PageHeader
        filAriane={[{ libelle: 'Validation', href: '/validation' }, { libelle: `Unité ${processus.codeUnite}` }]}
        titre={formatPeriode(processus.dateDebut, processus.dateFin)}
        badge={<BadgeStatutProcessus statut={processus.statut} />}
        actions={
          <div className="flex gap-3">
            {processus.statut !== 'EN_COURS_SAISIE' && (
              <Button variant="outline" onClick={telecharger} isLoading={telechargementEnCours}>
                Télécharger le document
              </Button>
            )}
            {peutDecider && (
              <>
                <Button variant="outline" onClick={() => setRetourModaleOuverte(true)} disabled={validationEnCours}>
                  Retourner
                </Button>
                <Button onClick={() => setConfirmationOuverte(true)} isLoading={validationEnCours}>
                  Valider
                </Button>
              </>
            )}
          </div>
        }
      />
      <div className="flex flex-col gap-6 p-8">
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

        {processus.statut === 'RETOURNE' && processus.motifRetour && (
          <Alert variant="warning">
            <AlertDescription>
              <p className="font-medium">Ce dossier a été retourné à l'agent d'unité.</p>
              <p>Motif : {processus.motifRetour}</p>
            </AlertDescription>
          </Alert>
        )}

        {erreurTelechargement && <AffichageErreur erreur={erreurTelechargement} />}

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <StatTile
            valeur={etat.montantTotalFcfa === null ? 'Indisponible' : formatMontantFcfa(etat.montantTotalFcfa)}
            libelle="montant total engagé"
          />
          <StatTile
            valeur={etat.nombreLignes ?? 0}
            libelle={`ligne${(etat.nombreLignes ?? 0) > 1 ? 's' : ''} · ${etat.nombreBeneficiaires ?? 0} bénéficiaire${(etat.nombreBeneficiaires ?? 0) > 1 ? 's' : ''}`}
          />
          <StatTile valeur={`${etat.nombreJournees ?? etat.journees.length} / ${joursPeriode}`} libelle="jours saisis" />
        </div>

        <Card>
          <CardHeader>
            <CardTitle>Détail par journée</CardTitle>
          </CardHeader>
          <CardContent className="p-0">
            <Tableau
              colonnes={COLONNES_JOURNEES}
              donnees={etat.journees}
              cleLigne={(journee) => journee.idFicheJournaliere}
              messageVide="Aucune journée saisie."
              onLigneClick={(journee) => setJourneeDetail(journee)}
              actions={(journee) => (
                <div className="flex justify-end">
                  <Button variant="ghost" size="sm" onClick={() => setJourneeDetail(journee)}>
                    <Eye className="h-4 w-4" aria-hidden="true" />
                    Détails
                  </Button>
                </div>
              )}
            />
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Parcours de signature</CardTitle>
          </CardHeader>
          <CardContent>
            <ul className="flex flex-col gap-2">
              {etapesAffichees.map((etape) => (
                <li
                  key={etape.cle}
                  className="flex items-center justify-between border-b border-neutral-100 pb-2 text-sm last:border-0 last:pb-0"
                >
                  <span className={etape.fait ? 'font-medium text-neutral-900' : 'text-neutral-600'}>
                    {etape.libelle}
                  </span>
                  <span className={etape.fait ? 'text-neutral-600' : 'text-neutral-500'}>{etape.detail}</span>
                </li>
              ))}
            </ul>
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
                  ? ', directement, sans passer par le chef d\'unité (RG-11)'
                  : ''}
                .
              </p>
              <p>Motif transmis à l'agent : {resultatRetour.etape.motifRetour}</p>
            </AlertDescription>
          </Alert>
        )}
      </div>

      {confirmationOuverte && (
        <Modale
          titre="Confirmer la validation"
          libelleConfirmer="Valider le dossier"
          variantConfirmer="default"
          largeur="max-w-lg"
          onAnnuler={() => setConfirmationOuverte(false)}
          onConfirmer={async () => {
            await valider()
            setConfirmationOuverte(false)
          }}
          contenu={
            <div className="flex flex-col gap-4">
              <Recapitulatif
                lignes={[
                  { libelle: 'Unité', valeur: processus.codeUnite },
                  { libelle: 'Période', valeur: formatPeriode(processus.dateDebut, processus.dateFin) },
                  { libelle: 'Type', valeur: processus.typeProcessus === 'COMPLEMENTAIRE' ? 'Complémentaire' : 'Normal' },
                  { libelle: 'Montant total', valeur: formatMontantFcfa(processus.montantTotal) },
                  { libelle: 'Suite', valeur: suiteDeLaValidation(processus) },
                ]}
              />
              <p className="text-sm text-neutral-700">
                Votre visa sera apposé sur le document de l'état. Cette validation ne peut pas être
                annulée.
              </p>
            </div>
          }
        />
      )}

      {retourModaleOuverte && (
        <RetourModale
          processus={processus}
          onFerme={() => setRetourModaleOuverte(false)}
          onSucces={(reponse) => {
            setResultatRetour(reponse)
            succes('Dossier retourné', "Il revient à l'agent d'unité avec votre motif")
            setRetourModaleOuverte(false)
          }}
        />
      )}

      {journeeDetail && <DetailJourneeModale journee={journeeDetail} onFerme={() => setJourneeDetail(null)} />}
    </>
  )
}
