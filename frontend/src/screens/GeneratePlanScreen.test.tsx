import { fireEvent, screen, waitFor } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { currentPath, renderApp } from '@/test/app'
import { aiPlan, HttpError, server } from '@/test/server'
import type { AiPlanResponse } from '@/types/api'

const GENERAR = '/workspaces/100/generar-plan'
const generar = () => screen.getByRole('button', { name: /Generar y repartir|Generando…/ })
const enunciado = () => new File(['Practica 3: algoritmos voraces'], 'enunciado.pdf', { type: 'application/pdf' })
const selectorDeDocumento = () => document.querySelector('input[type=file]') as HTMLInputElement

/** Una respuesta que no llega hasta que el test la suelta, para ver lo que se ensena mientras tanto. */
function enEspera<T>() {
  let soltar!: (value: T) => void
  const promesa = new Promise<T>((resolve) => { soltar = resolve })
  return { promesa, soltar }
}

async function abrir() {
  const app = renderApp(GENERAR, { as: 'lucia' })
  await screen.findByText('Se repartirá entre 3 personas')
  return app
}

describe('GeneratePlanScreen: preparar', () => {
  it('con fechas en el espacio el calendario es automatico', async () => {
    const { user } = await abrir()
    expect(screen.getByText('Calendario automático')).toBeInTheDocument()
    expect(screen.getByText('De 28 sept 2026 a 22 nov 2026')).toBeInTheDocument()
    expect(screen.getByText(/Si lo dejas vacío, se usará el guion ya guardado/)).toBeInTheDocument()
    expect(screen.getByText(/se envían a DeepSeek/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /Cambiar en ajustes/ }))
    await waitFor(() => expect(currentPath()).toBe('/workspaces/100/ajustes'))
  })

  it('el reparto incluye al equipo menos a la docente, y se puede quitar y volver a meter a alguien', async () => {
    const { user } = await abrir()
    expect(screen.getByLabelText('Preferencias de Lucía Fernández')).toBeInTheDocument()
    expect(screen.queryByLabelText('Preferencias de Carmen Molina')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Quitar a Pablo Ortega de este reparto' }))
    expect(screen.getByText('Se repartirá entre 2 personas')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /Volver a incluir a Pablo Ortega/ }))
    expect(screen.getByText('Se repartirá entre 3 personas')).toBeInTheDocument()
  })

  it('sin nadie en el reparto no se puede generar', async () => {
    const { user } = await abrir()
    for (const nombre of ['Lucía Fernández', 'Pablo Ortega', 'Elena Navarro']) {
      await user.click(screen.getByRole('button', { name: `Quitar a ${nombre} de este reparto` }))
    }
    expect(screen.getByText('Incluye al menos a una persona en el reparto')).toBeInTheDocument()
    expect(generar()).toBeDisabled()
  })

  it('guarda las preferencias de alguien en su ficha', async () => {
    const { user } = await abrir()
    await user.type(screen.getByLabelText('Preferencias de Pablo Ortega'), 'Prefiere backend')
    await user.click(screen.getByRole('button', { name: 'Guardar las preferencias de Pablo Ortega en su ficha' }))
    expect(await screen.findByText('Indicaciones de Pablo Ortega guardadas')).toBeInTheDocument()
    expect(server.callsTo('patch', '/teams/100/members/2/notes')[0].body).toEqual({ planNotes: 'Prefiere backend' })
    await waitFor(() =>
      expect(screen.queryByRole('button', { name: /Guardar las preferencias de Pablo/ })).not.toBeInTheDocument())
  })

  it('si no se pueden guardar las preferencias lo dice y no las pierde', async () => {
    server.on('patch', '/teams/:id/members/:userId/notes', () => { throw new HttpError(400, 'Demasiado largo') })
    const { user } = await abrir()
    await user.type(screen.getByLabelText('Preferencias de Elena Navarro'), 'Solo tardes')
    await user.click(screen.getByRole('button', { name: /Guardar las preferencias de Elena/ }))
    expect(await screen.findByText('Demasiado largo')).toBeInTheDocument()
    expect(screen.getByLabelText('Preferencias de Elena Navarro')).toHaveValue('Solo tardes')
  })

  it('sin guion, ni texto, ni documento no hay de donde sacar el plan', async () => {
    server.db.project.briefing = ''
    await abrir()
    expect(screen.getByText('Cuéntanos qué tienes que hacer, o sube un documento.')).toBeInTheDocument()
    expect(generar()).toBeDisabled()
  })

  it('un alumno no puede generar el plan', async () => {
    renderApp(GENERAR, { as: 'pablo' })
    await waitFor(() => expect(currentPath()).toBe('/workspaces/100/plan'))
  })
})

describe('GeneratePlanScreen: generar y aplicar', () => {
  it('genera a partir de una descripcion escrita', async () => {
    const { user } = await abrir()
    await user.type(screen.getByLabelText('Descripción del trabajo'), 'Practica de voraces')
    expect(screen.getByText('19 caracteres')).toBeInTheDocument()
    expect(screen.getByText('Cuéntanos qué tienes que hacer.')).toBeInTheDocument()
    await user.click(generar())

    expect(await screen.findByText('Propuesta: 2 tareas en 2 sprints')).toBeInTheDocument()
    expect(screen.getByText('Propuesta de planificación lista para «Algorítmica, práctica 3»')).toBeInTheDocument()
    expect(server.callsTo('post', '/workspaces/100/ai/plan')[0].body).toEqual({
      text: 'Practica de voraces',
      options: {
        startDate: '2026-09-28', deadline: '2026-11-22',
        team: [{ userId: 1, preferences: '' }, { userId: 2, preferences: '' }, { userId: 3, preferences: '' }],
      },
    })
  })

  it('al añadir el plan al tablero se vacia la descripcion escrita', async () => {
    const { user } = await abrir()
    await user.type(screen.getByLabelText('Descripción del trabajo'), 'Practica de voraces')
    await user.click(generar())
    await user.click(await screen.findByRole('button', { name: 'Añadir al tablero' }))
    await screen.findByRole('button', { name: /2 tareas añadidas al tablero/ })
    expect(screen.getByLabelText('Descripción del trabajo')).toHaveValue('')
    expect(screen.getByText('0 caracteres')).toBeInTheDocument()
  })

  it('al añadir el plan al tablero se quita el documento, y se puede volver a subir el mismo', async () => {
    const { user } = await abrir()
    const documento = enunciado()
    await user.upload(selectorDeDocumento(), documento)
    await user.click(generar())
    await user.click(await screen.findByRole('button', { name: 'Añadir al tablero' }))
    await screen.findByRole('button', { name: /2 tareas añadidas al tablero/ })
    expect(screen.queryByText('enunciado.pdf')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Descripción del trabajo')).toBeInTheDocument()

    await user.upload(selectorDeDocumento(), documento)
    expect(screen.getByText('enunciado.pdf')).toBeInTheDocument()
  })

  it('la propuesta ensena sprints, avisos, reparto y cada tarea con su detalle', async () => {
    const { user } = await abrir()
    await user.click(generar())
    await screen.findByText('Propuesta: 2 tareas en 2 sprints')

    expect(screen.getByText('guion')).toBeInTheDocument()
    expect(screen.getByText('Un aviso sobre este plan')).toBeInTheDocument()
    expect(screen.getByText('El sprint 2 va justo de horas')).toBeInTheDocument()
    expect(screen.getByText('Reparto del trabajo')).toBeInTheDocument()
    expect(screen.getByText('1 tarea · 4 h')).toBeInTheDocument()
    expect(screen.getByText('Tener el algoritmo base')).toBeInTheDocument()
    expect(screen.getByText('Apartado 2 del guion')).toBeInTheDocument()
    expect(screen.getByText('2 seleccionadas · modelo deepseek-chat. Marca o desmarca antes de llevarlas al tablero.')).toBeInTheDocument()
    expect(server.callsTo('post', '/projects/100/ai-plan')).toHaveLength(1)
  })

  it('se desmarca una tarea y se llevan al tablero solo las marcadas', async () => {
    const { user } = await abrir()
    await user.click(generar())
    await user.click(await screen.findByRole('button', { name: /Escribir las pruebas/ }))
    expect(screen.getByText(/^1 seleccionadas/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Añadir al tablero' }))

    // El toast y el resumen plegable de lo que se ha añadido.
    expect(await screen.findAllByText('1 tarea añadida al tablero')).toHaveLength(2)
    const aplicadas = server.callsTo('post', '/projects/100/ai-plan/apply')[0].body as { boardId: number; tasks: { title: string }[] }
    expect(aplicadas.boardId).toBe(100)
    expect(aplicadas.tasks.map((task) => task.title)).toEqual(['Diseñar la estructura de datos'])
    // El espacio ya tenia fechas: no se toca.
    expect(server.callsTo('put', '/workspaces/100')).toHaveLength(0)

    const resumen = screen.getByRole('button', { name: /1 tarea añadida al tablero/ })
    expect(resumen).toHaveAttribute('aria-expanded', 'false')
    await user.click(resumen)
    expect(screen.getByRole('listitem')).toHaveTextContent('Diseñar la estructura de datos')
  })

  it('sin ninguna marcada no se puede añadir; se puede regenerar', async () => {
    const { user } = await abrir()
    await user.click(generar())
    await user.click(await screen.findByRole('button', { name: /Diseñar la estructura de datos/ }))
    await user.click(screen.getByRole('button', { name: /Escribir las pruebas/ }))
    expect(screen.getByRole('button', { name: 'Añadir al tablero' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Regenerar' }))
    await waitFor(() => expect(server.callsTo('post', '/projects/100/ai-plan')).toHaveLength(2))
    expect(await screen.findByText(/^2 seleccionadas/)).toBeInTheDocument()
  })

  it('si la IA falla lo explica, avisa y deja reintentar', async () => {
    let intentos = 0
    server.on('post', '/projects/:id/ai-plan', () => {
      intentos++
      if (intentos === 1) throw new HttpError(503, 'DeepSeek no responde')
      return aiPlan()
    })
    const { user } = await abrir()
    await user.click(generar())
    expect(await screen.findByText('No se ha podido generar la planificación')).toBeInTheDocument()
    expect(screen.getByText('DeepSeek no responde')).toBeInTheDocument()
    expect(screen.getByText('No se ha podido generar la planificación de «Algorítmica, práctica 3»')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(await screen.findByText('Propuesta: 2 tareas en 2 sprints')).toBeInTheDocument()
  })

  it('una propuesta vacia ensena el mensaje del modelo', async () => {
    server.on('post', '/projects/:id/ai-plan', () =>
      aiPlan({ tasks: [], warnings: ['El documento no tiene tareas', 'Revisa el formato'], message: 'No he encontrado nada que planificar' }))
    const { user } = await abrir()
    await user.click(generar())
    expect(await screen.findByText('No he encontrado nada que planificar')).toBeInTheDocument()
    expect(screen.getByText('2 avisos sobre este plan')).toBeInTheDocument()
  })

  it('las tareas de un sprint que no existe van aparte y avisa de quien se queda sin tarea', async () => {
    const plan: AiPlanResponse = aiPlan()
    plan.tasks[1].sprint = 9
    plan.sprints = [plan.sprints[0], { number: 2, start: '2026-10-26', end: '2026-11-08' }]
    plan.workload.push({ memberId: 3, memberName: 'Elena Navarro', tasks: 0, hours: 0 })
    server.on('post', '/projects/:id/ai-plan', () => plan)
    const { user } = await abrir()
    await user.click(generar())
    expect(await screen.findByText('Sin sprint')).toBeInTheDocument()
    expect(screen.getByText('Sin tareas en este sprint.')).toBeInTheDocument()
    expect(screen.getByText('Han salido 2 tareas para 3 personas: no alcanzan para todos.')).toBeInTheDocument()
  })

  it('si hay tareas de sobra y aun asi alguien se queda a cero, lo explica', async () => {
    const plan = aiPlan()
    plan.workload = [
      { memberId: 1, memberName: 'Lucía Fernández', tasks: 2, hours: 7 },
      { memberId: 2, memberName: 'Pablo Ortega', tasks: 0, hours: 0 },
    ]
    server.on('post', '/projects/:id/ai-plan', () => plan)
    const { user } = await abrir()
    await user.click(generar())
    expect(await screen.findByText(/las propuestas no se pueden dividir más/)).toBeInTheDocument()
  })

  it('al aplicar, un fallo del servidor se avisa', async () => {
    server.on('post', '/projects/:id/ai-plan/apply', () => { throw new HttpError(400, 'Una tarea no tiene título') })
    const { user } = await abrir()
    await user.click(generar())
    await user.click(await screen.findByRole('button', { name: 'Añadir al tablero' }))
    expect(await screen.findByText('Una tarea no tiene título')).toBeInTheDocument()
  })

  it('al aplicar sin tablero lo dice', async () => {
    const { user } = await abrir()
    await user.click(generar())
    await screen.findByText('Propuesta: 2 tareas en 2 sprints')
    server.on('get', '/projects/:id/boards', () => [])
    // Se fuerza a releer los tableros: el espacio se ha quedado sin el suyo mientras tanto.
    const { queryClient } = await import('@/app/query-client')
    await queryClient.refetchQueries({ queryKey: ['projects', 'detalle', 100, 'boards'] })
    await user.click(screen.getByRole('button', { name: 'Añadir al tablero' }))
    expect(await screen.findByText('Este espacio de trabajo no tiene tablero al que añadir las tareas')).toBeInTheDocument()
  })
})

describe('GeneratePlanScreen: con documento y sin fechas', () => {
  it('pide las fechas, propone la de inicio del documento y las guarda al aplicar', async () => {
    Object.assign(server.db.workspace, { startDate: undefined, deadline: undefined })
    const progreso = enEspera<AiPlanResponse>()
    server.on('post', '/projects/:id/ai-plan/document', () => progreso.promesa)
    const { user } = await abrir()

    expect(screen.getByText('Calendario del plan')).toBeInTheDocument()
    expect(screen.getByText('Falta la fecha de inicio')).toBeInTheDocument()
    expect(screen.getByText('Falta la fecha de fin')).toBeInTheDocument()

    await user.upload(selectorDeDocumento(), enunciado())
    expect(screen.getByText('enunciado.pdf')).toBeInTheDocument()
    expect(screen.queryByLabelText('Descripción del trabajo')).not.toBeInTheDocument()
    const [inicio, fin] = screen.getAllByPlaceholderText('dd/mm/aaaa')
    await waitFor(() => expect(inicio).toHaveValue('12/10/2026'))
    expect(generar()).toBeDisabled()

    await user.type(fin, '30112026')
    await user.tab()
    await user.click(generar())

    // Mientras lee el documento se ven sus fases; la visual no ha hecho falta.
    expect(await screen.findByText('Leyendo «enunciado.pdf» y repartiendo el trabajo…')).toBeInTheDocument()
    expect(await screen.findByText('Sin contenido visual que analizar')).toBeInTheDocument()
    expect(screen.getByText('Generando planificación').closest('li')).toHaveAttribute('aria-current', 'step')
    expect(screen.getByRole('button', { name: /Generando…/ })).toBeDisabled()

    progreso.soltar(aiPlan())
    await user.click(await screen.findByRole('button', { name: 'Añadir al tablero' }))
    await screen.findByRole('button', { name: /2 tareas añadidas al tablero/ })
    expect(server.callsTo('put', '/workspaces/100')[0].body).toMatchObject({
      startDate: '2026-10-12', deadline: '2026-11-30', name: 'Algorítmica, práctica 3',
    })
  })

  it('si el documento necesita analisis visual se ve esa fase en marcha', async () => {
    server.on('get', '/projects/:id/ai-plan/progress', () => ({ stage: 'VISUAL' }))
    server.on('post', '/projects/:id/ai-plan/document', () => enEspera().promesa)
    const { user } = await abrir()
    await user.upload(selectorDeDocumento(), enunciado())
    await user.click(generar())
    const visual = await screen.findByText('Analizando contenido visual')
    await waitFor(() => expect(visual.closest('li')).toHaveAttribute('aria-current', 'step'))
    expect(screen.getByText(/solo si hace falta/)).toBeInTheDocument()
  })

  it('el documento se puede soltar encima, cambiar y quitar', async () => {
    const { user } = await abrir()
    const zona = screen.getByRole('button', { name: /Arrastra aquí el documento/ })
    fireEvent.dragOver(zona)
    fireEvent.dragLeave(zona)
    fireEvent.drop(zona, { dataTransfer: { files: [enunciado()] } })
    expect(screen.getByText('enunciado.pdf')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Cambiar archivo/ })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Quitar el documento' }))
    expect(screen.queryByText('enunciado.pdf')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Descripción del trabajo')).toBeInTheDocument()
  })

  it('soltar algo que no es un fichero no cambia nada', async () => {
    await abrir()
    fireEvent.drop(screen.getByRole('button', { name: /Arrastra aquí el documento/ }), { dataTransfer: { files: [] } })
    expect(screen.getByLabelText('Descripción del trabajo')).toBeInTheDocument()
  })

  it('la generacion sigue aunque se cambie de seccion, y «Ver» devuelve al resultado', async () => {
    const progreso = enEspera<AiPlanResponse>()
    server.on('post', '/projects/:id/ai-plan', () => progreso.promesa)
    const { user } = await abrir()
    await user.click(generar())
    expect(await screen.findByText(/Leyendo el guion guardado/)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /Tablero/ }))
    await waitFor(() => expect(currentPath()).toBe('/workspaces/100/tablero'))
    progreso.soltar(aiPlan())
    await user.click(await screen.findByRole('button', { name: 'Ver' }))
    await waitFor(() => expect(currentPath()).toBe(GENERAR))
    expect(await screen.findByText('Propuesta: 2 tareas en 2 sprints')).toBeInTheDocument()
  })

  it('cambiar la descripcion descarta la propuesta anterior', async () => {
    const { user } = await abrir()
    await user.click(generar())
    await screen.findByText('Propuesta: 2 tareas en 2 sprints')
    await user.type(screen.getByLabelText('Descripción del trabajo'), 'otra cosa')
    expect(screen.queryByText('Propuesta: 2 tareas en 2 sprints')).not.toBeInTheDocument()
  })
})
