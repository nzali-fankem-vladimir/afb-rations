import { useEffect, useState } from 'react'
import { CheckCircle2, Send } from 'lucide-react'

import { AffichageErreur } from '../../components/communs/AffichageErreur'
import { Alert, AlertDescription } from '../../components/communs/Alert'
import { Button } from '../../components/communs/Button'
import { Modale } from '../../components/communs/Modale'
import { StatTile } from '../../components/communs/StatTile'
import { Tableau } from '../../components/communs/Tableau'
import type { Colonne } from '../../components/communs/Tableau'
import type { ApiErrorResponse } from '../../api/apiClient'
import type { EtatProcessusResponse, SoumissionResponse } from '../../api/processusApi'
import { consulterEtatProcessus, soumettreProcessus, telechargerDocument } from '../../api/processusApi'
import { useToast } from '../../hooks/useToast'
import { cn } from '../../utils/cn'
import { declencherTelechargement } from '../../utils/declencherTelechargement'
import { enumererJours, formatDateJJMMAAAA, formatMontantFcfa } from '../../utils/formatters'

export interface ConsultationEtatTabProps {
  idProcessus: number
  /** EN_COURS_SAISIE ou RETOURNE : sinon la soumission est deja acquise ou hors de portee. */
  modifiable: boolean
  /** Le processus parent doit relire son statut : la clotures depend du montant, pas seulement du geste. */
  onSoumissionReussie: () => void
}

/**
 * Une journee de la periode, qu'elle porte ou non une fiche_journaliere.
 * GET /processus/{id}/etat ne rend que les journees ayant au moins une ligne
 * (RG-05, fiche creee au fil de la saisie) : les journees vides sont
 * reconstituees ici a partir des bornes de la periode, pour rester visibles
 * dans le tableau plutot que d'y disparaitre (retour utilisateur, maquette de
 * refonte du Sprint 7F.6).
 */
interface LigneJournee {
  dateJour: string
  nombreLignes: number
  sousTotalFcfa: number
  vide: boolean
}

const COLONNES: Colonne<LigneJournee>[] = [
  {
    cle: 'dateJour',
    entete: 'Journée',
    rendu: (journee) => (
      <span className={cn(journee.vide && 'text-neutral-400')}>{formatDateJJMMAAAA(journee.dateJour)}</span>
    ),
  },
  {
    cle: 'nombreLignes',
    entete: 'Lignes',
    rendu: (journee) => (
      <span className={cn(journee.vide && 'text-neutral-400')}>
        {journee.vide ? 'aucune ligne' : journee.nombreLignes}
      </span>
    ),
  },
  {
    cle: 'sousTotalFcfa',
    entete: 'Sous-total',
    className: 'tabular-nums',
    rendu: (journee) => (
      <span className={cn(journee.vide && 'text-neutral-400')}>{formatMontantFcfa(journee.sousTotalFcfa)}</span>
    ),
  },
]

/**
 * Consultation de l'etat consolide de la periode, et soumission (guide 7F.4,
 * etape 6). Le detail par journee et le total viennent tels quels de
 * GET /processus/{id}/etat -- aucune somme n'est refaite ici (RG-06, un seul
 * chemin de calcul, cote service Saisie).
 */
export function ConsultationEtatTab({ idProcessus, modifiable, onSoumissionReussie }: ConsultationEtatTabProps) {
  const { succes } = useToast()
  const [etat, setEtat] = useState<EtatProcessusResponse | null>(null)
  const [chargement, setChargement] = useState(true)
  const [erreurChargement, setErreurChargement] = useState<ApiErrorResponse | null>(null)
  const [erreurSoumission, setErreurSoumission] = useState<ApiErrorResponse | null>(null)
  const [soumissionReussie, setSoumissionReussie] = useState<SoumissionResponse | null>(null)
  const [confirmationOuverte, setConfirmationOuverte] = useState(false)

  const [telechargementEnCours, setTelechargementEnCours] = useState(false)
  const [erreurTelechargement, setErreurTelechargement] = useState<ApiErrorResponse | null>(null)

  // Un document existe des la premiere soumission (guide 7F.8) : le retour
  // eventuel a l'agent (RETOURNE) ne le fait pas disparaitre, il n'est
  // regenere qu'a la resoumission.
  const telecharger = async () => {
    setErreurTelechargement(null)
    setTelechargementEnCours(true)
    try {
      const document = await telechargerDocument(idProcessus)
      declencherTelechargement(document.contenu, document.nomFichier)
    } catch (erreurApi) {
      setErreurTelechargement(erreurApi as ApiErrorResponse)
    } finally {
      setTelechargementEnCours(false)
    }
  }

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
      succes(
        'État soumis',
        reponse.pieceJointe.nombreSignatures > 0
          ? `Document généré et signé (${reponse.pieceJointe.nombreSignatures} signature(s))`
          : undefined,
      )
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

  const joursPeriode = enumererJours(etat.dateDebut, etat.dateFin)
  const journeesParDate = new Map(etat.journees.map((journee) => [journee.dateJour, journee]))
  const lignesAffichees: LigneJournee[] = joursPeriode.map((dateJour) => {
    const journee = journeesParDate.get(dateJour)
    return journee
      ? { dateJour, nombreLignes: journee.nombreLignes, sousTotalFcfa: journee.sousTotalFcfa, vide: false }
      : { dateJour, nombreLignes: 0, sousTotalFcfa: 0, vide: true }
  })
  const joursSaisis = lignesAffichees.filter((journee) => !journee.vide).length
  const joursVides = lignesAffichees.length - joursSaisis

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

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <StatTile
          valeur={etat.montantTotalFcfa === null ? 'Indisponible' : formatMontantFcfa(etat.montantTotalFcfa)}
          libelle="total de la période"
        />
        <StatTile
          valeur={etat.nombreLignes ?? 0}
          libelle={`ligne${(etat.nombreLignes ?? 0) > 1 ? 's' : ''} · ${etat.nombreBeneficiaires ?? 0} bénéficiaire${(etat.nombreBeneficiaires ?? 0) > 1 ? 's' : ''}`}
        />
        <StatTile valeur={`${joursSaisis} / ${lignesAffichees.length}`} libelle="jours saisis" />
      </div>

      {joursVides > 0 && (
        <Alert variant="default">
          <AlertDescription>
            <p className="font-medium">
              {joursVides} journée{joursVides > 1 ? 's sont vides' : ' est vide'}.
            </p>
            <p>
              Ce n'est pas bloquant : un état peut être soumis avec des jours sans ligne. Vérifiez seulement
              qu'aucune n'a été oubliée.
            </p>
          </AlertDescription>
        </Alert>
      )}

      <Tableau
        colonnes={COLONNES}
        donnees={lignesAffichees}
        cleLigne={(journee) => journee.dateJour}
        messageVide="Aucune journée dans cette période."
      />

      {erreurTelechargement && <AffichageErreur erreur={erreurTelechargement} />}

      <div className="flex justify-end gap-3">
        {etat.statut !== 'EN_COURS_SAISIE' && (
          <Button variant="outline" onClick={telecharger} isLoading={telechargementEnCours}>
            Télécharger le document
          </Button>
        )}
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
                Le total de la période (
                {etat.montantTotalFcfa === null ? 'indisponible' : formatMontantFcfa(etat.montantTotalFcfa)})
                sera transmis pour validation. Une fois soumis, l'état n'est plus modifiable : seul un retour du
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
