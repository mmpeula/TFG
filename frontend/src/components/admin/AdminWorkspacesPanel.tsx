import { useMemo, useState } from 'react'
import { AlertTriangle, ChevronDown, ChevronRight, LayoutGrid, PauseCircle, PlayCircle, Trash2 } from 'lucide-react'
import { AdminSearch } from './AdminSearch'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { Tag } from '@/components/ui/Tag'
import { Chip, Dropdown, DropdownItem } from '@/components/ui/Controls'
import { EmptyState } from '@/components/ui/EmptyState'
import { Skeleton } from '@/components/ui/Skeleton'
import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import styles from './AdminPanel.module.css'
import { useAdminWorkspaceMutations, useAdminWorkspaces } from '@/hooks/useData'
import { ROLE_LABELS } from '@/lib/roles'
import { formatDate, matchesSearch, relativeTime } from '@/lib/format'
import type { AdminWorkspaceResponse, Role } from '@/types/api'

/** Por nombre del espacio, por quien lo creo o por el correo de cualquiera de su equipo. */
function matches(workspace: AdminWorkspaceResponse, term: string): boolean {
  return matchesSearch(term, workspace.name, workspace.ownerName, ...workspace.members.map((m) => m.email))
}

/**
 * Los workspaces de toda la plataforma, para dar soporte.
 *
 * La lista solo dice cual es cada uno: nombre, quien lo creo y si esta
 * atascado. Todo lo demas —el equipo, los numeros, las dos acciones— vive en el
 * detalle que se abre al pulsar, porque son cosas que se miran de uno en uno,
 * cuando ya sabes en cual te tienes que fijar. Meterlas en la fila llenaba la
 * pantalla de datos que no se leen.
 *
 * Lo que no hay, ni en la lista ni en el detalle, es el contenido del tablero:
 * ni tareas, ni comentarios. El backend tampoco los sirve aqui.
 */
export function AdminWorkspacesPanel() {
  const { data, isPending } = useAdminWorkspaces()
  const mutations = useAdminWorkspaceMutations()
  const [search, setSearch] = useState('')
  const [filtro, setFiltro] = useState<'todos' | 'bloqueados' | 'suspendidos'>('todos')
  const [abierto, setAbierto] = useState<number | null>(null)

  const todos = useMemo(() => data ?? [], [data])
  const workspaces = useMemo(
    () => todos.filter((w) => matches(w, search)
      && (filtro === 'todos'
        || (filtro === 'bloqueados' && w.blocked)
        || (filtro === 'suspendidos' && !w.enabled))),
    [todos, search, filtro],
  )
  const bloqueados = todos.filter((w) => w.blocked).length
  const suspendidos = todos.filter((w) => !w.enabled).length
  // Se busca en la lista viva: si se borra el que estaba abierto, el detalle se cierra solo.
  const detalle = todos.find((w) => w.id === abierto) ?? null

  if (isPending) return <Skeleton label="Cargando los espacios de trabajo…" lines={3} />

  return (
    <div className={styles.panel}>
      <div className={styles.toolbar}>
        <AdminSearch
          value={search}
          onChange={setSearch}
          placeholder="Buscar por espacio de trabajo, creador o correo"
          label="Buscar espacios de trabajo"
        />

        <div className={styles.filters}>
          <Chip active={filtro === 'todos'} onClick={() => setFiltro('todos')}>
            Todos {todos.length}
          </Chip>
          <Chip active={filtro === 'bloqueados'} onClick={() => setFiltro('bloqueados')}>
            Sin responsable {bloqueados}
          </Chip>
          <Chip active={filtro === 'suspendidos'} onClick={() => setFiltro('suspendidos')}>
            Suspendidos {suspendidos}
          </Chip>
        </div>
      </div>

      {workspaces.length === 0 ? (
        <EmptyState
          icon={<LayoutGrid size={22} strokeWidth={2.75} />}
          title={todos.length === 0 ? 'Todavía no hay ningún espacio de trabajo' : 'Sin resultados'}
          body={
            todos.length === 0
              ? 'Aquí aparecerán los espacios de trabajo de toda la plataforma en cuanto alguien cree el primero.'
              : 'Ningún espacio de trabajo coincide con ese filtro.'
          }
        />
      ) : (
        <ul className={styles.rows}>
          {workspaces.map((workspace) => (
            <li key={workspace.id}>
              <button
                className={[styles.rowButton, (workspace.blocked || !workspace.enabled) && styles.rowOff]
                  .filter(Boolean)
                  .join(' ')}
                onClick={() => setAbierto(workspace.id)}
              >
                <span className={styles.wsName}>
                  <span className={styles.wsTitle}>
                    {workspace.name}
                    {!workspace.enabled && (
                      <Tag variant="neutral" small>
                        <PauseCircle size={11} strokeWidth={2.75} />
                        Suspendido
                      </Tag>
                    )}
                    {workspace.blocked && (
                      <Tag variant="neutral" small>
                        <AlertTriangle size={11} strokeWidth={2.75} />
                        Sin responsable
                      </Tag>
                    )}
                  </span>
                  <span className={styles.wsOwner}>
                    {workspace.ownerName ? `Creado por ${workspace.ownerName}` : 'Sin creador'}
                  </span>
                </span>
                <ChevronRight size={15} strokeWidth={2.75} className={styles.chevron} aria-hidden="true" />
              </button>
            </li>
          ))}
        </ul>
      )}

      <WorkspaceDetail
        workspace={detalle}
        mutations={mutations}
        onClose={() => setAbierto(null)}
      />
    </div>
  )
}

/** El estado del workspace y las dos acciones de soporte, de uno en uno. */
function WorkspaceDetail({
  workspace,
  mutations,
  onClose,
}: {
  workspace: AdminWorkspaceResponse | null
  mutations: ReturnType<typeof useAdminWorkspaceMutations>
  onClose: () => void
}) {
  if (!workspace) return null

  const responsablesActivos = workspace.members.filter((m) => m.role === 'RESPONSABLE' && m.enabled)

  /**
   * Los roles que se le pueden dar a quien trabaja en el equipo: alumno o responsable.
   * Docente no, ni hacia el ni desde el: ese papel se elige al invitar y no se cambia
   * despues (un docente ni siquiera tiene menu, ver abajo). Y al ultimo responsable no se
   * le deja bajar, porque el equipo se quedaria sin nadie que pueda administrarlo; el
   * servidor tambien lo rechaza, esto solo evita ofrecer algo que va a fallar.
   */
  function rolesPara(member: AdminWorkspaceResponse['members'][number]): Role[] {
    const esUltimoResponsable = member.role === 'RESPONSABLE' && responsablesActivos.length === 1
    return esUltimoResponsable ? ['RESPONSABLE'] : ['RESPONSABLE', 'MIEMBRO']
  }

  return (
    <Modal open title={workspace.name} onClose={onClose}>
      <div className={styles.detail}>
        {!workspace.enabled && (
          <p className={styles.detailWarning}>
            <PauseCircle size={14} strokeWidth={2.75} />
            Suspendido: su equipo no puede abrirlo ni tocar nada suyo. No se ha borrado nada;
            al reactivarlo vuelve a estar como estaba.
          </p>
        )}

        {workspace.blocked && (
          <p className={styles.detailWarning}>
            <AlertTriangle size={14} strokeWidth={2.75} />
            Nadie puede administrar este espacio de trabajo: no queda ninguna persona responsable con la
            cuenta activa. Nombra a alguien del equipo para desatascarlo.
          </p>
        )}

        <dl className={styles.facts}>
          <div>
            <dt>Creado por</dt>
            <dd>{workspace.ownerName ?? '—'}</dd>
          </div>
          <div>
            <dt>Creado el</dt>
            <dd>{formatDate(workspace.createdAt)}</dd>
          </div>
          <div>
            <dt>Última actividad</dt>
            <dd>{workspace.lastActivityAt ? relativeTime(workspace.lastActivityAt) : 'sin actividad'}</dd>
          </div>
          <div>
            <dt>Tareas</dt>
            <dd>{workspace.taskCount}</dd>
          </div>
        </dl>

        <section>
          <h3 className={styles.detailTitle}>
            Equipo ({workspace.members.length})
          </h3>
          {workspace.members.length === 0 ? (
            <p className={styles.wsNobody}>El equipo está vacío.</p>
          ) : (
            <ul className={styles.team}>
              {workspace.members.map((member) => (
                <li
                  key={member.id}
                  className={[styles.teamRow, !member.enabled && styles.rowOff].filter(Boolean).join(' ')}
                >
                  <span className={styles.teamName}>{member.fullName}</span>
                  <span className={styles.cellMuted}>{member.email}</span>
                  {!member.enabled ? (
                    <span className={styles.teamRole}>deshabilitada</span>
                  ) : member.role === 'DOCENTE' ? (
                    <span className={styles.roleStatic} title="El rol de docente se elige al invitar y no se cambia">
                      {ROLE_LABELS[member.role]}
                    </span>
                  ) : (
                    <Dropdown
                      trigger={({ toggle }) => (
                        <button className={styles.roleButton} onClick={toggle}>
                          {ROLE_LABELS[member.role]}
                          <ChevronDown size={14} strokeWidth={2.75} aria-hidden="true" />
                        </button>
                      )}
                    >
                      {(close) => (
                        <>
                          {rolesPara(member).map((role) => (
                            <DropdownItem
                              key={role}
                              active={role === member.role}
                              onClick={() => {
                                close()
                                mutations.cambiarRol.mutate({
                                  workspaceId: workspace.id,
                                  userId: member.id,
                                  role,
                                })
                              }}
                            >
                              {ROLE_LABELS[role]}
                            </DropdownItem>
                          ))}
                        </>
                      )}
                    </Dropdown>
                  )}
                </li>
              ))}
            </ul>
          )}
          {responsablesActivos.length === 1 && (
            <p className={styles.help}>
              Solo queda una persona responsable: para poder cambiarle el rol, nombra antes a otra.
              Un equipo sin responsable no lo puede administrar nadie.
            </p>
          )}
        </section>

        <p className={styles.help}>
          Desde aquí se ve el estado del espacio de trabajo, no su contenido: las tareas y los comentarios
          son del equipo. Lo que hagas queda anotado en la actividad del propio espacio de trabajo.
        </p>

        <div className={styles.detailDanger}>
          {/* Parar esta antes que borrar a proposito: casi siempre es lo que hace
              falta, y borrar se lleva el trabajo del grupo sin vuelta atras. */}
          {workspace.enabled ? (
            <ConfirmDialog
              trigger={
                <Button variant="secondary" small>
                  <PauseCircle size={13} strokeWidth={2.75} />
                  Suspender
                </Button>
              }
              title={`Suspender «${workspace.name}»`}
              description="Su equipo dejará de poder abrirlo y de tocar nada suyo. No se borra nada: puedes reactivarlo cuando quieras."
              confirmLabel="Suspender"
              isPending={mutations.setEnabled.isPending}
              onConfirm={() =>
                mutations.setEnabled.mutateAsync({ workspaceId: workspace.id, enabled: false })
              }
            />
          ) : (
            <Button
              variant="secondary"
              small
              disabled={mutations.setEnabled.isPending}
              onClick={() =>
                mutations.setEnabled.mutate({ workspaceId: workspace.id, enabled: true })
              }
            >
              <PlayCircle size={13} strokeWidth={2.75} />
              Reactivar
            </Button>
          )}

          <ConfirmDialog
            trigger={
              <Button variant="secondary" small>
                <Trash2 size={13} strokeWidth={2.75} />
                Borrar este espacio de trabajo
              </Button>
            }
            title={`Borrar «${workspace.name}»`}
            description="Se borrará para todo el equipo, con su tablero, sus tareas y sus comentarios. No se puede deshacer."
            isPending={mutations.remove.isPending}
            onConfirm={() => mutations.remove.mutateAsync(workspace.id).then(onClose)}
          />
        </div>
      </div>
    </Modal>
  )
}
