import type { ReactNode } from 'react'
import { QueryClientProvider } from '@tanstack/react-query'
import { queryClient } from '@/app/query-client'
import { AuthProvider } from '@/state/auth'
import { UiProvider } from '@/state/ui'
import { CopilotJobsProvider } from '@/state/copilotJobs'
import { CopilotDraftsProvider } from '@/state/copilotDraft'
import { HelpChatProvider } from '@/state/helpChat'
import { ToastStack } from '@/components/ui/Toast'

/**
 * Providers globales: cache de servidor, sesion, estado de interfaz, las
 * generaciones de planificacion en curso y el chat de ayuda. Los dos ultimos
 * viven aqui, por encima de las rutas, porque ninguno debe depender de seguir en
 * la pantalla desde la que se lanzo: ni la generacion ni una pregunta a medias se
 * pierden al cambiar de seccion.
 */
export function AppProviders({ children }: { children: ReactNode }) {
  return (
    <QueryClientProvider client={queryClient}>
      <UiProvider>
        <CopilotJobsProvider>
          <CopilotDraftsProvider>
            <HelpChatProvider>
              <AuthProvider>
                {children}
                <ToastStack />
              </AuthProvider>
            </HelpChatProvider>
          </CopilotDraftsProvider>
        </CopilotJobsProvider>
      </UiProvider>
    </QueryClientProvider>
  )
}
