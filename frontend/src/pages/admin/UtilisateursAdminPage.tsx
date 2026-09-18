import { useEffect, useState } from 'react'

import { AffichageErreur } from '../../components/communs/AffichageErreur'
import { Alert, AlertDescription } from '../../components/communs/Alert'
import { Badge } from '../../components/communs/Badge'
import { Button } from '../../components/communs/Button'
import { ChampListe } from '../../components/communs/ChampListe'
import { ChampTexte } from '../../components/communs/ChampTexte'
import { Tableau } from '../../components/communs/Tableau'
import type { Colonne } from '../../components/communs/Tableau'
import { PageHeader } from '../../components/layout/PageHeader'
import { useAuth } from '../../hooks/useAuth'
import type { ApiErrorResponse } from '../../api/apiClient'
import { listerUtilisateurs } from '../../api/adminApi'
import type { UtilisateurResponse } from '../../api/adminApi'
import type { PageResponse } from '../../types/pagination'
import type { RoleEnum } from '../../types/enums'
import { formatDateHeure } from '../../utils/formatters'
import { LIBELLE_ROLE } from '../../utils/libelleRole'
import { AttributionRoleModale } from './AttributionRoleModale'

const TAILLE_PAGE = 20

const OPTIONS_ROLE_FILTRE = (Object.keys(LIBELLE_ROLE) as RoleEnum[]).map((role) => ({
  valeur: role,
  libelle: LIBELLE_ROLE[role],
}))

const OPTIONS_ACTIF = [
  { valeur: 'true', libelle: 'Actifs' },
  { valeur: 'false', libelle: 'Inactifs' },
]

/**
 * Administration des utilisateurs, réservée à l'ADMIN (guide 7F.6, étape 5).
 *
 * AUCUNE création ni suppression de compte : les comptes viennent de
 * l'annuaire (CLAUDE.md section 10), cet écran attribue seulement une
 * habilitation (rôle + code unité) à un profil déjà pré-provisionné.
 *
 * L'action d'attribution est désactivée sur la ligne de l'administrateur
 * connecté plutôt que laissée échouer : les deux refus 409 du Sprint 1.2
 * (auto-modification, dernier administrateur actif) restent gérés par
 * AttributionRoleModale pour l'appel direct par identifiant, mais ce cas
 * précis est anticipé côté interface.
 */
export function UtilisateursAdminPage() {
  const { utilisateur: profilCourant } = useAuth()

  const [page, setPage] = useState(0)
  const [filtreRole, setFiltreRole] = useState<RoleEnum | ''>('')
  const [filtreCodeUnite, setFiltreCodeUnite] = useState('')
  const [filtreActif, setFiltreActif] = useState<'' | 'true' | 'false'>('')

  const [donnees, setDonnees] = useState<PageResponse<UtilisateurResponse> | null>(null)
  const [chargement, setChargement] = useState(true)
  const [erreur, setErreur] = useState<ApiErrorResponse | null>(null)
  const [version, setVersion] = useState(0)

  const [utilisateurCible, setUtilisateurCible] = useState<UtilisateurResponse | null>(null)

  // Aucune reinitialisation synchrone de `chargement` ici : elle est deja vraie
  // au montage, et remise a vrai par les gestionnaires ci-dessous (changement
  // de page ou de filtre), jamais depuis l'effet lui-meme (meme convention que
  // ProcessusListPage, Sprint 7F.4).
  useEffect(() => {
    let annule = false
    listerUtilisateurs({
      role: filtreRole === '' ? undefined : filtreRole,
      codeUnite: filtreCodeUnite.trim() === '' ? undefined : filtreCodeUnite.trim(),
      actif: filtreActif === '' ? undefined : filtreActif === 'true',
      page,
      size: TAILLE_PAGE,
    })
      .then((reponse) => {
        if (annule) return
        setDonnees(reponse)
        setErreur(null)
      })
      .catch((erreurApi: ApiErrorResponse) => {
        if (!annule) setErreur(erreurApi)
      })
      .finally(() => {
        if (!annule) setChargement(false)
      })
    return () => {
      annule = true
    }
  }, [page, filtreRole, filtreCodeUnite, filtreActif, version])

  const changerPage = (nouvellePage: number) => {
    setChargement(true)
    setErreur(null)
    setPage(nouvellePage)
  }

  const changerFiltre = <T,>(setter: (valeur: T) => void) => (valeur: T) => {
    setChargement(true)
    setErreur(null)
    setPage(0)
    setter(valeur)
  }

  const recharger = () => {
    setChargement(true)
    setErreur(null)
    setVersion((precedent) => precedent + 1)
  }

  const COLONNES: Colonne<UtilisateurResponse>[] = [
    {
      cle: 'login',
      entete: 'Login',
      rendu: (utilisateur) => (
        <span className="flex items-center gap-2">
          {utilisateur.login}
          {profilCourant?.id === utilisateur.id && <Badge variant="neutre">vous</Badge>}
        </span>
      ),
    },
    { cle: 'nom', entete: 'Nom', rendu: (utilisateur) => `${utilisateur.nom} ${utilisateur.prenom}` },
    { cle: 'email', entete: 'Email' },
    { cle: 'role', entete: 'Rôle', rendu: (utilisateur) => LIBELLE_ROLE[utilisateur.role] },
    { cle: 'codeUnite', entete: 'Unité', rendu: (utilisateur) => utilisateur.codeUnite ?? 'Portée nationale' },
    {
      cle: 'actif',
      entete: 'Statut',
      rendu: (utilisateur) => (
        <Badge variant={utilisateur.actif ? 'positif' : 'neutre'}>{utilisateur.actif ? 'Actif' : 'Inactif'}</Badge>
      ),
    },
    {
      cle: 'dateDernierAcces',
      entete: 'Dernier accès',
      rendu: (utilisateur) => (utilisateur.dateDernierAcces ? formatDateHeure(utilisateur.dateDernierAcces) : 'Jamais'),
    },
  ]

  return (
    <>
      <PageHeader surTitre="Administration" titre="Utilisateurs" />
      <div className="flex flex-col gap-6 p-8">
        {erreur && <AffichageErreur erreur={erreur} />}

        <Alert variant="default">
          <AlertDescription>
            <p className="font-medium">Cet écran n'ouvre ni ne supprime de compte.</p>
            <p>
              Il attribue un rôle et un code unité à des comptes déjà existants dans l'annuaire de la
              banque (CLAUDE.md §10). L'ouverture d'un profil reste un geste distinct, hors de cet écran.
            </p>
          </AlertDescription>
        </Alert>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <ChampListe
            id="filtre-role"
            label="Rôle"
            libellePlaceholder="Tous les rôles"
            value={filtreRole}
            onChange={(event) => changerFiltre<RoleEnum | ''>(setFiltreRole)(event.target.value as RoleEnum | '')}
            options={OPTIONS_ROLE_FILTRE}
          />
          <ChampTexte
            id="filtre-code-unite"
            label="Code unité"
            placeholder="Ex. 00002"
            maxLength={5}
            value={filtreCodeUnite}
            onChange={(event) => changerFiltre<string>(setFiltreCodeUnite)(event.target.value)}
          />
          <ChampListe
            id="filtre-actif"
            label="Statut"
            libellePlaceholder="Tous"
            value={filtreActif}
            onChange={(event) => changerFiltre<'' | 'true' | 'false'>(setFiltreActif)(event.target.value as '' | 'true' | 'false')}
            options={OPTIONS_ACTIF}
          />
        </div>

        <Tableau
          colonnes={COLONNES}
          donnees={donnees?.content ?? []}
          cleLigne={(utilisateur) => utilisateur.id}
          chargement={chargement}
          messageVide="Aucun utilisateur ne correspond à ces filtres."
          actions={(utilisateur) => {
            const estSoiMeme = profilCourant?.id === utilisateur.id
            return (
              <Button
                variant="outline"
                size="sm"
                disabled={estSoiMeme}
                title={estSoiMeme ? 'Un administrateur ne peut pas modifier son propre rôle' : undefined}
                onClick={() => setUtilisateurCible(utilisateur)}
              >
                Attribuer
              </Button>
            )
          }}
          pagination={
            donnees
              ? {
                  page: donnees.page,
                  totalPages: donnees.totalPages,
                  totalElements: donnees.totalElements,
                  dernierePage: donnees.dernierePage,
                  onChangerPage: changerPage,
                }
              : undefined
          }
        />
      </div>

      {utilisateurCible && (
        <AttributionRoleModale
          utilisateur={utilisateurCible}
          onFerme={() => setUtilisateurCible(null)}
          onSucces={() => {
            setUtilisateurCible(null)
            recharger()
          }}
        />
      )}
    </>
  )
}
