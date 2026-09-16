import { useEffect, useState } from 'react'

import { AffichageErreur } from '../../components/communs/AffichageErreur'
import { Modale } from '../../components/communs/Modale'
import type { ApiErrorResponse } from '../../api/apiClient'
import type { FicheResponse, LigneResponse } from '../../api/saisieApi'
import { listerLignesFiche, ouvrirFiche, supprimerLigne } from '../../api/saisieApi'
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
 * L'ouverture de fiche est IDEMPOTENTE (RG-05) : changer de jour ne vide jamais
 * l'ecran, la fiche revient avec ses lignes existantes si le jour a deja ete
 * saisi.
 */
export function SaisieJournaliereTab({ idProcessus, dateDebut, dateFin, modifiable }: SaisieJournaliereTabProps) {
  const [dateSelectionnee, setDateSelectionnee] = useState(dateDebut)
  const [fiche, setFiche] = useState<FicheResponse | null>(null)
  const [chargementFiche, setChargementFiche] = useState(true)
  const [erreurFiche, setErreurFiche] = useState<ApiErrorResponse | null>(null)
  const [ligneEnModification, setLigneEnModification] = useState<LigneResponse | null>(null)
  const [ligneASupprimer, setLigneASupprimer] = useState<LigneResponse | null>(null)
  const [erreurSuppression, setErreurSuppression] = useState<ApiErrorResponse | null>(null)

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

  const selectionnerJour = (jour: string) => {
    setChargementFiche(true)
    setErreurFiche(null)
    setDateSelectionnee(jour)
  }

  // Source unique de verite apres toute ecriture : on redemande la fiche au
  // serveur plutot que de recomposer les lignes a la main -- le sous-total est
  // calcule cote backend (FicheResponse), jamais recalcule ici.
  const rafraichirFiche = async () => {
    if (!fiche) return
    const ficheRafraichie = await listerLignesFiche(fiche.id)
    setFiche(ficheRafraichie)
  }

  const confirmerSuppression = async () => {
    if (!ligneASupprimer) return
    setErreurSuppression(null)
    try {
      await supprimerLigne(ligneASupprimer.id)
      setLigneASupprimer(null)
      await rafraichirFiche()
    } catch (erreurApi) {
      setErreurSuppression(erreurApi as ApiErrorResponse)
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <SelecteurJour
        dateDebut={dateDebut}
        dateFin={dateFin}
        dateSelectionnee={dateSelectionnee}
        onSelectionner={selectionnerJour}
      />

      {erreurFiche && <AffichageErreur erreur={erreurFiche} />}

      {fiche && (
        <>
          <FormulaireAjoutLigne
            idFicheJournaliere={fiche.id}
            disabled={!modifiable}
            onAjout={() => {
              void rafraichirFiche()
            }}
          />

          <TableauLignesJour
            lignes={fiche.lignes}
            chargement={chargementFiche}
            sousTotalFcfa={fiche.sousTotalFcfa}
            modifiable={modifiable}
            onDemanderModification={setLigneEnModification}
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
          onSucces={() => {
            setLigneEnModification(null)
            void rafraichirFiche()
          }}
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
