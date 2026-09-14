import axios, { type AxiosError, type AxiosInstance } from 'axios'
import type { ApiError } from '@/types/api'

/**
 * Cliente HTTP unico. En desarrollo la baseURL es "/api" y Vite hace de proxy
 * hacia el backend; en Docker el proxy lo hace Nginx. En ambos casos, mismo
 * origen y por tanto sin CORS.
 */
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api'

export const apiClient: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: 20000,
  headers: { 'Content-Type': 'application/json' },
})

const TOKEN_KEY = 'planifia.token'
export const UNAUTHORIZED_EVENT = 'planifia:unauthorized'

export function getStoredToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}

export function setStoredToken(token: string | null): void {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token)
    else localStorage.removeItem(TOKEN_KEY)
  } catch {
    // Modo incognito: la sesion durara lo que la pestana.
  }
}

apiClient.interceptors.request.use((config) => {
  const token = getStoredToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

/** Si el token caduca, se borra y la aplicacion vuelve al login. */
apiClient.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiError>) => {
    const url = error.config?.url ?? ''
    const isAuthCall = url.includes('/auth/login') || url.includes('/auth/register')
    if (error.response?.status === 401 && !isAuthCall) {
      setStoredToken(null)
      window.dispatchEvent(new CustomEvent(UNAUTHORIZED_EVENT))
    }
    return Promise.reject(error)
  },
)

/** Mensaje legible a partir de un error de axios. */
export function toErrorMessage(error: unknown): string {
  if (axios.isAxiosError<ApiError>(error)) {
    const data = error.response?.data
    if (data?.fieldErrors?.length) {
      return data.fieldErrors.map((field) => `${field.field}: ${field.message}`).join(' · ')
    }
    if (data?.message) return data.message
    if (error.code === 'ECONNABORTED') return 'El servidor ha tardado demasiado en responder'
    return error.message
  }
  return error instanceof Error ? error.message : 'Error desconocido'
}
