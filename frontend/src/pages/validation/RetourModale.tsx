import { useState } from 'react'

import { AffichageErreur } from '../../components/communs/AffichageErreur'
import { ChampTexteMulti } from '../../components/communs/ChampTexteMulti'
import { Modale } from '../../components/communs/Modale'
import type { ApiErrorResponse } from '../../api/apiClient'
import { retournerProcessus } from '../../api/processusApi'
import type { RetourResponse } from '../../api/processusApi'

export interface RetourModaleProps {
  idProcessus: number
  onFerme: () => void
  onSucces: (reponse: RetourResponse) => void
}

/**
 * Retour motive d'un dossier a l'agent d'unite (guide 7F.5, etape 5, RG-10).
 *
 * Le bouton de confirmation reste inactif tant que le motif est vide ou compose
 * uniquement d'espaces -- le controle cote interface anticipe le refus serveur
 * (400 REQUETE_INVALIDE puis 422 MOTIF_OBLIGATOIRE, @NotBlank cote backend),
 * il ne le remplace pas : le serveur revalide de toute facon.
 */
export function RetourModale({ idProcessus, onFerme, onSucces }: RetourModaleProps) {
  const [motif, setMotif] = useState('')
  const [erreur, setErreur] = useState<ApiErrorResponse | null>(null)

  const motifUtile = motif.trim().length > 0

  const retourner = async () => {
    if (!motifUtile) return
    setErreur(null)
    try {
      const reponse = await retournerProcessus(idProcessus, motif)
      onSucces(reponse)
    } catch (erreurApi) {
      setErreur(erreurApi as ApiErrorResponse)
    }
  }

  return (
    <Modale
      titre="Retourner le dossier"
      libelleConfirmer="Retourner"
      variantConfirmer="destructive"
      largeur="max-w-lg"
      onAnnuler={onFerme}
      onConfirmer={retourner}
      confirmerDesactive={!motifUtile}
      contenu={
        <div className="flex flex-col gap-4">
          <p className="text-sm text-neutral-700">
            Le dossier revient à l'agent d'unité, quel que soit votre niveau de validation
            (RG-11). Le motif est obligatoire : il sera visible par l'agent.
          </p>

          <ChampTexteMulti
            id="retour-motif"
            label="Motif du retour"
            obligatoire
            value={motif}
            onChange={(event) => setMotif(event.target.value)}
            placeholder="Ce qui doit être corrigé avant une nouvelle soumission…"
            maxLength={255}
            autoFocus
          />

          {erreur && <AffichageErreur erreur={erreur} />}
        </div>
      }
    />
  )
}
