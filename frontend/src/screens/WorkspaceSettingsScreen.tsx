import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Pencil, Trash2 } from 'lucide-react'
import { WorkspaceShell } from '@/components/shell/WorkspaceShell'
import { WorkspaceFormModal } from '@/components/workspace/WorkspaceFormModal'
import { Button } from '@/components/ui/Button'
import { Card, CardGroup } from '@/components/ui/Card'
import { EmptyState } from '@/components/ui/EmptyState'
import { Skeleton } from '@/components/ui/Skeleton'
import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import { ActivityFeed } from '@/components/home/ActivityFeed'
import { Section } from './Section'
import styles from './WorkspaceSettingsScreen.module.css'
import { useActivity, useWorkspace, useWorkspaceMutations } from '@/hooks/useData'
import { canManage } from '@/lib/roles'
import { formatDate } from '@/lib/format'
import { DEFAULT_SPRINT_DAYS } from '@/lib/sprints'
import { ROUTES } from '@/app/routes'

/**
 * Ajustes del workspace: lo que se toca una vez y no se vuelve a mirar.
 *
 * Vive detras del engranaje y no en las pestanas a proposito: sacar de aqui el
 * borrado a la navegacion principal solo anadiria ruido al 99 % de las veces
 * que se entra al workspace a mirar el tablero.
 */
export function WorkspaceSettingsScreen() {
  const { workspaceId: param } = useParams()
  const workspaceId = Number(param)
  const navigate = useNavigate()

  const { data: workspace, isPending } = useWorkspace(workspaceId)
  const { data: activity } = useActivity(workspaceId)
  const mutations = useWorkspaceMutations(workspaceId)

  const [editOpen, setEditOpen] = useState(false)

  const manageable = canManage(workspace?.myRole)

  return (
    <WorkspaceShell workspace={workspace} workspaceId={workspaceId}>
      {isPending || !workspace ? (
        <Skeleton label="Cargando los ajustes…" lines={3} />
      ) : (
        <CardGroup>
          <Section
            title="Datos del espacio de trabajo"
            subtitle="Nombre, fechas y duración de los sprints."
            action={
              manageable && (
                <Button variant="secondary" onClick={() => setEditOpen(true)}>
                  <Pencil size={14} strokeWidth={2.5} />
                  Editar
                </Button>
              )
            }
          >
            <dl className={styles.data}>
              <div>
                <dt>Nombre</dt>
                <dd>{workspace.name}</dd>
              </div>
              <div>
                <dt>Descripción</dt>
                <dd>{workspace.description || 'Sin descripción'}</dd>
              </div>
              <div>
                <dt>Empieza</dt>
                <dd className={styles.measured}>{workspace.startDate ? formatDate(workspace.startDate) : 'Sin fecha'}</dd>
              </div>
              <div>
                <dt>Se entrega</dt>
                <dd className={styles.measured}>{workspace.deadline ? formatDate(workspace.deadline) : 'Sin fecha'}</dd>
              </div>
              <div>
                <dt>Duración del sprint</dt>
                <dd className={styles.measured}>{(workspace.sprintDays ?? DEFAULT_SPRINT_DAYS) / 7} semanas</dd>
              </div>
              <div>
                <dt>Tareas</dt>
                <dd className={styles.measured}>
                  {workspace.doneTasks} de {workspace.totalTasks} hechas
                </dd>
              </div>
            </dl>
          </Section>

          <Section title="Actividad reciente" subtitle="Los últimos cambios del equipo en el tablero.">
            {activity && activity.content.length > 0 ? (
              <ActivityFeed items={activity.content.slice(0, 8)} />
            ) : (
              <EmptyState title="Sin actividad" body="Aquí aparecerán los cambios del grupo." />
            )}
          </Section>

          {manageable && (
            <Card
              title="Borrar este espacio de trabajo"
              description="Se borran su tablero, sus tareas, sus comentarios y su historial. No se puede deshacer."
              aside={
                <ConfirmDialog
                  trigger={
                    <Button variant="secondary">
                      <Trash2 size={14} strokeWidth={2.5} />
                      Borrar
                    </Button>
                  }
                  title={`Borrar «${workspace.name}»`}
                  description="Se borrarán su tablero, sus tareas, sus comentarios y su historial. No se puede deshacer."
                  onConfirm={async () => {
                    await mutations.remove.mutateAsync()
                    navigate(ROUTES.workspaces)
                  }}
                />
              }
            />
          )}
        </CardGroup>
      )}

      <WorkspaceFormModal
        open={editOpen}
        workspace={workspace}
        isPending={mutations.update.isPending}
        onClose={() => setEditOpen(false)}
        onSubmit={(payload) => mutations.update.mutateAsync(payload)}
      />
    </WorkspaceShell>
  )
}
