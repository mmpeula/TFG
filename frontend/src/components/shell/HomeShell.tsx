import type { ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { LogOut } from 'lucide-react'
import { Avatar } from '@/components/ui/Avatar'
import { Tooltip } from '@/components/ui/Controls'
import styles from './HomeShell.module.css'
import logo from '@/assets/logo.png'
import { homeForRole, ROUTES } from '@/app/routes'
import { useAuth } from '@/state/context'

/**
 * Marco de la pantalla inicial. No lleva barra lateral a proposito: en el nivel
 * de «mis espacios de trabajo» no hay nada que navegar todavia, solo elegir uno.
 *
 * El panel docente no tiene boton aqui: es la segunda pestaña del inicio de
 * una cuenta docente (ver HomeTabs), no algo de la cabecera.
 */
export function HomeShell({ children }: { children: ReactNode }) {
  const { user, signOut } = useAuth()
  const navigate = useNavigate()

  return (
    <div className={styles.shell}>
      <header className={styles.top}>
        <button className={styles.brand} onClick={() => navigate(homeForRole(user?.role ?? 'MIEMBRO'))}>
          <span className={styles.mark}>
            <img src={logo} alt="" />
          </span>
          <span className={styles.brandName}>Planif<span className={styles.ia}>IA</span></span>
        </button>

        <div className={styles.actions}>
          <button className={styles.profile} onClick={() => navigate(ROUTES.configuracion)}>
            <Avatar user={user} avatarUrl={user?.avatarUrl} size={30} />
            <span className={styles.profileName}>{user?.fullName}</span>
          </button>
          <Tooltip label="Cerrar sesión">
            <button className={styles.icon} onClick={signOut} aria-label="Cerrar sesión">
              <LogOut size={16} strokeWidth={2.75} />
            </button>
          </Tooltip>
        </div>
      </header>

      <main className={styles.content}>{children}</main>
    </div>
  )
}
