interface PageHeaderProps {
  surTitre: string
  titre: string
}

/** En-tete commun : sur-titre, titre principal, separateur discret (accent rouge ponctuel, charte §8.1). */
export function PageHeader({ surTitre, titre }: PageHeaderProps) {
  return (
    <section className="border-b border-neutral-200 bg-white px-8 py-6">
      <div className="flex items-end justify-between gap-4">
        <div>
          <p className="mb-1 text-xxs font-semibold uppercase tracking-[0.2em] text-neutral-600">{surTitre}</p>
          <h1 className="text-2xl font-bold text-neutral-900">{titre}</h1>
        </div>
        <div
          aria-hidden
          className="hidden h-10 w-1 rounded-full bg-linear-to-b from-primary-500 to-transparent sm:block"
        />
      </div>
    </section>
  )
}
