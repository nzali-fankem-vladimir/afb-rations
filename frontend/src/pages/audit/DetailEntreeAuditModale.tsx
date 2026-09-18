import { Badge } from '../../components/communs/Badge'
import { Modale } from '../../components/communs/Modale'
import type { AuditEntreeResponse } from '../../api/auditApi'
import { analyserDetailJson, identifiantConcerne, libelleAction, libelleService } from '../../utils/auditLisible'
import { NON_RENSEIGNE, formatDateHeure } from '../../utils/formatters'

export interface DetailEntreeAuditModaleProps {
  entree: AuditEntreeResponse
  onFerme: () => void
}

/**
 * Detail complet d'une entree du journal d'audit, en lecture facilitee
 * (meme demande que pour le detail par journee de l'ecran de validation) :
 * la colonne "Ce qui s'est passe" du tableau tronque le delta a une zone de
 * defilement de 96px pour rester lisible en ligne -- cette modale montre
 * TOUT, sans troncature, avec le contexte complet de l'evenement.
 *
 * Lecture seule, comme le reste de cet ecran : le journal est immuable
 * (CLAUDE.md section 4), aucune action n'est proposee depuis cette modale.
 */
export function DetailEntreeAuditModale({ entree, onFerme }: DetailEntreeAuditModaleProps) {
  const delta = analyserDetailJson(entree.detailJson)
  const auteurNomme = delta.find((ligne) => ligne.cle === 'login' || ligne.cle === 'auteur')?.valeur

  return (
    <Modale
      titre={libelleAction(entree.action)}
      largeur="max-w-lg"
      onAnnuler={onFerme}
      contenu={
        <div className="flex flex-col gap-5">
          <dl className="grid grid-cols-2 gap-x-4 gap-y-3 rounded-lg border border-neutral-200 bg-neutral-50 p-4 text-sm">
            <div>
              <dt className="text-xs font-medium uppercase tracking-wide text-neutral-500">Quand</dt>
              <dd className="text-neutral-900">{formatDateHeure(entree.dateAction)}</dd>
            </div>
            <div>
              <dt className="text-xs font-medium uppercase tracking-wide text-neutral-500">Service</dt>
              <dd>
                <Badge variant="neutre">{libelleService(entree.serviceEmetteur)}</Badge>
              </dd>
            </div>
            <div className="col-span-2">
              <dt className="text-xs font-medium uppercase tracking-wide text-neutral-500">Concerne</dt>
              <dd className="text-neutral-900">{identifiantConcerne(entree.entiteCible, delta)}</dd>
            </div>
            <div>
              <dt className="text-xs font-medium uppercase tracking-wide text-neutral-500">Par</dt>
              <dd className="text-neutral-900">
                {auteurNomme ?? (entree.idUtilisateur !== null ? 'Compte identifié' : NON_RENSEIGNE)}
              </dd>
            </div>
            <div className="col-span-2">
              <dt className="text-xs font-medium uppercase tracking-wide text-neutral-500">Adresse IP</dt>
              <dd className="text-neutral-900">{entree.adresseIp ?? NON_RENSEIGNE}</dd>
            </div>
          </dl>

          {delta.length > 0 ? (
            <ul className="flex flex-col divide-y divide-neutral-100 rounded-lg border border-neutral-200">
              {delta.map((ligne) => (
                <li key={ligne.cle} className="flex flex-col gap-0.5 px-4 py-3 text-sm">
                  <span className="text-xs font-medium uppercase tracking-wide text-neutral-500">
                    {ligne.libelle}
                  </span>
                  {ligne.avant !== undefined ? (
                    <span className="text-neutral-900">
                      <span className="text-neutral-500 line-through">{ligne.avant}</span>{' '}
                      <span aria-hidden="true">→</span> {ligne.apres}
                    </span>
                  ) : (
                    <span className="text-neutral-900">{ligne.valeur}</span>
                  )}
                </li>
              ))}
            </ul>
          ) : (
            <p className="text-sm text-neutral-600">Aucun détail supplémentaire enregistré pour cet événement.</p>
          )}
        </div>
      }
    />
  )
}
