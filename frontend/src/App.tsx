import { Navigate, Route, Routes, useParams } from 'react-router-dom'
import { homeForRole, ROUTES } from './app/routes'
import { ProtectedRoute, RoleAreaRoute } from './screens/ProtectedRoute'
import { LoginScreen } from './screens/LoginScreen'
import { RegisterScreen } from './screens/RegisterScreen'
import { WorkspacesScreen } from './screens/WorkspacesScreen'
import { PlanScreen } from './screens/PlanScreen'
import { BoardScreen } from './screens/BoardScreen'
import { CopilotScreen } from './screens/CopilotScreen'
import { MembersScreen } from './screens/MembersScreen'
import { WorkspaceSettingsScreen } from './screens/WorkspaceSettingsScreen'
import { PulseScreen } from './screens/PulseScreen'
import { SettingsScreen } from './screens/SettingsScreen'
import { AdminScreen } from './screens/AdminScreen'
import { TeacherScreen } from './screens/TeacherScreen'
import { InvitationScreen } from './screens/InvitationScreen'
import { useAuth } from './state/context'

/**
 * Redireccion de una ruta antigua a su equivalente nueva, conservando el id.
 * Existe para que los enlaces guardados de la version anterior sigan valiendo.
 */
function LegacyRedirect({ to }: { to: (id: string) => string }) {
  const { workspaceId } = useParams()
  return <Navigate to={workspaceId ? to(workspaceId) : ROUTES.workspaces} replace />
}

/** Evita que una cuenta entre en un panel que no corresponde a su rol global. */
function RoleHomeRedirect() {
  const { user } = useAuth()
  return <Navigate to={homeForRole(user?.role ?? 'MIEMBRO')} replace />
}

export function App() {
  return (
    <Routes>
      <Route path={ROUTES.login} element={<LoginScreen />} />
      <Route path={ROUTES.register} element={<RegisterScreen />} />

      <Route element={<ProtectedRoute />}>
        <Route path={ROUTES.home} element={<RoleHomeRedirect />} />

        {/* Fuera del area por rol a proposito: responder a una invitacion no es
            entrar en el espacio de nadie, y la reciben tanto estudiantes como
            docentes. Lo que si hace falta es tener sesion, para saber de quien
            es la invitacion. */}
        <Route path={ROUTES.invitacion} element={<InvitationScreen />} />

        <Route element={<RoleAreaRoute />}>
          <Route path={ROUTES.workspaces} element={<WorkspacesScreen />} />

          {/* Entrar en un workspace es ver su plan; el tablero es el dia a dia. */}
          <Route path={ROUTES.workspace} element={<LegacyRedirect to={ROUTES.planById} />} />
          <Route path={ROUTES.plan} element={<PlanScreen />} />
          <Route path={ROUTES.tablero} element={<BoardScreen />} />
          <Route path={ROUTES.copiloto} element={<CopilotScreen />} />
          <Route path={ROUTES.miembros} element={<MembersScreen />} />
          <Route path={ROUTES.ajustes} element={<WorkspaceSettingsScreen />} />
          <Route path={ROUTES.pulso} element={<PulseScreen />} />

          {/* Solo la ve quien tiene rol global ADMIN. */}
          <Route path={ROUTES.administracion} element={<AdminScreen />} />
          <Route path={ROUTES.docente} element={<TeacherScreen />} />

          {/* Rutas de la version anterior. */}
          <Route path={ROUTES.legacyEquipos} element={<Navigate to={ROUTES.workspaces} replace />} />
          <Route path={ROUTES.legacyEquipo} element={<Navigate to={ROUTES.workspaces} replace />} />
          <Route path={ROUTES.legacyProyectos} element={<Navigate to={ROUTES.workspaces} replace />} />
          <Route path={ROUTES.legacyProyecto} element={<LegacyRedirect to={ROUTES.planById} />} />
          <Route path={ROUTES.legacyTablero} element={<LegacyRedirect to={ROUTES.tableroById} />} />
          <Route path={ROUTES.legacyCopiloto} element={<LegacyRedirect to={ROUTES.copilotoById} />} />
          <Route path={ROUTES.legacyPulso} element={<LegacyRedirect to={ROUTES.pulsoById} />} />
        </Route>

        <Route path={ROUTES.configuracion} element={<SettingsScreen />} />
      </Route>

      <Route path="*" element={<Navigate to={ROUTES.workspaces} replace />} />
    </Routes>
  )
}
