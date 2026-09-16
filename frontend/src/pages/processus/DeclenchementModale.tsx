import { useState } from 'react'

import { AffichageErreur } from '../../components/communs/AffichageErreur'
import { ChampTexte } from '../../components/communs/ChampTexte'
import { Modale } from '../../components/communs/Modale'
import { SelecteurPeriode } from '../../components/communs/SelecteurPeriode'
import type { ApiErrorResponse } from '../../api/apiClient'
import { declencherProcessus } from '../../api/processusApi'

export interface DeclenchementModaleProps {
  codeUnite: string | null
  onFerme: () => void
  onSucces: (idProcessus: number) => void
}

/**
 * Formulaire de declenchement d'un etat NORMAL (POST /processus).
 *
 * Aucune duree n'est imposee entre les deux dates -- ni minimum ni maximum : le
 * cycle est hebdomadaire aujourd'hui, mais l'intervalle a ete choisi
 * precisement pour ne pas figer une cadence (point M-04). L'unite est
 * preselectionnee depuis le profil et non modifiable : l'agent n'ouvre un etat
 * que pour sa propre unite (verifie de toute facon cote backend).
 *
 * Le refus 409 PROCESSUS_EXISTANT (deux etats NORMAL qui se chevauchent, meme
 * partiellement) est affiche tel que le backend le formule -- jamais un texte
 * fixe -- car le message differe selon que l'etat en conflit est encore ouvert
 * ou deja cloture.
 */
export function DeclenchementModale({ codeUnite, onFerme, onSucces }: DeclenchementModaleProps) {
  const [dateDebut, setDateDebut] = useState('')
  const [dateFin, setDateFin] = useState('')
  const [erreur, setErreur] = useState<ApiErrorResponse | null>(null)

  const declencher = async () => {
    if (!codeUnite) return
    setErreur(null)
    try {
      const processus = await declencherProcessus({ dateDebut, dateFin, codeUnite })
      onSucces(processus.idProcessus)
    } catch (erreurApi) {
      setErreur(erreurApi as ApiErrorResponse)
    }
  }

  return (
    <Modale
      titre="Déclencher un état"
      libelleConfirmer="Déclencher"
      variantConfirmer="default"
      largeur="max-w-lg"
      onAnnuler={onFerme}
      onConfirmer={declencher}
      contenu={
        <div className="flex flex-col gap-4">
          <p className="text-sm text-neutral-700">
            La période n'a aucune durée imposée : indiquez son premier et son dernier jour, tous
            deux inclus.
          </p>

          <ChampTexte id="declenchement-unite" label="Unité" value={codeUnite ?? ''} disabled />

          <SelecteurPeriode
            idPrefix="declenchement"
            dateDebut={dateDebut}
            dateFin={dateFin}
            onChangerDateDebut={setDateDebut}
            onChangerDateFin={setDateFin}
            obligatoire
          />

          {erreur && <AffichageErreur erreur={erreur} />}
        </div>
      }
    />
  )
}
