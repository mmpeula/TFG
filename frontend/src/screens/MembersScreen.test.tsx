import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { queryClient } from '@/app/query-client'
import { currentPath, renderApp } from '@/test/app'
import { HttpError, server } from '@/test/server'

const MIEMBROS = '/workspaces/100/miembros'
const correo = () => screen.findByRole('textbox', { name: 'Correo de la persona a invitar' })
const invitar = () => screen.getByRole('button', { name: /Invitar|Comprobando…|Enviando…/ })

describe('MembersScreen', () => {
  it('lista el equipo con su rol y las invitaciones pendientes', async () => {
    renderApp(MIEMBROS, { as: 'lucia' })
    expect(await screen.findByText('4 personas en este espacio de trabajo.')).toBeInTheDocument()
    expect(screen.getByText('(tú)')).toBeInTheDocument()
    expect(screen.getByText('carmen@uni.es')).toBeInTheDocument()
    expect(await screen.findByText('Jorge Torres')).toBeInTheDocument()
    expect(screen.getByText(/Invitación enviada/)).toBeInTheDocument()
  })

  it('invita a un alumno por su correo', async () => {
    const { user } = renderApp(MIEMBROS, { as: 'lucia' })
    await user.type(await correo(), 'nuevo@uni.es')
    await waitFor(() => expect(invitar()).toHaveTextContent('Invitar'))
    await user.click(invitar())

    expect(await screen.findByText('Invitación enviada a nuevo@uni.es')).toBeInTheDocument()
    expect(server.callsTo('post', '/teams/100/invitations')[0].body).toEqual({ email: 'nuevo@uni.es' })
    expect(await correo()).toHaveValue('')
  })

  it('si el correo no sale lo avisa', async () => {
    const { user } = renderApp(MIEMBROS, { as: 'lucia' })
    await user.type(await correo(), 'sincorreo@uni.es')
    await waitFor(() => expect(invitar()).toHaveTextContent('Invitar'))
    await user.click(invitar())
    expect(await screen.findByText(/pero el correo no ha podido salir/)).toBeInTheDocument()
  })

  it('a una cuenta de docente hay que elegir como entra', async () => {
    const { user } = renderApp(MIEMBROS, { as: 'lucia' })
    await user.type(await correo(), 'carmen@uni.es')
    expect(await screen.findByText(/Esta cuenta es de docente/)).toBeInTheDocument()
    expect(screen.getByText(/Elige una opción para poder invitar/)).toBeInTheDocument()
    expect(invitar()).toBeDisabled()

    await user.click(screen.getByRole('radio', { name: /Docente/ }))
    await user.click(invitar())
    await waitFor(() => expect(server.callsTo('post', '/teams/100/invitations')).toHaveLength(1))
    expect(server.callsTo('post', '/teams/100/invitations')[0].body).toEqual({ email: 'carmen@uni.es', role: 'DOCENTE' })
  })

  it('un correo sin cuenta da el error del servidor al invitar', async () => {
    const { user } = renderApp(MIEMBROS, { as: 'lucia' })
    await user.type(await correo(), 'nadie@uni.es')
    await waitFor(() => expect(invitar()).toHaveTextContent('Invitar'))
    await user.click(invitar())
    expect(await screen.findByText('No hay ninguna cuenta con ese correo')).toBeInTheDocument()
  })

  it('cancela una invitacion pendiente', async () => {
    const { user } = renderApp(MIEMBROS, { as: 'lucia' })
    await user.click(await screen.findByRole('button', { name: 'Cancelar la invitación de Jorge Torres' }))
    expect(await screen.findByText('Invitación cancelada')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByText('Jorge Torres')).not.toBeInTheDocument())
  })

  it('cambia el rol de un alumno a responsable', async () => {
    const { user } = renderApp(MIEMBROS, { as: 'lucia' })
    await screen.findByText('Pablo Ortega')
    const fila = screen.getByText('pablo@uni.es').closest('li')!
    await user.click(within(fila).getByRole('button', { name: /Alumno/ }))
    // El del menu recien abierto; el otro es el boton de rol de Lucia.
    await user.click(within(fila).getAllByRole('button', { name: 'Responsable' }).at(-1)!)
    expect(await screen.findByText('Rol actualizado')).toBeInTheDocument()
    expect(server.callsTo('patch', '/teams/100/members/2')[0].body).toEqual({ role: 'RESPONSABLE' })
  })

  it('quita a alguien del equipo tras confirmarlo', async () => {
    const { user } = renderApp(MIEMBROS, { as: 'lucia' })
    await screen.findByText('Pablo Ortega')
    const fila = screen.getByText('pablo@uni.es').closest('li')!
    await user.click(within(fila).getByRole('button', { name: 'Quitar del espacio de trabajo' }))
    await user.click(within(screen.getByRole('dialog', { name: 'Quitar a Pablo Ortega' })).getByRole('button', { name: 'Quitar' }))
    expect(await screen.findByText('Ya no está en el espacio de trabajo')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByText('pablo@uni.es')).not.toBeInTheDocument())
  })

  it('al quitar a alguien se recarga el tablero, porque el servidor suelta sus tareas', async () => {
    // Un tablero ya visto: con la cache de 30 s seguiria ensenando a Pablo en sus tareas.
    queryClient.setQueryData(['boards', 999], { id: 999, columns: [] })
    const { user } = renderApp(MIEMBROS, { as: 'lucia' })
    await screen.findByText('Pablo Ortega')
    const fila = screen.getByText('pablo@uni.es').closest('li')!
    await user.click(within(fila).getByRole('button', { name: 'Quitar del espacio de trabajo' }))
    await user.click(within(screen.getByRole('dialog', { name: 'Quitar a Pablo Ortega' })).getByRole('button', { name: 'Quitar' }))
    await screen.findByText('Ya no está en el espacio de trabajo')
    expect(queryClient.getQueryState(['boards', 999])?.isInvalidated).toBe(true)
  })

  it('un alumno ve el equipo, no invita, y puede salirse', async () => {
    const { user } = renderApp(MIEMBROS, { as: 'pablo' })
    await screen.findByText('pablo@uni.es')
    expect(screen.queryByRole('textbox', { name: 'Correo de la persona a invitar' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Cancelar la invitación/ })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Salir del espacio de trabajo' }))
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Salir' }))
    await waitFor(() => expect(currentPath()).toBe('/workspaces'))
  })

  it('si el servidor no deja quitar, lo dice y no cierra el dialogo', async () => {
    server.on('delete', '/teams/:id/members/:userId', () => { throw new HttpError(409, 'Tiene que quedar alguien responsable') })
    const { user } = renderApp(MIEMBROS, { as: 'lucia' })
    await screen.findByText('Pablo Ortega')
    const fila = screen.getByText('pablo@uni.es').closest('li')!
    await user.click(within(fila).getByRole('button', { name: 'Quitar del espacio de trabajo' }))
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Quitar' }))
    expect(await screen.findByText('Tiene que quedar alguien responsable')).toBeInTheDocument()
    expect(screen.getByRole('dialog')).toBeInTheDocument()
  })

  it('un equipo vacio invita a llamar a los compañeros', async () => {
    server.db.members = []
    renderApp(MIEMBROS, { as: 'lucia' })
    expect(await screen.findByText('Todavía estás solo aquí')).toBeInTheDocument()
  })
})
