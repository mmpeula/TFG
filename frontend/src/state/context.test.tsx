import { renderHook } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { useAssistant, useAuth, usePlanDrafts, usePlanJobs, useUi } from './context'

describe('hooks de contexto', () => {
  it.each([
    ['useUi', useUi, '<UiProvider>'],
    ['useAuth', useAuth, '<AuthProvider>'],
    ['usePlanJobs', usePlanJobs, '<PlanJobsProvider>'],
    ['usePlanDrafts', usePlanDrafts, '<PlanDraftsProvider>'],
    ['useAssistant', useAssistant, '<AssistantProvider>'],
  ])('%s fuera de su provider dice cual falta', (_, hook, provider) => {
    // React y jsdom tambien escriben el error en la consola; aqui solo interesa el mensaje.
    const consola = vi.spyOn(console, 'error').mockImplementation(() => {})
    const silenciar = (event: ErrorEvent) => event.preventDefault()
    window.addEventListener('error', silenciar)
    expect(() => renderHook(() => hook())).toThrow(provider)
    window.removeEventListener('error', silenciar)
    consola.mockRestore()
  })
})
