import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { LayoutGrid, Plus } from 'lucide-react'
import { HomeShell } from '@/components/shell/HomeShell'
import { WorkspaceCard } from '@/components/workspace/WorkspaceCard'
import { WorkspaceFormModal } from '@/components/workspace/WorkspaceFormModal'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { Skeleton } from '@/components/ui/Skeleton'
import styles from './WorkspacesScreen.module.css'
import { useWorkspaceMutations, useWorkspaces } from '@/hooks/useData'
import { useAuth } from '@/state/context'
import { ROUTES } from '@/app/routes'
import type { WorkspaceResponse } from '@/types/api'

/**
 * Pantalla inicial: las asignaturas y trabajos del estudiante.
 *
 * Es la unica lista de la aplicacion. Todo lo demas cuelga de entrar en una de
 * estas tarjetas, y de ahi que la unica accion destacada sea crear otra.
 */
export function WorkspacesScreen() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const { data: workspaces, isPending } = useWorkspaces()
  const { create } = useWorkspaceMutations()
  const [formOpen, setFormOpen] = useState(false)

  const pendientes = (workspaces ?? []).reduce((total, item) => total + item.pendingTasks, 0)
  const nombre = user?.fullName.split(' ')[0]

  function open(workspace: WorkspaceResponse) {
    // Se entra por el plan: la primera pregunta al abrir una asignatura es
    // «como voy», y el tablero esta a un clic para el dia a dia.
    navigate(ROUTES.planById(workspace.id))
  }

  return (
    <HomeShell>
      <header className={styles.head}>
        <div>
          <h1 className={styles.title}>Mis workspaces</h1>
          <p className={styles.subtitle}>
            {isPending
              ? 'Cargando…'
              : workspaces && workspaces.length > 0
                ? pendientes > 0
                  ? `Hola${nombre ? `, ${nombre}` : ''}. Tienes ${pendientes} ${pendientes === 1 ? 'tarea pendiente' : 'tareas pendientes'} en total.`
                  : `Hola${nombre ? `, ${nombre}` : ''}. No te queda nada pendiente.`
                : 'Una asignatura, una practica o un trabajo en grupo: cada cosa, su workspace.'}
          </p>
        </div>
        <Button onClick={() => setFormOpen(true)}>
          <Plus size={15} strokeWidth={2.75} />
          Nuevo workspace
        </Button>
      </header>

      {isPending ? (
        <Skeleton label="Cargando tus workspaces…" lines={3} />
      ) : !workspaces || workspaces.length === 0 ? (
        <EmptyState
          icon={<LayoutGrid size={22} strokeWidth={2.75} />}
          title="Todavia no tienes ningun workspace"
          body="Crea uno por cada asignatura o trabajo. Nace con su tablero listo: Por hacer, En progreso y Hecho."
          action={
            <Button onClick={() => setFormOpen(true)}>
              <Plus size={15} strokeWidth={2.75} />
              Crear mi primer workspace
            </Button>
          }
        />
      ) : (
        <div className={styles.grid}>
          {workspaces.map((workspace) => (
            <WorkspaceCard key={workspace.id} workspace={workspace} onOpen={open} />
          ))}
        </div>
      )}

      <WorkspaceFormModal
        open={formOpen}
        isPending={create.isPending}
        onClose={() => setFormOpen(false)}
        onSubmit={async (payload) => {
          const workspace = await create.mutateAsync(payload)
          // Se entra directamente: crear y quedarse mirando la lista no sirve de nada.
          navigate(ROUTES.planById(workspace.id))
        }}
      />
    </HomeShell>
  )
}
