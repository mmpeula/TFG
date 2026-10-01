import type { ReactNode } from 'react'

/**
 * El subconjunto de markdown que el asistente puede usar (ver su system prompt):
 * negrita, codigo en linea, bloques de codigo y listas. Nada de tablas ni
 * cabeceras, que no caben bien en la burbuja de un chat. No es un parser de
 * markdown completo a proposito: solo cubre lo que el prompt le permite escribir.
 */
export function renderMarkdown(text: string): ReactNode {
  const lines = text.split('\n')
  const blocks: ReactNode[] = []
  let i = 0
  let key = 0

  while (i < lines.length) {
    const line = lines[i]

    if (line.trim().startsWith('```')) {
      const code: string[] = []
      i++
      while (i < lines.length && !lines[i].trim().startsWith('```')) {
        code.push(lines[i])
        i++
      }
      i++ // salta la valla de cierre, si la hay
      blocks.push(
        <pre key={key++}>
          <code>{code.join('\n')}</code>
        </pre>,
      )
      continue
    }

    if (/^\s*\d+\.\s/.test(line)) {
      const items: string[] = []
      while (i < lines.length && /^\s*\d+\.\s/.test(lines[i])) {
        items.push(lines[i].replace(/^\s*\d+\.\s/, ''))
        i++
      }
      const listKey = key++
      blocks.push(
        <ol key={listKey}>
          {items.map((item, index) => (
            <li key={index}>
              <span>{renderInline(item, `${listKey}-${index}`)}</span>
            </li>
          ))}
        </ol>,
      )
      continue
    }

    if (/^\s*[-*]\s/.test(line)) {
      const items: string[] = []
      while (i < lines.length && /^\s*[-*]\s/.test(lines[i])) {
        items.push(lines[i].replace(/^\s*[-*]\s/, ''))
        i++
      }
      const listKey = key++
      blocks.push(
        <ul key={listKey}>
          {items.map((item, index) => <li key={index}>{renderInline(item, `${listKey}-${index}`)}</li>)}
        </ul>,
      )
      continue
    }

    if (line.trim() === '') {
      i++
      continue
    }

    // Parrafo: junta lineas seguidas hasta la siguiente linea en blanco, lista o bloque de codigo.
    const paragraph: string[] = []
    while (
      i < lines.length && lines[i].trim() !== ''
      && !lines[i].trim().startsWith('```')
      && !/^\s*\d+\.\s/.test(lines[i])
      && !/^\s*[-*]\s/.test(lines[i])
    ) {
      paragraph.push(lines[i])
      i++
    }
    const paragraphKey = key++
    blocks.push(<p key={paragraphKey}>{renderInline(paragraph.join(' '), `${paragraphKey}`)}</p>)
  }

  return blocks
}

/** Negrita (`**asi**`) y codigo en linea (`` `asi` ``) dentro de una linea de texto normal. */
function renderInline(text: string, keyPrefix: string): ReactNode[] {
  const parts: ReactNode[] = []
  const re = /(\*\*[^*]+\*\*|`[^`]+`)/g
  let last = 0
  let match: RegExpExecArray | null
  let index = 0

  while ((match = re.exec(text))) {
    if (match.index > last) parts.push(text.slice(last, match.index))
    const token = match[0]
    if (token.startsWith('**')) {
      parts.push(<strong key={`${keyPrefix}-${index++}`}>{token.slice(2, -2)}</strong>)
    } else {
      parts.push(<code key={`${keyPrefix}-${index++}`}>{token.slice(1, -1)}</code>)
    }
    last = re.lastIndex
  }
  if (last < text.length) parts.push(text.slice(last))
  return parts
}
