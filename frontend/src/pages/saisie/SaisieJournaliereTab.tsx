import { useEffect, useState } from 'react'

import { AffichageErreur } from '../../components/communs/AffichageErreur'
import { Alert, AlertDescription } from '../../components/communs/Alert'
import { Modale } from '../../components/communs/Modale'
import type { ApiErrorResponse } from '../../api/apiClient'
import type { EtatProcessusResponse } from '../../api/processusApi'
import { consulterEtatProcessus } from '../../api/processusApi'
import type { FicheResponse, LigneResponse } from '../../api/saisieApi'
import { listerLignesFiche, ouvrirFiche, supprimerLigne } from '../../api/saisieApi'
import { useToast } from '../../hooks/useToast'
import { formatMontantFcfa } from '../../utils/formatters'
import { FormulaireAjoutLigne } from './FormulaireAjoutLigne'
import { ModaleModificationLigne } from './ModaleModificationLigne'
import { SelecteurJour } from './SelecteurJour'
import { TableauLignesJour } from './TableauLignesJour'

export interface SaisieJournaliereTabProps {
  idProcessus: number
  dateDebut: string
  dateFin: string
  /** EN_COURS_SAISIE ou RETOURNE (guide 7F.4, etape 5) : sinon, tout est en lecture seule. */
  modifiable: boolean
}

/**
 * Ecran central du module (guide 7F.4, etape 3) : calendrier borne a la
 * periode, formulaire d'ajout, tableau des lignes du jour.
 *
 * L'ouverture de fiche est IDEMPOTENTE (RG-05) : la fiche revient avec ses lignes
 * existantes si le jour a deja ete saisi.
 *
 * Changer de jour vide le FORMULAIRE (Sprint 7F.6, proposition n°4), jamais les
 * lignes enregistrees. Si un beneficiaire etait en cours de saisie, une
 * confirmation est demandee avant de l'effacer.
 */
export function SaisieJournaliereTab({ idProcessus, dateDebut, dateFin, modifiable }: SaisieJournaliereTabProps) {
  const { succes } = useToast()
  const [dateSelectionnee, setDateSelectionnee] = useState(dateDebut)
  const [fiche, setFiche] = useState<FicheResponse | null>(null)
  const [chargementFiche, setChargementFiche] = useState(true)
  const [erreurFiche, setErreurFiche] = useState<ApiErrorResponse | null>(null)
  const [ligneEnModification, setLigneEnModification] = useState<LigneResponse | null>(null)
  const [ligneASupprimer, setLigneASupprimer] = useState<LigneResponse | null>(null)
  const [erreurSuppression, setErreurSuppression] = useState<ApiErrorResponse | null>(null)
  // Avertissement post-correction (retour utilisateur, demande n°6) : affiche
  // apres la fermeture de la modale, jamais dedans -- une bannière qui
  // disparaitrait avec elle ne serait jamais lue.
  const [avertissementCorrection, setAvertissementCorrection] = useState<string | null>(null)
  // Beneficiaire en cours de saisie dans le formulaire, non enregistre : changer
  // de jour l'effacerait, on le demande d'abord (Sprint 7F.6, proposition n°4).
  const [brouillonEnCours, setBrouillonEnCours] = useState(false)
  const [jourDemande, setJourDemande] = useState<string | null>(null)
  // Etat consolide de la periode : sert le nombre de lignes et le sous-total de
  // CHAQUE jour sur le selecteur, plus le total de la periode (Sprint 7F.6,
  // proposition n°5). Les chiffres viennent du service Saisie (RG-06), aucune
  // somme n'est refaite ici. Relu apres chaque ecriture de ligne.
  const [etatPeriode, setEtatPeriode] = useState<EtatProcessusResponse | null>(null)
  const [versionEtat, setVersionEtat] = useState(0)

  useEffect(() => {
    let annule = false
    consulterEtatProcessus(idProcessus)
      .then((reponse) => {
        if (!annule) setEtatPeriode(reponse)
      })
      .catch(() => {
        // Non bloquant : les compteurs par jour et le total de la periode
        // disparaissent, la saisie elle-meme continue de fonctionner.
      })
    return () => {
      annule = true
    }
  }, [idProcessus, versionEtat])

  useEffect(() => {
    let annule = false
    ouvrirFiche({ idProcessus, dateJour: dateSelectionnee })
      .then(({ fiche: ficheOuverte }) => {
        if (annule) return
        setFiche(ficheOuverte)
        setErreurFiche(null)
      })
      .catch((erreurApi: ApiErrorResponse) => {
        if (!annule) setErreurFiche(erreurApi)
      })
      .finally(() => {
        if (!annule) setChargementFiche(false)
      })
    return () => {
      annule = true
    }
  }, [idProcessus, dateSelectionnee])

  const changerJour = (jour: string) => {
    setChargementFiche(true)
    setErreurFiche(null)
    setBrouillonEnCours(false)
    setDateSelectionnee(jour)
  }

  const selectionnerJour = (jour: string) => {
    if (jour === dateSelectionnee) return
    if (brouillonEnCours) {
      setJourDemande(jour)
      return
    }
    changerJour(jour)
  }

  // La fiche affichee est-elle bien celle du jour selectionne ? Faux pendant le
  // chargement d'un nouveau jour, ET si ce chargement echoue. Dans les deux cas,
  // `fiche` porte encore la journee precedente : un ajout partirait sur le
  // mauvais jour. Le formulaire est donc desactive tant que ce n'est pas vrai.
  const ficheDuJourSelectionne = fiche !== null && fiche.dateJour === dateSelectionnee

  // Source unique de verite apres toute ecriture : on redemande la fiche au
  // serveur plutot que de recomposer les lignes a la main -- le sous-total est
  // calcule cote backend (FicheResponse), jamais recalcule ici.
  const rafraichirFiche = async () => {
    if (!fiche) return
    const ficheRafraichie = await listerLignesFiche(fiche.id)
    setFiche(ficheRafraichie)
    // Les compteurs des AUTRES jours et le total de la periode changent aussi.
    setVersionEtat((precedent) => precedent + 1)
  }

  const resumesParJour = Object.fromEntries(
    (etatPeriode?.journees ?? []).map((journee) => [
      journee.dateJour,
      { nombreLignes: journee.nombreLignes, sousTotalFcfa: journee.sousTotalFcfa },
    ]),
  )

  const confirmerSuppression = async () => {
    if (!ligneASupprimer) return
    setErreurSuppression(null)
    try {
      await supprimerLigne(ligneASupprimer.id)
      succes(
        'Ligne supprimée',
        `${ligneASupprimer.beneficiaire.nom} ${ligneASupprimer.beneficiaire.prenom}`,
      )
      setLigneASupprimer(null)
      await rafraichirFiche()
    } catch (erreurApi) {
      setErreurSuppression(erreurApi as ApiErrorResponse)
    }
  }

  return (
    <div className="flex flex-col gap-6">
      {avertissementCorrection && (
        <Alert variant="warning">
          <AlertDescription>{avertissementCorrection}</AlertDescription>
        </Alert>
      )}

      {etatPeriode && (
        <p className="text-right text-sm text-neutral-700">
          Total de la période :{' '}
          <span className="font-semibold tabular-nums text-neutral-900">
            {formatMontantFcfa(etatPeriode.montantTotalFcfa ?? 0)}
          </span>{' '}
          · {etatPeriode.nombreLignes ?? 0} ligne{(etatPeriode.nombreLignes ?? 0) > 1 ? 's' : ''}
        </p>
      )}

      <SelecteurJour
        dateDebut={dateDebut}
        dateFin={dateFin}
        dateSelectionnee={dateSelectionnee}
        resumes={resumesParJour}
        onSelectionner={selectionnerJour}
      />

      {erreurFiche && <AffichageErreur erreur={erreurFiche} />}

      {/* Chargement d'un nouveau jour : l'ancien contenu reste en place, grise.
          Echec de chargement : il disparait, seule l'erreur reste -- jamais les
          lignes d'un autre jour sous le nom du jour selectionne. */}
      {fiche && (ficheDuJourSelectionne || chargementFiche) && (
        <>
          {/* key : un nouveau jour remonte le formulaire, donc le vide
              entierement (nature, session, beneficiaire, erreurs, montant). */}
          <FormulaireAjoutLigne
            key={fiche.id}
            idFicheJournaliere={fiche.id}
            dateJour={fiche.dateJour}
            disabled={!modifiable || !ficheDuJourSelectionne}
            onBrouillonChange={setBrouillonEnCours}
            onAjout={() => {
              void rafraichirFiche()
            }}
          />

          <TableauLignesJour
            lignes={fiche.lignes}
            chargement={chargementFiche}
            sousTotalFcfa={fiche.sousTotalFcfa}
            modifiable={modifiable}
            onDemanderModification={(ligne) => {
              setAvertissementCorrection(null)
              setLigneEnModification(ligne)
            }}
            onDemanderSuppression={(ligne) => {
              setErreurSuppression(null)
              setLigneASupprimer(ligne)
            }}
          />
        </>
      )}

      {ligneEnModification && (
        <ModaleModificationLigne
          ligne={ligneEnModification}
          onFerme={() => setLigneEnModification(null)}
          onSucces={(avertissement) => {
            succes(
              'Ligne modifiée',
              `${ligneEnModification.beneficiaire.nom} ${ligneEnModification.beneficiaire.prenom}`,
            )
            setLigneEnModification(null)
            setAvertissementCorrection(avertissement)
            void rafraichirFiche()
          }}
        />
      )}

      {jourDemande && (
        <Modale
          titre="Saisie non enregistrée"
          libelleConfirmer="Changer de jour"
          variantConfirmer="destructive"
          libelleAnnuler="Rester sur ce jour"
          onAnnuler={() => setJourDemande(null)}
          onConfirmer={() => {
            changerJour(jourDemande)
            setJourDemande(null)
          }}
          message="Vous avez une ligne en cours de saisie, non enregistrée. Changer de jour l'effacera."
        />
      )}

      {ligneASupprimer && (
        <Modale
          titre="Supprimer la ligne"
          libelleConfirmer="Supprimer"
          variantConfirmer="destructive"
          onAnnuler={() => setLigneASupprimer(null)}
          onConfirmer={confirmerSuppression}
          contenu={
            <div className="flex flex-col gap-4">
              <p className="text-sm text-neutral-700">
                Supprimer la ligne de {ligneASupprimer.beneficiaire.nom} {ligneASupprimer.beneficiaire.prenom} ?
                Cette action est définitive.
              </p>
              {erreurSuppression && <AffichageErreur erreur={erreurSuppression} />}
            </div>
          }
        />
      )}
    </div>
  )
}
