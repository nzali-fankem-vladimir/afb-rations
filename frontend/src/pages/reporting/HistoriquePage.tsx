import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ArrowUpRight } from 'lucide-react'

import { AffichageErreur } from '../../components/communs/AffichageErreur'
import { Badge, BadgeStatutProcessus } from '../../components/communs/Badge'
import { Button } from '../../components/communs/Button'
import { Card, CardContent, CardHeader, CardTitle } from '../../components/communs/Card'
import { PageHeader } from '../../components/layout/PageHeader'
import { useAuth } from '../../hooks/useAuth'
import type { ApiErrorResponse } from '../../api/apiClient'
import type { EtapeHistoriqueResponse, HistoriqueResponse } from '../../api/reportingApi'
import { consulterHistorique } from '../../api/reportingApi'
import type { NomEtapeEnum, RoleEnum, StatutEtapeEnum } from '../../types/enums'
import { formatDateHeure, formatPeriode } from '../../utils/formatters'

/**
 * Où mène « Voir le dossier », selon le rôle qui consulte -- il n'existe pas
 * un seul écran de détail commun aux quatre rôles admis sur le suivi.
 * `/saisie/:id` n'est ouvert qu'à AGENT_UNITE (Sprint 7F.3) ; `/validation/:id`
 * qu'au circuit de validation, et reste en lecture seule une fois le dossier
 * clôturé (ExamenProcessusPage). Nul pour l'ARH : à portée nationale, aucun
 * des deux écrans ne lui est ouvert, et aucun équivalent n'existe encore --
 * l'historique reste sa seule vue d'un dossier (arbitré avec l'utilisateur,
 * rattrapage post-7F.7).
 */
function destinationDossier(role: RoleEnum | null, idProcessus: number): string | null {
  if (role === 'AGENT_UNITE') return `/saisie/${idProcessus}?onglet=consultation`
  if (role === 'CHEF_UNITE_DA' || role === 'DIRECTEUR_RESEAU_DR') return `/validation/${idProcessus}`
  return null
}

const LIBELLE_ETAPE: Record<NomEtapeEnum, string> = {
  SOUMISSION_AGENT: "Soumission · Agent d'unité",
  VALIDATION_DA: "Validation · Chef d'Unité",
  VALIDATION_DR: 'Validation · Directeur Réseau',
}

const VARIANTE_PAR_STATUT_ETAPE: Record<StatutEtapeEnum, 'neutre' | 'attente' | 'positif' | 'negatif'> = {
  EN_ATTENTE: 'attente',
  VALIDEE: 'positif',
  RETOURNEE: 'negatif',
}

const LIBELLE_STATUT_ETAPE: Record<StatutEtapeEnum, string> = {
  EN_ATTENTE: 'En attente',
  VALIDEE: 'Validée',
  RETOURNEE: 'Retournée',
}

/** Le login s'il existe, sinon le nom, sinon l'identifiant brut -- jamais un champ vide. */
function libelleActeur(etape: EtapeHistoriqueResponse): string {
  return etape.loginActeur ?? etape.nomActeur ?? (etape.idActeur !== null ? `acteur #${etape.idActeur}` : 'inconnu')
}

/**
 * Historique complet d'un dossier (guide 7F.7, etape 3), accessible depuis le
 * suivi. Rend TOUTES les etapes de GET /reporting/processus/{id}/historique,
 * dans l'ordre de leur rang -- y compris les passages repetes au meme niveau
 * apres un retour et une resoumission (Sprint 4.4). C'est la difference avec
 * le resume compact de l'ecran de validation (ExamenProcessusPage), qui ne
 * garde que la derniere validation de chaque niveau : ici, chaque passage
 * compte, c'est ce qui donne sa valeur de controle interne a cet ecran.
 */
export function HistoriquePage() {
  const { idProcessus } = useParams<{ idProcessus: string }>()
  const id = Number(idProcessus)
  const navigate = useNavigate()
  const { role } = useAuth()
  const destination = destinationDossier(role, id)

  const [historique, setHistorique] = useState<HistoriqueResponse | null>(null)
  const [chargement, setChargement] = useState(true)
  const [erreur, setErreur] = useState<ApiErrorResponse | null>(null)

  useEffect(() => {
    let annule = false
    consulterHistorique(id)
      .then((reponse) => {
        if (!annule) setHistorique(reponse)
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
  }, [id])

  if (chargement) {
    return (
      <>
        <PageHeader surTitre="Suivi" titre="Chargement…" />
        <div className="p-8" aria-busy="true" />
      </>
    )
  }

  if (erreur || !historique) {
    return (
      <>
        <PageHeader surTitre="Suivi" titre="Dossier introuvable" />
        <div className="p-8">{erreur && <AffichageErreur erreur={erreur} />}</div>
      </>
    )
  }

  return (
    <>
      <PageHeader
        filAriane={[{ libelle: 'Suivi', href: '/suivi' }, { libelle: `Unité ${historique.codeUnite}` }]}
        titre={formatPeriode(historique.dateDebut, historique.dateFin)}
        badge={<BadgeStatutProcessus statut={historique.statut} />}
        actions={
          destination && (
            <Button variant="outline" onClick={() => navigate(destination)}>
              Voir le dossier
              <ArrowUpRight className="h-4 w-4" aria-hidden="true" />
            </Button>
          )
        }
      />
      <div className="flex flex-col gap-6 p-8">
        <Card>
          <CardHeader>
            <CardTitle>Chronologie complète</CardTitle>
          </CardHeader>
          <CardContent>
            {historique.etapes.length === 0 ? (
              <p className="text-sm text-neutral-600">Aucune étape enregistrée pour ce dossier.</p>
            ) : (
              <ol className="flex flex-col gap-4">
                {historique.etapes.map((etape) => (
                  <li
                    key={`${etape.ordreEtape}-${etape.nomEtape}-${etape.dateAction}`}
                    className="flex gap-4 border-b border-neutral-100 pb-4 last:border-0 last:pb-0"
                  >
                    <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-neutral-100 text-xs font-semibold text-neutral-700">
                      {etape.ordreEtape}
                    </span>
                    <div className="flex flex-1 flex-col gap-1">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <span className="text-sm font-semibold text-neutral-900">{LIBELLE_ETAPE[etape.nomEtape]}</span>
                        <Badge variant={VARIANTE_PAR_STATUT_ETAPE[etape.statutEtape]}>
                          {LIBELLE_STATUT_ETAPE[etape.statutEtape]}
                        </Badge>
                      </div>
                      <p className="text-xs text-neutral-600">
                        {libelleActeur(etape)} · {formatDateHeure(etape.dateAction)}
                        {etape.signee && ' · signature apposée'}
                      </p>
                      {etape.motifRetour && (
                        <p className="mt-1 rounded-md bg-neutral-50 px-3 py-2 text-sm text-neutral-700">
                          Motif : {etape.motifRetour}
                        </p>
                      )}
                    </div>
                  </li>
                ))}
              </ol>
            )}
          </CardContent>
        </Card>
      </div>
    </>
  )
}
