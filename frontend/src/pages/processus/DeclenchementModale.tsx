import { useState } from 'react'

import { AffichageErreur } from '../../components/communs/AffichageErreur'
import { Alert, AlertDescription } from '../../components/communs/Alert'
import { ChampTexte } from '../../components/communs/ChampTexte'
import { Modale } from '../../components/communs/Modale'
import { Recapitulatif } from '../../components/communs/Recapitulatif'
import { SelecteurPeriode } from '../../components/communs/SelecteurPeriode'
import type { ApiErrorResponse } from '../../api/apiClient'
import { declencherProcessus } from '../../api/processusApi'
import { NON_RENSEIGNE, enumererJours, formatPeriode } from '../../utils/formatters'

export interface DeclenchementModaleProps {
  codeUnite: string | null
  onFerme: () => void
  onSucces: (idProcessus: number) => void
}

type Etape = 'saisie' | 'verification'

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
 *
 * Etape de verification avant l'envoi (proposition n°1 du 7F.6) : un etat
 * declenche ne se supprime pas (aucun DELETE au contrat) et bloque toute autre
 * periode qui le chevauche. La duree affichee n'est qu'un rappel, jamais une
 * contrainte.
 */
export function DeclenchementModale({ codeUnite, onFerme, onSucces }: DeclenchementModaleProps) {
  const [etape, setEtape] = useState<Etape>('saisie')
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

  if (etape === 'verification') {
    const nombreJours = enumererJours(dateDebut, dateFin).length
    return (
      <Modale
        titre="Vérifier l'état à déclencher"
        libelleConfirmer="Confirmer le déclenchement"
        variantConfirmer="default"
        libelleAnnuler="Retour"
        largeur="max-w-lg"
        onAnnuler={() => setEtape('saisie')}
        onConfirmer={declencher}
        contenu={
          <div className="flex flex-col gap-4">
            <Recapitulatif
              lignes={[
                { libelle: 'Unité', valeur: codeUnite ?? NON_RENSEIGNE },
                { libelle: 'Période', valeur: formatPeriode(dateDebut, dateFin) },
                {
                  libelle: 'Nombre de jours',
                  valeur: nombreJours > 0 ? `${nombreJours} jour${nombreJours > 1 ? 's' : ''}` : 'aucun jour',
                },
                { libelle: 'Type', valeur: 'Normal' },
              ]}
            />
            <Alert variant="warning">
              <AlertDescription>
                Un état déclenché ne peut pas être supprimé, et aucun autre état ne pourra être
                ouvert sur une période qui le chevauche.
              </AlertDescription>
            </Alert>
            {erreur && <AffichageErreur erreur={erreur} />}
          </div>
        }
      />
    )
  }

  return (
    <Modale
      titre="Déclencher un état"
      libelleConfirmer="Vérifier"
      variantConfirmer="default"
      largeur="max-w-lg"
      onAnnuler={onFerme}
      onConfirmer={() => setEtape('verification')}
      confirmerDesactive={!codeUnite || dateDebut === '' || dateFin === ''}
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
