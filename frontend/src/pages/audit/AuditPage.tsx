import { useEffect, useState } from 'react'
import { Eye } from 'lucide-react'

import { AffichageErreur } from '../../components/communs/AffichageErreur'
import { Badge } from '../../components/communs/Badge'
import { Button } from '../../components/communs/Button'
import { ChampListe } from '../../components/communs/ChampListe'
import { ChampTexte } from '../../components/communs/ChampTexte'
import { Tableau } from '../../components/communs/Tableau'
import type { Colonne } from '../../components/communs/Tableau'
import { PageHeader } from '../../components/layout/PageHeader'
import type { ApiErrorResponse } from '../../api/apiClient'
import { SERVICES_EMETTEURS, listerActionsDisponibles, rechercherAuditEntrees } from '../../api/auditApi'
import type { AuditEntreeResponse } from '../../api/auditApi'
import { listerUtilisateurs } from '../../api/adminApi'
import type { UtilisateurResponse } from '../../api/adminApi'
import type { PageResponse } from '../../types/pagination'
import { useAuth } from '../../hooks/useAuth'
import { analyserDetailJson, identifiantConcerne, libelleAction, libelleService } from '../../utils/auditLisible'
import { NON_RENSEIGNE, formatDateHeure } from '../../utils/formatters'
import { DetailEntreeAuditModale } from './DetailEntreeAuditModale'

const TAILLE_PAGE = 20

const OPTIONS_SERVICE = SERVICES_EMETTEURS.map((service) => ({ valeur: service, libelle: libelleService(service) }))

/**
 * Quatre raccourcis pour les recherches les plus courantes d'un contrôle
 * interne. Un seul code par raccourci : le filtre serveur compare l'action à
 * une égalité stricte (AuditLogRechercheRepositoryImpl), et "Validations"
 * recouvrirait deux actions distinctes (états, grilles) qu'une seule requête
 * ne peut pas combiner -- le libellé nomme donc précisément ce qui est
 * cherché plutôt que de suggérer une portée plus large que la réalité.
 */
const RACCOURCIS_ACTION: { libelle: string; action: string }[] = [
  { libelle: "Refus d'accès", action: 'ACCES_REFUSE' },
  { libelle: "Validations d'états", action: 'VALIDATION_PROCESSUS' },
  { libelle: 'Envois à la comptabilité', action: 'TRANSMISSION_COMPTABLE' },
  { libelle: 'Modifications de paramètres', action: 'MODIFICATION_PARAMETRE' },
]

/** "2026-09-01T00:00" (datetime-local, sans secondes) -> "2026-09-01T00:00:00" (ISO attendu par le serveur). */
function versDateHeureIso(valeurChamp: string): string | undefined {
  if (valeurChamp === '') return undefined
  return valeurChamp.length === 16 ? `${valeurChamp}:00` : valeurChamp
}

/** "2026-09-01T14:32:07" -> "2026-09-01T14:32" (format attendu par un champ datetime-local). */
function versDateTimeLocal(date: Date): string {
  const pad = (valeur: number) => String(valeur).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/**
 * Raccourcis de période, en complément des deux champs de date-heure precis
 * (retour utilisateur, Sprint 7F.6) : un controleur qui veut "les 7 derniers
 * jours" ne devrait pas avoir a calculer deux dates a la main.
 */
const RACCOURCIS_PERIODE: { libelle: string; jours: number }[] = [
  { libelle: '24 dernières heures', jours: 1 },
  { libelle: '7 derniers jours', jours: 7 },
  { libelle: '30 derniers jours', jours: 30 },
]

function ParQui({ entree }: { entree: AuditEntreeResponse }) {
  const delta = analyserDetailJson(entree.detailJson)
  const auteurNomme = delta.find((ligne) => ligne.cle === 'login' || ligne.cle === 'auteur')?.valeur
  if (auteurNomme) return <span className="text-neutral-900">{auteurNomme}</span>
  if (entree.idUtilisateur !== null) return <span className="text-neutral-600">Compte identifié</span>
  return <span className="text-neutral-500">{NON_RENSEIGNE}</span>
}

const COLONNES: Colonne<AuditEntreeResponse>[] = [
  {
    cle: 'dateAction',
    entete: 'Date et heure',
    className: 'whitespace-nowrap align-top',
    rendu: (entree) => formatDateHeure(entree.dateAction),
  },
  {
    cle: 'action',
    entete: "Ce qui s'est passé",
    className: 'align-top font-medium text-neutral-900',
    // Simplifie a la seule action (retour utilisateur) : le delta complet vit
    // desormais dans DetailEntreeAuditModale, un clic plus loin -- le repeter
    // ici l'aurait affiche deux fois.
    rendu: (entree) => libelleAction(entree.action),
  },
  {
    cle: 'entiteCible',
    entete: 'Concerne',
    className: 'align-top text-neutral-700',
    // Identifie LEQUEL dossier/grille/etc., pas seulement le type d'entite
    // (retour utilisateur) -- construit depuis le contexte deja present dans
    // detail_json, aucun appel reseau supplementaire.
    rendu: (entree) => identifiantConcerne(entree.entiteCible, analyserDetailJson(entree.detailJson)),
  },
  { cle: 'idUtilisateur', entete: 'Par', className: 'align-top', rendu: (entree) => <ParQui entree={entree} /> },
  {
    cle: 'serviceEmetteur',
    entete: 'Service',
    className: 'align-top',
    rendu: (entree) => <Badge variant="neutre">{libelleService(entree.serviceEmetteur)}</Badge>,
  },
]

/**
 * Journal d'audit, en lecture seule, ouvert à ARH, DRH et ADMIN (guide 7F.6,
 * étape 7).
 *
 * Aucune action de modification ni de suppression : le journal est immuable
 * (CLAUDE.md section 4, service Audit sans endpoint d'écriture). Aucun bouton
 * en ce sens, même désactivé : sa seule présence suggérerait le contraire.
 *
 * Tri imposé par le serveur sur date_action, jamais par colonne : l'ordre
 * d'arrivée des événements sur le topic n'est pas l'ordre des faits
 * (Sprint 6.3). Aucun paramètre `sort` n'est donc envoyé.
 *
 * Présentation revue au Sprint 7F.6 (retour utilisateur sur la maquette de
 * refonte) : aucun JSON ni identifiant technique brut n'apparaît plus à
 * l'écran, `detail_json` étant systématiquement traduit en phrases par
 * `libellesAudit.ts`. Les identifiants restent en base pour l'investigation
 * technique, mais cet écran n'en a plus besoin pour être utile à un
 * contrôleur interne.
 */
export function AuditPage() {
  const { role } = useAuth()
  const [page, setPage] = useState(0)
  const [serviceEmetteur, setServiceEmetteur] = useState('')
  const [action, setAction] = useState('')
  const [idUtilisateur, setIdUtilisateur] = useState('')
  const [dateDebut, setDateDebut] = useState('')
  const [dateFin, setDateFin] = useState('')

  const [donnees, setDonnees] = useState<PageResponse<AuditEntreeResponse> | null>(null)
  const [chargement, setChargement] = useState(true)
  const [erreur, setErreur] = useState<ApiErrorResponse | null>(null)

  // Entree dont le detail complet (sans troncature, contexte entier de
  // l'evenement) est affiche dans une modale -- la colonne "Ce qui s'est
  // passe" reste compacte, tronquee a une zone de defilement de 96px.
  const [entreeDetail, setEntreeDetail] = useState<AuditEntreeResponse | null>(null)

  // Options du filtre "Utilisateur" : GET /identite/utilisateurs est ouvert
  // a ADMIN, ARH et DRH (rattrapage post-7F.6 -- lecture seule, l'attribution
  // de role reste reservee a l'ADMIN). Les trois roles pouvant atteindre
  // cette page (ProtectedRoute) peuvent donc tous alimenter ce filtre.
  const [utilisateurs, setUtilisateurs] = useState<UtilisateurResponse[]>([])
  useEffect(() => {
    let annule = false
    listerUtilisateurs({ size: 100 })
      .then((reponse) => {
        if (!annule) setUtilisateurs(reponse.content)
      })
      .catch(() => {
        // Filtre de confort : une panne ne doit jamais empecher la
        // consultation du journal lui-meme, seule l'option de filtrage
        // disparait.
      })
    return () => {
      annule = true
    }
  }, [role])
  const optionsUtilisateur = utilisateurs.map((utilisateur) => ({
    valeur: String(utilisateur.id),
    libelle: `${utilisateur.prenom} ${utilisateur.nom} · ${utilisateur.login}`,
  }))

  // Options du filtre "Type d'action" : tirees de GET /audit/actions, donc
  // des codes REELLEMENT presents dans le journal (rattrapage post-7F.6,
  // retour utilisateur : "je veux savoir si les types d'action ... sont bien
  // tires du backend"). Avant cet ajout, la liste etait une copie figee
  // cote frontend des 30 codes de CLAUDE.md section 9.2 -- rien ne
  // garantissait qu'elle suive le backend si une septieme action apparaissait.
  // `libelleAction` traduit chaque code en francais, avec repli automatique
  // sur une mise en forme lisible pour un code qui n'aurait pas encore de
  // libellé dédié.
  const [actionsDisponibles, setActionsDisponibles] = useState<string[]>([])
  useEffect(() => {
    let annule = false
    listerActionsDisponibles()
      .then((reponse) => {
        if (!annule) setActionsDisponibles(reponse)
      })
      .catch(() => {
        // Filtre de confort : une panne ne doit jamais empecher la
        // consultation du journal lui-meme, seule l'option de filtrage
        // disparait.
      })
    return () => {
      annule = true
    }
  }, [])
  const optionsAction = actionsDisponibles
    .map((code) => ({ valeur: code, libelle: libelleAction(code) }))
    .sort((a, b) => a.libelle.localeCompare(b.libelle, 'fr'))

  // Aucune reinitialisation synchrone de `chargement` ici : elle est deja
  // vraie au montage, et remise a vrai par les gestionnaires de filtre et de
  // page ci-dessous (meme convention que ProcessusListPage, Sprint 7F.4).
  useEffect(() => {
    let annule = false
    rechercherAuditEntrees({
      serviceEmetteur: serviceEmetteur === '' ? undefined : serviceEmetteur,
      action: action === '' ? undefined : action,
      idUtilisateur: idUtilisateur === '' ? undefined : Number(idUtilisateur),
      dateDebut: versDateHeureIso(dateDebut),
      dateFin: versDateHeureIso(dateFin),
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
  }, [page, serviceEmetteur, action, idUtilisateur, dateDebut, dateFin])

  const changerPage = (nouvellePage: number) => {
    setChargement(true)
    setErreur(null)
    setPage(nouvellePage)
  }

  const changerFiltre = <T,>(setter: (valeur: T) => void, valeur: T) => {
    setChargement(true)
    setErreur(null)
    setPage(0)
    setter(valeur)
  }

  const appliquerRaccourciPeriode = (jours: number) => {
    const maintenant = new Date()
    const debut = new Date(maintenant.getTime() - jours * 24 * 60 * 60 * 1000)
    setChargement(true)
    setErreur(null)
    setPage(0)
    setDateDebut(versDateTimeLocal(debut))
    setDateFin(versDateTimeLocal(maintenant))
  }

  return (
    <>
      <PageHeader surTitre="Contrôle interne" titre="Journal d'audit" />
      <div className="flex flex-col gap-6 p-8">
        {erreur && <AffichageErreur erreur={erreur} />}

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3 lg:grid-cols-4">
          <ChampListe
            id="audit-service"
            label="Service émetteur"
            libellePlaceholder="Tous les services"
            value={serviceEmetteur}
            onChange={(event) => changerFiltre(setServiceEmetteur, event.target.value)}
            options={OPTIONS_SERVICE}
          />
          <ChampListe
            id="audit-action"
            label="Type d'action"
            libellePlaceholder="Toutes les actions"
            value={action}
            onChange={(event) => changerFiltre(setAction, event.target.value)}
            options={optionsAction}
          />
          <ChampListe
            id="audit-utilisateur"
            label="Utilisateur"
            libellePlaceholder="Tous les utilisateurs"
            value={idUtilisateur}
            onChange={(event) => changerFiltre(setIdUtilisateur, event.target.value)}
            options={optionsUtilisateur}
          />
          <ChampTexte
            id="audit-date-debut"
            label="Depuis"
            type="datetime-local"
            value={dateDebut}
            onChange={(event) => changerFiltre(setDateDebut, event.target.value)}
          />
          <ChampTexte
            id="audit-date-fin"
            label="Jusqu'à"
            type="datetime-local"
            value={dateFin}
            onChange={(event) => changerFiltre(setDateFin, event.target.value)}
          />
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <span className="text-xs font-medium text-neutral-600">Périodes :</span>
          {RACCOURCIS_PERIODE.map((raccourci) => (
            <button
              key={raccourci.libelle}
              type="button"
              onClick={() => appliquerRaccourciPeriode(raccourci.jours)}
              className="rounded-full border border-neutral-300 px-3 py-1 text-xs font-medium text-neutral-700 hover:bg-neutral-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
            >
              {raccourci.libelle}
            </button>
          ))}
          <span className="ml-2 text-xs font-medium text-neutral-600">Recherches courantes :</span>
          {RACCOURCIS_ACTION.map((raccourci) => (
            <button
              key={raccourci.action}
              type="button"
              aria-pressed={action === raccourci.action}
              onClick={() => changerFiltre(setAction, raccourci.action)}
              className={
                action === raccourci.action
                  ? 'rounded-full bg-primary-500 px-3 py-1 text-xs font-medium text-white'
                  : 'rounded-full border border-neutral-300 px-3 py-1 text-xs font-medium text-neutral-700 hover:bg-neutral-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-500'
              }
            >
              {raccourci.libelle}
            </button>
          ))}
        </div>

        <Tableau
          colonnes={COLONNES}
          donnees={donnees?.content ?? []}
          cleLigne={(entree) => entree.id}
          chargement={chargement}
          messageVide="Aucune entrée ne correspond à ces filtres."
          onLigneClick={(entree) => setEntreeDetail(entree)}
          actions={(entree) => (
            <div className="flex justify-end">
              <Button variant="ghost" size="sm" onClick={() => setEntreeDetail(entree)}>
                <Eye className="h-4 w-4" aria-hidden="true" />
                Détails
              </Button>
            </div>
          )}
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

      {entreeDetail && <DetailEntreeAuditModale entree={entreeDetail} onFerme={() => setEntreeDetail(null)} />}
    </>
  )
}
