import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { currentPath, renderApp } from '@/test/app'
import { server } from '@/test/server'

const foto = () => new File(['x'], 'yo.png', { type: 'image/png' })
const selectorDeFoto = () => document.querySelector('input[type=file]') as HTMLInputElement

describe('SettingsScreen: mi cuenta', () => {
  it('ensena el perfil, el rol global y la fecha de alta', async () => {
    renderApp('/configuracion', { as: 'lucia' })
    expect(await screen.findByRole('heading', { name: 'Mi cuenta' })).toBeInTheDocument()
    expect(screen.getByText('lucia@uni.es')).toBeInTheDocument()
    expect(screen.getByText('Alumno')).toBeInTheDocument()
    expect(screen.getByText(/1\/9\/26/)).toBeInTheDocument()
  })

  it('cambia el nombre', async () => {
    const { user } = renderApp('/configuracion', { as: 'lucia' })
    await user.click(await screen.findByRole('button', { name: /Editar perfil/ }))
    const dialogo = screen.getByRole('dialog', { name: 'Editar perfil' })
    const nombre = within(dialogo).getByRole('textbox')
    await user.clear(nombre)
    expect(within(dialogo).getByRole('button', { name: 'Guardar' })).toBeDisabled()
    await user.type(nombre, 'Lucía F. Gil{Enter}')

    expect(await screen.findByText('Perfil actualizado')).toBeInTheDocument()
    expect(server.callsTo('put', '/users/1')[0].body).toEqual({ fullName: 'Lucía F. Gil' })
    expect(await screen.findByText('Lucía F. Gil', { selector: 'div' })).toBeInTheDocument()
  })

  it('sube una foto de perfil', async () => {
    const { user } = renderApp('/configuracion', { as: 'lucia' })
    await user.click(await screen.findByRole('button', { name: /Editar perfil/ }))
    await user.upload(selectorDeFoto(), foto())
    expect(screen.getByAltText('Vista previa')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Guardar' }))
    expect(await screen.findByText('Foto de perfil actualizada')).toBeInTheDocument()
    expect(server.callsTo('post', '/users/1/avatar')).toHaveLength(1)
    expect(server.callsTo('put', '/users/1')).toHaveLength(0)
  })

  it('quita la foto que ya tenia', async () => {
    server.db.users.lucia.avatarUrl = '/api/users/1/avatar'
    const { user } = renderApp('/configuracion', { as: 'lucia' })
    await user.click(await screen.findByRole('button', { name: /Editar perfil/ }))
    await user.click(screen.getByRole('button', { name: 'Quitar foto' }))
    await user.click(screen.getByRole('button', { name: 'Guardar' }))
    expect(await screen.findByText('Foto de perfil eliminada')).toBeInTheDocument()
  })

  it('cambia la contraseña comprobando la actual y la repeticion', async () => {
    const { user } = renderApp('/configuracion', { as: 'lucia' })
    await user.click(await screen.findByRole('button', { name: /Cambiar contraseña/ }))
    const dialogo = screen.getByRole('dialog', { name: 'Cambiar contraseña' })
    const [actual, nueva, repetida] = within(dialogo).getAllByLabelText(/contraseña/i, { selector: 'input' })

    await user.type(nueva, 'corta')
    expect(within(dialogo).getByText('Al menos 8 caracteres.')).toBeInTheDocument()
    await user.type(nueva, 'ymas1')
    await user.type(repetida, 'otra')
    expect(within(dialogo).getByText('Las dos contraseñas no coinciden.')).toBeInTheDocument()
    expect(within(dialogo).getByRole('button', { name: 'Guardar' })).toBeDisabled()

    await user.clear(repetida)
    await user.type(repetida, 'cortaymas1')
    await user.type(actual, 'equivocada')
    await user.click(within(dialogo).getByRole('button', { name: 'Guardar' }))
    expect(await screen.findByText('La contraseña actual no es correcta')).toBeInTheDocument()
    expect(screen.getByRole('dialog', { name: 'Cambiar contraseña' })).toBeInTheDocument()

    await user.clear(actual)
    await user.type(actual, 'secreto123')
    await user.click(within(dialogo).getByRole('button', { name: 'Guardar' }))
    expect(await screen.findByText('Contraseña actualizada')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('cerrar sesion vuelve al login', async () => {
    const { user } = renderApp('/configuracion', { as: 'lucia' })
    await user.click(await screen.findByRole('button', { name: 'Cerrar sesión' }))
    await waitFor(() => expect(currentPath()).toBe('/login'))
  })

  it('la marca lleva al inicio de cada rol', async () => {
    const { user } = renderApp('/configuracion', { as: 'carmen' })
    await user.click(await screen.findByRole('button', { name: /^Planif/ }))
    await waitFor(() => expect(currentPath()).toBe('/docente'))
  })
})

describe('WorkspaceSettingsScreen', () => {
  const AJUSTES = '/workspaces/100/ajustes'

  it('ensena los datos del espacio y la actividad', async () => {
    renderApp(AJUSTES, { as: 'lucia' })
    expect(await screen.findByText('Práctica de algoritmos voraces')).toBeInTheDocument()
    expect(screen.getByText('28 sept 2026')).toBeInTheDocument()
    expect(screen.getByText('2 semanas')).toBeInTheDocument()
    expect(screen.getByText('1 de 4 hechas')).toBeInTheDocument()
    expect(await screen.findByText('movió «Memoria final» a Hecho')).toBeInTheDocument()
  })

  it('sin datos opcionales lo dice', async () => {
    Object.assign(server.db.workspace, { description: undefined, startDate: undefined, deadline: undefined, sprintDays: 10 })
    server.db.activity = []
    renderApp(AJUSTES, { as: 'lucia' })
    expect(await screen.findByText('Sin descripción')).toBeInTheDocument()
    expect(screen.getAllByText('Sin fecha')).toHaveLength(2)
    expect(screen.getByText('10 días')).toBeInTheDocument()
    expect(await screen.findByText('Sin actividad')).toBeInTheDocument()
  })

  it('edita las fechas y la duracion del sprint', async () => {
    const { user } = renderApp(AJUSTES, { as: 'lucia' })
    await user.click(await screen.findByRole('button', { name: /Editar/ }))
    const dialogo = screen.getByRole('dialog', { name: 'Editar el espacio de trabajo' })
    const [inicio, entrega] = within(dialogo).getAllByPlaceholderText('dd/mm/aaaa')
    await user.clear(inicio)
    await user.type(inicio, '05102026')
    await user.clear(entrega)
    await user.tab()
    await user.click(within(dialogo).getByRole('tab', { name: '3 semanas' }))
    await user.click(within(dialogo).getByRole('button', { name: 'Guardar' }))

    expect(await screen.findByText('Espacio de trabajo actualizado')).toBeInTheDocument()
    expect(server.callsTo('put', '/workspaces/100')[0].body).toMatchObject({
      name: 'Algorítmica, práctica 3', startDate: '2026-10-05', deadline: null, sprintDays: 21,
    })
  })

  it('cambia la foto del espacio al editar', async () => {
    server.db.workspace.coverUrl = '/api/workspaces/100/photo'
    const { user } = renderApp(AJUSTES, { as: 'lucia' })
    await user.click(await screen.findByRole('button', { name: /Editar/ }))
    await user.click(screen.getByRole('button', { name: 'Quitar foto' }))
    // Sin foto vuelven el color y el icono.
    expect(screen.getByRole('radiogroup', { name: 'Color del espacio de trabajo' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Guardar' }))
    expect(await screen.findByText('Foto del espacio de trabajo eliminada')).toBeInTheDocument()
  })

  it('borra el espacio y vuelve a la lista', async () => {
    const { user } = renderApp(AJUSTES, { as: 'lucia' })
    await screen.findByText('Práctica de algoritmos voraces')
    await user.click(screen.getByRole('button', { name: /^Borrar$/ }))
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Borrar' }))
    await waitFor(() => expect(currentPath()).toBe('/workspaces'))
  })

  it('un alumno lo ve sin poder editar ni borrar', async () => {
    renderApp(AJUSTES, { as: 'pablo' })
    await screen.findByText('Práctica de algoritmos voraces')
    expect(screen.queryByRole('button', { name: /Editar/ })).not.toBeInTheDocument()
    expect(screen.queryByText('Borrar este espacio de trabajo')).not.toBeInTheDocument()
  })

  it('los botones de la cabecera llevan a ajustes, a mi cuenta y de vuelta a la lista', async () => {
    const { user } = renderApp('/workspaces/100/miembros', { as: 'lucia' })
    await user.click(await screen.findByRole('button', { name: 'Ajustes del espacio de trabajo' }))
    await waitFor(() => expect(currentPath()).toBe(AJUSTES))
    await user.click(screen.getByRole('button', { name: 'Mi cuenta' }))
    await waitFor(() => expect(currentPath()).toBe('/configuracion'))
  })

  it('«Mis espacios de trabajo» vuelve a la lista', async () => {
    const { user } = renderApp(AJUSTES, { as: 'lucia' })
    await user.click(await screen.findByRole('button', { name: /Mis espacios de trabajo/ }))
    await waitFor(() => expect(currentPath()).toBe('/workspaces'))
  })
})
