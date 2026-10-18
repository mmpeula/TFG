import { screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { AssigneePicker } from './AssigneePicker'
import { member, user } from '@/test/factories'
import { renderWithProviders } from '@/test/render'

const equipo = [
  member(1, 'Lucía Fernández', 'RESPONSABLE'),
  member(2, 'Pablo Ortega'),
  member(3, 'Carla Ruiz', 'MIEMBRO', false),
  member(4, 'Carmen Molina', 'DOCENTE'),
]

describe('AssigneePicker', () => {
  it('no ofrece a docentes ni a cuentas dadas de baja', async () => {
    const { user: usuario } = renderWithProviders(
      <AssigneePicker members={equipo} selected={[]} onChange={vi.fn()} />,
    )
    await usuario.click(screen.getByRole('button', { name: /Sin asignar/ }))

    expect(screen.getByRole('button', { name: /Lucía Fernández/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Pablo Ortega/ })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Carla Ruiz/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Carmen Molina/ })).not.toBeInTheDocument()
  })

  it('una cuenta dada de baja que ya estaba asignada sale, para poder quitarla', async () => {
    const onChange = vi.fn()
    const { user: usuario } = renderWithProviders(
      <AssigneePicker members={equipo} selected={[user(3, 'Carla Ruiz')]} onChange={onChange} />,
    )
    await usuario.click(screen.getByRole('button', { name: /Carla Ruiz/ }))
    const opciones = screen.getAllByRole('button', { name: /Carla Ruiz/ })
    await usuario.click(opciones.at(-1)!)

    expect(onChange).toHaveBeenCalledWith([])
  })
})
