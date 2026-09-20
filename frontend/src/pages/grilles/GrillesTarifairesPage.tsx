import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ChevronDown, ChevronUp } from 'lucide-react'

import { AffichageErreur } from '../../components/communs/AffichageErreur'
import { Alert, AlertDescription } from '../../components/communs/Alert'
import { BadgeStatutGrille } from '../../components/communs/Badge'
import { Button } from '../../components/communs/Button'
import { Card, CardContent, CardHeader, CardTitle } from '../../components/communs/Card'
import { Tableau } from '../../components/communs/Tableau'
import type { Colonne } from '../../components/communs/Tableau'
import { PageHeader } from '../../components/layout/PageHeader'
import { useAuth } from '../../hooks/useAuth'
import { useToast } from '../../hooks/useToast'
import type { ApiErrorResponse } from '../../api/apiClient'
import { listerGrilles } from '../../api/grillesApi'
import type { GrilleResponse } from '../../api/grillesApi'
import type { NatureEnum, SessionEnum } from '../../types/enums'
import { NON_RENSEIGNE, SANS_OBJET, formatCombinaison, formatDateJJMMAAAA, formatMontantFcfa } from '../../utils/formatters'
import { CreationGrilleModale } from './CreationGrilleModale'
import { RetraitGrilleModale } from './RetraitGrilleModale'

interface Combinaison {
  nature: NatureEnum
  session: SessionEnum
  libelle: string
}

/** Les quatre combinaisons possibles (RG-01, RG-02), toujours dans le meme ordre. */
const COMBINAISONS: Combinaison[] = [
  { nature: 'RATION', session: 'JOUR', libelle: formatCombinaison('RATION', 'JOUR') },
  { nature: 'RATION', session: 'SOIR', libelle: formatCombinaison('RATION', 'SOIR') },
  { nature: 'TRANSPORT', session: 'JOUR', libelle: formatCombinaison('TRANSPORT', 'JOUR') },
  { nature: 'TRANSPORT', session: 'SOIR', libelle: formatCombinaison('TRANSPORT', 'SOIR') },
]

const COLONNES_HISTORIQUE: Colonne<GrilleResponse>[] = [
  {
    cle: 'combinaison',
    entete: 'Combinaison',
    rendu: (grille) => formatCombinaison(grille.nature, grille.session),
  },
  { cle: 'montantFcfa', entete: 'Montant', className: 'tabular-nums', rendu: (grille) => formatMontantFcfa(grille.montantFcfa) },
  { cle: 'dateDebut', entete: 'Début', rendu: (grille) => formatDateJJMMAAAA(grille.dateDebut) },
  {
    cle: 'dateFin',
    entete: 'Fin',
    // Une grille REJETEE n'a jamais eu de date de fin (elle n'a jamais ete
    // active) : "sans objet" plutot que "non renseigne", qui suggererait une
    // donnee manquante alors que la notion elle-meme ne s'applique pas.
    rendu: (grille) => (grille.dateFin ? formatDateJJMMAAAA(grille.dateFin) : SANS_OBJET),
  },
  { cle: 'statutValidation', entete: 'Statut', rendu: (grille) => <BadgeStatutGrille statut={grille.statutValidation} /> },
  { cle: 'createur', entete: 'Créée par', rendu: (grille) => grille.createur ?? NON_RENSEIGNE },
  {
    cle: 'motifRejet',
    entete: 'Détail',
    rendu: (grille) =>
      grille.statutValidation === 'REJETEE' ? `Rejetée : ${grille.motifRejet ?? ''}` : 'Remplacée',
  },
]

/** "AAAA-MM-JJ" du jour, pour situer chaque grille par rapport à aujourd'hui (RG-03). */
function aujourdhuiIso(): string {
  return new Date().toISOString().slice(0, 10)
}

/**
 * Vrai si la grille couvre la date du jour -- même règle de résolution que
 * RG-03 (montant résolu à la date de la prestation), appliquée à "aujourd'hui"
 * pour l'affichage.
 *
 * ATTENTION -- ne PAS confondre avec "dateFin === null". Une grille validée à
 * effet futur (fermeture programmée, Sprint 2.3) porte déjà `dateFin === null`
 * bien qu'elle ne soit pas encore en vigueur : `dateFin === null` signifie
 * seulement "n'a pas encore été remplacée", pas "en vigueur aujourd'hui". Deux
 * grilles ACTIVE consécutives peuvent coexister -- l'une couvrant aujourd'hui
 * avec sa `dateFin` déjà posée, l'autre `dateFin` nulle mais `dateDebut` futur.
 */
function enVigueurLe(grille: GrilleResponse, dateIso: string): boolean {
  return grille.dateDebut <= dateIso && (grille.dateFin === null || grille.dateFin >= dateIso)
}

/**
 * Ecran de consultation des grilles tarifaires (guide 7F.6, étape 2), ouvert à
 * l'ARH et à la DRH.
 *
 * Une grille close conserve le statut ACTIVE avec une dateFin renseignée --
 * la fermeture est une borne, pas un statut (Sprint 2.3, ValidationGrilleResponse.java).
 * La "grille active courante" d'un couple est celle qui couvre la date du
 * jour (voir `enVigueurLe`) ; l'historique regroupe celles dont la période est
 * entièrement passée, et une éventuelle grille validée à effet futur est
 * signalée à part ("grille programmée"), jamais confondue avec le montant
 * actif.
 *
 * Point essentiel : une grille EN_ATTENTE_DRH est affichée dans un bandeau
 * distinct du montant actif, jamais mélangée à lui -- pour qu'un Analyste RH
 * ne puisse jamais croire que sa proposition s'applique déjà (CLAUDE.md
 * décision Sprint 2.2, RG-14).
 */
export function GrillesTarifairesPage() {
  const navigate = useNavigate()
  const { role } = useAuth()
  const { succes } = useToast()

  const [actives, setActives] = useState<GrilleResponse[]>([])
  const [enAttente, setEnAttente] = useState<GrilleResponse[]>([])
  const [rejetees, setRejetees] = useState<GrilleResponse[]>([])
  const [chargement, setChargement] = useState(true)
  const [erreur, setErreur] = useState<ApiErrorResponse | null>(null)
  const [historiqueOuvert, setHistoriqueOuvert] = useState(false)
  const [modaleOuverte, setModaleOuverte] = useState(false)
  const [grilleARetirer, setGrilleARetirer] = useState<GrilleResponse | null>(null)
  const [version, setVersion] = useState(0)

  // Aucune reinitialisation synchrone de `chargement` ici : elle est deja
  // vraie au montage (valeur initiale) et remise a vrai par `recharger`
  // ci-dessous, jamais depuis l'effet lui-meme (meme convention que
  // ProcessusListPage, Sprint 7F.4).
  useEffect(() => {
    let annule = false
    // Trois requetes separees, une par statut : le statut ACTIVE seul melange
    // la grille couvrant aujourd'hui et celles deja closes (dateFin posee),
    // distinguees cote client par dateFin. Taille bornee a 100 -- suffisant
    // pour quatre combinaisons, sans pagination reelle sur cet ecran.
    Promise.all([
      listerGrilles({ statut: 'ACTIVE', size: 100 }),
      listerGrilles({ statut: 'EN_ATTENTE_DRH', size: 100 }),
      listerGrilles({ statut: 'REJETEE', size: 100 }),
    ])
      .then(([pageActives, pageEnAttente, pageRejetees]) => {
        if (annule) return
        setActives(pageActives.content)
        setEnAttente(pageEnAttente.content)
        setRejetees(pageRejetees.content)
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
  }, [version])

  const recharger = () => {
    setChargement(true)
    setErreur(null)
    setVersion((precedent) => precedent + 1)
  }

  const aujourdhui = aujourdhuiIso()

  const activeCourante = (combinaison: Combinaison) =>
    actives.find(
      (g) => g.nature === combinaison.nature && g.session === combinaison.session && enVigueurLe(g, aujourdhui),
    )

  /** Grille déjà validée mais dont la date de début n'est pas encore atteinte (fermeture programmée de l'ancienne). */
  const grilleProgrammee = (combinaison: Combinaison) =>
    actives.find(
      (g) => g.nature === combinaison.nature && g.session === combinaison.session && g.dateDebut > aujourdhui,
    )

  const propositionEnAttente = (combinaison: Combinaison) =>
    enAttente.find((g) => g.nature === combinaison.nature && g.session === combinaison.session)

  // Historique = periodes entierement passees uniquement : une grille dont la
  // dateFin est deja posee mais >= aujourd'hui (fermeture programmee) reste
  // en vigueur, ce n'est pas encore de l'historique.
  const activesFermees = actives.filter((g) => g.dateFin !== null && g.dateFin < aujourdhui)
  const historique = [...activesFermees, ...rejetees].sort((a, b) => b.dateCreation.localeCompare(a.dateCreation))

  return (
    <>
      <PageHeader surTitre="Grilles tarifaires" titre="Consultation des grilles" />
      <div className="flex flex-col gap-6 p-8">
        {erreur && <AffichageErreur erreur={erreur} />}

        <div className="flex items-center justify-between">
          <p className="text-sm text-neutral-600">
            Le montant actif est celui appliqué aux prestations datées à partir de sa date de
            début (RG-03). Une proposition en attente de la Directrice RH n'a aucun effet.
          </p>
          {role === 'ARH' && <Button onClick={() => setModaleOuverte(true)}>Proposer une grille</Button>}
          {role === 'DRH' && enAttente.length > 0 && (
            <Button onClick={() => navigate('/grilles/decisions')}>
              Décisions en attente ({enAttente.length})
            </Button>
          )}
        </div>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          {COMBINAISONS.map((combinaison) => {
            const active = activeCourante(combinaison)
            const programmee = grilleProgrammee(combinaison)
            const proposition = propositionEnAttente(combinaison)

            return (
              <Card key={`${combinaison.nature}-${combinaison.session}`}>
                <CardHeader>
                  <CardTitle>{combinaison.libelle}</CardTitle>
                </CardHeader>
                <CardContent className="flex flex-col gap-4">
                  {chargement ? (
                    <div className="h-6 w-2/3 animate-pulse rounded bg-neutral-200" />
                  ) : active ? (
                    <div className="flex items-baseline justify-between">
                      <span className="text-2xl font-semibold tabular-nums text-neutral-900">
                        {formatMontantFcfa(active.montantFcfa)}
                      </span>
                      <span className="text-xs text-neutral-600">
                        actif depuis le {formatDateJJMMAAAA(active.dateDebut)}
                      </span>
                    </div>
                  ) : (
                    <Alert variant="warning">
                      <AlertDescription>Aucune grille active pour cette combinaison.</AlertDescription>
                    </Alert>
                  )}

                  {programmee && (
                    <Alert variant="default">
                      <AlertDescription>
                        <p className="font-medium">Grille programmée</p>
                        <p>
                          {formatMontantFcfa(programmee.montantFcfa)} à partir du{' '}
                          {formatDateJJMMAAAA(programmee.dateDebut)}. Déjà validée par la
                          Directrice RH ; elle remplacera le montant actif ci-dessus à cette date.
                        </p>
                      </AlertDescription>
                    </Alert>
                  )}

                  {proposition && (
                    <Alert variant="warning">
                      <AlertDescription>
                        <div className="mb-1 flex items-center gap-2">
                          <BadgeStatutGrille statut={proposition.statutValidation} />
                          <span className="font-medium">Proposition en attente</span>
                        </div>
                        <p>
                          {formatMontantFcfa(proposition.montantFcfa)} à partir du{' '}
                          {formatDateJJMMAAAA(proposition.dateDebut)}, proposée par{' '}
                          {proposition.createur ?? 'un Analyste RH'}.
                        </p>
                        <p className="mt-1 font-medium">
                          Sans effet sur les saisies tant que la Directrice RH n'a pas tranché :
                          le montant actif reste celui affiché ci-dessus.
                        </p>
                        {role === 'ARH' && (
                          <Button
                            variant="outline"
                            size="sm"
                            className="mt-2"
                            onClick={() => setGrilleARetirer(proposition)}
                          >
                            Retirer ma proposition
                          </Button>
                        )}
                      </AlertDescription>
                    </Alert>
                  )}
                </CardContent>
              </Card>
            )
          })}
        </div>

        <div>
          <Button variant="ghost" onClick={() => setHistoriqueOuvert((ouvert) => !ouvert)}>
            {historiqueOuvert ? <ChevronUp className="h-4 w-4" aria-hidden="true" /> : <ChevronDown className="h-4 w-4" aria-hidden="true" />}
            Historique des grilles fermées et rejetées ({historique.length})
          </Button>

          {historiqueOuvert && (
            <div className="mt-3">
              <Tableau
                colonnes={COLONNES_HISTORIQUE}
                donnees={historique}
                cleLigne={(grille) => grille.id}
                chargement={chargement}
                messageVide="Aucune grille fermée ou rejetée pour le moment."
              />
            </div>
          )}
        </div>
      </div>

      {modaleOuverte && (
        <CreationGrilleModale
          montantEnVigueur={(nature, session) =>
            activeCourante({ nature, session, libelle: '' })?.montantFcfa ?? null
          }
          onFerme={() => setModaleOuverte(false)}
          onSucces={() => {
            setModaleOuverte(false)
            recharger()
          }}
        />
      )}

      {grilleARetirer && (
        <RetraitGrilleModale
          grille={grilleARetirer}
          onFerme={() => setGrilleARetirer(null)}
          onSucces={() => {
            succes('Proposition retirée', formatCombinaison(grilleARetirer.nature, grilleARetirer.session))
            setGrilleARetirer(null)
            recharger()
          }}
        />
      )}
    </>
  )
}
