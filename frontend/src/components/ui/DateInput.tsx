import { useRef, useState } from 'react'
import { CalendarDays } from 'lucide-react'
import styles from './DateInput.module.css'

/** Años que se aceptan al escribir: fuera de aqui es casi seguro una errata («0266», «20266»). */
const ANIO_MIN = 2000
const ANIO_MAX = 2100

/** «2026-10-05» → «05/10/2026». */
function toDisplay(iso: string): string {
  const [year, month, day] = iso.split('-')
  return year && month && day ? `${day}/${month}/${year}` : ''
}

/** «05/10/2026» → «2026-10-05», o null si no es una fecha que exista (o el año es una errata). */
function toIso(text: string): string | null {
  const match = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(text)
  if (!match) return null
  const [, day, month, year] = match
  if (Number(year) < ANIO_MIN || Number(year) > ANIO_MAX) return null
  const date = new Date(Number(year), Number(month) - 1, Number(day))
  const real = date.getFullYear() === Number(year) && date.getMonth() === Number(month) - 1 && date.getDate() === Number(day)
  return real ? `${year}-${month}-${day}` : null
}

/** Mete las barras solas mientras se escribe al final: «0510» → «05/10». */
function mask(raw: string): string {
  const digits = raw.replace(/\D/g, '').slice(0, 8)
  return [digits.slice(0, 2), digits.slice(2, 4), digits.slice(4)].filter(Boolean).join('/')
}

/**
 * Campo de fecha que siempre se lee día/mes/año.
 *
 * El `<input type="date">` del navegador no hace caso del idioma de la página:
 * sigue el del navegador, y en uno en ingles pone el mes delante. Aqui se
 * escribe en texto y el boton del calendario abre el selector nativo, que sigue
 * escondido debajo solo para eso. Hacia fuera habla en ISO («2026-10-05», o
 * vacio), igual que el input nativo.
 *
 * La fecha se entrega al salir del campo o con Enter, no a cada tecla: asi, en
 * sitios que guardan al momento (el panel de la tarea), editar una fecha no pasa
 * por estados intermedios ni guarda una a medias. Las barras solo se ponen solas
 * al escribir por el final; editando por en medio no se recolocan los digitos.
 */
export function DateInput({
  value,
  onChange,
  className,
  'aria-label': ariaLabel,
}: {
  value: string
  onChange: (iso: string) => void
  className?: string
  'aria-label'?: string
}) {
  const pickerRef = useRef<HTMLInputElement>(null)
  const [text, setText] = useState(toDisplay(value))
  // Si la fecha cambia desde fuera (el calendario, la detectada en un documento,
  // la respuesta del servidor), el texto se pone al dia sin necesidad de un efecto.
  const [lastValue, setLastValue] = useState(value)
  if (value !== lastValue) {
    setLastValue(value)
    setText(toDisplay(value))
  }

  function write(input: HTMLInputElement) {
    const raw = input.value
    const alFinal = input.selectionStart === raw.length
    // Escribiendo por el final, se ponen las barras; editando por en medio, solo se
    // quita lo que no sea digito o barra, sin mover nada de sitio.
    setText(alFinal && raw.length > text.length ? mask(raw) : raw.replace(/[^\d/]/g, '').slice(0, 10))
  }

  /** Entrega lo escrito si es una fecha valida; si no, vuelve a la ultima buena. */
  function commit() {
    const limpio = text.trim()
    if (limpio === '') {
      if (value !== '') onChange('')
      return
    }
    const iso = toIso(limpio)
    if (iso && iso !== value) onChange(iso)
    else if (!iso) setText(toDisplay(value))
  }

  function openPicker() {
    const picker = pickerRef.current
    if (!picker) return
    try {
      picker.showPicker()
    } catch {
      picker.focus()
    }
  }

  return (
    <span className={styles.wrap}>
      <input
        type="text"
        inputMode="numeric"
        className={[className, styles.text].filter(Boolean).join(' ')}
        value={text}
        placeholder="dd/mm/aaaa"
        aria-label={ariaLabel}
        onChange={(event) => write(event.target)}
        onBlur={commit}
        onKeyDown={(event) => {
          if (event.key === 'Enter') {
            event.preventDefault()
            commit()
          }
        }}
      />
      <button type="button" className={styles.button} onClick={openPicker} aria-label="Abrir el calendario">
        <CalendarDays size={15} strokeWidth={2} />
      </button>
      <input
        ref={pickerRef}
        type="date"
        tabIndex={-1}
        aria-hidden="true"
        className={styles.picker}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
    </span>
  )
}
