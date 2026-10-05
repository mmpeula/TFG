import { minutesSince, withinTheDay } from '@/lib/format'
import type { WorkspaceResponse } from '@/types/api'

/**
 * Los colores de workspace son la unica concesion al color de toda la interfaz,
 * y la tienen ganada: es lo que permite reconocer una asignatura sin leer.
 *
 * Son tonos apagados a proposito, para que seis tarjetas juntas no parezcan una
 * caja de rotuladores. Se guardan por nombre, no como hex, para que cambiar la
 * paleta no deje workspaces con colores huerfanos.
 */
export const WORKSPACE_COLORS = [
  { id: 'tinta', label: 'Tinta', value: '#3f3f46' },
  { id: 'azul', label: 'Azul', value: '#3466cf' },
  { id: 'verde', label: 'Verde', value: '#467e59' },
  { id: 'ambar', label: 'Ámbar', value: '#a86a1a' },
  { id: 'granate', label: 'Granate', value: '#9b3a44' },
  { id: 'morado', label: 'Morado', value: '#6b52a8' },
] as const

/** Si el workspace no tiene color, se le asigna uno estable a partir de su id. */
export function colorOf(workspace: Pick<WorkspaceResponse, 'id' | 'color'>): string {
  const chosen = WORKSPACE_COLORS.find((color) => color.id === workspace.color)
  return (chosen ?? WORKSPACE_COLORS[workspace.id % WORKSPACE_COLORS.length]).value
}

/** Iniciales de respaldo cuando no se ha elegido icono: «Bases de Datos» -> «BD». */
export function iconOf(workspace: Pick<WorkspaceResponse, 'name' | 'icon'>): string {
  if (workspace.icon?.trim()) return workspace.icon.trim()
  const words = workspace.name.trim().split(/\s+/).filter((word) => word.length > 2)
  if (words.length === 0) return workspace.name.slice(0, 2).toUpperCase()
  return words.slice(0, 2).map((word) => word[0].toUpperCase()).join('')
}

/** Texto de la ultima actividad en lenguaje corriente: «hace 2 dias». */
export function lastActivityLabel(iso?: string): string {
  const minutes = minutesSince(iso)
  if (minutes === null) return 'sin actividad todavía'

  const reciente = withinTheDay(minutes)
  if (reciente) return reciente

  const days = Math.floor(minutes / 1440)
  if (days === 1) return 'ayer'
  if (days < 30) return `hace ${days} días`
  const months = Math.floor(days / 30)
  return months === 1 ? 'hace un mes' : `hace ${months} meses`
}
