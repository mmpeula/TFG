import { fireEvent, screen, waitFor } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { currentPath, renderApp } from '@/test/app'
import { getStoredToken } from '@/lib/api-client'
import { server } from '@/test/server'

describe('LoginScreen', () => {
  it('entra con el email y la contraseña y guarda la sesion', async () => {
    const { user } = renderApp('/login')
    await user.type(await screen.findByLabelText('Email'), 'pablo@uni.es')
    await user.type(screen.getByLabelText('Contraseña'), 'secreto123')
    await user.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(await screen.findByRole('heading', { name: 'Mis espacios de trabajo' })).toBeInTheDocument()
    expect(getStoredToken()).toBe('tok-2')
  })

  it('cada rol aterriza en su inicio', async () => {
    const { user } = renderApp('/login')
    await user.type(await screen.findByLabelText('Email'), 'admin@uni.es')
    await user.type(screen.getByLabelText('Contraseña'), 'secreto123')
    await user.click(screen.getByRole('button', { name: 'Entrar' }))
    await waitFor(() => expect(currentPath()).toBe('/administracion'))
  })

  it('con credenciales malas ensena el error del servidor y no guarda nada', async () => {
    const { user } = renderApp('/login')
    await user.type(await screen.findByLabelText('Email'), 'pablo@uni.es')
    await user.type(screen.getByLabelText('Contraseña'), 'mala')
    await user.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(await screen.findByText('Email o contraseña incorrectos')).toBeInTheDocument()
    expect(getStoredToken()).toBeNull()
  })

  it('con sesion abierta salta el login', async () => {
    renderApp('/login', { as: 'pablo' })
    await waitFor(() => expect(currentPath()).toBe('/workspaces'))
  })

  it('enlaza con el registro', async () => {
    const { user } = renderApp('/login')
    await user.click(await screen.findByRole('link', { name: 'Regístrate' }))
    expect(await screen.findByRole('heading', { name: 'Crear cuenta' })).toBeInTheDocument()
  })
})

describe('RegisterScreen', () => {
  async function rellenar(user: ReturnType<typeof renderApp>['user'], email: string, repetida = 'secreto123') {
    await user.type(await screen.findByLabelText('Nombre completo'), 'Ana García')
    await user.type(screen.getByLabelText('Email'), email)
    await user.type(screen.getByLabelText('Contraseña'), 'secreto123')
    await user.type(screen.getByLabelText('Repite la contraseña'), repetida)
  }

  it('crea la cuenta de alumno y entra', async () => {
    const { user } = renderApp('/registro')
    await rellenar(user, 'ana@uni.es')
    await user.click(screen.getByRole('button', { name: 'Crear cuenta' }))

    await waitFor(() => expect(currentPath()).toBe('/workspaces'))
    expect(server.callsTo('post', '/auth/register')[0].body).toEqual({
      fullName: 'Ana García', email: 'ana@uni.es', password: 'secreto123', role: 'MIEMBRO',
    })
  })

  it('el docente se registra como tal y explica que solo observa', async () => {
    const { user } = renderApp('/registro')
    await user.click(await screen.findByRole('tab', { name: 'Docente' }))
    expect(screen.getByText(/en modo lectura/)).toBeInTheDocument()
    await rellenar(user, 'profe@uni.es')
    await user.click(screen.getByRole('button', { name: 'Crear cuenta' }))
    await waitFor(() => expect(currentPath()).toBe('/docente'))
  })

  it('no deja enviar si las contraseñas no coinciden', async () => {
    const { user } = renderApp('/registro')
    await rellenar(user, 'ana@uni.es', 'otracosa1')
    expect(screen.getByText('Las dos contraseñas no coinciden.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Crear cuenta' })).toBeDisabled()
  })

  it('ensena el error si el email ya existe', async () => {
    const { user } = renderApp('/registro')
    await rellenar(user, 'lucia@uni.es')
    await user.click(screen.getByRole('button', { name: 'Crear cuenta' }))
    expect(await screen.findByText('Ya hay una cuenta con ese email')).toBeInTheDocument()
  })
})

describe('PasswordInput', () => {
  it('muestra y oculta la contraseña', async () => {
    const { user } = renderApp('/login')
    const campo = await screen.findByLabelText('Contraseña')
    expect(campo).toHaveAttribute('type', 'password')
    await user.click(screen.getByRole('button', { name: 'Mostrar contraseña' }))
    expect(campo).toHaveAttribute('type', 'text')
    await user.click(screen.getByRole('button', { name: 'Ocultar contraseña' }))
    expect(campo).toHaveAttribute('type', 'password')
  })

  it('avisa del Bloq Mayús mientras se escribe', async () => {
    renderApp('/login')
    const campo = await screen.findByLabelText('Contraseña')
    fireEvent.focus(campo)
    fireEvent.keyDown(campo, { key: 'A', modifierCapsLock: true })
    expect(await screen.findByText('Bloq Mayús activado')).toBeInTheDocument()
    fireEvent.keyDown(campo, { key: 'CapsLock' })
    fireEvent.blur(campo)
    expect(screen.queryByText('Bloq Mayús activado')).not.toBeInTheDocument()
  })
})
