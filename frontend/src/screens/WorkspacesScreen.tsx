import { useMemo, useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { LayoutGrid, Plus } from 'lucide-react'
import { HomeShell } from '@/components/shell/HomeShell'
import { HomeTabs } from '@/components/shell/HomeTabs'
import { WorkspaceCard } from '@/components/workspace/WorkspaceCard'
import { WorkspaceCardActions } from '@/components/workspace/WorkspaceCardActions'
import { InvitationsBanner } from '@/components/workspace/InvitationsBanner'
import { WorkspaceFormModal } from '@/components/workspace/WorkspaceFormModal'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { Skeleton } from '@/components/ui/Skeleton'
import styles from './WorkspacesScreen.module.css'
import { useWorkspaceMutations, useWorkspaces } from '@/hooks/useData'
import { useAuth } from '@/state/context'
import { isAdmin, isTeacher } from '@/lib/roles'
import { homeForRole, ROUTES } from '@/app/routes'
import type { WorkspaceResponse } from '@/types/api'

/**
 * Pantalla inicial: las asignaturas y trabajos del estudiante.
 *
 * Es la unica lista de la aplicacion. Todo lo demas cuelga de entrar en una de
 * estas tarjetas, y de ahi que la unica accion destacada sea crear otra. El
 * administrador no pasa por aqui: tiene su propio panel.
 *
 * Un workspace donde solo se observa (donde se es DOCENTE) no aparece aqui como
 * tarjeta propia: no tiene sentido entrar a "trabajar" en algo que solo se
 * sigue. Esos workspaces viven dentro del panel docente. Una cuenta docente ve
 * esta misma pantalla, con dos pestañas arriba (ver HomeTabs): esta, con los
 * suyos, donde es responsable, y el panel docente, con los que sigue.
 */
export function WorkspacesScreen() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const { data: workspaces, isPending } = useWorkspaces()
  const { create } = useWorkspaceMutations()
  const [formOpen, setFormOpen] = useState(false)

  const propios = useMemo(() => (workspaces ?? []).filter((item) => !isTeacher(item.myRole)), [workspaces])
  const nadaQueMostrar = !isPending && propios.length === 0

  const pendientes = propios.reduce((total, item) => total + item.pendingTasks, 0)
  const nombre = user?.fullName.split(' ')[0]

  // Alumno y docente ven esta misma pantalla; solo el administrador tiene otra.
  if (user && isAdmin(user.role)) return <Navigate to={homeForRole(user.role)} replace />

  function open(workspace: WorkspaceResponse) {
    // Se entra por el plan: la primera pregunta al abrir una asignatura es
    // «como voy», y el tablero esta a un clic para el dia a dia.
    navigate(ROUTES.planById(workspace.id))
  }

  const saludo = isPending
    ? 'Cargando…'
    : propios.length > 0
      ? pendientes > 0
        ? `Hola${nombre ? `, ${nombre}` : ''}. Tienes ${pendientes} ${pendientes === 1 ? 'tarea pendiente' : 'tareas pendientes'} en total.`
        : `Hola${nombre ? `, ${nombre}` : ''}. No te queda nada pendiente.`
      : 'Una asignatura, una práctica o un trabajo en grupo: cada cosa, su espacio de trabajo.'

  const nuevo = (
    <Button onClick={() => setFormOpen(true)}>
      <Plus size={15} strokeWidth={2.75} />
      Nuevo espacio de trabajo
    </Button>
  )

  return (
    <HomeShell>
      {isTeacher(user?.role) ? (
        // Cuenta docente: las pestañas ya dicen donde se esta, no hace falta otro titulo.
        <div className={styles.tabbedHead}>
          <HomeTabs action={nuevo} />
          <p className={styles.subtitle}>{saludo}</p>
        </div>
      ) : (
        <header className={styles.head}>
          <div>
            <h1 className={styles.title}>Mis espacios de trabajo</h1>
            <p className={styles.subtitle}>{saludo}</p>
          </div>
          {nuevo}
        </header>
      )}

      <InvitationsBanner />

      {isPending ? (
        <Skeleton label="Cargando tus espacios de trabajo…" lines={3} />
      ) : nadaQueMostrar ? (
        <EmptyState
          icon={<LayoutGrid size={22} strokeWidth={2.75} />}
          title="Todavía no tienes ningún espacio de trabajo"
          body="Crea uno por cada asignatura o trabajo. Nace con su tablero listo: Por hacer, En progreso y Hecho."
          action={
            <Button onClick={() => setFormOpen(true)}>
              <Plus size={15} strokeWidth={2.75} />
              Crear mi primer espacio de trabajo
            </Button>
          }
        />
      ) : (
        <div className={styles.grid}>
          {propios.map((workspace) => (
            <WorkspaceCard
              key={workspace.id}
              workspace={workspace}
              onOpen={open}
              actions={<WorkspaceCardActions workspace={workspace} />}
            />
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
          // La foto (si se eligio una) sigue subiendose de fondo tras la navegacion.
          navigate(ROUTES.planById(workspace.id))
          return workspace
        }}
      />
    </HomeShell>
  )
}
