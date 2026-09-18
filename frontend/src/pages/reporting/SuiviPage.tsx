import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ChevronRight, RotateCcw } from 'lucide-react'

import { AffichageErreur } from '../../components/communs/AffichageErreur'
import { Badge, BadgeStatutProcessus } from '../../components/communs/Badge'
import { ChampListe } from '../../components/communs/ChampListe'
import { ChampTexte } from '../../components/communs/ChampTexte'
import { MessageListeVide } from '../../components/communs/MessageListeVide'
import { Tableau } from '../../components/communs/Tableau'
import type { Colonne } from '../../components/communs/Tableau'
import { PageHeader } from '../../components/layout/PageHeader'
import { useAuth } from '../../hooks/useAuth'
import type { ApiErrorResponse } from '../../api/apiClient'
import { rechercherDemandes } from '../../api/reportingApi'
import type { DemandeResponse, SituationIntegrationEnum } from '../../api/reportingApi'
import type { NatureEnum, RoleEnum, SessionEnum } from '../../types/enums'
import type { PageResponse } from '../../types/pagination'
import { formatMontantFcfa, formatPeriode } from '../../utils/formatters'
import type { MoisAnnee } from '../../utils/periodeMensuelle'
import { OPTIONS_MOIS, bornesDuMois, moisEtAnneeCourants, moisEtAnneePrecedents, optionsAnnee } from '../../utils/periodeMensuelle'

const TAILLE_PAGE = 20

/**
 * Roles a portee NATIONALE (Sprint 1.1) : seuls eux tirent un effet reel du
 * filtre "Unite", qui leur permet de se restreindre a une seule unite parmi
 * toutes celles qu'ils voient par defaut. Pour un agent ou un chef d'unite,
 * la portee est deja bornee a leur propre unite cote serveur -- l'afficher
 * leur montrerait un champ qui ne change jamais rien, contraire a la
 * doctrine du module (ne jamais montrer une commande sans effet).
 */
const ROLES_PORTEE_NATIONALE: RoleEnum[] = ['ARH', 'DIRECTEUR_RESEAU_DR']

const OPTIONS_NATURE = [
  { valeur: 'RATION' as NatureEnum, libelle: 'Ration' },
  { valeur: 'TRANSPORT' as NatureEnum, libelle: 'Transport' },
]

const OPTIONS_SESSION = [
  { valeur: 'JOUR' as SessionEnum, libelle: 'Jour' },
  { valeur: 'SOIR' as SessionEnum, libelle: 'Soir' },
]

/**
 * Vocabulaire impose (decision Sprint 6.2, redit au guide 7F.7) : "envoye a la
 * comptabilite", jamais "transmis" ni "paye". Un etat envoye peut etre rejete
 * ensuite -- rien n'a ete verse aux agents. Cinq situations distinctes plutot
 * que de reconstruire la logique a partir de transmisComptabilite et
 * statutIntegration (DemandeResponse les porte deja tous les deux, mais
 * `situationIntegration` est deja la traduction faite cote serveur).
 */
const VARIANTE_PAR_SITUATION: Record<SituationIntegrationEnum, 'neutre' | 'attente' | 'positif' | 'negatif'> = {
  NON_TRANSMIS: 'neutre',
  PUBLICATION_NON_CONFIRMEE: 'attente',
  EN_ATTENTE_ACCUSE: 'attente',
  INTEGRE: 'positif',
  REJETE: 'negatif',
}

const LIBELLE_SITUATION: Record<SituationIntegrationEnum, string> = {
  NON_TRANSMIS: 'Pas encore envoyé',
  // La situation la plus delicate a nommer juste : le module ignore si l'etat
  // est reellement parti sur le topic (Sprint 5.3, verrou de transmission).
  // "Non confirme" et non "echoue" : rien ne prouve un echec non plus.
  PUBLICATION_NON_CONFIRMEE: 'Envoi non confirmé',
  EN_ATTENTE_ACCUSE: "Envoyé, en attente d'accusé",
  INTEGRE: 'Envoyé et intégré',
  REJETE: 'Rejeté par la comptabilité',
}

function BadgeSituationIntegration({ situation }: { situation: SituationIntegrationEnum }) {
  return <Badge variant={VARIANTE_PAR_SITUATION[situation]}>{LIBELLE_SITUATION[situation]}</Badge>
}

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
    rendu: (demande) =>
      demande.typeProcessus === 'COMPLEMENTAIRE' ? <Badge variant="attente">Complémentaire</Badge> : 'Normal',
  },
  {
    cle: 'montantTotal',
    entete: 'Montant total',
    className: 'tabular-nums font-semibold text-neutral-900',
    rendu: (demande) => formatMontantFcfa(demande.montantTotal),
  },
  {
    cle: 'statut',
    entete: 'Avancement',
    rendu: (demande) => <BadgeStatutProcessus statut={demande.statut} />,
  },
  {
    cle: 'situationIntegration',
    entete: 'Comptabilité',
    rendu: (demande) => <BadgeSituationIntegration situation={demande.situationIntegration} />,
  },
  {
    cle: 'chevron',
    entete: '',
    className: 'w-8 text-neutral-400',
    rendu: () => <ChevronRight className="h-4 w-4" aria-hidden />,
  },
]

interface Filtres {
  /** 1 a 12. Jamais vide : contrairement aux autres filtres, une periode est toujours active. */
  mois: string
  annee: string
  codeUnite: string
  nature: string
  session: string
  beneficiaire: string
}

/**
 * Fonction, pas constante figee au chargement du module -- meme raison que
 * `optionsAnnee` : appelee a chaque reinitialisation, jamais une seule fois
 * au demarrage, sinon "aujourd'hui" resterait celui du chargement de la page
 * sur un onglet reste ouvert.
 */
function filtresInitiaux(): Filtres {
  return { ...moisEtAnneeCourants(), codeUnite: '', nature: '', session: '', beneficiaire: '' }
}

/**
 * Suivi multicritere des demandes (guide 7F.7, etape 2), ouvert a l'agent, au
 * circuit de validation et a l'ARH -- la DRH n'y a pas acces (le service
 * Reporting ne l'autorise pas, tableau du guide 7F.2). Chaque ligne mene a
 * l'historique complet du dossier.
 *
 * Recherche par MOIS ET ANNEE plutot que par bornes exactes (retour
 * utilisateur : exiger de connaitre les deux dates precises d'une periode
 * hebdomadaire oblige a les retenir par coeur). Le filtre serveur reste
 * `dateDebut`/`dateFin` (aucun changement d'API) : ce composant traduit le
 * mois choisi en bornes couvrant du premier au dernier jour, et le filtre
 * serveur est un CHEVAUCHEMENT (verifie dans ProcessusSpecifications.java,
 * pas suppose) -- un etat a cheval sur deux mois apparait dans les deux,
 * jamais invisible dans les deux. C'est la degradation du bon cote : une
 * periode ne peut jamais disparaitre d'une recherche par mois, au pire elle
 * apparait dans un mois de plus que necessaire.
 */
export function SuiviPage() {
  const navigate = useNavigate()
  const { role } = useAuth()
  const filtreUniteUtile = role !== null && ROLES_PORTEE_NATIONALE.includes(role)

  const [filtres, setFiltres] = useState<Filtres>(filtresInitiaux)
  const [filtresActifs, setFiltresActifs] = useState(false)
  const [page, setPage] = useState(0)
  const [donnees, setDonnees] = useState<PageResponse<DemandeResponse> | null>(null)
  const [chargement, setChargement] = useState(true)
  const [erreur, setErreur] = useState<ApiErrorResponse | null>(null)

  useEffect(() => {
    let annule = false
    const { dateDebut, dateFin } = bornesDuMois(Number(filtres.annee), Number(filtres.mois))
    rechercherDemandes({
      dateDebut,
      dateFin,
      codeUnite: filtres.codeUnite.trim() === '' ? undefined : filtres.codeUnite.trim(),
      nature: filtres.nature === '' ? undefined : (filtres.nature as NatureEnum),
      session: filtres.session === '' ? undefined : (filtres.session as SessionEnum),
      beneficiaire: filtres.beneficiaire.trim() === '' ? undefined : filtres.beneficiaire.trim(),
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
  }, [filtres, page])

  const changerFiltre = <K extends keyof Filtres>(cle: K, valeur: Filtres[K]) => {
    setChargement(true)
    setErreur(null)
    setPage(0)
    const suivants = { ...filtres, [cle]: valeur }
    setFiltres(suivants)
    setFiltresActifs(
      suivants.codeUnite !== '' || suivants.nature !== '' || suivants.session !== '' || suivants.beneficiaire !== '',
    )
  }

  const appliquerRaccourciMois = (calculer: () => MoisAnnee) => {
    setChargement(true)
    setErreur(null)
    setPage(0)
    setFiltres((precedents) => ({ ...precedents, ...calculer() }))
  }

  const changerPage = (nouvellePage: number) => {
    setChargement(true)
    setErreur(null)
    setPage(nouvellePage)
  }

  // Reinitialise VRAIMENT tout, y compris le mois et l'annee (retour au mois
  // courant) -- retour utilisateur : un filtre de periode qui n'a pas d'etat
  // neutre (contrairement a Nature/Session, qui retombent sur "toutes les
  // valeurs") a besoin d'une porte de sortie explicite, visible en
  // permanence, pas seulement proposee quand une recherche ne rend rien.
  const reinitialiserFiltres = () => {
    setChargement(true)
    setErreur(null)
    setPage(0)
    setFiltres(filtresInitiaux())
    setFiltresActifs(false)
  }

  const courant = moisEtAnneeCourants()
  const moisEstCourant = filtres.mois === courant.mois && filtres.annee === courant.annee
  const precedent = moisEtAnneePrecedents()
  const moisEstPrecedent = filtres.mois === precedent.mois && filtres.annee === precedent.annee

  return (
    <>
      <PageHeader surTitre="Suivi" titre="Suivi des demandes" />
      <div className="flex flex-col gap-6 p-8">
        {erreur && <AffichageErreur erreur={erreur} />}

        <div className="flex flex-col gap-4 rounded-lg border border-neutral-200 bg-white p-4">
          <div className="flex flex-wrap items-end gap-4">
            <ChampListe
              id="suivi-mois"
              label="Mois"
              value={filtres.mois}
              onChange={(event) => changerFiltre('mois', event.target.value)}
              options={OPTIONS_MOIS}
              className="w-40"
            />
            <ChampListe
              id="suivi-annee"
              label="Année"
              value={filtres.annee}
              onChange={(event) => changerFiltre('annee', event.target.value)}
              options={optionsAnnee()}
              className="w-28"
            />
            <div className="flex gap-2 pb-1.5">
              <button
                type="button"
                onClick={() => appliquerRaccourciMois(moisEtAnneeCourants)}
                aria-pressed={moisEstCourant}
                className={
                  moisEstCourant
                    ? 'rounded-full bg-primary-500 px-3 py-1 text-xs font-medium text-white'
                    : 'rounded-full border border-neutral-300 px-3 py-1 text-xs font-medium text-neutral-700 hover:bg-neutral-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-500'
                }
              >
                Ce mois-ci
              </button>
              <button
                type="button"
                onClick={() => appliquerRaccourciMois(moisEtAnneePrecedents)}
                aria-pressed={moisEstPrecedent}
                className={
                  moisEstPrecedent
                    ? 'rounded-full bg-primary-500 px-3 py-1 text-xs font-medium text-white'
                    : 'rounded-full border border-neutral-300 px-3 py-1 text-xs font-medium text-neutral-700 hover:bg-neutral-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-500'
                }
              >
                Mois dernier
              </button>
            </div>

            <button
              type="button"
              onClick={reinitialiserFiltres}
              className="ml-auto flex items-center gap-1.5 rounded px-2 py-1.5 text-xs font-medium text-neutral-600 hover:bg-neutral-100 hover:text-neutral-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
            >
              <RotateCcw className="h-3.5 w-3.5" aria-hidden="true" />
              Réinitialiser les filtres
            </button>
          </div>

          <div
            className={`grid grid-cols-1 gap-4 sm:grid-cols-2 ${filtreUniteUtile ? 'lg:grid-cols-4' : 'lg:grid-cols-3'}`}
          >
            {filtreUniteUtile && (
              <ChampTexte
                id="suivi-unite"
                label="Unité"
                placeholder="Toutes les unités"
                value={filtres.codeUnite}
                onChange={(event) => changerFiltre('codeUnite', event.target.value)}
              />
            )}
            <ChampListe
              id="suivi-nature"
              label="Nature"
              libellePlaceholder="Ration et transport"
              value={filtres.nature}
              onChange={(event) => changerFiltre('nature', event.target.value)}
              options={OPTIONS_NATURE}
            />
            <ChampListe
              id="suivi-session"
              label="Session"
              libellePlaceholder="Jour et soir"
              value={filtres.session}
              onChange={(event) => changerFiltre('session', event.target.value)}
              options={OPTIONS_SESSION}
            />
            <ChampTexte
              id="suivi-beneficiaire"
              label="Bénéficiaire"
              placeholder="Nom ou numéro de compte"
              value={filtres.beneficiaire}
              onChange={(event) => changerFiltre('beneficiaire', event.target.value)}
            />
          </div>
        </div>

        <Tableau
          colonnes={COLONNES}
          donnees={donnees?.content ?? []}
          cleLigne={(demande) => demande.idProcessus}
          chargement={chargement}
          onLigneClick={(demande) => navigate(`/suivi/${demande.idProcessus}`)}
          messageVide={
            filtresActifs ? (
              <MessageListeVide message="Aucune demande ne correspond à ces filtres." onEffacerFiltres={reinitialiserFiltres} />
            ) : (
              'Aucune demande sur cette période.'
            )
          }
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
