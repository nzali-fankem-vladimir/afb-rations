import { PageHeader } from '../components/layout/PageHeader'

interface PageProvisoireProps {
  titre: string
}

/** Espace reserve : l'ecran reel vient d'un sous-sprint ulterieur. */
export function PageProvisoire({ titre }: PageProvisoireProps) {
  return (
    <>
      <PageHeader surTitre="Rations et transport garde armée" titre={titre} />
      <div className="p-8">
        <p className="text-sm text-neutral-600">Cet écran sera construit dans un sous-sprint ultérieur.</p>
      </div>
    </>
  )
}
