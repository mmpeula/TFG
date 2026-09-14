import type { ReactNode } from 'react'
import { QueryClientProvider } from '@tanstack/react-query'
import { queryClient } from '@/app/query-client'
import { AuthProvider } from '@/state/auth'
import { UiProvider } from '@/state/ui'
import { CopilotJobsProvider } from '@/state/copilotJobs'
import { ToastStack } from '@/components/ui/Toast'

/**
 * Providers globales: cache de servidor, sesion, estado de interfaz y las
 * generaciones de planificacion en curso. CopilotJobsProvider vive aqui, por
 * encima de las rutas, para que generar una planificacion no dependa de seguir
 * en la pestana Copiloto: la peticion sigue aunque el usuario navegue a otra.
 */
export function AppProviders({ children }: { children: ReactNode }) {
  return (
    <QueryClientProvider client={queryClient}>
      <UiProvider>
        <CopilotJobsProvider>
          <AuthProvider>
            {children}
            <ToastStack />
          </AuthProvider>
        </CopilotJobsProvider>
      </UiProvider>
    </QueryClientProvider>
  )
}
