import { useState } from 'react'

import { AffichageErreur } from '../../components/communs/AffichageErreur'
import { ChampTexteMulti } from '../../components/communs/ChampTexteMulti'
import { Modale } from '../../components/communs/Modale'
import { Recapitulatif } from '../../components/communs/Recapitulatif'
import type { ApiErrorResponse } from '../../api/apiClient'
import { rejeterGrille } from '../../api/grillesApi'
import type { GrilleResponse } from '../../api/grillesApi'
import { NON_RENSEIGNE, formatCombinaison, formatDateJJMMAAAA, formatMontantFcfa } from '../../utils/formatters'

export interface RejetGrilleModaleProps {
  grille: GrilleResponse
  /** Montant en vigueur aujourd'hui sur le couple, nul s'il n'y en a pas. */
  montantActif: number | null
  onFerme: () => void
  onSucces: (grille: GrilleResponse) => void
}

/**
 * Rejet motivé d'une proposition, réservé à la Directrice RH (guide 7F.6,
 * étape 4, RG-10). Même convention que RetourModale (Sprint 7F.5) : bouton
 * de confirmation inactif tant que le motif est vide ou composé d'espaces.
 *
 * La fenêtre du motif tient lieu de confirmation : elle rappelle ce qui est
 * rejeté plutôt que d'empiler une seconde fenêtre (proposition n°1, 7F.6).
 *
 * La grille active du couple n'est jamais touchée : un rejet dit « ce tarif
 * ne s'appliquera pas », pas « il n'y a plus de tarif » (Sprint 2.3).
 */
export function RejetGrilleModale({ grille, montantActif, onFerme, onSucces }: RejetGrilleModaleProps) {
  const [motif, setMotif] = useState('')
  const [erreur, setErreur] = useState<ApiErrorResponse | null>(null)

  const motifUtile = motif.trim().length > 0

  const rejeter = async () => {
    if (!motifUtile) return
    setErreur(null)
    try {
      const rejetee = await rejeterGrille(grille.id, motif)
      onSucces(rejetee)
    } catch (erreurApi) {
      setErreur(erreurApi as ApiErrorResponse)
    }
  }

  return (
    <Modale
      titre="Rejeter la proposition"
      libelleConfirmer="Rejeter"
      variantConfirmer="destructive"
      largeur="max-w-lg"
      onAnnuler={onFerme}
      onConfirmer={rejeter}
      confirmerDesactive={!motifUtile}
      contenu={
        <div className="flex flex-col gap-4">
          <Recapitulatif
            lignes={[
              { libelle: 'Combinaison', valeur: formatCombinaison(grille.nature, grille.session) },
              { libelle: 'Montant proposé', valeur: formatMontantFcfa(grille.montantFcfa) },
              { libelle: "Date d'effet demandée", valeur: formatDateJJMMAAAA(grille.dateDebut) },
              { libelle: 'Proposée par', valeur: grille.createur ?? NON_RENSEIGNE },
              {
                libelle: 'Tarif qui reste en vigueur',
                valeur: montantActif === null ? 'Aucun' : formatMontantFcfa(montantActif),
              },
            ]}
          />

          <p className="text-sm text-neutral-700">
            La grille actuellement active n'est pas touchée : les saisies continuent au montant
            inchangé. Le motif est obligatoire (RG-10) : il sera transmis à l'Analyste RH pour
            qu'il sache quoi corriger.
          </p>

          <ChampTexteMulti
            id="rejet-grille-motif"
            label="Motif du rejet"
            obligatoire
            value={motif}
            onChange={(event) => setMotif(event.target.value)}
            placeholder="Ex. montant supérieur au barème en vigueur…"
            maxLength={255}
            autoFocus
          />

          {erreur && <AffichageErreur erreur={erreur} />}
        </div>
      }
    />
  )
}
