import { render } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { renderMarkdown } from './markdown'

const html = (text: string) => render(<div>{renderMarkdown(text)}</div>).container.firstElementChild!.innerHTML

describe('renderMarkdown', () => {
  it('parrafos con negrita y codigo en linea', () => {
    expect(html('Empieza por **el caso base** y usa `heapq`.')).toBe(
      '<p>Empieza por <strong>el caso base</strong> y usa <code>heapq</code>.</p>')
  })

  it('une las lineas de un parrafo y separa por lineas en blanco', () => {
    expect(html('una\ndos\n\ntres')).toBe('<p>una dos</p><p>tres</p>')
  })

  it('listas con guion o asterisco y listas numeradas', () => {
    expect(html('- a\n* **b**')).toBe('<ul><li>a</li><li><strong>b</strong></li></ul>')
    expect(html('1. uno\n2. dos')).toBe('<ol><li><span>uno</span></li><li><span>dos</span></li></ol>')
  })

  it('bloques de codigo, cerrados o no', () => {
    expect(html('```\nx = 1\n```\nfin')).toBe('<pre><code>x = 1</code></pre><p>fin</p>')
    expect(html('```\nsin cerrar')).toBe('<pre><code>sin cerrar</code></pre>')
  })

  it('un parrafo se corta al empezar una lista', () => {
    expect(html('texto\n- item')).toBe('<p>texto</p><ul><li>item</li></ul>')
  })
})
