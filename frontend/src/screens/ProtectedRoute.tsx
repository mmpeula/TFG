import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { Loader2 } from 'lucide-react'
import { useAuth } from '@/state/context'
import { homeForRole, ROUTES } from '@/app/routes'

/** Deja pasar solo a los usuarios autenticados; el resto va al login. */
export function ProtectedRoute() {
  const { isAuthenticated, isLoading } = useAuth()
  const location = useLocation()

  if (isLoading) {
    return (
      <div style={{ display: 'grid', placeItems: 'center', minHeight: '100vh' }}>
        <Loader2 size={24} strokeWidth={2.75} aria-label="Cargando" />
      </div>
    )
  }

  if (!isAuthenticated) {
    return <Navigate to={ROUTES.login} state={{ from: location.pathname }} replace />
  }

  return <Outlet />
}

/**
 * Separa las areas principales de la aplicacion. No basta con ocultar
 * enlaces: una URL pegada tampoco puede abrir el panel de otro tipo de cuenta.
 */
export function RoleAreaRoute() {
  const { user } = useAuth()
  const location = useLocation()
  const path = location.pathname
  const isWorkspaceArea = path.startsWith(ROUTES.workspaces)

  // Alumno y docente comparten area: lo que se puede hacer dentro de cada espacio lo
  // decide el rol que se tiene en ese espacio (y el servidor lo comprueba), no el de la
  // cuenta. Lo unico propio del docente es su panel de seguimiento.
  const hasAccess = user?.role === 'ADMIN'
    ? path === ROUTES.administracion
    : user?.role === 'DOCENTE'
      ? path === ROUTES.docente || isWorkspaceArea
      : isWorkspaceArea

  if (!hasAccess) return <Navigate to={homeForRole(user?.role ?? 'MIEMBRO')} replace />

  return <Outlet />
}
