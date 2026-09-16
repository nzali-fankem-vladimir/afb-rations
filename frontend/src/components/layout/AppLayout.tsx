import { Outlet } from 'react-router-dom'

import { Sidebar } from './Sidebar'

/** Coquille commune : sidebar fixe a gauche, contenu defilant independamment a droite. */
export function AppLayout() {
  return (
    <div className="flex h-screen bg-neutral-50">
      <Sidebar />
      <main className="min-w-0 flex-1 overflow-y-auto">
        <Outlet />
      </main>
    </div>
  )
}
