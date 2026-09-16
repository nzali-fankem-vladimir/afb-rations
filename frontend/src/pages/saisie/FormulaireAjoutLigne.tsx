import { useState } from 'react'
import type { FormEvent } from 'react'
import { Plus } from 'lucide-react'

import { AffichageErreur } from '../../components/communs/AffichageErreur'
import { Button } from '../../components/communs/Button'
import { ChampListe } from '../../components/communs/ChampListe'
import type { OptionListe } from '../../components/communs/ChampListe'
import { ChampMontant } from '../../components/communs/ChampMontant'
import { ChampTexte } from '../../components/communs/ChampTexte'
import type { ApiErrorResponse } from '../../api/apiClient'
import type { LigneResponse } from '../../api/saisieApi'
import { creerLigne } from '../../api/saisieApi'
import type { NatureEnum, SessionEnum } from '../../types/enums'

const OPTIONS_NATURE: OptionListe[] = [
  { valeur: 'RATION', libelle: 'Ration' },
  { valeur: 'TRANSPORT', libelle: 'Transport' },
]

const OPTIONS_SESSION: OptionListe[] = [
  { valeur: 'JOUR', libelle: 'Jour' },
  { valeur: 'SOIR', libelle: 'Soir' },
]

export interface FormulaireAjoutLigneProps {
  idFicheJournaliere: number
  /** L'etat n'est plus modifiable (ETAT_NON_MODIFIABLE) : le formulaire se desactive plutot que d'echouer a l'appel. */
  disabled: boolean
  onAjout: (ligne: LigneResponse) => void
}

/**
 * Formulaire d'ajout d'une ligne de prestation, epingle en haut du tableau du
 * jour (guide 7F.4, etape 3) -- pas de modale : l'agent enchaine plusieurs
 * lignes rapidement sur la meme journee.
 *
 * Aucun champ montant en saisie (RG-03) : il apparait en lecture seule apres
 * l'enregistrement, avec la valeur resolue par le serveur, et se reinitialise
 * des que l'agent modifie un champ -- jamais affiche comme la valeur d'une
 * ligne qu'il n'a pas encore soumise.
 */
export function FormulaireAjoutLigne({ idFicheJournaliere, disabled, onAjout }: FormulaireAjoutLigneProps) {
  const [nature, setNature] = useState<NatureEnum | ''>('')
  const [session, setSession] = useState<SessionEnum | ''>('')
  const [nom, setNom] = useState('')
  const [prenom, setPrenom] = useState('')
  const [numCompteCourant, setNumCompteCourant] = useState('')
  const [codeAgence, setCodeAgence] = useState('')
  const [montantResolu, setMontantResolu] = useState<number | null>(null)
  const [enregistrement, setEnregistrement] = useState(false)
  const [erreur, setErreur] = useState<ApiErrorResponse | null>(null)

  // Toute modification invalide le montant affiche : il ne decrit que la
  // derniere ligne effectivement enregistree, jamais un brouillon.
  function champModifie<T>(setter: (valeur: T) => void) {
    return (valeur: T) => {
      setter(valeur)
      setMontantResolu(null)
    }
  }

  const reinitialiserBeneficiaire = () => {
    setNom('')
    setPrenom('')
    setNumCompteCourant('')
    setCodeAgence('')
  }

  const soumettre = async (evenement: FormEvent) => {
    evenement.preventDefault()
    if (!nature || !session) return

    setErreur(null)
    setEnregistrement(true)
    try {
      const ligne = await creerLigne({
        idFicheJournaliere,
        beneficiaire: { nom, prenom, numCompteCourant, codeAgence },
        nature,
        session,
      })
      setMontantResolu(ligne.montantApplique)
      reinitialiserBeneficiaire()
      onAjout(ligne)
    } catch (erreurApi) {
      setErreur(erreurApi as ApiErrorResponse)
    } finally {
      setEnregistrement(false)
    }
  }

  return (
    <form onSubmit={soumettre} className="flex flex-col gap-4 rounded-lg border border-neutral-200 bg-white p-4">
      <h3 className="text-sm font-semibold text-neutral-900">Ajouter une ligne</h3>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <ChampListe
          id="ligne-nature"
          label="Nature"
          obligatoire
          disabled={disabled}
          options={OPTIONS_NATURE}
          libellePlaceholder="Sélectionner..."
          value={nature}
          onChange={(event) => champModifie(setNature)(event.target.value as NatureEnum)}
        />
        <ChampListe
          id="ligne-session"
          label="Session"
          obligatoire
          disabled={disabled}
          options={OPTIONS_SESSION}
          libellePlaceholder="Sélectionner..."
          value={session}
          onChange={(event) => champModifie(setSession)(event.target.value as SessionEnum)}
        />
        <ChampMontant id="ligne-montant" label="Montant" montant={montantResolu} />

        <ChampTexte
          id="ligne-nom"
          label="Nom"
          obligatoire
          disabled={disabled}
          value={nom}
          onChange={(event) => champModifie(setNom)(event.target.value)}
        />
        <ChampTexte
          id="ligne-prenom"
          label="Prénom"
          obligatoire
          disabled={disabled}
          value={prenom}
          onChange={(event) => champModifie(setPrenom)(event.target.value)}
        />
        <ChampTexte
          id="ligne-compte"
          label="N° compte courant"
          obligatoire
          disabled={disabled}
          value={numCompteCourant}
          onChange={(event) => champModifie(setNumCompteCourant)(event.target.value)}
        />
        <ChampTexte
          id="ligne-agence"
          label="Code agence"
          obligatoire
          disabled={disabled}
          maxLength={5}
          value={codeAgence}
          onChange={(event) => champModifie(setCodeAgence)(event.target.value)}
        />
      </div>

      {erreur && <AffichageErreur erreur={erreur} />}

      <div className="flex justify-end">
        <Button type="submit" disabled={disabled} isLoading={enregistrement}>
          {!enregistrement && <Plus className="h-4 w-4" aria-hidden="true" />}
          Ajouter
        </Button>
      </div>
    </form>
  )
}
