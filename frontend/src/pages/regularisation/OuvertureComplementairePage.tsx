import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { CheckCircle2, RotateCcw } from 'lucide-react'

import { AffichageErreur } from '../../components/communs/AffichageErreur'
import { Alert, AlertDescription } from '../../components/communs/Alert'
import { Button } from '../../components/communs/Button'
import { Card, CardContent, CardHeader, CardTitle } from '../../components/communs/Card'
import { ChampListe } from '../../components/communs/ChampListe'
import { ChampTexte } from '../../components/communs/ChampTexte'
import { Modale } from '../../components/communs/Modale'
import { Recapitulatif } from '../../components/communs/Recapitulatif'
import { PageHeader } from '../../components/layout/PageHeader'
import type { ApiErrorResponse } from '../../api/apiClient'
import { declencherProcessus } from '../../api/processusApi'
import { rechercherDemandes } from '../../api/reportingApi'
import type { DemandeResponse } from '../../api/reportingApi'
import { useToast } from '../../hooks/useToast'
import { cn } from '../../utils/cn'
import { formatMontantFcfa, formatPeriode } from '../../utils/formatters'
import type { MoisAnnee } from '../../utils/periodeMensuelle'
import { OPTIONS_MOIS, bornesDuMois, moisEtAnneeCourants, moisEtAnneePrecedents, optionsAnnee } from '../../utils/periodeMensuelle'

const TAILLE_LISTE_ORIGINES = 50

/**
 * Ouverture d'un etat complementaire (guide 7F.7, etape 6), reservee a l'agent
 * d'unite et accessible seulement quand RATTRAPAGE_ACTIF est ouvert -- la route
 * elle-meme est protegee, pas seulement le lien de menu (etape 5).
 *
 * <h2>Ce que cet ecran n'est pas</h2>
 *
 * Il n'y a NI formulaire de reclamation, NI liste de beneficiaires a rattraper.
 * Le signalement d'un oubli est externe au systeme, et aucune liste de
 * beneficiaires attendus n'existe dans ce processus : il n'y a pas
 * d'enrolement (CLAUDE.md section 15). C'est la difference de fond avec le mode
 * rattrapage du projet DOTTEL, qui calcule « les beneficiaires non payes » a
 * partir d'une liste d'enroles -- transposer ce calcul reintroduirait
 * l'enrolement par la porte de la regularisation.
 *
 * <h2>Pourquoi l'unite et les bornes ne sont pas saisissables</h2>
 *
 * Le backend compare codeUnite, dateDebut et dateFin a ceux de l'etat
 * d'origine et refuse au moindre ecart (PERIODE_NON_CONCORDANTE,
 * UNITE_NON_CONCORDANTE). Les laisser saisir reviendrait a offrir a l'agent de
 * provoquer un refus : ils sont donc pre-remplis depuis l'origine choisie et
 * rendus en lecture seule.
 *
 * <h2>Choix de l'origine par mois, pas par defilement d'une liste unique</h2>
 *
 * Meme constat que sur l'ecran de suivi (retour utilisateur, rattrapage
 * post-7F.7) : une liste d'etats clotures grossit indefiniment, et y
 * retrouver le bon a l'oeil devient penible. Le filtre `statut=CLOTURE` (deja
 * cote serveur) se combine desormais a `dateDebut`/`dateFin` du mois choisi --
 * meme module partage (`utils/periodeMensuelle.ts`) que le suivi.
 *
 * **Defaut sur le mois COURANT, comme le suivi** (arbitrage explicite de
 * l'utilisateur : comportement identique et previsible entre les deux ecrans,
 * plutot qu'un defaut different par ecran). Un etat du mois en cours n'etant
 * le plus souvent pas encore cloture, l'ecran peut s'ouvrir vide -- le
 * raccourci "Mois dernier", juste a cote, corrige cela en un clic.
 */
export function OuvertureComplementairePage() {
  const navigate = useNavigate()
  const { succes } = useToast()

  const [periode, setPeriode] = useState<MoisAnnee>(moisEtAnneeCourants)
  const [origines, setOrigines] = useState<DemandeResponse[]>([])
  const [chargementOrigines, setChargementOrigines] = useState(true)
  const [erreurOrigines, setErreurOrigines] = useState<ApiErrorResponse | null>(null)

  const [idOrigine, setIdOrigine] = useState('')
  const [motifOuverture, setMotifOuverture] = useState('')
  const [confirmationOuverte, setConfirmationOuverte] = useState(false)
  const [erreurOuverture, setErreurOuverture] = useState<ApiErrorResponse | null>(null)

  // Filtre par statut ET par periode cote SERVEUR : filtrer une liste deja
  // paginee cote client donnerait une selection fausse des que la premiere
  // page ne contient aucun etat cloture du mois choisi.
  useEffect(() => {
    let annule = false
    const { dateDebut, dateFin } = bornesDuMois(Number(periode.annee), Number(periode.mois))
    rechercherDemandes({ statut: 'CLOTURE', dateDebut, dateFin, page: 0, size: TAILLE_LISTE_ORIGINES })
      .then((reponse) => {
        if (annule) return
        setOrigines(reponse.content)
        setErreurOrigines(null)
      })
      .catch((erreurApi: ApiErrorResponse) => {
        if (!annule) setErreurOrigines(erreurApi as ApiErrorResponse)
      })
      .finally(() => {
        if (!annule) setChargementOrigines(false)
      })
    return () => {
      annule = true
    }
  }, [periode])

  const changerPeriode = (suivante: MoisAnnee) => {
    setChargementOrigines(true)
    setIdOrigine('')
    setPeriode(suivante)
  }

  // Retour au mois COURANT, comme au suivi (arbitrage explicite de
  // l'utilisateur, voir la javadoc de la fonction ci-dessus) -- meme porte de
  // sortie qu'au suivi, pour la meme raison : un filtre de periode n'a pas
  // d'etat neutre vers lequel un second clic sur un raccourci pourrait
  // retomber.
  const reinitialiserFiltres = () => changerPeriode(moisEtAnneeCourants())

  const origine = origines.find((demande) => String(demande.idProcessus) === idOrigine) ?? null
  const motifUtile = motifOuverture.trim()
  const formulaireComplet = origine !== null && motifUtile !== ''

  const courant = moisEtAnneeCourants()
  const moisEstCourant = periode.mois === courant.mois && periode.annee === courant.annee
  const precedent = moisEtAnneePrecedents()
  const moisEstPrecedent = periode.mois === precedent.mois && periode.annee === precedent.annee

  const ouvrir = async () => {
    if (!origine) return
    setErreurOuverture(null)
    try {
      // Les trois champs de l'origine sont recopies a l'identique : le backend
      // les compare, il ne les deduit pas.
      const complementaire = await declencherProcessus({
        dateDebut: origine.dateDebut,
        dateFin: origine.dateFin,
        codeUnite: origine.codeUnite,
        typeProcessus: 'COMPLEMENTAIRE',
        idProcessusOrigine: origine.idProcessus,
        motifOuverture: motifUtile,
      })
      setConfirmationOuverte(false)
      succes(
        'État complémentaire ouvert',
        `Période ${formatPeriode(complementaire.dateDebut, complementaire.dateFin)}`,
      )
      navigate(`/saisie/${complementaire.idProcessus}`)
    } catch (erreurApi) {
      setConfirmationOuverte(false)
      setErreurOuverture(erreurApi as ApiErrorResponse)
    }
  }

  return (
    <>
      <PageHeader surTitre="Régularisation" titre="Ouvrir un état complémentaire" />
      <div className="flex flex-col gap-6 p-8">
        {erreurOrigines && <AffichageErreur erreur={erreurOrigines} />}
        {erreurOuverture && <AffichageErreur erreur={erreurOuverture} />}

        <Alert variant="warning">
          <AlertDescription>
            <p className="font-medium">L'état d'origine ne sera pas modifié.</p>
            <p>
              Il reste clôturé avec ses signatures. Un nouvel état distinct est ouvert sur la même période, et il sera
              toujours validé par le Directeur Réseau, quel que soit son montant.
            </p>
          </AlertDescription>
        </Alert>

        <Card>
          <CardHeader>
            <CardTitle>État clôturé à régulariser</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-5">
            <div className="flex flex-wrap items-end gap-4">
              <ChampListe
                id="regularisation-mois"
                label="Mois"
                value={periode.mois}
                onChange={(event) => changerPeriode({ ...periode, mois: event.target.value })}
                options={OPTIONS_MOIS}
                className="w-40"
              />
              <ChampListe
                id="regularisation-annee"
                label="Année"
                value={periode.annee}
                onChange={(event) => changerPeriode({ ...periode, annee: event.target.value })}
                options={optionsAnnee()}
                className="w-28"
              />
              <div className="flex gap-2 pb-1.5">
                <button
                  type="button"
                  onClick={() => changerPeriode(moisEtAnneeCourants())}
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
                  onClick={() => changerPeriode(moisEtAnneePrecedents())}
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

            {chargementOrigines ? (
              <div aria-busy="true" className="flex flex-col gap-2">
                {Array.from({ length: 3 }).map((_, index) => (
                  <div key={index} className="h-16 animate-pulse rounded-lg bg-neutral-100" />
                ))}
              </div>
            ) : origines.length === 0 ? (
              <p className="text-sm text-neutral-600">
                Aucun état clôturé sur ce mois : choisissez un autre mois, ou une autre année.
              </p>
            ) : (
              <div role="radiogroup" aria-label="État clôturé à régulariser" className="flex max-h-80 flex-col gap-2 overflow-y-auto">
                {origines.map((demande) => {
                  const selectionne = String(demande.idProcessus) === idOrigine
                  return (
                    <button
                      key={demande.idProcessus}
                      type="button"
                      role="radio"
                      aria-checked={selectionne}
                      onClick={() => setIdOrigine(String(demande.idProcessus))}
                      className={cn(
                        'flex items-center justify-between gap-4 rounded-lg border px-4 py-3 text-left transition-colors',
                        selectionne
                          ? 'border-primary-500 bg-primary-50'
                          : 'border-neutral-200 bg-white hover:border-neutral-300 hover:bg-neutral-50',
                      )}
                    >
                      <div className="flex flex-col gap-0.5">
                        <span className="text-sm font-semibold text-neutral-900">
                          Unité {demande.codeUnite} · {formatPeriode(demande.dateDebut, demande.dateFin)}
                        </span>
                        <span className="text-xs text-neutral-500">n° {demande.idProcessus}</span>
                      </div>
                      <div className="flex items-center gap-3">
                        <span className="text-sm font-semibold tabular-nums text-neutral-900">
                          {formatMontantFcfa(demande.montantTotal)}
                        </span>
                        <CheckCircle2
                          className={cn('h-5 w-5', selectionne ? 'text-primary-500' : 'text-neutral-200')}
                          aria-hidden="true"
                        />
                      </div>
                    </button>
                  )
                })}
              </div>
            )}

            {origine && (
              <>
                <Recapitulatif
                  lignes={[
                    { libelle: 'Unité', valeur: origine.codeUnite },
                    { libelle: 'Période', valeur: formatPeriode(origine.dateDebut, origine.dateFin) },
                    { libelle: "Montant de l'état d'origine", valeur: formatMontantFcfa(origine.montantTotal) },
                  ]}
                />
                <p className="text-xs text-neutral-500">
                  Unité et période sont reprises de l'état d'origine et ne sont pas modifiables : le serveur les
                  compare, et le moindre écart ferait refuser l'ouverture.
                </p>
              </>
            )}

            <ChampTexte
              id="regularisation-motif"
              label="Motif d'ouverture"
              obligatoire
              placeholder="Ex. bénéficiaire non payé sur la journée du 3, signalé le 15 septembre"
              value={motifOuverture}
              onChange={(event) => setMotifOuverture(event.target.value)}
            />

            <div>
              <Button disabled={!formulaireComplet} onClick={() => setConfirmationOuverte(true)}>
                Ouvrir l'état complémentaire
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>

      {confirmationOuverte && origine && (
        <Modale
          titre="Confirmer l'ouverture"
          libelleConfirmer="Ouvrir l'état"
          variantConfirmer="default"
          largeur="max-w-lg"
          onAnnuler={() => setConfirmationOuverte(false)}
          onConfirmer={ouvrir}
          contenu={
            <div className="flex flex-col gap-4">
              <Recapitulatif
                lignes={[
                  { libelle: "État d'origine", valeur: `n° ${origine.idProcessus}` },
                  { libelle: 'Unité', valeur: origine.codeUnite },
                  { libelle: 'Période', valeur: formatPeriode(origine.dateDebut, origine.dateFin) },
                  { libelle: 'Motif', valeur: motifUtile },
                  { libelle: 'Suite', valeur: 'Validation par le Directeur Réseau, quel que soit le montant' },
                ]}
              />
              <p className="text-sm text-neutral-700">
                L'état n° {origine.idProcessus} reste clôturé et n'est pas modifié. Vous serez conduit à la saisie du
                nouvel état.
              </p>
            </div>
          }
        />
      )}
    </>
  )
}
