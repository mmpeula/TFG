import { useState } from 'react'
import { Navigate } from 'react-router-dom'
import { HomeShell } from '@/components/shell/HomeShell'
import { Segmented } from '@/components/ui/Controls'
import { AdminAccountsPanel } from '@/components/admin/AdminAccountsPanel'
import { AdminWorkspacesPanel } from '@/components/admin/AdminWorkspacesPanel'
import styles from './AdminScreen.module.css'
import { useAuth } from '@/state/context'
import { isAdmin } from '@/lib/roles'
import { ROUTES } from '@/app/routes'

/**
 * El panel de quien administra la plataforma. Dos cosas, y nada mas: las
 * cuentas y, para dar soporte, el estado de los workspaces.
 *
 * Distinto de MembersScreen a proposito: ese gestiona el rol de equipo de un
 * workspace concreto; aqui se gestiona el rol de la cuenta, que no depende de
 * ningun equipo.
 */
export function AdminScreen() {
  const { user } = useAuth()
  const [seccion, setSeccion] = useState<'cuentas' | 'workspaces'>('cuentas')

  if (!isAdmin(user?.role)) {
    return <Navigate to={ROUTES.home} replace />
  }

  return (
    <HomeShell>
      <div className={styles.layout}>
        <header className={styles.head}>
          <h1 className={styles.title}>Administración de la plataforma</h1>
          <Segmented
            ariaLabel="Qué administrar"
            value={seccion}
            onChange={setSeccion}
            options={[
              { value: 'cuentas', label: 'Cuentas' },
              { value: 'workspaces', label: 'Espacios de trabajo' },
            ]}
          />
        </header>

        {seccion === 'cuentas' ? <AdminAccountsPanel /> : <AdminWorkspacesPanel />}
      </div>
    </HomeShell>
  )
}
