import { useMemo, useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { AlertTriangle, ArrowLeft, GraduationCap, Plus } from 'lucide-react'
import { HomeShell } from '@/components/shell/HomeShell'
import { WorkspaceCard } from '@/components/workspace/WorkspaceCard'
import { InvitationsBanner } from '@/components/workspace/InvitationsBanner'
import { WorkspaceFormModal } from '@/components/workspace/WorkspaceFormModal'
import { Button } from '@/components/ui/Button'
import { Tag } from '@/components/ui/Tag'
import { EmptyState } from '@/components/ui/EmptyState'
import { Skeleton } from '@/components/ui/Skeleton'
import styles from './WorkspacesScreen.module.css'
import { useBoard, useWorkspaceMutations, useWorkspaces } from '@/hooks/useData'
import { deriveSignals } from '@/lib/signals'
import { isTeacher } from '@/lib/roles'
import { ROUTES } from '@/app/routes'
import { useAuth } from '@/state/context'
import type { WorkspaceResponse } from '@/types/api'

/**
 * Panel docente: los mismos equipos y la misma tarjeta que "Mis espacios de trabajo",
 * pero solo los que sigues como DOCENTE y con una alerta si el tablero
 * tiene algun cuello de botella (ver lib/signals.ts). Sin tablero por medio:
 * la alerta ya dice si hace falta entrar o no.
 *
 * No es la pantalla de inicio del docente —esa es la misma que la del alumno—, asi que
 * lleva la vuelta a ella y la opcion de crear un espacio propio, donde es responsable.
 */
export function TeacherScreen() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const { data: workspaces, isPending } = useWorkspaces()
  const { create } = useWorkspaceMutations()
  const [formOpen, setFormOpen] = useState(false)

  const observed = useMemo(
    () => (workspaces ?? []).filter((workspace) => isTeacher(workspace.myRole)),
    [workspaces],
  )

  if (user?.role !== 'DOCENTE') return <Navigate to={ROUTES.home} replace />

  function open(workspace: WorkspaceResponse) {
    navigate(ROUTES.pulsoById(workspace.id))
  }

  return (
    <HomeShell>
      <header className={styles.head}>
        <div>
          <button className={styles.backLink} onClick={() => navigate(ROUTES.workspaces)}>
            <ArrowLeft size={15} strokeWidth={2.75} />
            Mis espacios de trabajo
          </button>
          <h1 className={styles.title}>Panel docente</h1>
          <p className={styles.subtitle}>Los equipos que sigues como docente, de un vistazo.</p>
        </div>
        <Button onClick={() => setFormOpen(true)}>
          <Plus size={15} strokeWidth={2.75} />
          Nuevo espacio de trabajo
        </Button>
      </header>

      {/* Las invitaciones tambien se ven aqui: es donde el docente mira lo que sigue. */}
      <InvitationsBanner />

      {isPending ? (
        <Skeleton label="Cargando los equipos…" lines={3} />
      ) : observed.length === 0 ? (
        <EmptyState
          icon={<GraduationCap size={22} strokeWidth={2.75} />}
          title="Todavia no sigues ningun equipo"
          body="En cuanto un responsable te añada como docente a un espacio de trabajo, aparecerá aquí."
        />
      ) : (
        <div className={styles.grid}>
          {observed.map((workspace) => (
            <TeacherCard key={workspace.id} workspace={workspace} onOpen={open} />
          ))}
        </div>
      )}
      <WorkspaceFormModal
        open={formOpen}
        isPending={create.isPending}
        onClose={() => setFormOpen(false)}
        onSubmit={async (payload) => {
          const workspace = await create.mutateAsync(payload)
          // Como en el inicio: se entra directamente en el espacio recien creado.
          navigate(ROUTES.planById(workspace.id))
          return workspace
        }}
      />
    </HomeShell>
  )
}

function TeacherCard({ workspace, onOpen }: { workspace: WorkspaceResponse; onOpen: (workspace: WorkspaceResponse) => void }) {
  const { data: board } = useBoard(workspace.boardId)
  const signals = useMemo(() => deriveSignals(board), [board])

  return (
    <WorkspaceCard
      workspace={workspace}
      onOpen={onOpen}
      alert={
        signals.length > 0 && (
          <Tag variant="neutral" small>
            <AlertTriangle size={11} strokeWidth={2.75} />
            {signals.length === 1 ? '1 alerta' : `${signals.length} alertas`}
          </Tag>
        )
      }
    />
  )
}
