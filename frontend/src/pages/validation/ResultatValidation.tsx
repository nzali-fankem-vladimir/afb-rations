import { AlertTriangle, ArrowUpRight, CheckCircle2 } from 'lucide-react'

import { Alert, AlertDescription } from '../../components/communs/Alert'
import type { ValidationResponse } from '../../api/processusApi'
import { formatMontantFcfa } from '../../utils/formatters'

export interface ResultatValidationProps {
  resultat: ValidationResponse
}

/**
 * Rend explicite ce qu'une validation vient de declencher (guide 7F.5, etape 4) :
 * les TROIS valeurs d'aiguillage plus `null`, chacune avec son propre message --
 * jamais "transfere au DR parce que le montant depasse le seuil" pour toute
 * issue EN_ATTENTE_DR, qui serait FAUX sur un etat COMPLEMENTAIRE (Sprint 6bis.1).
 *
 * Le seuil n'est jamais affiche s'il est nul : un seuil nul veut dire qu'aucune
 * comparaison n'a eu lieu, pas qu'il vaut zero (CLAUDE.md section 15).
 */
export function ResultatValidation({ resultat }: ResultatValidationProps) {
  const { aiguillage, seuilApplique, transmission } = resultat

  let titre: string
  let detail: string | null
  let cloture: boolean

  switch (aiguillage) {
    case 'SOUS_SEUIL_CLOTURE_DIRECTE':
      titre = 'Dossier clôturé, envoyé à la comptabilité.'
      detail =
        seuilApplique !== null
          ? `Le montant est au plus égal au seuil appliqué (${formatMontantFcfa(seuilApplique)}).`
          : null
      cloture = true
      break
    case 'ENVOI_DIRECTEUR_RESEAU':
      titre = 'Dossier transféré au Directeur Réseau.'
      detail =
        seuilApplique !== null
          ? `Le montant dépasse le seuil appliqué (${formatMontantFcfa(seuilApplique)}).`
          : null
      cloture = false
      break
    case 'COMPLEMENTAIRE_ENVOI_DIRECTEUR_RESEAU':
      titre = 'Dossier transféré au Directeur Réseau.'
      // Ne JAMAIS parler de seuil ici : c'est une regularisation, elle monte
      // quel que soit le montant, et seuilApplique est toujours nul (aucune
      // comparaison n'a eu lieu).
      detail = "État complémentaire : il monte toujours au Directeur Réseau, quel que soit le montant."
      cloture = false
      break
    case null:
      titre = 'Dossier clôturé, envoyé à la comptabilité.'
      detail = "Validation par le Directeur Réseau : dernier niveau du circuit, aucune comparaison au seuil n'a eu lieu."
      cloture = true
      break
    default:
      titre = 'Décision enregistrée.'
      detail = null
      cloture = false
  }

  return (
    <div className="flex flex-col gap-3">
      <Alert variant="default">
        {cloture ? (
          <CheckCircle2 className="h-4 w-4 text-emerald-600" aria-hidden="true" />
        ) : (
          <ArrowUpRight className="h-4 w-4 text-amber-600" aria-hidden="true" />
        )}
        <AlertDescription>
          <p className="font-medium">{titre}</p>
          {detail && <p>{detail}</p>}
        </AlertDescription>
      </Alert>

      {/* Nul quand la validation ne cloture pas : un etat aiguille vers le DR
          n'a rien a transmettre. Ce bloc n'apparait donc jamais en meme temps
          que la fleche amber ci-dessus. */}
      {transmission && !transmission.transmis && (
        <Alert variant="warning">
          <AlertTriangle className="h-4 w-4" aria-hidden="true" />
          <AlertDescription>
            <p className="font-medium">
              Le dossier est clôturé, mais il n'est pas encore parti en paiement.
            </p>
            <p>{transmission.motif ?? 'Motif non précisé par le service de transmission.'}</p>
            <p>
              Aucune reprise automatique n'existe : signalez-le sans attendre à l'administrateur
              ou à la DSI.
            </p>
          </AlertDescription>
        </Alert>
      )}

      {transmission && transmission.transmis && (
        <p className="text-sm text-neutral-600">Transmis à la comptabilité.</p>
      )}
    </div>
  )
}
