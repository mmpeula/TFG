import { Fragment, type ReactNode } from 'react'
import { QueryClientProvider } from '@tanstack/react-query'
import { queryClient } from '@/app/query-client'
import { AuthProvider } from '@/state/auth'
import { UiProvider } from '@/state/ui'
import { PlanJobsProvider } from '@/state/planJobs'
import { PlanDraftsProvider } from '@/state/planDraft'
import { AssistantProvider } from '@/state/assistant'
import { useAuth } from '@/state/context'
import { ToastStack } from '@/components/ui/Toast'

/**
 * Providers globales: cache de servidor, estado de interfaz, sesion, y lo que es de
 * cada sesion: las generaciones de planificacion en curso, los borradores de Generar
 * plan y el chat de ayuda. Esos tres viven por encima de las rutas porque ninguno debe
 * depender de seguir en la pantalla desde la que se lanzo: ni la generacion ni una
 * pregunta a medias se pierden al cambiar de seccion.
 */
export function AppProviders({ children }: { children: ReactNode }) {
  return (
    <QueryClientProvider client={queryClient}>
      <UiProvider>
        <AuthProvider>
          <SessionScope>
            <PlanJobsProvider>
              <PlanDraftsProvider>
                <AssistantProvider>{children}</AssistantProvider>
              </PlanDraftsProvider>
            </PlanJobsProvider>
          </SessionScope>
          <ToastStack />
        </AuthProvider>
      </UiProvider>
    </QueryClientProvider>
  )
}

/**
 * Lo de cada sesion se monta de nuevo cuando cambia quien la tiene abierta. Cerrar
 * sesion no recarga la pagina: sin esto, quien entrara despues en la misma pestana
 * veria la conversacion con el asistente o el documento a medio subir del anterior.
 */
function SessionScope({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  return <Fragment key={user?.id ?? 'sin-sesion'}>{children}</Fragment>
}
