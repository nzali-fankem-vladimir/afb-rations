import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Plus } from 'lucide-react'

import { AffichageErreur } from '../../components/communs/AffichageErreur'
import { BadgeStatutProcessus } from '../../components/communs/Badge'
import { Button } from '../../components/communs/Button'
import { Tableau } from '../../components/communs/Tableau'
import type { Colonne } from '../../components/communs/Tableau'
import { PageHeader } from '../../components/layout/PageHeader'
import { useAuth } from '../../hooks/useAuth'
import type { ApiErrorResponse } from '../../api/apiClient'
import { rechercherDemandes } from '../../api/reportingApi'
import type { DemandeResponse } from '../../api/reportingApi'
import type { PageResponse } from '../../types/pagination'
import { formatMontantFcfa, formatPeriode } from '../../utils/formatters'
import { DeclenchementModale } from './DeclenchementModale'

const TAILLE_PAGE = 20

const COLONNES: Colonne<DemandeResponse>[] = [
  {
    cle: 'periode',
    entete: 'Période',
    rendu: (demande) => formatPeriode(demande.dateDebut, demande.dateFin),
  },
  { cle: 'codeUnite', entete: 'Unité' },
  {
    cle: 'montantTotal',
    entete: 'Montant total',
    className: 'tabular-nums',
    rendu: (demande) => formatMontantFcfa(demande.montantTotal),
  },
  {
    cle: 'statut',
    entete: 'Statut',
    rendu: (demande) => <BadgeStatutProcessus statut={demande.statut} />,
  },
]

/**
 * Liste des processus de l'agent, et point d'entree du declenchement (guide
 * 7F.4, etape 2). La liste vient de GET /reporting/demandes -- la portee
 * d'acces y est deja resolue depuis le jeton, cote service Workflow : un agent
 * n'y voit que les etats de sa propre unite.
 */
export function ProcessusListPage() {
  const navigate = useNavigate()
  const { codeUnite } = useAuth()
  const [page, setPage] = useState(0)
  const [donnees, setDonnees] = useState<PageResponse<DemandeResponse> | null>(null)
  const [chargement, setChargement] = useState(true)
  const [erreurListe, setErreurListe] = useState<ApiErrorResponse | null>(null)
  const [modaleOuverte, setModaleOuverte] = useState(false)

  // Aucune reinitialisation synchrone de `chargement` ici : elle est deja vraie
  // au montage (valeur initiale) et remise a vrai par le changement de page
  // (gestionnaire de clic ci-dessous), jamais depuis l'effet lui-meme.
  useEffect(() => {
    let annule = false
    rechercherDemandes({ page, size: TAILLE_PAGE })
      .then((reponse) => {
        if (annule) return
        setDonnees(reponse)
        setErreurListe(null)
      })
      .catch((erreurApi: ApiErrorResponse) => {
        if (!annule) setErreurListe(erreurApi)
      })
      .finally(() => {
        if (!annule) setChargement(false)
      })
    return () => {
      annule = true
    }
  }, [page])

  const changerPage = (nouvellePage: number) => {
    setChargement(true)
    setErreurListe(null)
    setPage(nouvellePage)
  }

  return (
    <>
      <PageHeader surTitre="Saisie" titre="Mes processus" />
      <div className="flex flex-col gap-6 p-8">
        {erreurListe && <AffichageErreur erreur={erreurListe} />}

        <div className="flex justify-end">
          <Button onClick={() => setModaleOuverte(true)}>
            <Plus className="h-4 w-4" aria-hidden="true" />
            Déclencher un état
          </Button>
        </div>

        <Tableau
          colonnes={COLONNES}
          donnees={donnees?.content ?? []}
          cleLigne={(demande) => demande.idProcessus}
          chargement={chargement}
          onLigneClick={(demande) => navigate(`/saisie/${demande.idProcessus}`)}
          messageVide="Aucun état déclenché pour le moment."
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

      {modaleOuverte && (
        <DeclenchementModale
          codeUnite={codeUnite}
          onFerme={() => setModaleOuverte(false)}
          onSucces={(idProcessus) => {
            setModaleOuverte(false)
            navigate(`/saisie/${idProcessus}`)
          }}
        />
      )}
    </>
  )
}
