import { useState } from 'react'
import { FileDown } from 'lucide-react'

import { AffichageErreur } from '../../components/communs/AffichageErreur'
import { Alert, AlertDescription } from '../../components/communs/Alert'
import { Badge } from '../../components/communs/Badge'
import { Button } from '../../components/communs/Button'
import { ChampTexte } from '../../components/communs/ChampTexte'
import { SelecteurPeriode } from '../../components/communs/SelecteurPeriode'
import { StatTile } from '../../components/communs/StatTile'
import { Tableau } from '../../components/communs/Tableau'
import type { Colonne } from '../../components/communs/Tableau'
import { PageHeader } from '../../components/layout/PageHeader'
import type { ApiErrorResponse } from '../../api/apiClient'
import type { LigneRapport, RapportResponse, SousTotalUnite } from '../../api/reportingApi'
import { exporterRapport, produireRapport } from '../../api/reportingApi'
import type { SituationIntegrationEnum } from '../../api/reportingApi'
import { declencherTelechargement } from '../../utils/declencherTelechargement'
import { formatMontantFcfa } from '../../utils/formatters'

/** Meme vocabulaire et meme palette que l'ecran de suivi (Sprint 6.2 : "envoye", jamais "transmis" ni "paye"). */
const VARIANTE_PAR_SITUATION: Record<SituationIntegrationEnum, 'neutre' | 'attente' | 'positif' | 'negatif'> = {
  NON_TRANSMIS: 'neutre',
  PUBLICATION_NON_CONFIRMEE: 'attente',
  EN_ATTENTE_ACCUSE: 'attente',
  INTEGRE: 'positif',
  REJETE: 'negatif',
}

const LIBELLE_SITUATION: Record<SituationIntegrationEnum, string> = {
  NON_TRANSMIS: 'Pas encore envoyé',
  PUBLICATION_NON_CONFIRMEE: 'Envoi non confirmé',
  EN_ATTENTE_ACCUSE: "En attente d'accusé",
  INTEGRE: 'Intégré',
  REJETE: 'Rejeté',
}

const COLONNES_LIGNES: Colonne<LigneRapport>[] = [
  { cle: 'idProcessus', entete: 'Dossier', rendu: (ligne) => `n° ${ligne.idProcessus}` },
  { cle: 'codeUnite', entete: 'Unité' },
  {
    cle: 'typeProcessus',
    entete: 'Type',
    rendu: (ligne) => (ligne.typeProcessus === 'COMPLEMENTAIRE' ? <Badge variant="attente">Complémentaire</Badge> : 'Normal'),
  },
  {
    cle: 'montantTotal',
    entete: 'Montant',
    className: 'tabular-nums font-semibold text-neutral-900',
    rendu: (ligne) => formatMontantFcfa(ligne.montantTotal),
  },
  {
    cle: 'situationIntegration',
    entete: 'Comptabilité',
    rendu: (ligne) => (
      <Badge variant={VARIANTE_PAR_SITUATION[ligne.situationIntegration]}>
        {LIBELLE_SITUATION[ligne.situationIntegration]}
      </Badge>
    ),
  },
]

const COLONNES_SOUS_TOTAUX: Colonne<SousTotalUnite>[] = [
  { cle: 'codeUnite', entete: 'Unité' },
  { cle: 'nombreEtats', entete: "Nombre d'états", className: 'tabular-nums' },
  {
    cle: 'montantTotal',
    entete: 'Montant',
    className: 'tabular-nums font-semibold text-neutral-900',
    rendu: (sousTotal) => formatMontantFcfa(sousTotal.montantTotal),
  },
]

/**
 * Rapports d'activite et exports (guide 7F.7, etape 4), reserve a l'ARH.
 *
 * dateDebut et dateFin sont OBLIGATOIRES (contrairement au suivi) : un
 * rapport est toujours date d'une periode, et generer sans bornes au montage
 * enverrait une requete vouee a 400 PERIODE_INVALIDE. Le bouton "Generer"
 * est donc le seul declencheur.
 *
 * ATTENTION vocabulaire (CLAUDE.md, decision Sprint 6.2 redite au 7F.7) : le
 * parametre et le libelle de cet ecran disent "unite", jamais "agence" --
 * l'agence domicilie le compte du beneficiaire, l'unite supporte la charge,
 * et confondre les deux ferait mentir un rapport regroupant plusieurs unites.
 */
export function RapportsPage() {
  const [dateDebut, setDateDebut] = useState('')
  const [dateFin, setDateFin] = useState('')
  const [codeUnite, setCodeUnite] = useState('')

  const [rapport, setRapport] = useState<RapportResponse | null>(null)
  const [chargement, setChargement] = useState(false)
  const [erreur, setErreur] = useState<ApiErrorResponse | null>(null)
  const [exportEnCours, setExportEnCours] = useState<'pdf' | 'excel' | null>(null)
  const [erreurExport, setErreurExport] = useState<ApiErrorResponse | null>(null)

  const criteres = { dateDebut, dateFin, codeUnite: codeUnite.trim() === '' ? undefined : codeUnite.trim() }
  const perioteIncomplete = dateDebut === '' || dateFin === ''

  const generer = async () => {
    setErreur(null)
    setErreurExport(null)
    setChargement(true)
    try {
      const reponse = await produireRapport(criteres)
      setRapport(reponse)
    } catch (erreurApi) {
      setRapport(null)
      setErreur(erreurApi as ApiErrorResponse)
    } finally {
      setChargement(false)
    }
  }

  const exporter = async (format: 'pdf' | 'excel') => {
    setErreurExport(null)
    setExportEnCours(format)
    try {
      const fichier = await exporterRapport(criteres, format)
      declencherTelechargement(fichier.contenu, fichier.nomFichier)
    } catch (erreurApi) {
      setErreurExport(erreurApi as ApiErrorResponse)
    } finally {
      setExportEnCours(null)
    }
  }

  return (
    <>
      <PageHeader surTitre="Rapports" titre="Rapports d'activité" />
      <div className="flex flex-col gap-6 p-8">
        <div className="flex flex-col gap-4 rounded-lg border border-neutral-200 bg-white p-4">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-end">
            <SelecteurPeriode
              idPrefix="rapport"
              dateDebut={dateDebut}
              dateFin={dateFin}
              onChangerDateDebut={setDateDebut}
              onChangerDateFin={setDateFin}
              obligatoire
              className="flex-1"
            />
            <ChampTexte
              id="rapport-unite"
              label="Unité"
              placeholder="Toutes les unités"
              value={codeUnite}
              onChange={(event) => setCodeUnite(event.target.value)}
              className="sm:w-48"
            />
            <Button onClick={generer} disabled={perioteIncomplete} isLoading={chargement}>
              Générer
            </Button>
          </div>
        </div>

        {erreur && <AffichageErreur erreur={erreur} />}

        {rapport?.vide && (
          <Alert variant="default">
            <AlertDescription>Aucun état sur cette période : le rapport est vide, ce n'est pas une erreur.</AlertDescription>
          </Alert>
        )}

        {rapport && (
          <>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <StatTile valeur={formatMontantFcfa(rapport.synthese.montantTotalPeriode)} libelle="Montant total de la période" />
              <StatTile
                valeur={formatMontantFcfa(rapport.synthese.montantEnvoyeComptabilite)}
                libelle="Envoyé à la comptabilité"
              />
              <StatTile
                valeur={formatMontantFcfa(rapport.synthese.montantNonEnvoyeComptabilite)}
                libelle="Non encore envoyé"
              />
              <StatTile
                valeur={formatMontantFcfa(rapport.synthese.montantRejeteComptabilite)}
                libelle="Rejeté par la comptabilité"
              />
            </div>

            <Alert variant="default">
              <AlertDescription>
                « Envoyé à la comptabilité » ne veut pas dire « payé » : un état envoyé peut être rejeté ensuite, et
                rien n'aura alors été versé aux agents.
              </AlertDescription>
            </Alert>

            {!rapport.vide && (
              <>
                <Tableau
                  colonnes={COLONNES_LIGNES}
                  donnees={rapport.lignes}
                  cleLigne={(ligne) => ligne.idProcessus}
                  messageVide="Aucun état sur cette période."
                />

                {rapport.sousTotauxParAgence.length > 0 && (
                  <div className="flex flex-col gap-2">
                    <h3 className="text-sm font-semibold text-neutral-900">Cumuls par unité</h3>
                    <Tableau
                      colonnes={COLONNES_SOUS_TOTAUX}
                      donnees={rapport.sousTotauxParAgence}
                      cleLigne={(sousTotal) => sousTotal.codeUnite}
                      messageVide="Aucun cumul disponible."
                    />
                  </div>
                )}
              </>
            )}

            {erreurExport && <AffichageErreur erreur={erreurExport} />}

            <div className="flex gap-3">
              <Button variant="outline" onClick={() => exporter('pdf')} isLoading={exportEnCours === 'pdf'}>
                <FileDown className="h-4 w-4" aria-hidden="true" />
                Exporter en PDF
              </Button>
              <Button variant="outline" onClick={() => exporter('excel')} isLoading={exportEnCours === 'excel'}>
                <FileDown className="h-4 w-4" aria-hidden="true" />
                Exporter en Excel
              </Button>
            </div>
            <p className="text-xs text-neutral-500">
              Généré le {new Date(rapport.dateGeneration).toLocaleString('fr-FR')} par {rapport.loginUtilisateur}. Les
              totaux exportés sont identiques à ceux affichés ici : rien n'est recalculé côté interface.
            </p>
          </>
        )}
      </div>
    </>
  )
}
