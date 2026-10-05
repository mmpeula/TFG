import type { ReactNode } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { GraduationCap, LayoutGrid } from 'lucide-react'
import styles from './HomeTabs.module.css'
import { useAuth } from '@/state/context'
import { isTeacher } from '@/lib/roles'
import { ROUTES } from '@/app/routes'

const ICON = { size: 17, strokeWidth: 2.25 } as const

/**
 * Las dos pantallas de inicio de una cuenta docente: sus espacios, donde
 * trabaja o es responsable, y los equipos que sigue como docente. Pestañas
 * como las de dentro de un espacio (Plan, Tablero…), para que se vea que son
 * dos vistas hermanas y no una tarjeta mas de la cuadricula.
 *
 * Hacen de titulo de la pagina: encima no va otro «Mis espacios de trabajo»
 * que repita la pestaña. Panel docente va primero porque es lo propio de una
 * cuenta docente.
 *
 * Solo sale en cuentas docentes: un alumno no
 * tiene panel docente, y una sola pestaña no es un menu.
 */
export function HomeTabs({ action }: { action?: ReactNode }) {
  const { user } = useAuth()
  const { pathname } = useLocation()
  const navigate = useNavigate()

  if (!isTeacher(user?.role)) return null

  const tabs = [
    { label: 'Panel docente', icon: <GraduationCap {...ICON} />, to: ROUTES.docente },
    { label: 'Mis espacios de trabajo', icon: <LayoutGrid {...ICON} />, to: ROUTES.workspaces },
  ]
  // La pantalla de inicio tambien se sirve en «/», que es la misma que «Mis espacios».
  const active = pathname === ROUTES.docente ? ROUTES.docente : ROUTES.workspaces

  return (
    <nav className={styles.tabs} aria-label="Vistas de inicio">
      {tabs.map((tab) => (
        <button
          key={tab.to}
          type="button"
          className={[styles.tab, active === tab.to && styles.tabActive].filter(Boolean).join(' ')}
          aria-current={active === tab.to ? 'page' : undefined}
          onClick={() => navigate(tab.to)}
        >
          {tab.icon}
          {tab.label}
        </button>
      ))}
      {action && <span className={styles.action}>{action}</span>}
    </nav>
  )
}
