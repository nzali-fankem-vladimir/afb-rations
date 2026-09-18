import { useEffect, useState } from 'react'

import { AffichageErreur } from '../../components/communs/AffichageErreur'
import { Alert, AlertDescription } from '../../components/communs/Alert'
import { BadgeStatutGrille } from '../../components/communs/Badge'
import { Button } from '../../components/communs/Button'
import { Card, CardContent, CardHeader, CardTitle } from '../../components/communs/Card'
import { Modale } from '../../components/communs/Modale'
import { Recapitulatif } from '../../components/communs/Recapitulatif'
import { PageHeader } from '../../components/layout/PageHeader'
import type { ApiErrorResponse } from '../../api/apiClient'
import { listerGrilles, validerGrille } from '../../api/grillesApi'
import type { GrilleResponse, ValidationGrilleResponse } from '../../api/grillesApi'
import { formatCombinaison, formatDateJJMMAAAA, formatMontantFcfa, veilleIso } from '../../utils/formatters'
import { RejetGrilleModale } from './RejetGrilleModale'

/** Ecart entre le montant propose et le montant actuellement actif, ou nul si aucune grille active n'existe. */
function ecart(montantPropose: number, montantActif: number | null): string | null {
  if (montantActif === null) return null
  const difference = montantPropose - montantActif
  if (difference === 0) return 'Aucun changement de montant'
  const signe = difference > 0 ? '+' : ''
  return `${signe}${difference.toLocaleString('fr-FR')} FCFA par rapport au tarif actuel`
}

/**
 * L'ecart comme tuile a part entiere, avec le pourcentage (retour utilisateur
 * sur la maquette de refonte, Sprint 7F.6) : "valider un tarif sans voir ce
 * qu'il remplace, c'est decider a l'aveugle". Nul si aucune grille active
 * n'existe encore sur la combinaison (premiere grille, aucun pourcentage
 * calculable).
 */
function ecartTuile(montantPropose: number, montantActif: number | null): { texte: string; pourcentage: string; hausse: boolean } | null {
  if (montantActif === null || montantActif === 0) return null
  const difference = montantPropose - montantActif
  const pourcentage = (difference / montantActif) * 100
  const signe = difference > 0 ? '+' : ''
  return {
    texte: `${signe}${difference.toLocaleString('fr-FR')} FCFA`,
    pourcentage: `soit ${signe}${pourcentage.toFixed(0)} %`,
    hausse: difference >= 0,
  }
}

/** true si la date (AAAA-MM-JJ) est strictement future par rapport a aujourd'hui. */
function estFuture(dateIso: string): boolean {
  const aujourdhui = new Date().toISOString().slice(0, 10)
  return dateIso > aujourdhui
}

interface ResultatDecision {
  idGrille: number
  validation?: ValidationGrilleResponse
  rejet?: GrilleResponse
}

/**
 * Ecran de décision, réservé à la Directrice RH (guide 7F.6, étape 4, RG-14).
 *
 * L'écart avec la grille en vigueur est une aide à la décision, pas un
 * ornement : valider un tarif sans voir ce qu'il remplace revient à décider
 * à l'aveugle (guide 7F.6, section « Points de vigilance »).
 *
 * Après validation : l'ancienne grille est fermée A LA VEILLE de la date de
 * début de la nouvelle (Sprint 2.3) -- si cette date est future, c'est une
 * FERMETURE PROGRAMMÉE, pas une fermeture déjà effective. La nouvelle
 * s'applique aux PRESTATIONS DATÉES à partir de sa date de début (Sprint
 * 2.4), jamais "aux nouvelles saisies" : une saisie rétroactive d'une
 * journée antérieure prend l'ancienne grille, et c'est voulu.
 */
export function DecisionGrillesPage() {
  const [enAttente, setEnAttente] = useState<GrilleResponse[]>([])
  const [actives, setActives] = useState<GrilleResponse[]>([])
  const [chargement, setChargement] = useState(true)
  const [erreur, setErreur] = useState<ApiErrorResponse | null>(null)

  const [idEnValidation, setIdEnValidation] = useState<number | null>(null)
  const [idModaleRejet, setIdModaleRejet] = useState<number | null>(null)
  const [grilleAValider, setGrilleAValider] = useState<GrilleResponse | null>(null)
  const [resultats, setResultats] = useState<Record<number, ResultatDecision>>({})

  // Aucune reinitialisation synchrone de `chargement` ici : elle est deja vraie
  // au montage (valeur initiale), et cet ecran ne recharge jamais la liste --
  // une decision met a jour son propre resultat local (setResultats),
  // jamais un nouvel appel a listerGrilles (meme convention que
  // ProcessusListPage, Sprint 7F.4).
  useEffect(() => {
    let annule = false
    Promise.all([
      listerGrilles({ statut: 'EN_ATTENTE_DRH', size: 100 }),
      listerGrilles({ statut: 'ACTIVE', size: 100 }),
    ])
      .then(([pageEnAttente, pageActives]) => {
        if (annule) return
        setEnAttente(pageEnAttente.content)
        setActives(pageActives.content)
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
  }, [])

  // Meme piege qu'a la consultation (GrillesTarifairesPage) : "dateFin === null"
  // ne veut PAS dire "en vigueur aujourd'hui" -- une grille deja validee a effet
  // futur (fermeture programmee) porte aussi dateFin === null. L'ecart de
  // montant doit se comparer au tarif reellement applique aujourd'hui.
  const aujourdhui = new Date().toISOString().slice(0, 10)

  const activeCourante = (grille: GrilleResponse) =>
    actives.find(
      (g) =>
        g.nature === grille.nature &&
        g.session === grille.session &&
        g.dateDebut <= aujourdhui &&
        (g.dateFin === null || g.dateFin >= aujourdhui),
    )

  const valider = async (grille: GrilleResponse) => {
    setIdEnValidation(grille.id)
    setErreur(null)
    try {
      const resultat = await validerGrille(grille.id)
      setResultats((precedent) => ({ ...precedent, [grille.id]: { idGrille: grille.id, validation: resultat } }))
    } catch (erreurApi) {
      setErreur(erreurApi as ApiErrorResponse)
    } finally {
      setIdEnValidation(null)
      setGrilleAValider(null)
    }
  }

  const activeAValider = grilleAValider ? activeCourante(grilleAValider) : undefined
  const grilleARejeter = enAttente.find((g) => g.id === idModaleRejet) ?? null

  return (
    <>
      <PageHeader surTitre="Grilles tarifaires" titre="Décisions en attente" />
      <div className="flex flex-col gap-6 p-8">
        {erreur && <AffichageErreur erreur={erreur} />}

        {chargement && <p className="text-sm text-neutral-600">Chargement…</p>}

        {!chargement && enAttente.length === 0 && (
          <p className="text-sm text-neutral-600">Aucune proposition en attente de décision.</p>
        )}

        {enAttente.map((grille) => {
          const active = activeCourante(grille)
          const resultat = resultats[grille.id]
          const decisionPrise = Boolean(resultat)

          return (
            <Card key={grille.id}>
              <CardHeader>
                <CardTitle>{formatCombinaison(grille.nature, grille.session)}</CardTitle>
              </CardHeader>
              <CardContent className="flex flex-col gap-4">
                <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
                  <div className="rounded border border-neutral-200 p-3">
                    <p className="text-xs uppercase text-neutral-600">Tarif actuel</p>
                    <p className="text-lg font-semibold tabular-nums text-neutral-900">
                      {active ? formatMontantFcfa(active.montantFcfa) : 'Aucun tarif actif'}
                    </p>
                    {active && (
                      <p className="text-xs text-neutral-600">depuis le {formatDateJJMMAAAA(active.dateDebut)}</p>
                    )}
                  </div>
                  <div className="rounded border border-neutral-200 bg-neutral-50 p-3">
                    <p className="text-xs uppercase text-neutral-600">Proposition</p>
                    <p className="text-lg font-semibold tabular-nums text-neutral-900">
                      {formatMontantFcfa(grille.montantFcfa)}
                    </p>
                    <p className="text-xs text-neutral-600">à partir du {formatDateJJMMAAAA(grille.dateDebut)}</p>
                  </div>
                  {(() => {
                    const tuile = ecartTuile(grille.montantFcfa, active?.montantFcfa ?? null)
                    return (
                      <div
                        className={
                          tuile === null
                            ? 'rounded border border-neutral-200 bg-neutral-50 p-3'
                            : tuile.hausse
                              ? 'rounded border border-emerald-200 bg-emerald-50 p-3'
                              : 'rounded border border-amber-200 bg-amber-50 p-3'
                        }
                      >
                        <p
                          className={
                            'text-xs uppercase ' + (tuile === null ? 'text-neutral-600' : tuile.hausse ? 'text-emerald-700' : 'text-amber-700')
                          }
                        >
                          Écart
                        </p>
                        <p
                          className={
                            'text-lg font-semibold tabular-nums ' +
                            (tuile === null ? 'text-neutral-900' : tuile.hausse ? 'text-emerald-700' : 'text-amber-700')
                          }
                        >
                          {tuile ? tuile.texte : 'Première grille'}
                        </p>
                        {tuile && (
                          <p className={tuile.hausse ? 'text-xs text-emerald-700' : 'text-xs text-amber-700'}>
                            {tuile.pourcentage}
                          </p>
                        )}
                      </div>
                    )
                  })()}
                </div>

                {active && (
                  <p className="text-sm text-neutral-600">
                    Si elle est validée, la grille actuelle sera close au{' '}
                    {formatDateJJMMAAAA(veilleIso(grille.dateDebut))}
                    {estFuture(grille.dateDebut) ? ' (fermeture programmée)' : ''}.
                  </p>
                )}

                <p className="text-xs text-neutral-600">
                  Proposée par {grille.createur ?? 'un Analyste RH'} le{' '}
                  {formatDateJJMMAAAA(grille.dateCreation.slice(0, 10))}.
                </p>

                {resultat?.validation && (
                  <Alert variant="default">
                    <AlertDescription>
                      <p className="font-medium">Grille validée et active.</p>
                      <p>
                        S'applique aux prestations datées à partir du{' '}
                        {formatDateJJMMAAAA(resultat.validation.grille.dateDebut)}, et non aux nouvelles saisies :
                        une saisie rétroactive d'une journée antérieure conserve l'ancien tarif.
                      </p>
                      {resultat.validation.ancienneFermee && (
                        <p>
                          Ancienne grille {estFuture(resultat.validation.ancienneFermee.dateFin ?? '') ? (
                            <strong>fermeture programmée</strong>
                          ) : (
                            'fermée'
                          )}{' '}
                          au {formatDateJJMMAAAA(resultat.validation.ancienneFermee.dateFin ?? '')}.
                        </p>
                      )}
                    </AlertDescription>
                  </Alert>
                )}

                {resultat?.rejet && (
                  <Alert variant="warning">
                    <AlertDescription>
                      <p className="font-medium">Grille rejetée.</p>
                      <p>Motif transmis à l'Analyste RH : {resultat.rejet.motifRejet}</p>
                      <p>La grille actuellement active n'a pas été modifiée.</p>
                    </AlertDescription>
                  </Alert>
                )}

                {!decisionPrise && (
                  <div className="flex items-center gap-3">
                    <BadgeStatutGrille statut={grille.statutValidation} />
                    <div className="ml-auto flex gap-3">
                      <Button
                        variant="outline"
                        onClick={() => setIdModaleRejet(grille.id)}
                        disabled={idEnValidation === grille.id}
                      >
                        Rejeter
                      </Button>
                      <Button onClick={() => setGrilleAValider(grille)} isLoading={idEnValidation === grille.id}>
                        Valider
                      </Button>
                    </div>
                  </div>
                )}
              </CardContent>
            </Card>
          )
        })}
      </div>

      {grilleAValider && (
        <Modale
          titre="Confirmer la validation"
          libelleConfirmer="Valider la grille"
          variantConfirmer="default"
          largeur="max-w-lg"
          onAnnuler={() => setGrilleAValider(null)}
          onConfirmer={() => valider(grilleAValider)}
          contenu={
            <div className="flex flex-col gap-4">
              <Recapitulatif
                lignes={[
                  { libelle: 'Combinaison', valeur: formatCombinaison(grilleAValider.nature, grilleAValider.session) },
                  {
                    libelle: 'Montant',
                    avant: activeAValider ? formatMontantFcfa(activeAValider.montantFcfa) : undefined,
                    valeur: formatMontantFcfa(grilleAValider.montantFcfa),
                  },
                  {
                    libelle: 'Écart',
                    valeur:
                      ecart(grilleAValider.montantFcfa, activeAValider?.montantFcfa ?? null) ??
                      'Première grille de cette combinaison',
                  },
                  { libelle: "Date d'effet", valeur: formatDateJJMMAAAA(grilleAValider.dateDebut) },
                  ...(activeAValider
                    ? [
                        {
                          libelle: 'Grille actuelle',
                          valeur: `${
                            estFuture(grilleAValider.dateDebut) ? 'fermeture programmée' : 'fermée'
                          } au ${formatDateJJMMAAAA(veilleIso(grilleAValider.dateDebut))}`,
                        },
                      ]
                    : []),
                ]}
              />
              <p className="text-sm text-neutral-700">
                La nouvelle grille s'appliquera aux prestations datées à partir du{' '}
                {formatDateJJMMAAAA(grilleAValider.dateDebut)}. Cette décision est définitive.
              </p>
            </div>
          }
        />
      )}

      {grilleARejeter && (
        <RejetGrilleModale
          grille={grilleARejeter}
          montantActif={activeCourante(grilleARejeter)?.montantFcfa ?? null}
          onFerme={() => setIdModaleRejet(null)}
          onSucces={(grille) => {
            setResultats((precedent) => ({ ...precedent, [grille.id]: { idGrille: grille.id, rejet: grille } }))
            setIdModaleRejet(null)
          }}
        />
      )}
    </>
  )
}
