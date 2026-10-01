import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Pencil, Trash2 } from 'lucide-react'
import { WorkspaceShell } from '@/components/shell/WorkspaceShell'
import { WorkspaceFormModal } from '@/components/workspace/WorkspaceFormModal'
import { Button } from '@/components/ui/Button'
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
        <div className={styles.layout}>
          <Section
            title="Datos del espacio de trabajo"
            action={
              manageable && (
                <Button small variant="secondary" onClick={() => setEditOpen(true)}>
                  <Pencil size={13} strokeWidth={2.75} />
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
                <dt>Descripcion</dt>
                <dd>{workspace.description || 'Sin descripcion'}</dd>
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
                <dt>Duracion del sprint</dt>
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

          <Section title="Actividad reciente">
            {activity && activity.content.length > 0 ? (
              <ActivityFeed items={activity.content.slice(0, 8)} />
            ) : (
              <EmptyState title="Sin actividad" body="Aqui apareceran los cambios del grupo." />
            )}
          </Section>

          {manageable && (
            <div className={styles.danger}>
              <ConfirmDialog
                trigger={
                  <Button variant="secondary" small>
                    <Trash2 size={13} strokeWidth={2.75} />
                    Borrar este espacio de trabajo
                  </Button>
                }
                title={`Borrar «${workspace.name}»`}
                description="Se borraran su tablero, sus tareas, sus comentarios y sus ficheros. No se puede deshacer."
                onConfirm={async () => {
                  await mutations.remove.mutateAsync()
                  navigate(ROUTES.workspaces)
                }}
              />
            </div>
          )}
        </div>
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
