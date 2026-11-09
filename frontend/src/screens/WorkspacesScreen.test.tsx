import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { currentPath, renderApp } from '@/test/app'
import { HttpError, server } from '@/test/server'

const tarjeta = () => screen.findByRole('button', { name: /^Algorítmica, práctica 3/ })

describe('WorkspacesScreen', () => {
  it('saluda con las tareas pendientes y ensena la tarjeta del espacio', async () => {
    renderApp('/workspaces', { as: 'lucia' })
    expect(await screen.findByText('Hola, Lucía. Tienes 3 tareas pendientes en total.')).toBeInTheDocument()
    const card = await tarjeta()
    expect(within(card).getByText('3 tareas pendientes')).toBeInTheDocument()
    expect(within(card).getByText('25%')).toBeInTheDocument()
    // Cortada a una linea en la tarjeta; entera al pasar el raton.
    expect(within(card).getByText('Práctica de algoritmos voraces')).toHaveAttribute('title', 'Práctica de algoritmos voraces')
  })

  it('abrir la tarjeta lleva al plan del espacio', async () => {
    const { user } = renderApp('/workspaces', { as: 'lucia' })
    await user.click(await tarjeta())
    await waitFor(() => expect(currentPath()).toBe('/workspaces/100/plan'))
  })

  it('sin espacios ofrece crear el primero', async () => {
    server.on('get', '/workspaces', () => [])
    renderApp('/workspaces', { as: 'pablo' })
    expect(await screen.findByText('Todavía no tienes ningún espacio de trabajo')).toBeInTheDocument()
    expect(screen.getByText(/cada cosa, su espacio de trabajo/)).toBeInTheDocument()
  })

  it('con todo terminado dice que no queda nada', async () => {
    server.db.tasks = server.db.tasks.map((task) => ({ ...task, columnId: 40 }))
    renderApp('/workspaces', { as: 'lucia' })
    expect(await screen.findByText('Hola, Lucía. No te queda nada pendiente.')).toBeInTheDocument()
    expect(await screen.findByText('Todo hecho')).toBeInTheDocument()
  })

  it('un espacio suspendido no se puede abrir', async () => {
    server.db.workspace.enabled = false
    renderApp('/workspaces', { as: 'lucia' })
    expect(await tarjeta()).toBeDisabled()
    expect(screen.getByText('Suspendido')).toBeInTheDocument()
  })

  it('crea un espacio nuevo con nombre, color e icono y entra en el', async () => {
    const { user } = renderApp('/workspaces', { as: 'lucia' })
    await user.click(await screen.findByRole('button', { name: 'Nuevo espacio de trabajo' }))
    const dialogo = screen.getByRole('dialog', { name: 'Nuevo espacio de trabajo' })

    const crear = within(dialogo).getByRole('button', { name: 'Crear espacio de trabajo' })
    expect(crear).toBeDisabled()

    await user.type(within(dialogo).getByPlaceholderText('Bases de Datos'), 'Redes')
    await user.type(within(dialogo).getByPlaceholderText(/Práctica 2/), 'Práctica de sockets')
    await user.click(within(dialogo).getAllByRole('radio')[2])
    await user.type(within(dialogo).getByPlaceholderText('BD'), 'RD')
    await user.click(crear)

    await waitFor(() => expect(currentPath()).toBe('/workspaces/100/plan'))
    expect(server.callsTo('post', '/workspaces')[0].body).toMatchObject({
      name: 'Redes', description: 'Práctica de sockets', icon: 'RD', sprintDays: 14,
    })
  })

  it('al crear permite elegir la duración del sprint', async () => {
    const { user } = renderApp('/workspaces', { as: 'lucia' })
    await user.click(await screen.findByRole('button', { name: 'Nuevo espacio de trabajo' }))
    const dialogo = screen.getByRole('dialog', { name: 'Nuevo espacio de trabajo' })

    await user.type(within(dialogo).getByPlaceholderText('Bases de Datos'), 'Redes')
    await user.click(within(dialogo).getByRole('tab', { name: '1 semana' }))
    await user.click(within(dialogo).getByRole('button', { name: 'Crear espacio de trabajo' }))

    await waitFor(() => expect(server.callsTo('post', '/workspaces')).toHaveLength(1))
    expect(server.callsTo('post', '/workspaces')[0].body).toMatchObject({ name: 'Redes', sprintDays: 7 })
  })

  it('al crear con foto la sube despues del alta', async () => {
    const { user, container } = renderApp('/workspaces', { as: 'lucia' })
    await user.click(await screen.findByRole('button', { name: 'Nuevo espacio de trabajo' }))
    await user.type(screen.getByPlaceholderText('Bases de Datos'), 'Redes')
    await user.upload(container.ownerDocument.querySelector('input[type=file]') as HTMLInputElement,
      new File(['x'], 'foto.png', { type: 'image/png' }))
    expect(screen.getByAltText('Vista previa')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Crear espacio de trabajo' }))

    await waitFor(() => expect(server.callsTo('post', '/workspaces/100/photo')).toHaveLength(1))
  })

  it('cancelar o Escape cierran el formulario sin crear nada', async () => {
    const { user } = renderApp('/workspaces', { as: 'lucia' })
    await user.click(await screen.findByRole('button', { name: 'Nuevo espacio de trabajo' }))
    await user.click(screen.getByRole('button', { name: 'Cancelar' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Nuevo espacio de trabajo' }))
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(server.callsTo('post', '/workspaces')).toHaveLength(0)
  })

  it('el responsable puede borrar el espacio tras confirmarlo', async () => {
    const { user } = renderApp('/workspaces', { as: 'lucia' })
    await user.click(await screen.findByRole('button', { name: 'Acciones de Algorítmica, práctica 3' }))
    await user.click(screen.getByRole('button', { name: 'Borrar' }))
    const dialogo = screen.getByRole('dialog', { name: 'Borrar «Algorítmica, práctica 3»' })
    await user.click(within(dialogo).getByRole('button', { name: 'Borrar' }))

    expect(await screen.findByText('Espacio de trabajo borrado')).toBeInTheDocument()
    expect(server.callsTo('delete', '/workspaces/100')).toHaveLength(1)
  })

  it('un alumno solo puede salir del espacio', async () => {
    const { user } = renderApp('/workspaces', { as: 'pablo' })
    await user.click(await screen.findByRole('button', { name: 'Acciones de Algorítmica, práctica 3' }))
    expect(screen.queryByRole('button', { name: 'Borrar' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Salir del espacio de trabajo' }))
    await user.click(screen.getByRole('button', { name: 'Salir' }))

    expect(await screen.findByText('Ya no está en el espacio de trabajo')).toBeInTheDocument()
    expect(server.callsTo('delete', '/teams/100/members/2')).toHaveLength(1)
  })

  it('cancelar la confirmacion no borra', async () => {
    const { user } = renderApp('/workspaces', { as: 'lucia' })
    await user.click(await screen.findByRole('button', { name: 'Acciones de Algorítmica, práctica 3' }))
    await user.click(screen.getByRole('button', { name: 'Borrar' }))
    await user.click(screen.getByRole('button', { name: 'Cancelar' }))
    expect(server.callsTo('delete', '/workspaces/100')).toHaveLength(0)
  })
})

describe('InvitationsBanner', () => {
  it('acepta una invitacion pendiente', async () => {
    const { user } = renderApp('/workspaces', { as: 'pablo' })
    expect(await screen.findByText(/te ha invitado a/)).toHaveTextContent(
      'Elena Navarro te ha invitado a Bases de Datos como alumno.')
    await user.click(screen.getByRole('button', { name: 'Aceptar' }))
    expect(await screen.findByText('Ya estás en el equipo')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByText(/te ha invitado a/)).not.toBeInTheDocument())
  })

  it('rechaza una invitacion', async () => {
    const { user } = renderApp('/workspaces', { as: 'pablo' })
    await user.click(await screen.findByRole('button', { name: 'Rechazar' }))
    expect(await screen.findByText('Invitación rechazada')).toBeInTheDocument()
  })

  it('un error del servidor se avisa con un toast', async () => {
    server.on('post', '/invitations/:token/accept', () => { throw new HttpError(410, 'La invitación ha caducado') })
    const { user } = renderApp('/workspaces', { as: 'pablo' })
    await user.click(await screen.findByRole('button', { name: 'Aceptar' }))
    expect(await screen.findByText('La invitación ha caducado')).toBeInTheDocument()
  })
})
