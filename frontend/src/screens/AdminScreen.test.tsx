import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { currentPath, renderApp } from '@/test/app'
import { server } from '@/test/server'

const ADMIN = '/administracion'
const fila = (texto: string) => screen.getByText(texto).closest('li')!

describe('AdminScreen: cuentas', () => {
  it('lista todas las cuentas con su recuento por rol', async () => {
    renderApp(ADMIN, { as: 'admin' })
    expect(await screen.findByRole('heading', { name: 'Administración de la plataforma' })).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: 'Todas 5' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Alumno 3' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Docente 1' })).toBeInTheDocument()
    expect(within(fila('admin@uni.es')).getByText('tú')).toBeInTheDocument()
  })

  it('busca sin tildes y filtra por rol', async () => {
    const { user } = renderApp(ADMIN, { as: 'admin' })
    await user.type(await screen.findByRole('textbox', { name: 'Buscar cuentas' }), 'lucia')
    expect(screen.getByText('lucia@uni.es')).toBeInTheDocument()
    expect(screen.queryByText('pablo@uni.es')).not.toBeInTheDocument()

    await user.clear(screen.getByRole('textbox', { name: 'Buscar cuentas' }))
    await user.click(screen.getByRole('button', { name: 'Docente 1' }))
    expect(screen.getByText('carmen@uni.es')).toBeInTheDocument()
    expect(screen.queryByText('lucia@uni.es')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Todas 5' }))
    await user.type(screen.getByRole('textbox', { name: 'Buscar cuentas' }), 'zzz')
    expect(screen.getByText('Sin resultados')).toBeInTheDocument()
  })

  it('sube a un alumno a docente tras confirmarlo', async () => {
    const { user } = renderApp(ADMIN, { as: 'admin' })
    await screen.findByText('pablo@uni.es')
    await user.click(within(fila('pablo@uni.es')).getByRole('button', { name: /Alumno/ }))
    await user.click(screen.getByRole('button', { name: 'Hacer docente' }))
    const dialogo = screen.getByRole('dialog', { name: 'Hacer docente a Pablo Ortega' })
    expect(dialogo).toHaveTextContent('No se puede volver a hacer alumno.')
    await user.click(within(dialogo).getByRole('button', { name: 'Hacer docente' }))
    expect(await screen.findByText('Rol actualizado')).toBeInTheDocument()
    expect(server.callsTo('patch', '/users/2/role')[0].body).toEqual({ role: 'DOCENTE' })
  })

  it('una docente solo puede subir a administradora', async () => {
    const { user } = renderApp(ADMIN, { as: 'admin' })
    await screen.findByText('carmen@uni.es')
    await user.click(within(fila('carmen@uni.es')).getByRole('button', { name: /Docente/ }))
    expect(screen.queryByRole('button', { name: 'Hacer docente' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Hacer administrador' }))
    expect(screen.getByRole('dialog', { name: 'Hacer administrador a Carmen Molina' }))
      .toHaveTextContent('Un administrador no se puede volver a degradar.')
    await user.click(screen.getByRole('button', { name: 'Cancelar' }))
    expect(server.callsTo('patch', '/users/4/role')).toHaveLength(0)
  })

  it('deshabilita y reactiva una cuenta, pero no la propia', async () => {
    const { user } = renderApp(ADMIN, { as: 'admin' })
    await screen.findByText('elena@uni.es')
    expect(screen.getByRole('button', { name: 'Deshabilitar a Admin Soporte' })).toBeDisabled()

    await user.click(screen.getByRole('button', { name: 'Deshabilitar a Elena Navarro' }))
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Deshabilitar' }))
    expect(await screen.findByText('Cuenta deshabilitada')).toBeInTheDocument()
    expect(await screen.findByText('1 cuenta deshabilitada: no puede entrar, pero conserva su historial.')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Reactivar a Elena Navarro' }))
    expect(await screen.findByText('Cuenta reactivada')).toBeInTheDocument()
  })

  it('con varias deshabilitadas lo dice en plural', async () => {
    server.db.users.pablo.enabled = false
    server.db.users.elena.enabled = false
    renderApp(ADMIN, { as: 'admin' })
    expect(await screen.findByText(/2 cuentas deshabilitadas/)).toBeInTheDocument()
  })

  it('quien no es administrador no entra', async () => {
    renderApp(ADMIN, { as: 'lucia' })
    await waitFor(() => expect(currentPath()).toBe('/workspaces'))
  })
})

describe('AdminScreen: espacios de trabajo', () => {
  async function abrirPestana() {
    const app = renderApp(ADMIN, { as: 'admin' })
    await app.user.click(await screen.findByRole('tab', { name: 'Espacios de trabajo' }))
    await screen.findByText('TFG abandonado')
    return app
  }

  it('lista los espacios con su estado y filtra', async () => {
    const { user } = await abrirPestana()
    expect(screen.getByText('Creado por Lucía Fernández')).toBeInTheDocument()
    expect(screen.getByText('Sin creador')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Sin responsable 1' }))
    expect(screen.queryByText('Algorítmica, práctica 3')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Suspendidos 1' }))
    expect(screen.getByText('TFG abandonado')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Todos 2' }))
    await user.type(screen.getByRole('textbox', { name: 'Buscar espacios de trabajo' }), 'pablo@uni.es')
    expect(screen.getByText('Algorítmica, práctica 3')).toBeInTheDocument()
    expect(screen.queryByText('TFG abandonado')).not.toBeInTheDocument()
    await user.type(screen.getByRole('textbox', { name: 'Buscar espacios de trabajo' }), 'zzz')
    expect(screen.getByText('Ningún espacio de trabajo coincide con ese filtro.')).toBeInTheDocument()
  })

  it('sin espacios en la plataforma lo dice', async () => {
    server.db.adminWorkspaces = []
    const { user } = renderApp(ADMIN, { as: 'admin' })
    await user.click(await screen.findByRole('tab', { name: 'Espacios de trabajo' }))
    expect(await screen.findByText('Todavía no hay ningún espacio de trabajo')).toBeInTheDocument()
  })

  it('el detalle ensena el equipo; la docente no tiene menu de rol', async () => {
    const { user } = await abrirPestana()
    await user.click(screen.getByRole('button', { name: /^Algorítmica, práctica 3/ }))
    const detalle = screen.getByRole('dialog', { name: 'Algorítmica, práctica 3' })
    expect(within(detalle).getByText('Equipo (4)')).toBeInTheDocument()
    expect(within(detalle).getByText('Docente')).toHaveAttribute('title', 'El rol de docente se elige al invitar y no se cambia')
    expect(within(detalle).getByText(/Solo queda una persona responsable/)).toBeInTheDocument()
  })

  it('a la ultima responsable no se le puede quitar el rol; a un alumno se le sube', async () => {
    const { user } = await abrirPestana()
    await user.click(screen.getByRole('button', { name: /^Algorítmica, práctica 3/ }))
    const detalle = screen.getByRole('dialog', { name: 'Algorítmica, práctica 3' })

    const lucia = within(detalle).getByText('lucia@uni.es').closest('li')!
    await user.click(within(lucia).getByRole('button', { name: /Responsable/ }))
    // Su menu solo ofrece seguir como responsable: el boton y esa unica opcion.
    expect(within(lucia).getAllByRole('button').map((boton) => boton.textContent)).toEqual(['Responsable', 'Responsable'])
    await user.click(within(lucia).getAllByRole('button')[0])

    const pablo = within(screen.getByRole('dialog')).getByText('pablo@uni.es').closest('li')!
    await user.click(within(pablo).getByRole('button', { name: /Alumno/ }))
    await user.click(within(pablo).getByRole('button', { name: 'Responsable' }))
    expect(await screen.findByText('Rol actualizado')).toBeInTheDocument()
    expect(server.callsTo('put', '/admin/workspaces/100/members/2/role')[0].body).toEqual({ role: 'RESPONSABLE' })
  })

  it('suspende un espacio tras confirmarlo', async () => {
    const { user } = await abrirPestana()
    await user.click(screen.getByRole('button', { name: /^Algorítmica, práctica 3/ }))
    await user.click(screen.getByRole('button', { name: /Suspender/ }))
    await user.click(within(screen.getByRole('dialog', { name: 'Suspender «Algorítmica, práctica 3»' }))
      .getByRole('button', { name: 'Suspender' }))
    expect(await screen.findByText('Espacio de trabajo suspendido')).toBeInTheDocument()
  })

  it('reactiva uno suspendido y explica por que esta atascado', async () => {
    const { user } = await abrirPestana()
    await user.click(screen.getByRole('button', { name: /^TFG abandonado/ }))
    const detalle = screen.getByRole('dialog', { name: 'TFG abandonado' })
    expect(within(detalle).getByText(/Suspendido: su equipo no puede abrirlo/)).toBeInTheDocument()
    expect(within(detalle).getByText(/Nombra a alguien del equipo para desatascarlo/)).toBeInTheDocument()
    expect(within(detalle).getByText('El equipo está vacío.')).toBeInTheDocument()
    expect(within(detalle).getByText('sin actividad')).toBeInTheDocument()
    await user.click(within(detalle).getByRole('button', { name: /Reactivar/ }))
    expect(await screen.findByText('Espacio de trabajo reactivado')).toBeInTheDocument()
  })

  it('borra un espacio y cierra el detalle', async () => {
    const { user } = await abrirPestana()
    await user.click(screen.getByRole('button', { name: /^TFG abandonado/ }))
    await user.click(screen.getByRole('button', { name: /Borrar este espacio de trabajo/ }))
    await user.click(within(screen.getByRole('dialog', { name: 'Borrar «TFG abandonado»' })).getByRole('button', { name: 'Borrar' }))
    expect(await screen.findByText('Espacio de trabajo borrado')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(screen.queryByText('TFG abandonado')).not.toBeInTheDocument()
  })
})

describe('TeacherScreen', () => {
  it('lista los equipos que sigue con sus avisos', async () => {
    renderApp('/docente', { as: 'carmen' })
    expect(await screen.findByText('Los equipos que sigues como docente, de un vistazo.')).toBeInTheDocument()
    expect(await screen.findByText('3 avisos')).toBeInTheDocument()
  })

  it('abrir un equipo lleva a su plan', async () => {
    const { user } = renderApp('/docente', { as: 'carmen' })
    await user.click(await screen.findByRole('button', { name: /^Algorítmica, práctica 3/ }))
    await waitFor(() => expect(currentPath()).toBe('/workspaces/100/plan'))
  })

  it('con un solo aviso lo dice en singular', async () => {
    server.db.tasks = server.db.tasks.filter((task) => task.id === 501)
    renderApp('/docente', { as: 'carmen' })
    expect(await screen.findByText('1 aviso')).toBeInTheDocument()
  })

  it('sin equipos que seguir lo explica', async () => {
    server.on('get', '/workspaces', () => [])
    renderApp('/docente', { as: 'carmen' })
    expect(await screen.findByText('Todavía no sigues ningún equipo')).toBeInTheDocument()
  })

  it('las pestañas llevan a sus propios espacios', async () => {
    const { user } = renderApp('/docente', { as: 'carmen' })
    await user.click(await screen.findByRole('button', { name: 'Mis espacios de trabajo' }))
    await waitFor(() => expect(currentPath()).toBe('/workspaces'))
    // Los espacios donde es docente no salen aqui, y el saludo es el de no tener ninguno.
    expect(await screen.findByText(/cada cosa, su espacio de trabajo/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Panel docente' }))
    await waitFor(() => expect(currentPath()).toBe('/docente'))
  })

  it('un alumno no tiene panel docente', async () => {
    renderApp('/docente', { as: 'pablo' })
    await waitFor(() => expect(currentPath()).toBe('/workspaces'))
  })

  it('en un espacio que sigue, «Panel docente» le devuelve', async () => {
    const { user } = renderApp('/workspaces/100/tablero', { as: 'carmen' })
    await user.click(await screen.findByRole('button', { name: /Panel docente/ }))
    await waitFor(() => expect(currentPath()).toBe('/docente'))
  })
})

describe('InvitationScreen', () => {
  it('acepta la invitacion del enlace del correo', async () => {
    const { user } = renderApp('/invitacion/inv-1', { as: 'pablo' })
    expect(await screen.findByText('Te han invitado a Bases de Datos')).toBeInTheDocument()
    expect(screen.getByText(/Elena Navarro te ha invitado como alumno/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Aceptar invitación' }))
    await waitFor(() => expect(currentPath()).toBe('/workspaces'))
    expect(server.callsTo('post', '/invitations/inv-1/accept')).toHaveLength(1)
  })

  it('la rechaza', async () => {
    const { user } = renderApp('/invitacion/inv-1', { as: 'pablo' })
    await user.click(await screen.findByRole('button', { name: 'Rechazar' }))
    await waitFor(() => expect(currentPath()).toBe('/workspaces'))
  })

  it('una invitacion que ya no existe lo explica', async () => {
    const { user } = renderApp('/invitacion/caducada', { as: 'pablo' })
    expect(await screen.findByText('Esta invitación ya no está disponible')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Ir a mis espacios de trabajo' }))
    await waitFor(() => expect(currentPath()).toBe('/workspaces'))
  })
})
