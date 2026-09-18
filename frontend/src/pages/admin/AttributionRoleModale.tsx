import { useState } from 'react'

import { AffichageErreur } from '../../components/communs/AffichageErreur'
import { ChampListe } from '../../components/communs/ChampListe'
import { ChampTexte } from '../../components/communs/ChampTexte'
import { Modale } from '../../components/communs/Modale'
import { Recapitulatif } from '../../components/communs/Recapitulatif'
import type { ApiErrorResponse } from '../../api/apiClient'
import { ROLES_PORTEE_LOCALE, attribuerRole } from '../../api/adminApi'
import type { UtilisateurResponse } from '../../api/adminApi'
import type { RoleEnum } from '../../types/enums'

export interface AttributionRoleModaleProps {
  utilisateur: UtilisateurResponse
  onFerme: () => void
  onSucces: (utilisateur: UtilisateurResponse) => void
}

const LIBELLE_ROLE: Record<RoleEnum, string> = {
  AGENT_UNITE: "Agent d'unité",
  CHEF_UNITE_DA: "Chef d'unité",
  DIRECTEUR_RESEAU_DR: 'Directeur réseau',
  ARH: 'Analyste RH',
  DRH: 'Directrice RH',
  ADMIN: 'Administrateur',
}

const OPTIONS_ROLE = (Object.keys(LIBELLE_ROLE) as RoleEnum[]).map((role) => ({
  valeur: role,
  libelle: LIBELLE_ROLE[role],
}))

const REGEX_CODE_UNITE = /^\d{5}$/

type Etape = 'saisie' | 'verification'

/**
 * Attribution d'un rôle et d'un code unité à un profil existant, réservée à
 * l'administrateur (guide 7F.6, étape 5). Aucune création de compte : ce
 * formulaire ne fait qu'attribuer une habilitation à un compte déjà
 * pré-provisionné (CLAUDE.md section 10).
 *
 * Le code unité est obligatoire pour les rôles à portée locale (AGENT_UNITE,
 * CHEF_UNITE_DA) et facultatif pour les rôles à portée nationale --
 * ROLES_PORTEE_LOCALE reprend la même notion que PorteeAccesService côté
 * backend, uniquement pour adapter le formulaire ; le contrôle réel reste
 * celui du serveur (400 CODE_UNITE_INCOHERENT).
 *
 * Étape de vérification « avant → après » avant l'envoi (proposition n°1 du
 * 7F.6) : le changement de rôle prend effet dès la requête suivante de
 * l'utilisateur, sans reconnexion (décision Sprint 1.2).
 */
export function AttributionRoleModale({ utilisateur, onFerme, onSucces }: AttributionRoleModaleProps) {
  const [etape, setEtape] = useState<Etape>('saisie')
  const [role, setRole] = useState<RoleEnum>(utilisateur.role)
  const [codeUnite, setCodeUnite] = useState(utilisateur.codeUnite ?? '')
  const [erreur, setErreur] = useState<ApiErrorResponse | null>(null)

  const codeUniteSaisi = codeUnite.trim()
  const codeUniteObligatoire = ROLES_PORTEE_LOCALE.includes(role)
  const codeUniteValide = codeUniteSaisi === '' || REGEX_CODE_UNITE.test(codeUniteSaisi)
  const erreurCodeUnite = !codeUniteValide
    ? 'Cinq chiffres exactement'
    : codeUniteObligatoire && codeUniteSaisi === ''
      ? 'Obligatoire pour ce rôle'
      : undefined

  const formulaireValide = codeUniteValide && (!codeUniteObligatoire || codeUniteSaisi !== '')

  const soumettre = async () => {
    setErreur(null)
    try {
      const profil = await attribuerRole(utilisateur.id, {
        role,
        codeUnite: codeUniteSaisi === '' ? null : codeUniteSaisi,
      })
      onSucces(profil)
    } catch (erreurApi) {
      setErreur(erreurApi as ApiErrorResponse)
    }
  }

  const titre = `Habilitation de ${utilisateur.prenom} ${utilisateur.nom}`

  if (etape === 'verification') {
    return (
      <Modale
        titre={titre}
        libelleConfirmer="Confirmer l'attribution"
        variantConfirmer="default"
        libelleAnnuler="Retour"
        largeur="max-w-lg"
        onAnnuler={() => setEtape('saisie')}
        onConfirmer={soumettre}
        contenu={
          <div className="flex flex-col gap-4">
            <Recapitulatif
              lignes={[
                { libelle: 'Compte', valeur: utilisateur.login },
                { libelle: 'Rôle', avant: LIBELLE_ROLE[utilisateur.role], valeur: LIBELLE_ROLE[role] },
                {
                  libelle: 'Code unité',
                  avant: utilisateur.codeUnite ?? 'aucune (portée nationale)',
                  valeur: codeUniteSaisi === '' ? 'aucune (portée nationale)' : codeUniteSaisi,
                },
              ]}
            />
            <p className="text-sm text-neutral-700">
              Le changement s'applique dès la prochaine action de cet utilisateur, sans qu'il ait à
              se reconnecter.
            </p>
            {erreur && <AffichageErreur erreur={erreur} />}
          </div>
        }
      />
    )
  }

  return (
    <Modale
      titre={titre}
      libelleConfirmer="Vérifier"
      variantConfirmer="default"
      largeur="max-w-lg"
      onAnnuler={onFerme}
      onConfirmer={() => setEtape('verification')}
      confirmerDesactive={!formulaireValide}
      contenu={
        <div className="flex flex-col gap-4">
          <p className="text-sm text-neutral-700">
            Login : <span className="font-medium text-neutral-900">{utilisateur.login}</span>. L'identité vient
            de l'annuaire ; seul le rôle et le code unité sont gérés ici.
          </p>

          <ChampListe
            id="attribution-role"
            label="Rôle"
            obligatoire
            value={role}
            onChange={(event) => setRole(event.target.value as RoleEnum)}
            options={OPTIONS_ROLE}
          />

          <ChampTexte
            id="attribution-code-unite"
            label="Code unité"
            obligatoire={codeUniteObligatoire}
            erreur={erreurCodeUnite}
            value={codeUnite}
            onChange={(event) => setCodeUnite(event.target.value)}
            placeholder={codeUniteObligatoire ? 'Cinq chiffres, ex. 00002' : 'Facultatif pour ce rôle'}
            maxLength={5}
          />

          {erreur && <AffichageErreur erreur={erreur} />}
        </div>
      }
    />
  )
}
