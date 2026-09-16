import { useState } from 'react'

import { AffichageErreur } from '../../components/communs/AffichageErreur'
import { ChampListe } from '../../components/communs/ChampListe'
import type { OptionListe } from '../../components/communs/ChampListe'
import { Modale } from '../../components/communs/Modale'
import type { ApiErrorResponse } from '../../api/apiClient'
import type { LigneResponse } from '../../api/saisieApi'
import { modifierLigne } from '../../api/saisieApi'
import type { NatureEnum, SessionEnum } from '../../types/enums'

const OPTIONS_NATURE: OptionListe[] = [
  { valeur: 'RATION', libelle: 'Ration' },
  { valeur: 'TRANSPORT', libelle: 'Transport' },
]

const OPTIONS_SESSION: OptionListe[] = [
  { valeur: 'JOUR', libelle: 'Jour' },
  { valeur: 'SOIR', libelle: 'Soir' },
]

export interface ModaleModificationLigneProps {
  ligne: LigneResponse
  onFerme: () => void
  onSucces: (ligne: LigneResponse) => void
}

/**
 * Modification d'une ligne (guide 7F.4, etape 5) : seules nature et session se
 * changent -- le beneficiaire et le montant ne sont pas dans ce formulaire, a
 * l'image du contrat backend (ModificationLigneRequest.java n'a pas d'autre
 * champ). Le montant affiche dans le tableau se rafraichit avec la valeur
 * retournee par le serveur apres succes, jamais recalcule ici.
 */
export function ModaleModificationLigne({ ligne, onFerme, onSucces }: ModaleModificationLigneProps) {
  const [nature, setNature] = useState<NatureEnum>(ligne.nature)
  const [session, setSession] = useState<SessionEnum>(ligne.session)
  const [erreur, setErreur] = useState<ApiErrorResponse | null>(null)

  const enregistrer = async () => {
    setErreur(null)
    try {
      const ligneModifiee = await modifierLigne(ligne.id, { nature, session })
      onSucces(ligneModifiee)
    } catch (erreurApi) {
      setErreur(erreurApi as ApiErrorResponse)
    }
  }

  return (
    <Modale
      titre={`Modifier la ligne de ${ligne.beneficiaire.nom} ${ligne.beneficiaire.prenom}`}
      libelleConfirmer="Enregistrer"
      variantConfirmer="default"
      onAnnuler={onFerme}
      onConfirmer={enregistrer}
      contenu={
        <div className="flex flex-col gap-4">
          <ChampListe
            id="modification-nature"
            label="Nature"
            obligatoire
            options={OPTIONS_NATURE}
            value={nature}
            onChange={(event) => setNature(event.target.value as NatureEnum)}
          />
          <ChampListe
            id="modification-session"
            label="Session"
            obligatoire
            options={OPTIONS_SESSION}
            value={session}
            onChange={(event) => setSession(event.target.value as SessionEnum)}
          />
          {erreur && <AffichageErreur erreur={erreur} />}
        </div>
      }
    />
  )
}
