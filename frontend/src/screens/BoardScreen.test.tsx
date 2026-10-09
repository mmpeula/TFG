import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { currentPath, renderApp } from '@/test/app'
import { setMobile } from '@/test/setup'
import { HttpError, server } from '@/test/server'

const TABLERO = '/workspaces/100/tablero'
const columna = (nombre: string) => screen.findByRole('region', { name: nombre })
const tarjeta = (titulo: string) => screen.findByRole('button', { name: titulo })

describe('BoardScreen: lo que se ve', () => {
  it('pinta las cuatro columnas con sus tareas y cuantas hay', async () => {
    renderApp(TABLERO, { as: 'lucia' })
    const porHacer = await columna('Por hacer')
    expect(within(porHacer).getByText('Leer el enunciado')).toBeInTheDocument()
    expect(within(porHacer).getByText('1')).toBeInTheDocument()
    expect(within(await columna('Hecho')).getByText('Memoria final')).toBeInTheDocument()
  })

  it('cada tarjeta lleva su aviso y el tablero el mas grave', async () => {
    renderApp(TABLERO, { as: 'lucia' })
    expect(await screen.findByText('venció hace 2 d')).toBeInTheDocument()
    expect(screen.getByText('9 d sin avance')).toBeInTheDocument()
    expect(screen.getByText('sin responsable')).toBeInTheDocument()
    expect(screen.getByText('«Leer el enunciado» venció hace 2 d y sigue en Por hacer.')).toBeInTheDocument()
  })

  it('«Ver el detalle» del aviso lleva al panel de seguimiento', async () => {
    const { user } = renderApp(TABLERO, { as: 'lucia' })
    await user.click(await screen.findByRole('button', { name: 'Ver el detalle' }))
    await waitFor(() => expect(currentPath()).toBe('/workspaces/100/seguimiento'))
  })

  it('una columna con mas de cuatro tareas esconde el resto tras «+N más»', async () => {
    const base = server.db.tasks[0]
    server.db.tasks.push(...[1, 2, 3, 4, 5].map((n) => ({ ...base, id: 600 + n, title: `Extra ${n}`, position: n })))
    const { user } = renderApp(TABLERO, { as: 'lucia' })
    await user.click(await screen.findByRole('button', { name: '+ 2 más' }))
    expect(screen.getByText('Extra 5')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Ver menos' }))
    expect(screen.queryByText('Extra 5')).not.toBeInTheDocument()
  })

  it('un tablero sin tareas invita a crear la primera o a generar el plan', async () => {
    server.db.tasks = []
    const { user } = renderApp(TABLERO, { as: 'lucia' })
    expect(await screen.findByText('Este espacio de trabajo está vacío')).toBeInTheDocument()
    // El de la pestaña y el del estado vacio: el segundo es el de la invitacion.
    await user.click(screen.getAllByRole('button', { name: 'Generar plan' })[1])
    await waitFor(() => expect(currentPath()).toBe('/workspaces/100/generar-plan'))
  })

  it('un alumno con el tablero vacio solo puede crear tareas', async () => {
    server.db.tasks = []
    renderApp(TABLERO, { as: 'pablo' })
    expect(await screen.findByText('Empieza creando tu primera tarea.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Generar plan' })).not.toBeInTheDocument()
  })

  it('la docente ve el tablero pero no puede tocarlo', async () => {
    renderApp(TABLERO, { as: 'carmen' })
    expect(await screen.findByText('Leer el enunciado')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Añadir tarea' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Acciones de/ })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Panel docente/ })).toBeInTheDocument()
  })

  it('sin tablero lo explica', async () => {
    server.on('get', '/projects/:id/boards', () => [])
    renderApp(TABLERO, { as: 'lucia' })
    expect(await screen.findByText('Este espacio de trabajo todavía no tiene tablero')).toBeInTheDocument()
  })

  it('en el movil se ve una columna y se cambia con las pestañas', async () => {
    setMobile(true)
    const { user } = renderApp(TABLERO, { as: 'lucia' })
    expect(await screen.findByText('Leer el enunciado')).toBeInTheDocument()
    expect(screen.queryByText('Memoria final')).not.toBeInTheDocument()
    await user.click(screen.getByRole('tab', { name: 'Hecho' }))
    expect(screen.getByText('Memoria final')).toBeInTheDocument()
  })

  it('un espacio suspendido no ensena el tablero', async () => {
    server.db.workspace.enabled = false
    renderApp(TABLERO, { as: 'lucia' })
    expect(await screen.findByText('Este espacio de trabajo está suspendido')).toBeInTheDocument()
  })
})

describe('BoardScreen: filtros', () => {
  it('el buscador filtra por titulo y Escape lo borra', async () => {
    const { user } = renderApp(TABLERO, { as: 'lucia' })
    const buscar = await screen.findByRole('textbox', { name: 'Buscar tarea' })
    await user.type(buscar, 'memoria')
    expect(screen.getByText('Memoria final')).toBeInTheDocument()
    expect(screen.queryByText('Leer el enunciado')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Borrar la búsqueda' }))
    expect(screen.getByText('Leer el enunciado')).toBeInTheDocument()

    await user.type(buscar, '#502{Escape}')
    expect(buscar).toHaveValue('')
  })

  it('sin resultados ofrece quitar los filtros', async () => {
    const { user } = renderApp(TABLERO, { as: 'lucia' })
    await user.type(await screen.findByRole('textbox', { name: 'Buscar tarea' }), 'nada de esto')
    expect(screen.getByText('Ninguna tarea coincide con estos filtros.')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Quitar los filtros' }))
    expect(screen.getByText('Leer el enunciado')).toBeInTheDocument()
  })

  it('el filtro por persona desde las caras', async () => {
    const { user } = renderApp(TABLERO, { as: 'lucia' })
    await user.click(await screen.findByRole('button', { name: 'Pablo Ortega' }))
    expect(screen.getByText('Implementar el algoritmo voraz')).toBeInTheDocument()
    expect(screen.queryByText('Leer el enunciado')).not.toBeInTheDocument()
  })

  it('el menu Filtros filtra por estado, tipo y avisos', async () => {
    const { user } = renderApp(TABLERO, { as: 'lucia' })
    await user.click(await screen.findByRole('button', { name: /Filtros/ }))

    await user.click(screen.getByRole('button', { name: 'Estado' }))
    await user.click(screen.getByRole('button', { name: /^En revisión/ }))
    expect(screen.queryByText('Leer el enunciado')).not.toBeInTheDocument()
    expect(screen.getByText('Pruebas de rendimiento')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /^En revisión/ }))

    await user.click(screen.getByRole('button', { name: 'Tipo de tarea' }))
    await user.click(screen.getByRole('button', { name: /^Bug/ }))
    expect(screen.getByText('Implementar el algoritmo voraz')).toBeInTheDocument()
    expect(screen.queryByText('Memoria final')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /^Bug/ }))

    await user.click(screen.getByRole('button', { name: 'Avisos' }))
    await user.click(screen.getByRole('button', { name: /Con aviso/ }))
    expect(screen.queryByText('Memoria final')).not.toBeInTheDocument()
    expect(screen.getByText('Leer el enunciado')).toBeInTheDocument()
  })

  it('se puede llegar con el filtro de avisos ya puesto', async () => {
    renderApp({ pathname: TABLERO, state: { onlyWarnings: true } }, { as: 'lucia' })
    expect(await screen.findByText('Leer el enunciado')).toBeInTheDocument()
    expect(screen.queryByText('Memoria final')).not.toBeInTheDocument()
  })
})

describe('BoardScreen: tareas', () => {
  it('mover a «Hecho» desde el menu avisa, deja deshacer y pide las horas reales', async () => {
    const { user } = renderApp(TABLERO, { as: 'lucia' })
    await user.click(await screen.findByRole('button', { name: 'Acciones de Implementar el algoritmo voraz' }))
    await user.click(screen.getByRole('button', { name: 'Mover a Hecho' }))

    expect(await screen.findByText('Movida a Hecho')).toBeInTheDocument()
    const dialogo = await screen.findByRole('dialog', { name: 'Tarea terminada' })
    const horas = within(dialogo).getByPlaceholderText('Por ejemplo: 1,5')
    expect(horas).toHaveValue('6')

    await user.clear(horas)
    await user.type(horas, 'mucho')
    expect(within(dialogo).getByText(/Escribe las horas con un número/)).toBeInTheDocument()
    expect(within(dialogo).getByRole('button', { name: 'Guardar' })).toBeDisabled()

    await user.clear(horas)
    await user.type(horas, '7,5{Enter}')
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(server.callsTo('put', '/tasks/502')[0].body).toMatchObject({ actualHours: 7.5 })
    expect(server.callsTo('patch', '/tasks/502/move')[0].body).toEqual({ columnId: 40, position: 1 })
  })

  it('«Deshacer» devuelve la tarea a su columna', async () => {
    const { user } = renderApp(TABLERO, { as: 'lucia' })
    await user.click(await screen.findByRole('button', { name: 'Acciones de Pruebas de rendimiento' }))
    await user.click(screen.getByRole('button', { name: 'Mover a En progreso' }))
    await user.click(await screen.findByRole('button', { name: 'Deshacer' }))
    await waitFor(() => expect(server.callsTo('patch', '/tasks/503/move')).toHaveLength(2))
    expect(server.callsTo('patch', '/tasks/503/move')[1].body).toEqual({ columnId: 30, position: 2 })
  })

  it('«Ahora no» cierra el dialogo de horas sin guardar', async () => {
    const { user } = renderApp(TABLERO, { as: 'lucia' })
    await user.click(await screen.findByRole('button', { name: 'Acciones de Pruebas de rendimiento' }))
    await user.click(screen.getByRole('button', { name: 'Mover a Hecho' }))
    await user.click(await screen.findByRole('button', { name: 'Ahora no' }))
    expect(server.callsTo('put', '/tasks/503')).toHaveLength(0)
  })

  it('si el servidor rechaza el movimiento lo avisa', async () => {
    server.on('patch', '/tasks/:id/move', () => { throw new HttpError(409, 'La columna ya no existe') })
    const { user } = renderApp(TABLERO, { as: 'lucia' })
    await user.click(await screen.findByRole('button', { name: 'Acciones de Leer el enunciado' }))
    await user.click(screen.getByRole('button', { name: 'Mover a En progreso' }))
    expect(await screen.findByText('La columna ya no existe')).toBeInTheDocument()
  })

  it('borra una tarea tras confirmarlo', async () => {
    const { user } = renderApp(TABLERO, { as: 'lucia' })
    await user.click(await screen.findByRole('button', { name: 'Acciones de Leer el enunciado' }))
    await user.click(screen.getByRole('button', { name: 'Borrar' }))
    const dialogo = screen.getByRole('dialog', { name: 'Borrar «Leer el enunciado»' })
    await user.click(within(dialogo).getByRole('button', { name: 'Borrar' }))
    expect(await screen.findByText('Tarea borrada')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByText('Leer el enunciado')).not.toBeInTheDocument())
  })

  it('crea una tarea rapida en la columna con Enter y cancela con Escape', async () => {
    const { user } = renderApp(TABLERO, { as: 'lucia' })
    const revision = await columna('En revisión')
    await user.click(within(revision).getByRole('button', { name: /Añadir tarea/ }))
    await user.type(within(revision).getByRole('textbox'), 'Revisar el código{Enter}')

    expect(await screen.findByText('Tarea creada')).toBeInTheDocument()
    expect(server.callsTo('post', '/columns/30/tasks')[0].body).toEqual({ title: 'Revisar el código', priority: 'MEDIA' })

    await user.type(within(revision).getByRole('textbox'), 'a medias{Escape}')
    expect(within(revision).queryByRole('textbox')).not.toBeInTheDocument()
  })

  it('crea una tarea completa desde «Añadir tarea»', async () => {
    const { user } = renderApp(TABLERO, { as: 'lucia' })
    await screen.findByText('Leer el enunciado')
    await user.click(screen.getAllByRole('button', { name: 'Añadir tarea' })[0])
    const dialogo = screen.getByRole('dialog', { name: 'Nueva tarea' })

    await user.type(within(dialogo).getByPlaceholderText('Modelar la base de datos'), 'Preparar la defensa')
    await user.click(within(dialogo).getByRole('radio', { name: 'Bug' }))
    await user.type(within(dialogo).getByPlaceholderText('Qué hay que hacer exactamente'), 'Diapositivas')
    await user.type(within(dialogo).getByPlaceholderText('dd/mm/aaaa'), '20102026')
    await user.tab()
    await user.type(within(dialogo).getByPlaceholderText('Por ejemplo: 1,5'), 'dos')
    expect(within(dialogo).getByText(/Escribe las horas con un número/)).toBeInTheDocument()
    expect(within(dialogo).getByRole('button', { name: 'Crear tarea' })).toBeDisabled()
    await user.clear(within(dialogo).getByPlaceholderText('Por ejemplo: 1,5'))
    await user.type(within(dialogo).getByPlaceholderText('Por ejemplo: 1,5'), '2,5')

    await user.click(within(dialogo).getByText('··').closest('button')!)
    // La de la lista del desplegable; la otra es su cara en el filtro por persona.
    await user.click(screen.getAllByRole('button', { name: /Elena Navarro/ }).at(-1)!)
    await user.selectOptions(within(dialogo).getByRole('combobox'), 'En progreso')
    await user.click(within(dialogo).getByRole('button', { name: 'Crear tarea' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(server.callsTo('post', '/columns/20/tasks')[0].body).toEqual({
      title: 'Preparar la defensa', type: 'BUG', description: 'Diapositivas', dueDate: '2026-10-20',
      estimatedHours: 2.5, assigneeIds: [3],
    })
  })

  it('no hay atajos de teclado: las teclas sueltas no abren ni navegan', async () => {
    const { user } = renderApp(TABLERO, { as: 'lucia' })
    await screen.findByText('Leer el enunciado')
    await user.keyboard('nipbg?')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(currentPath()).toBe(TABLERO)
  })

  it('abrir una tarjeta abre su panel', async () => {
    const { user } = renderApp(TABLERO, { as: 'lucia' })
    await user.click(await tarjeta('Memoria final'))
    expect(await screen.findByRole('dialog', { name: /Memoria final/ })).toBeInTheDocument()
  })

  it('se puede llegar con una tarea concreta abierta', async () => {
    renderApp({ pathname: TABLERO, state: { openTaskId: 503 } }, { as: 'lucia' })
    expect(await screen.findByRole('dialog', { name: /Pruebas de rendimiento/ })).toBeInTheDocument()
  })
})

describe('BoardScreen: arrastrar con el teclado', () => {
  const COLUMNAS = ['Por hacer', 'En progreso', 'En revisión', 'Hecho']
  const caja = (x: number, y: number, width: number, height: number) =>
    ({ x, y, left: x, top: y, width, height, right: x + width, bottom: y + height, toJSON() {} }) as DOMRect

  /**
   * jsdom no calcula posiciones y dnd-kit las necesita para saber a donde se mueve la
   * tarjeta: cada columna ocupa su franja de 300 px y sus tarjetas van una debajo de otra.
   */
  function conPosiciones() {
    const original = Element.prototype.getBoundingClientRect
    Element.prototype.getBoundingClientRect = function () {
      const columna = this.closest('section[aria-label]')
      const i = COLUMNAS.indexOf(columna?.getAttribute('aria-label') ?? '')
      if (i < 0) return original.call(this)
      if (this === columna) return caja(i * 300, 0, 280, 800)
      const tarjeta = this.closest('article')
      if (!tarjeta) return caja(i * 300, 0, 280, 40)
      const n = [...columna!.querySelectorAll('article')].indexOf(tarjeta)
      return caja(i * 300 + 10, 60 + n * 120, 260, 100)
    }
    return () => { Element.prototype.getBoundingClientRect = original }
  }

  it('Espacio coge la tarjeta, la flecha la lleva a la columna de al lado y Espacio la suelta', async () => {
    const restaurar = conPosiciones()
    const { user } = renderApp(TABLERO, { as: 'lucia' })
    ;(await screen.findByRole('button', { name: 'Arrastrar Leer el enunciado' })).focus()
    await user.keyboard(' ')
    await user.keyboard('{ArrowRight}')
    await user.keyboard(' ')
    await waitFor(() => expect(server.callsTo('patch', '/tasks/501/move')).toHaveLength(1))
    expect(server.callsTo('patch', '/tasks/501/move')[0].body).toEqual({ columnId: 20, position: 0 })
    expect(await screen.findByText('Movida a En progreso')).toBeInTheDocument()
    restaurar()
  })

  it('Escape cancela el arrastre sin mover nada', async () => {
    const restaurar = conPosiciones()
    const { user } = renderApp(TABLERO, { as: 'lucia' })
    ;(await screen.findByRole('button', { name: 'Arrastrar Leer el enunciado' })).focus()
    await user.keyboard(' ')
    await user.keyboard('{ArrowRight}')
    await user.keyboard('{Escape}')
    expect(server.callsTo('patch', '/tasks/501/move')).toHaveLength(0)
    restaurar()
  })
})
