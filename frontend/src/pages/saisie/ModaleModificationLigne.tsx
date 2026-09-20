import { useState } from 'react'

import { AffichageErreur } from '../../components/communs/AffichageErreur'
import { Alert, AlertDescription } from '../../components/communs/Alert'
import { BoutonsSegmentes } from '../../components/communs/BoutonsSegmentes'
import { ChampTexte } from '../../components/communs/ChampTexte'
import { Modale } from '../../components/communs/Modale'
import type { ApiErrorResponse } from '../../api/apiClient'
import type { LigneResponse } from '../../api/saisieApi'
import { modifierLigne } from '../../api/saisieApi'
import type { NatureEnum, SessionEnum } from '../../types/enums'
import {
  LONGUEUR_CODE_AGENCE,
  LONGUEUR_COMPTE_COURANT,
  sansEspaces,
  validerBeneficiaire,
} from '../../utils/validationSaisie'

const OPTIONS_NATURE = [
  { valeur: 'RATION' as NatureEnum, libelle: 'Ration' },
  { valeur: 'TRANSPORT' as NatureEnum, libelle: 'Transport' },
]

const OPTIONS_SESSION = [
  { valeur: 'JOUR' as SessionEnum, libelle: 'Jour' },
  { valeur: 'SOIR' as SessionEnum, libelle: 'Soir' },
]

export interface ModaleModificationLigneProps {
  ligne: LigneResponse
  onFerme: () => void
  /**
   * `avertissement` non nul quand le nouveau compte désignait un bénéficiaire
   * déjà connu dont les données diffèrent de celles saisies : elles n'ont pas été
   * écrasées. La modale se ferme dans tous les cas de succès, c'est donc à
   * l'appelant d'afficher l'avertissement -- une bannière disparaissant avec la
   * modale ne serait jamais lue.
   */
  onSucces: (avertissement: string | null) => void
}

/**
 * Modifie une ligne (guide 7F.4, étape 5 ; fusionnée au rattrapage post-7F.6,
 * retour utilisateur : "un seul bouton Modifier, tous les champs préchargés").
 *
 * <b>Un seul chemin, sur place</b> : `PUT /saisie/lignes/{id}` porte nature,
 * session et identité du bénéficiaire, et le serveur modifie la ligne en une
 * seule transaction (RG-03, RG-04 et RG-15 rejouées avant d'écrire). L'ancien
 * parcours « créer la nouvelle ligne puis supprimer l'ancienne » est abandonné :
 * il refusait la correction d'une agence (RG-04 voyait un doublon de la ligne
 * qu'on remplaçait) et pouvait laisser deux lignes pour la même prestation.
 */
export function ModaleModificationLigne({ ligne, onFerme, onSucces }: ModaleModificationLigneProps) {
  const [nom, setNom] = useState(ligne.beneficiaire.nom)
  const [prenom, setPrenom] = useState(ligne.beneficiaire.prenom)
  const [numCompteCourant, setNumCompteCourant] = useState(ligne.beneficiaire.numCompteCourant)
  const [codeAgence, setCodeAgence] = useState(ligne.beneficiaire.codeAgence)
  const [nature, setNature] = useState<NatureEnum>(ligne.nature)
  const [session, setSession] = useState<SessionEnum>(ligne.session)
  const [tentative, setTentative] = useState(false)
  const [erreur, setErreur] = useState<ApiErrorResponse | null>(null)

  const erreursBeneficiaire = validerBeneficiaire({ nom, prenom, numCompteCourant, codeAgence })
  const aucuneErreur = Object.keys(erreursBeneficiaire).length === 0

  const compteModifie = numCompteCourant !== ligne.beneficiaire.numCompteCourant

  const beneficiaireModifie =
    nom !== ligne.beneficiaire.nom ||
    prenom !== ligne.beneficiaire.prenom ||
    numCompteCourant !== ligne.beneficiaire.numCompteCourant ||
    codeAgence !== ligne.beneficiaire.codeAgence

  const enregistrer = async () => {
    setTentative(true)
    setErreur(null)
    if (!aucuneErreur) return

    try {
      const reponse = await modifierLigne(ligne.id, {
        nature,
        session,
        nom,
        prenom,
        numCompteCourant,
        codeAgence,
      })
      const b = reponse.beneficiaire
      // Nouveau compte deja connu : le serveur garde les donnees du beneficiaire
      // existant, il ne les ecrase pas. On le dit, sinon l'agent croirait avoir
      // enregistre le nom ou l'agence qu'il vient de saisir.
      const divergence =
        compteModifie && (b.nom !== nom || b.prenom !== prenom || b.codeAgence !== codeAgence)
      onSucces(
        divergence
          ? `Ce numéro de compte existait déjà : la ligne est rattachée à ${b.nom} ${b.prenom} (agence ${b.codeAgence}), dont les informations n'ont pas été modifiées.`
          : null,
      )
    } catch (erreurApi) {
      setErreur(erreurApi as ApiErrorResponse)
    }
  }

  return (
    <Modale
      titre={`Modifier la ligne de ${ligne.beneficiaire.nom} ${ligne.beneficiaire.prenom}`}
      libelleConfirmer="Enregistrer"
      variantConfirmer="default"
      largeur="max-w-lg"
      onAnnuler={onFerme}
      onConfirmer={enregistrer}
      confirmerDesactive={tentative && !aucuneErreur}
      contenu={
        <div className="flex flex-col gap-4">
          {beneficiaireModifie && (
            <Alert variant="warning">
              <AlertDescription>
                {compteModifie
                  ? "Le numéro de compte a changé : la ligne sera rattachée au bénéficiaire de ce compte (créé s'il n'existe pas). Le montant est recalculé à l'enregistrement."
                  : "Vous corrigez la fiche de ce bénéficiaire : le changement vaut pour toutes ses lignes à venir. Les documents déjà produits ne changent pas."}
              </AlertDescription>
            </Alert>
          )}

          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <ChampTexte
              id="modification-nom"
              label="Nom"
              obligatoire
              erreur={tentative ? erreursBeneficiaire.nom : undefined}
              value={nom}
              onChange={(event) => setNom(event.target.value)}
            />
            <ChampTexte
              id="modification-prenom"
              label="Prénom"
              obligatoire
              erreur={tentative ? erreursBeneficiaire.prenom : undefined}
              value={prenom}
              onChange={(event) => setPrenom(event.target.value)}
            />
          </div>

          <div className="grid grid-cols-1 gap-3 sm:grid-cols-[2fr_1fr]">
            <ChampTexte
              id="modification-compte"
              label={`N° compte courant (${LONGUEUR_COMPTE_COURANT} chiffres)`}
              obligatoire
              inputMode="numeric"
              className="[&_input]:tabular-nums"
              erreur={tentative ? erreursBeneficiaire.numCompteCourant : undefined}
              value={numCompteCourant}
              onChange={(event) => setNumCompteCourant(sansEspaces(event.target.value))}
            />
            <ChampTexte
              id="modification-agence"
              label={`Code agence (${LONGUEUR_CODE_AGENCE} chiffres)`}
              obligatoire
              inputMode="numeric"
              maxLength={LONGUEUR_CODE_AGENCE}
              className="[&_input]:tabular-nums"
              erreur={tentative ? erreursBeneficiaire.codeAgence : undefined}
              value={codeAgence}
              onChange={(event) => setCodeAgence(sansEspaces(event.target.value))}
            />
          </div>

          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div className="flex flex-col gap-1.5">
              <span className="text-sm font-medium text-neutral-900">
                Nature <span className="text-primary-500">*</span>
              </span>
              <BoutonsSegmentes
                libelleGroupe="Nature"
                options={OPTIONS_NATURE}
                valeur={nature}
                onChange={setNature}
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <span className="text-sm font-medium text-neutral-900">
                Session <span className="text-primary-500">*</span>
              </span>
              <BoutonsSegmentes
                libelleGroupe="Session"
                options={OPTIONS_SESSION}
                valeur={session}
                onChange={setSession}
              />
            </div>
          </div>

          {erreur && <AffichageErreur erreur={erreur} />}
        </div>
      }
    />
  )
}
