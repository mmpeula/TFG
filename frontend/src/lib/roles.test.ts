import { describe, expect, it } from 'vitest'
import { canManage, canWrite, isAdmin, isTeacher, ROLE_LABELS } from './roles'

describe('permisos por rol', () => {
  it('escriben el responsable y los alumnos, no el docente', () => {
    expect(canWrite('RESPONSABLE')).toBe(true)
    expect(canWrite('MIEMBRO')).toBe(true)
    expect(canWrite('DOCENTE')).toBe(false)
    expect(canWrite(null)).toBe(false)
  })

  it('solo el responsable gestiona', () => {
    expect(canManage('RESPONSABLE')).toBe(true)
    expect(canManage('MIEMBRO')).toBe(false)
  })

  it('admin y docente se reconocen', () => {
    expect(isAdmin('ADMIN')).toBe(true)
    expect(isAdmin('RESPONSABLE')).toBe(false)
    expect(isTeacher('DOCENTE')).toBe(true)
    expect(isTeacher(undefined)).toBe(false)
  })

  it('MIEMBRO se muestra como «Alumno»', () => {
    expect(ROLE_LABELS.MIEMBRO).toBe('Alumno')
  })
})
