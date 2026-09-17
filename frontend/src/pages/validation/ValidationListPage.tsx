import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'

import { AffichageErreur } from '../../components/communs/AffichageErreur'
import { Badge } from '../../components/communs/Badge'
import { Tableau } from '../../components/communs/Tableau'
import type { Colonne } from '../../components/communs/Tableau'
import { PageHeader } from '../../components/layout/PageHeader'
import { useAuth } from '../../hooks/useAuth'
import type { ApiErrorResponse } from '../../api/apiClient'
import { rechercherDemandes } from '../../api/reportingApi'
import type { DemandeResponse } from '../../api/reportingApi'
import type { PageResponse } from '../../types/pagination'
import { STATUT_ATTENTE_PAR_ROLE } from '../../utils/statutProcessus'
import { formatMontantFcfa, formatPeriode } from '../../utils/formatters'

const TAILLE_PAGE = 20

const COLONNES: Colonne<DemandeResponse>[] = [
  {
    cle: 'periode',
    entete: 'Période',
    rendu: (demande) => formatPeriode(demande.dateDebut, demande.dateFin),
  },
  { cle: 'codeUnite', entete: 'Unité' },
  {
    cle: 'typeProcessus',
    entete: 'Type',
    // Un etat COMPLEMENTAIRE monte TOUJOURS au Directeur Reseau, quel que soit
    // son montant (Sprint 6bis.1) : signale ici pour qu'un DR ne s'etonne pas
    // de recevoir un dossier de quelques milliers de FCFA (guide 7F.5, etape 2).
    rendu: (demande) =>
      demande.typeProcessus === 'COMPLEMENTAIRE' ? (
        <Badge variant="attente">Complémentaire — toujours vers le DR</Badge>
      ) : (
        'Normal'
      ),
  },
  {
    cle: 'montantTotal',
    entete: 'Montant total',
    // Mis en evidence par le poids de la police, pas par la couleur : la charte
    // reserve le rouge aux accents (guide 7F.5, etape 2). C'est ce montant qui
    // determinera l'aiguillage a la validation.
    className: 'tabular-nums font-semibold text-neutral-900',
    rendu: (demande) => formatMontantFcfa(demande.montantTotal),
  },
]

/**
 * Liste des dossiers en attente du niveau de l'utilisateur connecte (guide
 * 7F.5, etape 2). Le statut EN_ATTENTE_DA ou EN_ATTENTE_DR est relaye au
 * serveur (GET /reporting/demandes?statut=...), qui resout deja la portee
 * (unite pour le Chef d'Unite, perimetre pour le Directeur Reseau) depuis le
 * jeton -- rien n'est filtre cote client sur une liste paginee par le serveur.
 */
export function ValidationListPage() {
  const navigate = useNavigate()
  const { role } = useAuth()
  const statut = role ? STATUT_ATTENTE_PAR_ROLE[role] : undefined

  const [page, setPage] = useState(0)
  const [donnees, setDonnees] = useState<PageResponse<DemandeResponse> | null>(null)
  const [chargement, setChargement] = useState(true)
  const [erreur, setErreur] = useState<ApiErrorResponse | null>(null)

  useEffect(() => {
    if (!statut) return
    let annule = false
    rechercherDemandes({ statut, page, size: TAILLE_PAGE })
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
  }, [statut, page])

  const changerPage = (nouvellePage: number) => {
    setChargement(true)
    setErreur(null)
    setPage(nouvellePage)
  }

  return (
    <>
      <PageHeader
        surTitre="Validation"
        titre={role === 'DIRECTEUR_RESEAU_DR' ? 'Dossiers en attente (Directeur Réseau)' : "Dossiers en attente (Chef d'Unité)"}
      />
      <div className="flex flex-col gap-6 p-8">
        {erreur && <AffichageErreur erreur={erreur} />}

        <Tableau
          colonnes={COLONNES}
          donnees={donnees?.content ?? []}
          cleLigne={(demande) => demande.idProcessus}
          chargement={chargement}
          onLigneClick={(demande) => navigate(`/validation/${demande.idProcessus}`)}
          messageVide="Aucun dossier en attente de votre niveau pour le moment."
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
    </>
  )
}
