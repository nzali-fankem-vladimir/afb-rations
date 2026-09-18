import { useState } from 'react'

import { AffichageErreur } from '../../components/communs/AffichageErreur'
import { Alert, AlertDescription } from '../../components/communs/Alert'
import { ChampListe } from '../../components/communs/ChampListe'
import { ChampTexte } from '../../components/communs/ChampTexte'
import { Modale } from '../../components/communs/Modale'
import { Recapitulatif } from '../../components/communs/Recapitulatif'
import type { ApiErrorResponse } from '../../api/apiClient'
import { proposerGrille } from '../../api/grillesApi'
import type { GrilleResponse } from '../../api/grillesApi'
import type { NatureEnum, SessionEnum } from '../../types/enums'
import { formatCombinaison, formatDateJJMMAAAA, formatMontantFcfa } from '../../utils/formatters'

export interface CreationGrilleModaleProps {
  /** Montant en vigueur aujourd'hui sur un couple, nul s'il n'y en a pas -- pour le recapitulatif. */
  montantEnVigueur: (nature: NatureEnum, session: SessionEnum) => number | null
  onFerme: () => void
  onSucces: (grille: GrilleResponse) => void
}

type Etape = 'saisie' | 'verification'

/**
 * Formulaire de proposition d'une grille, réservé à l'Analyste RH (guide
 * 7F.6, étape 3, RG-14).
 *
 * ATTENTION -- une grille ACTIVE sur le couple ne bloque JAMAIS la
 * proposition : proposer une date postérieure à celle en vigueur est le
 * remplacement normal (décision Sprint 2.2), et le backend l'accepte. Les
 * deux seuls refus 409 possibles sont distincts et ne disent pas la même
 * chose : GRILLE_EN_ATTENTE_EXISTANTE (une proposition attend déjà la DRH
 * sur ce couple, il faut attendre sa décision) et GRILLE_ACTIVE_EXISTANTE
 * (la date de début n'est pas strictement postérieure à celle en vigueur,
 * il faut une date plus tardive) -- le message du backend nomme déjà la
 * combinaison et la grille en cause, jamais remplacé par un texte fixe ici.
 *
 * Trois temps dans la même fenêtre : saisie, vérification « avant → après »
 * (proposition n°1 du 7F.6), puis confirmation qui rappelle que la
 * proposition est sans effet jusqu'à la décision de la DRH.
 */
export function CreationGrilleModale({ montantEnVigueur, onFerme, onSucces }: CreationGrilleModaleProps) {
  const [etape, setEtape] = useState<Etape>('saisie')
  const [nature, setNature] = useState<NatureEnum | ''>('')
  const [session, setSession] = useState<SessionEnum | ''>('')
  const [montantFcfa, setMontantFcfa] = useState('')
  const [dateDebut, setDateDebut] = useState('')
  const [erreur, setErreur] = useState<ApiErrorResponse | null>(null)
  const [grilleCreee, setGrilleCreee] = useState<GrilleResponse | null>(null)

  const formulaireComplet = nature !== '' && session !== '' && montantFcfa.trim() !== '' && dateDebut !== ''

  const proposer = async () => {
    if (nature === '' || session === '') return
    setErreur(null)
    try {
      const grille = await proposerGrille({ nature, session, montantFcfa: Number(montantFcfa), dateDebut })
      setGrilleCreee(grille)
    } catch (erreurApi) {
      setErreur(erreurApi as ApiErrorResponse)
    }
  }

  if (grilleCreee) {
    return (
      <Modale
        titre="Proposition enregistrée"
        onAnnuler={() => onSucces(grilleCreee)}
        contenu={
          <Alert variant="warning">
            <AlertDescription>
              <p className="font-medium">
                {formatCombinaison(grilleCreee.nature, grilleCreee.session)} :{' '}
                {formatMontantFcfa(grilleCreee.montantFcfa)}, à partir du{' '}
                {formatDateJJMMAAAA(grilleCreee.dateDebut)}.
              </p>
              <p>
                Elle est en attente de la Directrice RH et sans effet sur les saisies jusqu'à sa
                décision : le montant actuellement actif continue de s'appliquer.
              </p>
            </AlertDescription>
          </Alert>
        }
      />
    )
  }

  if (etape === 'verification' && nature !== '' && session !== '') {
    const actuel = montantEnVigueur(nature, session)
    return (
      <Modale
        titre="Vérifier la proposition"
        libelleConfirmer="Confirmer la proposition"
        variantConfirmer="default"
        libelleAnnuler="Retour"
        largeur="max-w-lg"
        onAnnuler={() => setEtape('saisie')}
        onConfirmer={proposer}
        contenu={
          <div className="flex flex-col gap-4">
            <Recapitulatif
              lignes={[
                { libelle: 'Combinaison', valeur: formatCombinaison(nature, session) },
                {
                  libelle: 'Montant',
                  avant: actuel === null ? undefined : formatMontantFcfa(actuel),
                  valeur: formatMontantFcfa(Number(montantFcfa)),
                },
                { libelle: "Date d'effet demandée", valeur: formatDateJJMMAAAA(dateDebut) },
              ]}
            />
            <p className="text-sm text-neutral-700">
              La proposition partira à la Directrice RH. Elle n'aura aucun effet sur les saisies
              tant qu'elle ne l'aura pas validée.
            </p>
            {erreur && <AffichageErreur erreur={erreur} />}
          </div>
        }
      />
    )
  }

  return (
    <Modale
      titre="Proposer une grille tarifaire"
      libelleConfirmer="Vérifier"
      variantConfirmer="default"
      largeur="max-w-lg"
      onAnnuler={onFerme}
      onConfirmer={() => setEtape('verification')}
      confirmerDesactive={!formulaireComplet}
      contenu={
        <div className="flex flex-col gap-4">
          <div className="flex gap-4">
            <ChampListe
              id="creation-grille-nature"
              label="Nature"
              obligatoire
              className="flex-1"
              libellePlaceholder="Sélectionner…"
              value={nature}
              onChange={(event) => setNature(event.target.value as NatureEnum)}
              options={[
                { valeur: 'RATION', libelle: 'Ration' },
                { valeur: 'TRANSPORT', libelle: 'Transport' },
              ]}
            />
            <ChampListe
              id="creation-grille-session"
              label="Session"
              obligatoire
              className="flex-1"
              libellePlaceholder="Sélectionner…"
              value={session}
              onChange={(event) => setSession(event.target.value as SessionEnum)}
              options={[
                { valeur: 'JOUR', libelle: 'Jour' },
                { valeur: 'SOIR', libelle: 'Soir' },
              ]}
            />
          </div>

          <ChampTexte
            id="creation-grille-montant"
            type="number"
            min={1}
            step={1}
            label="Montant (FCFA)"
            obligatoire
            value={montantFcfa}
            onChange={(event) => setMontantFcfa(event.target.value)}
            placeholder="Entier, sans décimale"
          />

          <ChampTexte
            id="creation-grille-date-debut"
            type="date"
            label="Date de début"
            obligatoire
            value={dateDebut}
            onChange={(event) => setDateDebut(event.target.value)}
          />

          {/* Refus serveur (409) : affiche aussi apres "Retour", pour corriger la saisie. */}
          {erreur && <AffichageErreur erreur={erreur} />}
        </div>
      }
    />
  )
}
