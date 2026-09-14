import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { AuthContext, type AuthValue } from './context'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { auth } from '@/api/endpoints'
import { getStoredToken, setStoredToken, UNAUTHORIZED_EVENT } from '@/lib/api-client'
import type { AuthResponse } from '@/types/api'

/**
 * Sesion del usuario. El token vive en localStorage y los datos se piden a
 * /api/auth/me, de modo que al recargar la pagina la sesion se recupera sola.
 * Si el backend responde 401, el interceptor emite un evento y aqui se cierra.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [token, setToken] = useState<string | null>(() => getStoredToken())

  const { data, isLoading } = useQuery({
    queryKey: ['auth', 'me', token],
    queryFn: auth.me,
    enabled: token !== null,
    retry: false,
    staleTime: 5 * 60 * 1000,
  })

  const signIn = useCallback(
    (response: AuthResponse) => {
      setStoredToken(response.token)
      setToken(response.token)
      queryClient.setQueryData(['auth', 'me', response.token], response.user)
    },
    [queryClient],
  )

  const signOut = useCallback(() => {
    setStoredToken(null)
    setToken(null)
    queryClient.clear()
  }, [queryClient])

  useEffect(() => {
    const handler = () => signOut()
    window.addEventListener(UNAUTHORIZED_EVENT, handler)
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, handler)
  }, [signOut])

  const value = useMemo<AuthValue>(
    () => ({
      user: data ?? null,
      isAuthenticated: token !== null && data != null,
      isLoading: token !== null && isLoading,
      signIn,
      signOut,
    }),
    [data, token, isLoading, signIn, signOut],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

