import { useEffect, useState } from 'react'

import { AffichageErreur } from '../../components/communs/AffichageErreur'
import { Alert, AlertDescription } from '../../components/communs/Alert'
import { Button } from '../../components/communs/Button'
import { Card, CardContent, CardHeader, CardTitle } from '../../components/communs/Card'
import { PageHeader } from '../../components/layout/PageHeader'
import type { ApiErrorResponse } from '../../api/apiClient'
import { rechercherAuditEntrees } from '../../api/auditApi'
import type { AuditEntreeResponse } from '../../api/auditApi'
import { CODES_PARAMETRES_MODIFIABLES, consulterParametre } from '../../api/parametresApi'
import type { ParametreResponse } from '../../api/parametresApi'
import { analyserDetailJson } from '../../utils/auditLisible'
import { formatDateHeure } from '../../utils/formatters'
import { ModificationParametreModale } from './ModificationParametreModale'

/**
 * Titre court et unite d'affichage par parametre, retour utilisateur sur la
 * maquette de refonte (Sprint 7F.6, demande n°11) : "chaque parametre est
 * explique en francais, avec son code technique en dessous, en petit".
 * `parametre.libelle`, venu du backend, reste la phrase complete -- utile,
 * mais trop longue pour un titre de ligne.
 */
const PRESENTATION: Record<string, { titre: string; formater: (valeur: string) => string }> = {
  SEUIL_AIGUILLAGE_DR: {
    titre: 'Seuil d\'aiguillage vers le Directeur Réseau',
    formater: (valeur) => `${Number(valeur).toLocaleString('fr-FR')} FCFA`,
  },
  DELAI_REGULARISATION_JOURS: {
    titre: 'Délai de régularisation',
    formater: (valeur) => `${valeur} jour${Number(valeur) > 1 ? 's' : ''}`,
  },
  COMPTE_CHARGE_RATIONS: {
    titre: 'Compte de charge',
    formater: (valeur) => valeur,
  },
}

/**
 * Paramètres système, réservé à l'ADMIN (guide 7F.6, étape 6).
 *
 * Ajout backend scopé, tranché avec l'utilisateur en cours de sprint :
 * aucun sprint backend n'était plus prévu après le 7F, et laisser ces trois
 * valeurs modifiables uniquement par UPDATE SQL direct serait resté un
 * manque permanent. Voir
 * docs/decisions/2026-09-17-endpoint-ecriture-parametres-systeme.md.
 *
 * RATTRAPAGE_ACTIF n'apparaît pas ici : il reste gouverné par sa propre
 * doctrine (migration + UPDATE hors module en cas d'urgence, CLAUDE.md
 * section 7) et n'est pas modifiable par cet endpoint.
 *
 * Présentation revue au Sprint 7F.6 (retour utilisateur, demande n°11) : une
 * ligne par paramètre plutôt qu'une carte, valeur avec son unité, et le
 * rappel des dernières modifications lu depuis le journal d'audit -- traduit
 * en phrases par `utils/auditLisible.ts`, jamais un identifiant ni un JSON.
 */
export function ParametresAdminPage() {
  const [parametres, setParametres] = useState<Record<string, ParametreResponse>>({})
  const [chargement, setChargement] = useState(true)
  const [erreur, setErreur] = useState<ApiErrorResponse | null>(null)
  const [version, setVersion] = useState(0)
  const [codeEnEdition, setCodeEnEdition] = useState<string | null>(null)

  const [dernieresModifications, setDernieresModifications] = useState<AuditEntreeResponse[]>([])
  const [erreurAudit, setErreurAudit] = useState<ApiErrorResponse | null>(null)

  // Aucune reinitialisation synchrone de `chargement` ici : elle est deja
  // vraie au montage, et remise a vrai par `recharger` ci-dessous (meme
  // convention que ProcessusListPage, Sprint 7F.4).
  useEffect(() => {
    let annule = false
    Promise.all(CODES_PARAMETRES_MODIFIABLES.map((code) => consulterParametre(code)))
      .then((resultats) => {
        if (annule) return
        setParametres(Object.fromEntries(resultats.map((parametre) => [parametre.code, parametre])))
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

  // Rappel des dernieres modifications, lu depuis le journal d'audit (retour
  // utilisateur, demande n°11) : "on voit qui a change quoi sans quitter
  // l'ecran". Panne non bloquante -- une erreur ici n'empeche jamais de
  // consulter ou de modifier les parametres eux-memes.
  useEffect(() => {
    let annule = false
    rechercherAuditEntrees({ action: 'MODIFICATION_PARAMETRE', page: 0, size: 5 })
      .then((page) => {
        if (!annule) setDernieresModifications(page.content)
      })
      .catch((erreurApi: ApiErrorResponse) => {
        if (!annule) setErreurAudit(erreurApi)
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

  return (
    <>
      <PageHeader surTitre="Administration" titre="Paramètres système" />
      <div className="flex flex-col gap-6 p-8">
        <Alert variant="warning">
          <AlertDescription>
            <p className="font-medium">
              Ces valeurs commandent le circuit d'approbation de la banque et l'imputation comptable.
            </p>
            <p>
              Toute modification s'applique immédiatement, sans redémarrage, et est tracée au journal
              d'audit avec l'ancienne et la nouvelle valeur.
            </p>
          </AlertDescription>
        </Alert>

        {erreur && <AffichageErreur erreur={erreur} />}

        {chargement && !erreur && <p className="text-sm text-neutral-600">Chargement…</p>}

        <Card>
          <CardContent className="flex flex-col gap-0 p-0">
            {CODES_PARAMETRES_MODIFIABLES.map((code, index) => {
              const parametre = parametres[code]
              if (!parametre) return null
              const presentation = PRESENTATION[code]

              return (
                <div
                  key={code}
                  className={
                    'flex flex-col items-start justify-between gap-4 p-4 sm:flex-row sm:items-center' +
                    (index > 0 ? ' border-t border-neutral-100' : '')
                  }
                >
                  <div>
                    <p className="text-sm font-semibold text-neutral-900">{presentation?.titre ?? parametre.libelle}</p>
                    <p className="text-xs text-neutral-600">{parametre.libelle}</p>
                    <p className="mt-1 text-xs uppercase tracking-wide text-neutral-500">{parametre.code}</p>
                  </div>
                  <div className="flex shrink-0 flex-col items-end gap-1.5">
                    <p className="text-xl font-semibold tabular-nums text-neutral-900">
                      {presentation ? presentation.formater(parametre.valeur) : parametre.valeur}
                    </p>
                    <Button variant="outline" size="sm" onClick={() => setCodeEnEdition(code)}>
                      Modifier
                    </Button>
                  </div>
                </div>
              )
            })}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">Dernières modifications</CardTitle>
          </CardHeader>
          <CardContent>
            {erreurAudit ? (
              <p className="text-sm text-neutral-600">Journal d'audit indisponible pour le moment.</p>
            ) : dernieresModifications.length === 0 ? (
              <p className="text-sm text-neutral-600">Aucune modification enregistrée pour le moment.</p>
            ) : (
              <ul className="flex flex-col gap-2">
                {dernieresModifications.map((entree) => {
                  const delta = analyserDetailJson(entree.detailJson)
                  const auteur = delta.find((ligne) => ligne.cle === 'login' || ligne.cle === 'auteur')?.valeur
                  const code = delta.find((ligne) => ligne.cle === 'code')?.valeur
                  const valeur = delta.find((ligne) => ligne.cle === 'valeur')
                  return (
                    <li
                      key={entree.id}
                      className="flex flex-col gap-0.5 border-b border-neutral-100 pb-2 text-sm last:border-0 last:pb-0"
                    >
                      <div className="flex items-center justify-between">
                        <span className="font-medium text-neutral-900">{code ?? 'Paramètre système'}</span>
                        <span className="text-neutral-600">{formatDateHeure(entree.dateAction)}</span>
                      </div>
                      <span className="text-xs text-neutral-600">
                        {valeur ? (
                          <>
                            <span className="line-through">{valeur.avant}</span> → {valeur.apres}
                          </>
                        ) : (
                          'valeur modifiée'
                        )}
                        {auteur ? ` · par ${auteur}` : ''}
                      </span>
                    </li>
                  )
                })}
              </ul>
            )}
          </CardContent>
        </Card>
      </div>

      {codeEnEdition && parametres[codeEnEdition] && (
        <ModificationParametreModale
          parametre={parametres[codeEnEdition]}
          onFerme={() => setCodeEnEdition(null)}
          onSucces={() => {
            setCodeEnEdition(null)
            recharger()
          }}
        />
      )}
    </>
  )
}
