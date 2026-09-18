import { useState } from 'react'

import { AffichageErreur } from '../../components/communs/AffichageErreur'
import { ChampTexteMulti } from '../../components/communs/ChampTexteMulti'
import { Modale } from '../../components/communs/Modale'
import { Recapitulatif } from '../../components/communs/Recapitulatif'
import type { ApiErrorResponse } from '../../api/apiClient'
import { retirerGrille } from '../../api/grillesApi'
import type { GrilleResponse } from '../../api/grillesApi'
import { formatCombinaison, formatDateJJMMAAAA, formatMontantFcfa } from '../../utils/formatters'

export interface RetraitGrilleModaleProps {
  grille: GrilleResponse
  onFerme: () => void
  onSucces: (grille: GrilleResponse) => void
}

/**
 * Retrait d'une proposition en attente, à l'initiative de son propre auteur
 * (retour utilisateur, demande n°7 de la vérification visuelle du Sprint 7F.6).
 *
 * Aucune modification en place : le retrait ferme cette proposition précise
 * (statut REJETEE, comme un rejet de la DRH), et l'Analyste RH en soumet une
 * nouvelle, corrigée, via « Proposer une grille ». Si la grille n'appartient
 * pas à l'appelant, le serveur refuse (403 GRILLE_NON_PROPRIETAIRE) : ce n'est
 * pas vérifié côté écran, le libellé affiché n'étant pas une identité fiable.
 */
export function RetraitGrilleModale({ grille, onFerme, onSucces }: RetraitGrilleModaleProps) {
  const [motif, setMotif] = useState('')
  const [erreur, setErreur] = useState<ApiErrorResponse | null>(null)

  const motifUtile = motif.trim().length > 0

  const retirer = async () => {
    if (!motifUtile) return
    setErreur(null)
    try {
      const retiree = await retirerGrille(grille.id, motif)
      onSucces(retiree)
    } catch (erreurApi) {
      setErreur(erreurApi as ApiErrorResponse)
    }
  }

  return (
    <Modale
      titre="Retirer ma proposition"
      libelleConfirmer="Retirer"
      variantConfirmer="destructive"
      largeur="max-w-lg"
      onAnnuler={onFerme}
      onConfirmer={retirer}
      confirmerDesactive={!motifUtile}
      contenu={
        <div className="flex flex-col gap-4">
          <Recapitulatif
            lignes={[
              { libelle: 'Combinaison', valeur: formatCombinaison(grille.nature, grille.session) },
              { libelle: 'Montant proposé', valeur: formatMontantFcfa(grille.montantFcfa) },
              { libelle: "Date d'effet demandée", valeur: formatDateJJMMAAAA(grille.dateDebut) },
            ]}
          />

          <p className="text-sm text-neutral-700">
            Cette proposition ne sera plus soumise à la Directrice RH. Le montant actif ne change
            pas. Vous pourrez proposer une nouvelle grille corrigée juste après.
          </p>

          <ChampTexteMulti
            id="retrait-grille-motif"
            label="Motif du retrait"
            obligatoire
            value={motif}
            onChange={(event) => setMotif(event.target.value)}
            placeholder="Ex. erreur de montant, je repropose une grille corrigée…"
            maxLength={255}
            autoFocus
          />

          {erreur && <AffichageErreur erreur={erreur} />}
        </div>
      }
    />
  )
}
