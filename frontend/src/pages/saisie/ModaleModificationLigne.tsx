import { useState } from 'react'

import { AffichageErreur } from '../../components/communs/AffichageErreur'
import { Alert, AlertDescription } from '../../components/communs/Alert'
import { BoutonsSegmentes } from '../../components/communs/BoutonsSegmentes'
import { ChampTexte } from '../../components/communs/ChampTexte'
import { Modale } from '../../components/communs/Modale'
import type { ApiErrorResponse } from '../../api/apiClient'
import type { LigneResponse } from '../../api/saisieApi'
import { creerLigne, modifierLigne, supprimerLigne } from '../../api/saisieApi'
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
   * `suppressionEchouee` a vrai uniquement quand le bénéficiaire a changé ET
   * que la suppression de l'ancienne ligne a échoué après la création de la
   * nouvelle : la modale se ferme dans tous les cas de succès, c'est donc à
   * l'appelant d'afficher l'avertissement -- une bannière disparaissant avec
   * la modale ne serait jamais lue.
   */
  onSucces: (suppressionEchouee: boolean) => void
}

/**
 * Modifie une ligne (guide 7F.4, étape 5 ; fusionnée au rattrapage post-7F.6,
 * retour utilisateur : "un seul bouton Modifier, tous les champs préchargés").
 *
 * <b>Deux chemins selon ce qui change réellement</b>, transparents pour
 * l'agent :
 * - <b>bénéficiaire inchangé</b> (nom, prénom, compte, agence identiques) :
 *   `PUT /saisie/lignes/{id}` -- seul endpoint qui modifie une ligne en place,
 *   et il ne porte que nature et session (`ModificationLigneRequest.java`).
 * - <b>bénéficiaire modifié</b> : aucun endpoint ne réécrit un bénéficiaire en
 *   place (décision Sprint 3.1 -- un bénéficiaire est identifié par son seul
 *   numéro de compte, et son nom enregistré n'est jamais réécrit). La ligne
 *   est donc supprimée et recréée, avec toutes les valeurs du formulaire.
 *   <b>Créer d'abord, supprimer ensuite</b> : si la création échoue (RG-04,
 *   RG-03...), la ligne d'origine reste intacte.
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

  const beneficiaireModifie =
    nom !== ligne.beneficiaire.nom ||
    prenom !== ligne.beneficiaire.prenom ||
    numCompteCourant !== ligne.beneficiaire.numCompteCourant ||
    codeAgence !== ligne.beneficiaire.codeAgence

  const enregistrer = async () => {
    setTentative(true)
    setErreur(null)
    if (!aucuneErreur) return

    if (!beneficiaireModifie) {
      // Seules nature et/ou session ont pu changer : modification en place,
      // le montant est de toute facon revalide et refige par le serveur.
      try {
        await modifierLigne(ligne.id, { nature, session })
        onSucces(false)
      } catch (erreurApi) {
        setErreur(erreurApi as ApiErrorResponse)
      }
      return
    }

    // Le beneficiaire a change : supprimer et recreer (decision Sprint 3.1).
    try {
      await creerLigne({
        idFicheJournaliere: ligne.idFicheJournaliere,
        beneficiaire: { nom, prenom, numCompteCourant, codeAgence },
        nature,
        session,
      })
    } catch (erreurApi) {
      setErreur(erreurApi as ApiErrorResponse)
      return
    }

    try {
      await supprimerLigne(ligne.id)
      onSucces(false)
    } catch {
      // La correction a reussi (la nouvelle ligne existe) : seule la
      // suppression de l'ancienne a echoue. L'agent voit desormais les DEUX
      // lignes et peut retirer lui-meme celle de trop.
      onSucces(true)
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
                Le bénéficiaire a changé : cette ligne sera remplacée par une nouvelle (nouveau
                montant résolu à l'enregistrement, RG-03).
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
