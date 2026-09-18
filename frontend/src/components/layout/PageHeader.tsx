import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { ChevronRight } from 'lucide-react'

/** Un maillon du fil d'Ariane : le dernier n'a pas de `href`, c'est la page courante. */
export interface MaillonFilAriane {
  libelle: string
  href?: string
}

interface PageHeaderProps {
  /** Ignoré si `filAriane` est fourni : les deux ne se combinent pas, un seul repère de contexte à la fois. */
  surTitre?: string
  titre: string
  /**
   * Trace de navigation ("Validation › Unité 00002"), pour une page de détail
   * atteinte depuis une liste (retour utilisateur, Sprint 7F.6, maquette de
   * refonte). Remplace `surTitre` quand fourni.
   */
  filAriane?: MaillonFilAriane[]
  /** Bouton(s) d'action principaux de la page, alignés à côté du titre plutôt qu'en bas de contenu. */
  actions?: ReactNode
  /** Badge de statut affiché à côté du titre (ex. BadgeStatutProcessus). */
  badge?: ReactNode
}

/** En-tete commun : sur-titre ou fil d'Ariane, titre principal, separateur discret (accent rouge ponctuel, charte §8.1). */
export function PageHeader({ surTitre, titre, filAriane, actions, badge }: PageHeaderProps) {
  return (
    <section className="border-b border-neutral-200 bg-white px-8 py-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          {filAriane ? (
            <nav aria-label="Fil d'Ariane" className="mb-1 flex items-center gap-1 text-xs text-neutral-600">
              {filAriane.map((maillon, index) => (
                <span key={index} className="flex items-center gap-1">
                  {index > 0 && <ChevronRight className="h-3 w-3 text-neutral-400" aria-hidden />}
                  {maillon.href ? (
                    <Link to={maillon.href} className="hover:text-primary-700 hover:underline">
                      {maillon.libelle}
                    </Link>
                  ) : (
                    <span>{maillon.libelle}</span>
                  )}
                </span>
              ))}
            </nav>
          ) : (
            surTitre && (
              <p className="mb-1 text-xxs font-semibold uppercase tracking-[0.2em] text-neutral-600">{surTitre}</p>
            )
          )}
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-bold text-neutral-900">{titre}</h1>
            {badge}
          </div>
        </div>
        <div className="flex items-center gap-3">
          {actions}
          <div
            aria-hidden
            className="hidden h-10 w-1 rounded-full bg-linear-to-b from-primary-500 to-transparent sm:block"
          />
        </div>
      </div>
    </section>
  )
}
