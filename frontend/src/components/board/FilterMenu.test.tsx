import { useState } from 'react'
import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { columns, member, task, user } from '@/test/factories'
import { renderWithProviders } from '@/test/render'
import type { TaskType } from '@/types/api'
import { FilterMenu } from './FilterMenu'

const ana = user(1, 'Ana López')
const tablero = columns([
  task({ id: 1, columnId: 10, type: 'BUG', assignees: [ana] }),
  task({ id: 2, columnId: 20, type: 'HISTORIA' }),
  task({ id: 3, columnId: 20, type: 'BUG' }),
])

/** El menu con su estado de verdad, como lo usa el tablero. */
function Harness() {
  const [people, setPeople] = useState<number[]>([])
  const [statuses, setStatuses] = useState<number[]>([])
  const [types, setTypes] = useState<TaskType[]>([])
  const [onlySignals, setOnlySignals] = useState(false)
  return (
    <>
      <FilterMenu
        members={[member(1, 'Ana López'), member(2, 'Pablo Ortega'), member(3, 'Carmen Molina', 'DOCENTE')]}
        currentUserId={1}
        columns={tablero}
        people={people}
        onPeople={setPeople}
        statuses={statuses}
        onStatuses={setStatuses}
        types={types}
        onTypes={setTypes}
        onlySignals={onlySignals}
        onOnlySignals={setOnlySignals}
        signalCount={2}
      />
      <output data-testid="estado">{JSON.stringify({ people, statuses, types, onlySignals })}</output>
    </>
  )
}

const estado = () => JSON.parse(screen.getByTestId('estado').textContent!)
const opcion = (nombre: RegExp | string) => screen.getByRole('button', { name: nombre })

describe('FilterMenu', () => {
  it('esta cerrado hasta pulsar «Filtros»', async () => {
    const { user } = renderWithProviders(<Harness />)
    expect(screen.queryByRole('navigation', { name: 'Campos del filtro' })).not.toBeInTheDocument()
    await user.click(opcion(/Filtros/))
    const campos = screen.getByRole('navigation', { name: 'Campos del filtro' })
    expect(within(campos).getAllByRole('button').map((b) => b.textContent)).toEqual([
      'Persona', 'Estado', 'Tipo de tarea', 'Avisos',
    ])
  })

  it('persona: lista sin docentes, con «(tú)» y «Sin asignar», y marca varias', async () => {
    const { user } = renderWithProviders(<Harness />)
    await user.click(opcion(/Filtros/))

    expect(opcion(/Ana López \(tú\)/)).toBeInTheDocument()
    expect(screen.queryByText('Carmen Molina')).not.toBeInTheDocument()

    await user.click(opcion(/Sin asignar/))
    await user.click(opcion(/Pablo Ortega/))
    expect(estado().people).toEqual([0, 2])
    expect(opcion(/Pablo Ortega/)).toHaveAttribute('aria-pressed', 'true')

    await user.click(opcion(/Pablo Ortega/))
    expect(estado().people).toEqual([0])
  })

  it('estado: una opcion por columna, con su numero de tareas', async () => {
    const { user } = renderWithProviders(<Harness />)
    await user.click(opcion(/Filtros/))
    await user.click(opcion('Estado'))

    expect(opcion(/En progreso/)).toHaveTextContent('En progreso2')
    await user.click(opcion(/En progreso/))
    expect(estado().statuses).toEqual([20])
  })

  it('tipo de tarea: cuenta las tareas de cada tipo y filtra', async () => {
    const { user } = renderWithProviders(<Harness />)
    await user.click(opcion(/Filtros/))
    await user.click(opcion('Tipo de tarea'))

    expect(opcion(/^Bug/)).toHaveTextContent('Bug2')
    await user.click(opcion(/^Bug/))
    expect(estado().types).toEqual(['BUG'])
  })

  it('avisos: una casilla con el numero de tareas con aviso', async () => {
    const { user } = renderWithProviders(<Harness />)
    await user.click(opcion(/Filtros/))
    await user.click(opcion('Avisos'))

    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
    await user.click(opcion(/Con aviso/))
    expect(estado().onlySignals).toBe(true)
  })

  it('el buscador filtra las opciones sin tildes', async () => {
    const { user } = renderWithProviders(<Harness />)
    await user.click(opcion(/Filtros/))
    await user.type(screen.getByRole('textbox', { name: 'Buscar en el filtro' }), 'lopez')

    expect(opcion(/Ana López/)).toBeInTheDocument()
    expect(screen.queryByText('Pablo Ortega')).not.toBeInTheDocument()

    await user.clear(screen.getByRole('textbox'))
    await user.type(screen.getByRole('textbox'), 'zzz')
    expect(screen.getByText('Nada que coincida')).toBeInTheDocument()
  })

  it('el boton dice cuantos filtros hay marcados', async () => {
    const { user } = renderWithProviders(<Harness />)
    await user.click(opcion(/Filtros/))
    await user.click(opcion(/Pablo Ortega/))
    await user.click(opcion('Avisos'))
    await user.click(opcion(/Con aviso/))

    expect(screen.getByTitle('Filtrar las tareas del tablero')).toHaveTextContent('Filtros2')
  })
})
