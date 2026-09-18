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
 * Separa los tres espacios principales de la aplicacion. No basta con ocultar
 * enlaces: una URL pegada tampoco puede abrir el panel de otro tipo de cuenta.
 */
export function RoleAreaRoute() {
  const { user } = useAuth()
  const location = useLocation()
  const path = location.pathname
  const isWorkspaceArea = path.startsWith('/workspaces')
    || path.startsWith('/equipos') || path.startsWith('/proyectos') || path.startsWith('/proyecto')
  const isTeacherWorkspaceArea = /^\/workspaces\/[^/]+\/(plan|tablero|miembros|pulso)$/.test(path)

  const hasAccess = user?.role === 'ADMIN'
    ? path === ROUTES.administracion
    : user?.role === 'DOCENTE'
      ? path === ROUTES.docente || isTeacherWorkspaceArea
      : isWorkspaceArea

  if (!hasAccess) return <Navigate to={homeForRole(user?.role ?? 'MIEMBRO')} replace />

  return <Outlet />
}
