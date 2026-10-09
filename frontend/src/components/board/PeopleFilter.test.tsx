import { screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { member } from '@/test/factories'
import { renderWithProviders } from '@/test/render'
import { PeopleFilter } from './PeopleFilter'

const equipo = [
  member(1, 'Ana López'),
  member(2, 'Pablo Ortega'),
  member(3, 'Carmen Molina', 'DOCENTE'),
  ...[4, 5, 6, 7, 8, 9].map((id) => member(id, `Alumno ${id}`)),
]

describe('PeopleFilter', () => {
  it('pone primero «Sin asignar» y tu cara, y deja fuera a los docentes', () => {
    renderWithProviders(<PeopleFilter members={equipo} selected={[]} currentUserId={2} onChange={vi.fn()} />)
    const caras = screen.getAllByRole('button').map((boton) => boton.title)
    expect(caras.slice(0, 2)).toEqual(['Sin asignar', 'Pablo Ortega (tú)'])
    expect(caras).not.toContain('Carmen Molina')
  })

  it('a partir de seis personas agrupa el resto en «+N»', () => {
    renderWithProviders(<PeopleFilter members={equipo} selected={[]} onChange={vi.fn()} />)
    // 8 alumnos: 6 caras y «+2».
    expect(screen.getByTitle('2 personas más')).toHaveTextContent('+2')
  })

  it('pulsar una cara la marca y pulsarla otra vez la desmarca', async () => {
    const onChange = vi.fn()
    const { user, rerender } = renderWithProviders(
      <PeopleFilter members={equipo} selected={[]} onChange={onChange} />,
    )
    const ana = () => screen.getByRole('button', { name: 'Ana López' })
    await user.click(ana())
    expect(onChange).toHaveBeenLastCalledWith([1])

    rerender(<PeopleFilter members={equipo} selected={[1]} onChange={onChange} />)
    expect(ana()).toHaveAttribute('aria-pressed', 'true')
    await user.click(ana())
    expect(onChange).toHaveBeenLastCalledWith([])
  })

  it('las personas escondidas se eligen desde el desplegable', async () => {
    const onChange = vi.fn()
    const { user } = renderWithProviders(<PeopleFilter members={equipo} selected={[]} onChange={onChange} />)
    await user.click(screen.getByTitle('2 personas más'))
    await user.click(screen.getByRole('button', { name: /Alumno 9/ }))
    expect(onChange).toHaveBeenCalledWith([9])
  })
})
