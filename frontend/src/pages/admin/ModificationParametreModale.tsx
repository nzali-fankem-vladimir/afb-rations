import { useState } from 'react'

import { AffichageErreur } from '../../components/communs/AffichageErreur'
import { Alert, AlertDescription } from '../../components/communs/Alert'
import { ChampTexte } from '../../components/communs/ChampTexte'
import { Modale } from '../../components/communs/Modale'
import { Recapitulatif } from '../../components/communs/Recapitulatif'
import type { ApiErrorResponse } from '../../api/apiClient'
import { modifierParametre } from '../../api/parametresApi'
import type { ParametreResponse } from '../../api/parametresApi'

export interface ModificationParametreModaleProps {
  parametre: ParametreResponse
  onFerme: () => void
  onSucces: (parametre: ParametreResponse) => void
}

type Etape = 'saisie' | 'verification'

/**
 * Modification d'un parametre systeme, reservee a l'ADMIN (guide 7F.6, etape
 * 6, ajout backend scope). Effet immediat et sans redeploiement.
 *
 * Aucune validation de forme cote client au-dela du champ non vide : le
 * format exact (entier positif pour un seuil ou un delai, texte libre pour
 * un compte) est verifie par le serveur (400 VALEUR_PARAMETRE_INVALIDE), et
 * le dupliquer ici risquerait de diverger de la regle reelle.
 *
 * Etape de verification « avant → apres » avant l'envoi (proposition n°1 du
 * 7F.6) : c'est la valeur qui commande le niveau d'approbation de la banque.
 */
export function ModificationParametreModale({
  parametre,
  onFerme,
  onSucces,
}: ModificationParametreModaleProps) {
  const [etape, setEtape] = useState<Etape>('saisie')
  const [valeur, setValeur] = useState(parametre.valeur)
  const [erreur, setErreur] = useState<ApiErrorResponse | null>(null)

  const nouvelleValeur = valeur.trim()
  const inchangee = nouvelleValeur === parametre.valeur

  const modifier = async () => {
    setErreur(null)
    try {
      const resultat = await modifierParametre(parametre.code, nouvelleValeur)
      onSucces(resultat)
    } catch (erreurApi) {
      setErreur(erreurApi as ApiErrorResponse)
    }
  }

  if (etape === 'verification') {
    return (
      <Modale
        titre="Confirmer la modification"
        libelleConfirmer="Confirmer"
        variantConfirmer="destructive"
        libelleAnnuler="Retour"
        largeur="max-w-lg"
        onAnnuler={() => setEtape('saisie')}
        onConfirmer={modifier}
        contenu={
          <div className="flex flex-col gap-4">
            <Recapitulatif
              lignes={[
                { libelle: 'Paramètre', valeur: parametre.libelle },
                { libelle: 'Valeur', avant: parametre.valeur, valeur: nouvelleValeur },
              ]}
            />
            <Alert variant="warning">
              <AlertDescription>
                La nouvelle valeur s'applique immédiatement, sans redémarrage. La modification est
                tracée au journal d'audit avec l'ancienne et la nouvelle valeur.
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
      titre={`Modifier ${parametre.libelle}`}
      libelleConfirmer="Vérifier"
      variantConfirmer="default"
      largeur="max-w-lg"
      onAnnuler={onFerme}
      onConfirmer={() => setEtape('verification')}
      confirmerDesactive={nouvelleValeur === '' || inchangee}
      contenu={
        <div className="flex flex-col gap-4">
          <Alert variant="warning">
            <AlertDescription>
              Cette valeur commande le niveau d'approbation requis par la banque ou
              l'imputation comptable.
            </AlertDescription>
          </Alert>

          <ChampTexte id="parametre-code" label="Code" value={parametre.code} disabled />

          <ChampTexte
            id="parametre-valeur-actuelle"
            label="Valeur actuelle"
            value={parametre.valeur}
            disabled
          />

          <ChampTexte
            id="parametre-nouvelle-valeur"
            label="Nouvelle valeur"
            obligatoire
            value={valeur}
            onChange={(event) => setValeur(event.target.value)}
            autoFocus
          />

          {erreur && <AffichageErreur erreur={erreur} />}
        </div>
      }
    />
  )
}
