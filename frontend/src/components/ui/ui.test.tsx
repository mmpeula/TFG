import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { BreakableText } from './BreakableText'
import { DateInput } from './DateInput'

describe('BreakableText', () => {
  it('deja saltar de linea despues de flechas y barras sin cambiar el texto', () => {
    const { container } = render(<p><BreakableText>percepción→decisión/acción</BreakableText></p>)
    expect(container.textContent).toBe('percepción→decisión/acción')
    expect(container.querySelectorAll('wbr')).toHaveLength(2)
  })

  it('un texto sin separadores queda tal cual', () => {
    const { container } = render(<p><BreakableText>Memoria final</BreakableText></p>)
    expect(container.innerHTML).toBe('<p>Memoria final</p>')
  })
})

describe('DateInput', () => {
  function Campo({ inicial = '', onChange = vi.fn() }: { inicial?: string; onChange?: (iso: string) => void }) {
    const [value, setValue] = useState(inicial)
    return (
      <>
        <DateInput value={value} aria-label="Fecha" onChange={(iso) => { setValue(iso); onChange(iso) }} />
        <button onClick={() => setValue('2026-12-24')}>Nochebuena</button>
      </>
    )
  }

  it('pone las barras mientras se escribe y entrega la fecha ISO al salir', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<Campo onChange={onChange} />)
    const campo = screen.getByLabelText('Fecha')
    await user.type(campo, '05102026')
    expect(campo).toHaveValue('05/10/2026')
    await user.tab()
    expect(onChange).toHaveBeenCalledWith('2026-10-05')
  })

  it('una fecha imposible vuelve a la que habia', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<Campo inicial="2026-10-05" onChange={onChange} />)
    const campo = screen.getByLabelText('Fecha')
    await user.clear(campo)
    await user.type(campo, '31022026{Enter}')
    expect(campo).toHaveValue('05/10/2026')
    await user.clear(campo)
    await user.type(campo, '01011999{Enter}')
    expect(campo).toHaveValue('05/10/2026')
    expect(onChange).not.toHaveBeenCalled()
  })

  it('vaciarlo borra la fecha', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<Campo inicial="2026-10-05" onChange={onChange} />)
    await user.clear(screen.getByLabelText('Fecha'))
    await user.tab()
    expect(onChange).toHaveBeenCalledWith('')
  })

  it('editar en medio no reformatea y se queda solo con numeros y barras', async () => {
    const user = userEvent.setup()
    render(<Campo inicial="2026-10-05" />)
    const campo = screen.getByLabelText('Fecha') as HTMLInputElement
    fireEvent.change(campo, { target: { value: '0x5/10/2026', selectionStart: 1 } })
    expect(campo).toHaveValue('05/10/2026')
    await user.tab()
  })

  it('sigue a la fecha si cambia desde fuera', async () => {
    const user = userEvent.setup()
    render(<Campo inicial="2026-10-05" />)
    await user.click(screen.getByRole('button', { name: 'Nochebuena' }))
    expect(screen.getByLabelText('Fecha')).toHaveValue('24/12/2026')
  })

  it('el boton abre el calendario del navegador, o enfoca el selector si no hay', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()
    const { container } = render(<Campo onChange={onChange} />)
    const selector = container.querySelector('input[type=date]') as HTMLInputElement
    selector.showPicker = vi.fn()
    await user.click(screen.getByRole('button', { name: 'Abrir el calendario' }))
    expect(selector.showPicker).toHaveBeenCalled()

    selector.showPicker = () => { throw new Error('no soportado') }
    await user.click(screen.getByRole('button', { name: 'Abrir el calendario' }))
    expect(selector).toHaveFocus()

    fireEvent.change(selector, { target: { value: '2026-11-02' } })
    expect(onChange).toHaveBeenCalledWith('2026-11-02')
  })
})
