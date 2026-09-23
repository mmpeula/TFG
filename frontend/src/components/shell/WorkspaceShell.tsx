import { useMemo, useState, type ReactNode } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { ArrowLeft, Bot, CalendarRange, Columns3, LogOut, PauseCircle, Settings, Users } from 'lucide-react'
import { Avatar } from '@/components/ui/Avatar'
import { EmptyState } from '@/components/ui/EmptyState'
import { Tooltip } from '@/components/ui/Controls'
import { Assistant } from '@/components/assistant/Assistant'
import { ShortcutsDialog } from './ShortcutsDialog'
import { useShortcuts } from '@/hooks/useShortcuts'
import styles from './WorkspaceShell.module.css'
import { ROUTES } from '@/app/routes'
import { useAuth } from '@/state/context'
import { useWorkspaces } from '@/hooks/useData'
import { colorOf, iconOf } from '@/lib/workspace'
import { canManage } from '@/lib/roles'
import type { WorkspaceResponse } from '@/types/api'

const ICON = { size: 15, strokeWidth: 2.75 } as const

interface Props {
  workspace?: WorkspaceResponse
  workspaceId: number
  /** Accion principal de la pantalla, a la derecha de la cabecera. */
  action?: ReactNode
  /** Atajo «N»: crear tarea. Solo lo pasa el tablero. */
  onNewTask?: () => void
  children: ReactNode
}

/**
 * Marco de todo lo que pasa dentro de un workspace.
 *
 * Solo hay dos niveles de navegacion: volver a la lista, o cambiar de seccion.
 * Por eso la cabecera lleva la vuelta atras a la izquierda y cuatro pestanas, y
 * no una barra lateral con el resto de la aplicacion dentro.
 */
export function WorkspaceShell({ workspace, workspaceId, action, onNewTask, children }: Props) {
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const { user, signOut } = useAuth()
  const [helpOpen, setHelpOpen] = useState(false)
  const isTeacher = user?.role === 'DOCENTE'
  // Generar el plan con IA es cosa de quien administra el equipo;
  // preguntarle algo (ver Assistant) sigue abierto a cualquiera que no sea
  // el docente, que solo observa.
  const puedeAutomatizar = canManage(workspace?.myRole)

  // Suspendido: el servidor deja de servir su contenido, asi que sin esto la
  // pantalla se queda cargando para siempre sin decir por que. El dato sale de
  // la lista, que si lo sigue devolviendo precisamente para poder explicarlo.
  const { data: misWorkspaces } = useWorkspaces()
  const suspendido = misWorkspaces?.find((item) => item.id === workspaceId)?.enabled === false

  useShortcuts(
    useMemo(
      () => ({
        newTask: onNewTask,
        board: () => navigate(ROUTES.tableroById(workspaceId)),
        plan: () => navigate(ROUTES.planById(workspaceId)),
        generatePlan: puedeAutomatizar ? () => navigate(ROUTES.generarPlanById(workspaceId)) : undefined,
        pulse: () => navigate(ROUTES.pulsoById(workspaceId)),
        help: () => setHelpOpen(true),
        escape: () => setHelpOpen(false),
      }),
      [puedeAutomatizar, navigate, onNewTask, workspaceId],
    ),
  )

  const tabs = [
    { label: 'Plan', icon: <CalendarRange {...ICON} />, to: ROUTES.planById(workspaceId) },
    { label: 'Tablero', icon: <Columns3 {...ICON} />, to: ROUTES.tableroById(workspaceId) },
    ...(puedeAutomatizar ? [{ label: 'Generar plan', icon: <Bot {...ICON} />, to: ROUTES.generarPlanById(workspaceId) }] : []),
    { label: 'Miembros', icon: <Users {...ICON} />, to: ROUTES.miembrosById(workspaceId) },
  ]

  return (
    <div className={styles.shell}>
      <header className={styles.top}>
        <div className={styles.identity}>
          <button className={styles.back} onClick={() => navigate(isTeacher ? ROUTES.docente : ROUTES.workspaces)}>
            <ArrowLeft size={15} strokeWidth={2.75} />
            {isTeacher ? 'Panel docente' : 'Mis espacios de trabajo'}
          </button>
          <h1 className={styles.name}>
            {workspace && (
              <span className={styles.badge} style={{ background: colorOf(workspace) }} aria-hidden="true">
                {iconOf(workspace)}
              </span>
            )}
            {workspace?.name ?? 'Workspace'}
          </h1>
        </div>

        <div className={styles.actions}>
          {action}
          {canManage(workspace?.myRole) && (
            <Tooltip label="Ajustes del workspace">
              <button
                className={styles.icon}
                onClick={() => navigate(ROUTES.ajustesById(workspaceId))}
                aria-label="Ajustes del workspace"
              >
                <Settings size={16} strokeWidth={2.75} />
              </button>
            </Tooltip>
          )}
          <button className={styles.profile} onClick={() => navigate(ROUTES.configuracion)} aria-label="Mi cuenta">
            <Avatar user={user} size={28} />
          </button>
          <Tooltip label="Cerrar sesion">
            <button className={styles.icon} onClick={signOut} aria-label="Cerrar sesion">
              <LogOut size={16} strokeWidth={2.75} />
            </button>
          </Tooltip>
        </div>
      </header>

      {!suspendido && (
      <nav className={styles.tabs} aria-label="Secciones del workspace">
        {tabs.map((tab) => (
          <button
            key={tab.to}
            className={[styles.tab, pathname === tab.to && styles.tabActive].filter(Boolean).join(' ')}
            aria-current={pathname === tab.to ? 'page' : undefined}
            onClick={() => navigate(tab.to)}
          >
            {tab.icon}
            {tab.label}
          </button>
        ))}
      </nav>
      )}

      <main className={styles.content}>
        {suspendido ? (
          <EmptyState
            icon={<PauseCircle size={22} strokeWidth={2.75} />}
            title="Este workspace está suspendido"
            body="Lo ha parado quien administra la plataforma. No se ha perdido nada: el tablero, las tareas y los ficheros siguen ahí y volverán en cuanto lo reactiven."
          />
        ) : (
          children
        )}
      </main>

      {/* Flotante sobre cualquier seccion del workspace: preguntar «que hago
          primero?» se hace mirando el tablero, no yendose a otra pantalla. El
          docente no lo ve: observa el trabajo, no lo hace. */}
      {!isTeacher && <Assistant workspaceId={workspaceId} />}

      <ShortcutsDialog open={helpOpen} onClose={() => setHelpOpen(false)} />
    </div>
  )
}
