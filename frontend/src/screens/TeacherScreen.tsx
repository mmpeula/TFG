import { useMemo } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { AlertTriangle, GraduationCap } from 'lucide-react'
import { HomeShell } from '@/components/shell/HomeShell'
import { WorkspaceCard } from '@/components/workspace/WorkspaceCard'
import { Tag } from '@/components/ui/Tag'
import { EmptyState } from '@/components/ui/EmptyState'
import { Skeleton } from '@/components/ui/Skeleton'
import styles from './WorkspacesScreen.module.css'
import { useBoard, useWorkspaces } from '@/hooks/useData'
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
 */
export function TeacherScreen() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const { data: workspaces, isPending } = useWorkspaces()

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
          <h1 className={styles.title}>Panel docente</h1>
          <p className={styles.subtitle}>Los equipos que sigues como docente, de un vistazo.</p>
        </div>
      </header>

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
