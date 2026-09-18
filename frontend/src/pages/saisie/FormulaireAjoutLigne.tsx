import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Plus } from 'lucide-react'

import { AffichageErreur } from '../../components/communs/AffichageErreur'
import { Alert, AlertDescription } from '../../components/communs/Alert'
import { BoutonsSegmentes } from '../../components/communs/BoutonsSegmentes'
import { Button } from '../../components/communs/Button'
import { ChampTexte } from '../../components/communs/ChampTexte'
import type { ApiErrorResponse } from '../../api/apiClient'
import { resoudreMontant } from '../../api/grillesApi'
import type { LigneResponse } from '../../api/saisieApi'
import { creerLigne } from '../../api/saisieApi'
import { useToast } from '../../hooks/useToast'
import type { NatureEnum, SessionEnum } from '../../types/enums'
import { formatCombinaison, formatDateJJMMAAAA, formatDateLongue, formatMontantFcfa } from '../../utils/formatters'
import {
  LONGUEUR_CODE_AGENCE,
  LONGUEUR_COMPTE_COURANT,
  sansEspaces,
  validerBeneficiaire,
} from '../../utils/validationSaisie'

const OPTIONS_NATURE = [
  { valeur: 'RATION' as NatureEnum, libelle: 'Ration' },
  { valeur: 'TRANSPORT' as NatureEnum, libelle: 'Transport' },
]

const OPTIONS_SESSION = [
  { valeur: 'JOUR' as SessionEnum, libelle: 'Jour' },
  { valeur: 'SOIR' as SessionEnum, libelle: 'Soir' },
]

/**
 * Resultat de la resolution du tarif, rattache a la combinaison demandee
 * (`cle`) : un resultat dont la cle ne correspond plus a la saisie courante est
 * perime et n'est jamais affiche.
 */
type ResultatTarif = { cle: string } & (
  | { etat: 'trouve'; montant: number }
  | { etat: 'absent' }
  | { etat: 'indisponible' }
)

export interface FormulaireAjoutLigneProps {
  idFicheJournaliere: number
  /** Journee de la fiche (AAAA-MM-JJ) : le tarif affiche est celui de CETTE date (RG-03). */
  dateJour: string
  /** L'etat n'est plus modifiable (ETAT_NON_MODIFIABLE) : le formulaire se desactive plutot que d'echouer a l'appel. */
  disabled: boolean
  onAjout: (ligne: LigneResponse) => void
  /**
   * Vrai des qu'un champ du beneficiaire est rempli sans avoir ete enregistre.
   * La nature et la session seules ne comptent pas : elles se reselectionnent en
   * deux clics, et restent volontairement en place apres chaque ajout.
   */
  onBrouillonChange?: (brouillon: boolean) => void
}

/**
 * Formulaire d'ajout d'une ligne de prestation (guide 7F.4, etape 3 ; mise en
 * page revue au Sprint 7F.6, proposition n°5, variante B).
 *
 * <b>Deux blocs, dans l'ordre du geste</b> : « qui » (le beneficiaire) puis
 * « quoi » (la prestation). L'ancienne grille a trois colonnes melangeait les
 * deux, et l'agent passait de l'un a l'autre en remplissant une seule ligne.
 * Pas de modale : l'agent enchaine plusieurs lignes sur la meme journee.
 *
 * Nature et session en boutons a un clic (RG-01, RG-02) plutot qu'en listes
 * deroulantes : deux valeurs chacune, et le geste se repete a chaque ligne.
 *
 * Montant affiche des que la nature et la session sont choisies (proposition
 * n°2) : il est lu sur GET /grilles/active A LA DATE DE LA FICHE, jamais a la
 * date du jour. C'est un AFFICHAGE : aucun montant ne part dans la requete, le
 * service Saisie le resout et le fige lui-meme (RG-03). Si la valeur
 * enregistree differe de celle affichee -- une grille validee entre les deux --,
 * un avertissement le dit.
 *
 * Controle de forme au clic sur « Ajouter » (proposition n°3) : compte courant
 * a 11 chiffres (T-02), code agence a 5 chiffres, nom, prenom, nature et
 * session renseignes. Les erreurs s'affichent sous chaque champ, le premier
 * champ fautif prend le focus, et rien ne part au serveur tant qu'il en reste
 * une. Le serveur revalide de toute facon.
 *
 * Aucun tarif a cette date : le bouton « Ajouter » est desactive, le serveur
 * refuserait de toute facon (422 GRILLE_INDISPONIBLE). Service Grilles
 * injoignable : le bouton RESTE actif -- une panne n'est pas une regle, c'est
 * au serveur de trancher.
 */
export function FormulaireAjoutLigne({
  idFicheJournaliere,
  dateJour,
  disabled,
  onAjout,
  onBrouillonChange,
}: FormulaireAjoutLigneProps) {
  const { succes } = useToast()
  const [nature, setNature] = useState<NatureEnum | ''>('')
  const [session, setSession] = useState<SessionEnum | ''>('')
  const [nom, setNom] = useState('')
  const [prenom, setPrenom] = useState('')
  const [numCompteCourant, setNumCompteCourant] = useState('')
  const [codeAgence, setCodeAgence] = useState('')
  const [resultatTarif, setResultatTarif] = useState<ResultatTarif | null>(null)
  const [avertissementTarif, setAvertissementTarif] = useState<string | null>(null)
  const [enregistrement, setEnregistrement] = useState(false)
  const [erreur, setErreur] = useState<ApiErrorResponse | null>(null)
  // Les erreurs de champ ne s'affichent qu'apres un premier clic sur « Ajouter »,
  // puis se mettent a jour a chaque frappe : l'agent voit l'erreur disparaitre
  // des qu'il l'a corrigee, sans etre interrompu pendant qu'il tape.
  const [tentative, setTentative] = useState(false)

  const erreursBeneficiaire = validerBeneficiaire({ nom, prenom, numCompteCourant, codeAgence })
  const erreurNature = nature === '' ? 'Obligatoire' : undefined
  const erreurSession = session === '' ? 'Obligatoire' : undefined
  const idPremierChampInvalide = erreursBeneficiaire.nom
    ? 'ligne-nom'
    : erreursBeneficiaire.prenom
      ? 'ligne-prenom'
      : erreursBeneficiaire.numCompteCourant
        ? 'ligne-compte'
        : erreursBeneficiaire.codeAgence
          ? 'ligne-agence'
          : null

  const brouillon = nom !== '' || prenom !== '' || numCompteCourant !== '' || codeAgence !== ''

  // Synchronise la page parente (avertissement au changement de jour). Aucun
  // setState local ici : on informe le parent, on ne derive rien soi-meme.
  useEffect(() => {
    onBrouillonChange?.(brouillon)
  }, [brouillon, onBrouillonChange])

  const cleTarif = nature && session ? `${nature}|${session}|${dateJour}` : null

  // Aucun setState synchrone ici (convention du projet) : tant que le resultat
  // de la nouvelle combinaison n'est pas arrive, sa cle differe de `cleTarif`
  // et l'affichage le traite comme « recherche en cours ».
  useEffect(() => {
    if (!nature || !session) return
    let annule = false
    const cle = `${nature}|${session}|${dateJour}`
    resoudreMontant(nature, session, dateJour)
      .then((reponse) => {
        if (annule) return
        setResultatTarif(
          reponse.disponible && reponse.montantFcfa !== null
            ? { cle, etat: 'trouve', montant: reponse.montantFcfa }
            : { cle, etat: 'absent' },
        )
      })
      .catch(() => {
        if (!annule) setResultatTarif({ cle, etat: 'indisponible' })
      })
    return () => {
      annule = true
    }
  }, [nature, session, dateJour])

  const tarif = cleTarif !== null && resultatTarif?.cle === cleTarif ? resultatTarif : null
  const rechercheEnCours = cleTarif !== null && tarif === null
  const aucunTarif = tarif?.etat === 'absent'

  const champModifie = <T,>(setter: (valeur: T) => void) => (valeur: T) => {
    setter(valeur)
    setAvertissementTarif(null)
  }

  const reinitialiserBeneficiaire = () => {
    setNom('')
    setPrenom('')
    setNumCompteCourant('')
    setCodeAgence('')
  }

  const soumettre = async (evenement: FormEvent) => {
    evenement.preventDefault()
    setTentative(true)
    if (idPremierChampInvalide !== null) {
      document.getElementById(idPremierChampInvalide)?.focus()
      return
    }
    if (!nature || !session || aucunTarif) return

    setErreur(null)
    setAvertissementTarif(null)
    setEnregistrement(true)
    try {
      const ligne = await creerLigne({
        idFicheJournaliere,
        beneficiaire: { nom, prenom, numCompteCourant, codeAgence },
        nature,
        session,
      })
      if (tarif?.etat === 'trouve' && ligne.montantApplique !== null && ligne.montantApplique !== tarif.montant) {
        setAvertissementTarif(
          `Le tarif a changé entre-temps : montant enregistré ${formatMontantFcfa(ligne.montantApplique)} ` +
            `au lieu de ${formatMontantFcfa(tarif.montant)} affiché.`,
        )
      }
      succes(
        'Ligne ajoutée',
        `${nom} ${prenom} · ${formatCombinaison(nature, session)} · ` +
          (ligne.montantApplique === null ? 'Indisponible' : formatMontantFcfa(ligne.montantApplique)),
      )
      reinitialiserBeneficiaire()
      setTentative(false)
      // Le curseur revient sur le premier champ : l'agent enchaine la ligne
      // suivante sans repasser a la souris. Nature et session restent en place.
      document.getElementById('ligne-nom')?.focus()
      onAjout(ligne)
    } catch (erreurApi) {
      setErreur(erreurApi as ApiErrorResponse)
    } finally {
      setEnregistrement(false)
    }
  }

  return (
    <form
      onSubmit={soumettre}
      className="overflow-hidden rounded-lg border border-neutral-200 bg-white"
      aria-label={`Ajouter une ligne au ${formatDateJJMMAAAA(dateJour)}`}
    >
      <div className="flex flex-wrap items-baseline justify-between gap-2 border-b border-neutral-100 px-5 py-3.5">
        <h3 className="text-sm font-semibold text-neutral-900">
          Nouvelle ligne · {formatDateLongue(dateJour)}
        </h3>
        <p className="text-xs text-neutral-600">
          Les champs marqués <span className="text-primary-500">*</span> sont obligatoires
        </p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-[1.25fr_1fr]">
        {/* Bloc 1 : qui est servi. */}
        <fieldset className="flex flex-col gap-3 border-b border-neutral-200 p-5 lg:border-b-0 lg:border-r">
          <legend className="sr-only">Bénéficiaire</legend>
          <EnteteBloc numero={1} titre="Bénéficiaire" />

          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <ChampTexte
              id="ligne-nom"
              label="Nom"
              obligatoire
              disabled={disabled}
              erreur={tentative ? erreursBeneficiaire.nom : undefined}
              value={nom}
              onChange={(event) => champModifie(setNom)(event.target.value)}
            />
            <ChampTexte
              id="ligne-prenom"
              label="Prénom"
              obligatoire
              disabled={disabled}
              erreur={tentative ? erreursBeneficiaire.prenom : undefined}
              value={prenom}
              onChange={(event) => champModifie(setPrenom)(event.target.value)}
            />
          </div>

          <div className="grid grid-cols-1 gap-3 sm:grid-cols-[2fr_1fr]">
            {/* Pas de maxLength : un numero colle a 14 chiffres doit etre signale
                (« vous en avez saisi 14 »), pas tronque en silence a 11. */}
            <ChampTexte
              id="ligne-compte"
              label={`N° compte courant (${LONGUEUR_COMPTE_COURANT} chiffres)`}
              obligatoire
              disabled={disabled}
              inputMode="numeric"
              placeholder="03702099911"
              className="[&_input]:tabular-nums"
              erreur={tentative ? erreursBeneficiaire.numCompteCourant : undefined}
              value={numCompteCourant}
              onChange={(event) => champModifie(setNumCompteCourant)(sansEspaces(event.target.value))}
            />
            <ChampTexte
              id="ligne-agence"
              label={`Code agence (${LONGUEUR_CODE_AGENCE} chiffres)`}
              obligatoire
              disabled={disabled}
              inputMode="numeric"
              maxLength={LONGUEUR_CODE_AGENCE}
              placeholder="00002"
              className="[&_input]:tabular-nums"
              erreur={tentative ? erreursBeneficiaire.codeAgence : undefined}
              value={codeAgence}
              onChange={(event) => champModifie(setCodeAgence)(sansEspaces(event.target.value))}
            />
          </div>
        </fieldset>

        {/* Bloc 2 : ce qui lui est servi, et ce que cela coute. */}
        <fieldset className="flex flex-col gap-3 bg-neutral-50/60 p-5">
          <legend className="sr-only">Prestation</legend>
          <EnteteBloc numero={2} titre="Prestation" />

          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div className="flex flex-col gap-1.5">
              <span className="text-sm font-medium text-neutral-900">
                Nature <span className="text-primary-500">*</span>
              </span>
              <BoutonsSegmentes
                libelleGroupe="Nature"
                options={OPTIONS_NATURE}
                valeur={nature}
                disabled={disabled}
                erreur={tentative ? erreurNature : undefined}
                onChange={(valeur) => champModifie(setNature)(valeur)}
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <span className="text-sm font-medium text-neutral-900">
                Session <span className="text-primary-500">*</span>
              </span>
              <BoutonsSegmentes
                libelleGroupe="Session"
                options={OPTIONS_SESSION}
                valeur={session}
                disabled={disabled}
                erreur={tentative ? erreurSession : undefined}
                onChange={(valeur) => champModifie(setSession)(valeur)}
              />
            </div>
          </div>

          <TuileMontant dateJour={dateJour} tarif={tarif} rechercheEnCours={rechercheEnCours} />

          <Button
            type="submit"
            className="w-full"
            disabled={disabled || aucunTarif}
            isLoading={enregistrement}
            title={aucunTarif ? 'Aucun tarif applicable à cette date' : undefined}
          >
            {!enregistrement && <Plus className="h-4 w-4" aria-hidden="true" />}
            Ajouter la ligne
          </Button>
        </fieldset>
      </div>

      {(avertissementTarif || erreur) && (
        <div className="flex flex-col gap-3 border-t border-neutral-100 p-5">
          {avertissementTarif && (
            <Alert variant="warning">
              <AlertDescription>{avertissementTarif}</AlertDescription>
            </Alert>
          )}
          {erreur && <AffichageErreur erreur={erreur} />}
        </div>
      )}
    </form>
  )
}

function EnteteBloc({ numero, titre }: { numero: number; titre: string }) {
  return (
    <p className="flex items-center gap-2 text-sm font-semibold text-neutral-900">
      {/* Numerotation reelle : l'ordre de remplissage du formulaire. */}
      <span
        aria-hidden="true"
        className="grid h-5 w-5 place-items-center rounded-full bg-neutral-900 text-xxs font-bold text-white"
      >
        {numero}
      </span>
      {titre}
    </p>
  )
}

/**
 * Montant applicable, en lecture seule. Jamais un champ de saisie : le montant
 * vient toujours de la grille active (RG-03).
 */
function TuileMontant({
  dateJour,
  tarif,
  rechercheEnCours,
}: {
  dateJour: string
  tarif: ResultatTarif | null
  rechercheEnCours: boolean
}) {
  const aucunTarif = tarif?.etat === 'absent'

  return (
    <div
      className={`flex flex-col gap-0.5 rounded border border-dashed p-3 ${
        aucunTarif ? 'border-primary-500 bg-primary-50' : 'border-neutral-300 bg-white'
      }`}
    >
      <span className="text-xs text-neutral-600">Montant appliqué</span>
      <span
        aria-live="polite"
        className={`text-2xl font-bold tabular-nums ${aucunTarif ? 'text-primary-700' : 'text-neutral-900'}`}
      >
        {tarif?.etat === 'trouve'
          ? formatMontantFcfa(tarif.montant)
          : rechercheEnCours
            ? 'Recherche du tarif…'
            : aucunTarif
              ? 'Aucun tarif'
              : tarif?.etat === 'indisponible'
                ? 'Indisponible'
                : 'Aucune sélection'}
      </span>
      <span className="text-xs text-neutral-600">
        {tarif?.etat === 'trouve'
          ? `Tarif du ${formatDateJJMMAAAA(dateJour)} · confirmé à l'enregistrement`
          : aucunTarif
            ? `Aucune grille ne couvre le ${formatDateJJMMAAAA(dateJour)} : la ligne ne peut pas être ajoutée`
            : tarif?.etat === 'indisponible'
              ? "Service des grilles injoignable : le serveur déterminera le montant à l'enregistrement"
              : 'Choisissez la nature et la session'}
      </span>
    </div>
  )
}
