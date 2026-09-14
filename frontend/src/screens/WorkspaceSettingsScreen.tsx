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
import {
  useActivity, useBoardMutations, useProject, useProjectBoards, useWorkspace, useWorkspaceMutations,
} from '@/hooks/useData'
import { canManage } from '@/lib/roles'
import { formatDate } from '@/lib/format'
import { DEFAULT_SPRINT_DAYS } from '@/lib/sprints'
import { ROUTES } from '@/app/routes'

/**
 * Ajustes del workspace: lo que se toca una vez y no se vuelve a mirar.
 *
 * Vive detras del engranaje y no en las pestanas a proposito: sacar de aqui el
 * guion, los tableros o el borrado a la navegacion principal solo anadiria ruido
 * al 99 % de las veces que se entra al workspace a mirar el tablero.
 */
export function WorkspaceSettingsScreen() {
  const { workspaceId: param } = useParams()
  const workspaceId = Number(param)
  const navigate = useNavigate()

  const { data: workspace, isPending } = useWorkspace(workspaceId)
  const { data: project } = useProject(workspaceId)
  const { data: boards } = useProjectBoards(workspaceId)
  const { data: activity } = useActivity(workspaceId)
  const mutations = useWorkspaceMutations(workspaceId)
  const boardMutations = useBoardMutations(boards?.[0]?.id, workspaceId)

  const [editOpen, setEditOpen] = useState(false)

  const manageable = canManage(workspace?.myRole)

  return (
    <WorkspaceShell workspace={workspace} workspaceId={workspaceId}>
      {isPending || !workspace ? (
        <Skeleton label="Cargando los ajustes…" lines={3} />
      ) : (
        <div className={styles.layout}>
          <Section
            title="Datos del workspace"
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
                <dd>{workspace.startDate ? formatDate(workspace.startDate) : 'Sin fecha'}</dd>
              </div>
              <div>
                <dt>Se entrega</dt>
                <dd>{workspace.deadline ? formatDate(workspace.deadline) : 'Sin fecha'}</dd>
              </div>
              <div>
                <dt>Duracion del sprint</dt>
                <dd>{(workspace.sprintDays ?? DEFAULT_SPRINT_DAYS) / 7} semanas</dd>
              </div>
              <div>
                <dt>Tareas</dt>
                <dd>
                  {workspace.doneTasks} de {workspace.totalTasks} hechas
                </dd>
              </div>
            </dl>
          </Section>

          <Section title="Tableros" count={boards?.length}>
            {boards?.length === 0 ? (
              <EmptyState title="Sin tableros" body="Este workspace todavia no tiene ninguno." />
            ) : (
              <div className={styles.rows}>
                {boards?.map((board) => (
                  <div key={board.id} className={styles.row}>
                    <div className={styles.rowText}>
                      <div className={styles.rowName}>{board.name}</div>
                      <div className={styles.rowMeta}>{board.description || 'Sin descripcion'}</div>
                    </div>
                    <Button variant="secondary" small onClick={() => navigate(ROUTES.tableroById(workspaceId))}>
                      Abrir
                    </Button>
                    {manageable && boards.length > 1 && (
                      <ConfirmDialog
                        trigger={
                          <Button variant="ghost" small aria-label="Borrar tablero">
                            <Trash2 size={13} strokeWidth={2.75} />
                          </Button>
                        }
                        title={`Borrar «${board.name}»`}
                        description="Se borraran sus columnas y tareas."
                        onConfirm={() => boardMutations.removeBoard.mutateAsync(board.id)}
                      />
                    )}
                  </div>
                ))}
              </div>
            )}
          </Section>

          {project?.briefing && (
            <Section title="Enunciado guardado" subtitle="lo que lee el copiloto para proponer tareas">
              <p className={styles.briefing}>{project.briefing}</p>
            </Section>
          )}

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
                    Borrar este workspace
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
