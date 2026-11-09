import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { renderApp, type Who } from '@/test/app'
import { server } from '@/test/server'

/** Abre el tablero con el panel de esa tarea ya abierto. */
async function abrir(taskId: number, as: Who = 'lucia') {
  const app = renderApp({ pathname: '/workspaces/100/tablero', state: { openTaskId: taskId } }, { as })
  const panel = await screen.findByRole('dialog', { name: server.db.tasks.find((t) => t.id === taskId)!.title })
  return { ...app, panel }
}

const ultimoPut = (id: number) => server.callsTo('put', `/tasks/${id}`).at(-1)?.body

describe('TaskPanel: lo que ensena', () => {
  it('la cabecera, el aviso, las propiedades y el historial', async () => {
    const { panel } = await abrir(501)
    expect(within(panel).getByText('Tarea #501 · Por hacer')).toBeInTheDocument()
    expect(within(panel).getByText('Esta tarea va tarde')).toBeInTheDocument()
    expect(within(panel).getByLabelText('Descripción')).toHaveValue('Resumir **los requisitos** de la práctica.\n\n- Entrada\n- Salida')
    expect(within(panel).getByText('Historia de usuario')).toBeInTheDocument()
    expect(within(panel).getByText('Alta')).toBeInTheDocument()
    expect(within(panel).getByLabelText('Horas estimadas de esta tarea')).toHaveValue('2')
    expect(within(panel).getByText('Todavía no hay comentarios.')).toBeInTheDocument()
    expect(within(panel).getByText(/Creada por desconocido/)).toBeInTheDocument()
  })

  it('una tarea terminada ensena cuando y las horas reales frente a lo estimado', async () => {
    const { panel } = await abrir(504)
    expect(within(panel).getByText('Terminada')).toBeInTheDocument()
    expect(within(panel).getByLabelText('Horas reales dedicadas a esta tarea')).toHaveValue('4')
    expect(within(panel).getByText('↑ 1 h vs. estimado')).toBeInTheDocument()
  })

  it('las otras dos clases de aviso', async () => {
    const parada = await abrir(502)
    expect(within(parada.panel).getByText('Esta tarea está parada')).toBeInTheDocument()
    expect(within(parada.panel).getByText('Generada por IA')).toBeInTheDocument()
    parada.unmount()
    const sinNadie = await abrir(503)
    expect(within(sinNadie.panel).getByText('Esta tarea no tiene responsable')).toBeInTheDocument()
  })

  it('la docente lo ve todo en solo lectura', async () => {
    const { panel } = await abrir(504, 'carmen')
    expect(within(panel).getByLabelText('Título de la tarea')).toHaveAttribute('readonly')
    expect(within(panel).getByText('Elena Navarro')).toBeInTheDocument()
    expect(within(panel).getByText('3 h')).toBeInTheDocument()
    expect(within(panel).getByText('4 h')).toBeInTheDocument()
    expect(within(panel).getByText('Tu rol en este equipo es de solo lectura.')).toBeInTheDocument()
    expect(within(panel).queryByRole('button', { name: /Borrar la tarea/ })).not.toBeInTheDocument()
  })

  it('una tarea sin responsable en solo lectura dice «Sin asignar»', async () => {
    const { panel } = await abrir(503, 'carmen')
    expect(within(panel).getAllByText('Sin asignar').length).toBeGreaterThan(0)
    expect(within(panel).getByText('—')).toBeInTheDocument()
  })

  it('se cierra con la X y con Escape', async () => {
    const { user, panel } = await abrir(501)
    await user.click(within(panel).getByRole('button', { name: 'Cerrar el panel' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Leer el enunciado' }))
    await screen.findByRole('dialog', { name: 'Leer el enunciado' })
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})

describe('TaskPanel: editar', () => {
  it('cambia el titulo al salir del campo; vacio vuelve al de antes', async () => {
    const { user, panel } = await abrir(501)
    const titulo = within(panel).getByLabelText('Título de la tarea')
    await user.clear(titulo)
    await user.tab()
    expect(titulo).toHaveValue('Leer el enunciado')
    expect(ultimoPut(501)).toBeUndefined()

    await user.clear(titulo)
    await user.type(titulo, 'Leer y resumir el enunciado')
    await user.tab()
    await waitFor(() => expect(ultimoPut(501)).toMatchObject({ title: 'Leer y resumir el enunciado', priority: 'ALTA' }))
  })

  it('cambia la descripcion', async () => {
    const { user, panel } = await abrir(503)
    await user.type(within(panel).getByLabelText('Descripción'), 'Medir con 10^6 elementos')
    await user.tab()
    await waitFor(() => expect(ultimoPut(503)).toMatchObject({ description: 'Medir con 10^6 elementos' }))
  })

  it('cambia el tipo y la prioridad desde sus menus', async () => {
    const { user, panel } = await abrir(501)
    await user.click(within(panel).getByRole('button', { name: /Historia de usuario/ }))
    await user.click(screen.getByRole('button', { name: 'Bug' }))
    await waitFor(() => expect(ultimoPut(501)).toMatchObject({ type: 'BUG' }))

    await user.click(within(panel).getByRole('button', { name: /^Alta/ }))
    await user.click(screen.getByRole('button', { name: 'Crítica' }))
    await waitFor(() => expect(ultimoPut(501)).toMatchObject({ priority: 'CRITICA' }))
  })

  it('cambia la fecha limite y la estimacion; una estimacion que no es un numero se descarta', async () => {
    const { user, panel } = await abrir(501)
    const fecha = within(panel).getByLabelText('Fecha límite de la tarea')
    await user.clear(fecha)
    await user.type(fecha, '30102026{Enter}')
    await waitFor(() => expect(ultimoPut(501)).toMatchObject({ dueDate: '2026-10-30' }))

    const horas = within(panel).getByLabelText('Horas estimadas de esta tarea')
    await user.clear(horas)
    await user.type(horas, 'muchas')
    await user.tab()
    expect(horas).toHaveValue('2')

    await user.clear(horas)
    await user.type(horas, '3,5')
    await user.tab()
    await waitFor(() => expect(ultimoPut(501)).toMatchObject({ estimatedHours: 3.5 }))
  })

  it('las horas reales de una tarea terminada; vacias se borran', async () => {
    const { user, panel } = await abrir(504)
    const reales = within(panel).getByLabelText('Horas reales dedicadas a esta tarea')
    await user.clear(reales)
    await user.type(reales, 'x')
    await user.tab()
    expect(reales).toHaveValue('4')

    await user.clear(reales)
    await user.tab()
    await waitFor(() => expect(ultimoPut(504)).toMatchObject({ actualHours: null }))
  })

  it('asigna responsables', async () => {
    const { user, panel } = await abrir(503)
    await user.click(within(panel).getByText('··').closest('button')!)
    await user.click(screen.getAllByRole('button', { name: /Pablo Ortega/ }).at(-1)!)
    await waitFor(() => expect(ultimoPut(503)).toMatchObject({ assigneeIds: [2] }))
  })

  it('marcar a dos personas seguidas no pierde a la primera, aunque el servidor tarde', async () => {
    // Con la red lenta, el segundo clic llegaba antes de que se recargara el tablero y se
    // mandaba sin la primera persona: la tarea acababa solo con la segunda.
    let soltar!: () => void
    const primeraEnCamino = new Promise<void>((resolve) => { soltar = resolve })
    let llamadas = 0
    server.on('put', '/tasks/:id', async (ctx) => {
      if (++llamadas === 1) await primeraEnCamino
      const task = ctx.db.tasks.find((item) => item.id === Number(ctx.params.id))!
      const ids = (ctx.body as { assigneeIds: number[] }).assigneeIds
      task.assignees = ids.map((id) => {
        const person = Object.values(ctx.db.users).find((item) => item.id === id)!
        return { id: person.id, fullName: person.fullName, email: person.email, avatarUrl: null }
      })
      return task
    })
    const { user, panel } = await abrir(503)
    await user.click(within(panel).getByText('··').closest('button')!)
    await user.click(screen.getAllByRole('button', { name: /Pablo Ortega/ }).at(-1)!)
    await user.click(screen.getAllByRole('button', { name: /Elena Navarro/ }).at(-1)!)

    // Mientras la primera sigue sin contestar, el panel ya ensena a las dos.
    expect(within(panel).getByText('Pablo Ortega, Elena Navarro')).toBeInTheDocument()
    soltar()

    await waitFor(() => expect(server.callsTo('put', '/tasks/503')).toHaveLength(2))
    expect(ultimoPut(503)).toMatchObject({ assigneeIds: [2, 3] })
    await waitFor(() => expect(server.db.tasks.find((item) => item.id === 503)!.assignees.map((a) => a.id)).toEqual([2, 3]))
  })

  it('cambiar el estado a «Hecho» mueve la tarea y pregunta las horas', async () => {
    const { user, panel } = await abrir(502)
    await user.click(within(panel).getByRole('button', { name: /^En progreso/ }))
    await user.click(screen.getByRole('button', { name: 'Hecho' }))
    expect(await screen.findByRole('dialog', { name: 'Tarea terminada' })).toBeInTheDocument()
    expect(server.callsTo('patch', '/tasks/502/move')[0].body).toEqual({ columnId: 40, position: 1 })
  })

  it('elegir el mismo estado no hace nada', async () => {
    const { user, panel } = await abrir(502)
    await user.click(within(panel).getByRole('button', { name: /^En progreso/ }))
    await user.click(screen.getAllByRole('button', { name: 'En progreso' }).at(-1)!)
    expect(server.callsTo('patch', '/tasks/502/move')).toHaveLength(0)
  })

  it('borra la tarea y cierra el panel', async () => {
    const { user, panel } = await abrir(503)
    await user.click(within(panel).getByRole('button', { name: /Borrar la tarea/ }))
    const confirmar = screen.getByRole('dialog', { name: 'Borrar «Pruebas de rendimiento»' })
    await user.click(within(confirmar).getByRole('button', { name: 'Borrar' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(server.callsTo('delete', '/tasks/503')).toHaveLength(1)
  })
})

describe('TaskPanel: comentarios', () => {
  it('lista los comentarios con su autor', async () => {
    const { panel } = await abrir(502)
    expect(await within(panel).findByText('Empiezo por el caso base')).toBeInTheDocument()
    expect(within(panel).getByText('Comentarios (1)')).toBeInTheDocument()
  })

  it('comenta con el boton; Ctrl+Enter no envia', async () => {
    const { user, panel } = await abrir(501)
    const caja = within(panel).getByPlaceholderText('Escribe un comentario…')
    expect(within(panel).getByRole('button', { name: /Comentar/ })).toBeDisabled()

    await user.type(caja, 'Lo leo esta tarde{Control>}{Enter}{/Control}')
    // Si se hubiera enviado, la caja se habria vaciado.
    expect(caja).toHaveValue('Lo leo esta tarde')
    expect(within(panel).queryByText('⌘+Enter envía')).not.toBeInTheDocument()

    await user.click(within(panel).getByRole('button', { name: /Comentar/ }))
    expect(await within(panel).findByText(/Lo leo esta tarde/)).toBeInTheDocument()
    expect(caja).toHaveValue('')
  })

  it('la responsable puede borrar comentarios de otros', async () => {
    const { user, panel } = await abrir(502)
    await within(panel).findByText('Empiezo por el caso base')
    await user.click(within(panel).getByRole('button', { name: 'Borrar' }))
    const confirmar = screen.getByRole('dialog', { name: 'Borrar el comentario' })
    await user.click(within(confirmar).getByRole('button', { name: 'Borrar' }))
    expect(await screen.findByText('Comentario borrado')).toBeInTheDocument()
    await waitFor(() => expect(within(panel).queryByText('Empiezo por el caso base')).not.toBeInTheDocument())
  })

  it('un alumno no puede borrar comentarios ajenos', async () => {
    const { panel } = await abrir(502, 'elena')
    await within(panel).findByText('Empiezo por el caso base')
    expect(within(panel).queryByRole('button', { name: 'Borrar' })).not.toBeInTheDocument()
  })

  it('cancelar el borrado del comentario no borra', async () => {
    const { user, panel } = await abrir(502)
    await within(panel).findByText('Empiezo por el caso base')
    await user.click(within(panel).getByRole('button', { name: 'Borrar' }))
    await user.click(screen.getByRole('button', { name: 'Cancelar' }))
    expect(server.callsTo('delete', '/comments/900')).toHaveLength(0)
  })
})
