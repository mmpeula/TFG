import {
  useLayoutEffect, useRef, useState, type ChangeEvent, type InputHTMLAttributes, type KeyboardEvent,
} from 'react'
import { ArrowBigUp, Eye, EyeOff } from 'lucide-react'
import styles from './PasswordInput.module.css'

const IS_MAC = typeof navigator !== 'undefined' && /Mac|iPhone|iPad/.test(navigator.platform)
const DOT = '•'

interface Props extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> {
  /** Clase del input de cada pantalla (Auth.module.css, SettingsScreen.module.css...). */
  className?: string
}

/**
 * Campo de contrasena con tres ayudas:
 *
 * - Mientras escribes se ve la ultima letra y el resto son puntos, como en un movil;
 *   al salir del campo todo vuelve a puntos.
 * - Un boton para ensenarla entera y volver a ocultarla.
 * - Un aviso mientras tienes el Bloq Mayus activado, el motivo mas habitual de una
 *   contrasena «incorrecta» que no se ve, porque los puntos no dicen que letra es.
 *
 * Por debajo sigue siendo un {@code <input type="password">} de verdad —con su
 * autocompletado, su gestor de contrasenas, su edicion en cualquier punto del texto—;
 * lo que se ve de la ultima letra es una capa encima, sin eventos, que dibuja los
 * puntos en la misma tipografia (monoespaciada, para que cada caracter ocupe lo
 * mismo que el punto de debajo y el cursor caiga donde toca).
 */
export function PasswordInput({
  className, style, onKeyDown, onKeyUp, onBlur, onFocus, onChange, ...rest
}: Props) {
  const [visible, setVisible] = useState(false)
  const [focused, setFocused] = useState(false)
  // null: aun no se sabe. Un navegador no dice si el Bloq Mayus esta puesto hasta
  // que llega una tecla normal, y mejor no decir nada que avisar de algo falso.
  const [capsLock, setCapsLock] = useState<boolean | null>(null)
  // Posicion de la ultima letra escrita; null si lo ultimo fue borrar, pegar o autocompletar.
  const [peek, setPeek] = useState<number | null>(null)
  const [ownValue, setOwnValue] = useState(String(rest.defaultValue ?? ''))

  const inputRef = useRef<HTMLInputElement>(null)
  const maskRef = useRef<HTMLSpanElement>(null)
  const textRef = useRef<HTMLSpanElement>(null)

  const text = rest.value !== undefined ? String(rest.value) : ownValue
  const display = Array.from(text, (character, index) => (focused && index === peek ? character : DOT)).join('')

  // La capa copia la caja del input (borde, relleno, tamano de letra), que viene de la clase de
  // cada pantalla: asi no hay que saber nada de ella y el texto cae exactamente donde toca.
  useLayoutEffect(() => {
    const input = inputRef.current
    const mask = maskRef.current
    if (!input) return
    if (!mask) {
      input.style.letterSpacing = ''
      return
    }
    const css = getComputedStyle(input)
    const box = (side: 'Top' | 'Right' | 'Bottom' | 'Left') =>
      parseFloat(css[`border${side}Width`]) + parseFloat(css[`padding${side}`])
    mask.style.padding = `${box('Top')}px ${box('Right')}px ${box('Bottom')}px ${box('Left')}px`
    mask.style.fontSize = css.fontSize
    mask.style.lineHeight = css.lineHeight

    // Se mide en el propio navegador, que cada uno dibuja el punto a su manera: 20 puntos de un
    // input de contrasena frente a 20 caracteres de la capa, con la misma tipografia.
    const wrap = mask.parentElement!
    const font = { fontFamily: getComputedStyle(mask).fontFamily, fontSize: css.fontSize, letterSpacing: 'normal' }
    const hidden = { position: 'absolute', visibility: 'hidden', left: '-9999px', padding: '0', border: '0' }
    const nativeProbe = document.createElement('input')
    nativeProbe.type = 'password'
    nativeProbe.value = 'x'.repeat(20)
    Object.assign(nativeProbe.style, hidden, font, { width: '1px' })
    const maskProbe = document.createElement('span')
    maskProbe.textContent = DOT.repeat(20)
    Object.assign(maskProbe.style, hidden, font, { whiteSpace: 'pre' })
    wrap.append(nativeProbe, maskProbe)
    const nativeAdvance = nativeProbe.scrollWidth / 20
    const maskAdvance = maskProbe.getBoundingClientRect().width / 20
    nativeProbe.remove()
    maskProbe.remove()
    // Cuanto hay que separar las letras del input para que cada punto suyo mida lo mismo que un
    // caracter de la capa: el punto que dibuja el navegador puede ser mas estrecho. Se pone en el
    // elemento y no en el estilo de React, porque es un dato medido en el navegador, no de estado.
    if (nativeAdvance > 0 && maskAdvance > 0) input.style.letterSpacing = `${maskAdvance - nativeAdvance}px`
  }, [visible, className])

  // Un campo largo se desplaza por dentro: la capa tiene que ir a la par.
  const follow = () => {
    if (textRef.current && inputRef.current) {
      textRef.current.style.transform = `translateX(${-inputRef.current.scrollLeft}px)`
    }
  }
  useLayoutEffect(follow, [display])

  // Al pulsar la propia tecla los navegadores no se ponen de acuerdo: unos informan del estado
  // de antes de pulsarla y otros del de despues. Con cualquier otra tecla si es fiable, asi
  // que ahi se lee el estado real; con la del Bloq Mayus se da la vuelta al que ya se tenia,
  // una vez por pulsacion.
  const trackCapsLock = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key !== 'CapsLock') {
      setCapsLock(event.getModifierState('CapsLock'))
      return
    }
    // Windows y Linux avisan al bajar y al soltar la tecla: se cuenta solo al bajar.
    // En macOS solo avisan al activarla (bajar) y al desactivarla (soltar): se cuentan ambas.
    const countsThisEvent = IS_MAC || event.type === 'keydown'
    if (countsThisEvent && !event.repeat) setCapsLock((current) => (current === null ? null : !current))
  }

  const handleChange = (event: ChangeEvent<HTMLInputElement>) => {
    const native = event.nativeEvent as InputEvent
    const typedOneLetter = native.inputType === 'insertText' && native.data?.length === 1
    setPeek(typedOneLetter && event.target.selectionStart ? event.target.selectionStart - 1 : null)
    setOwnValue(event.target.value)
    onChange?.(event)
  }

  return (
    <>
      <div className={styles.wrap}>
        <input
          {...rest}
          ref={inputRef}
          type={visible ? 'text' : 'password'}
          className={[className, styles.input, !visible && styles.masked].filter(Boolean).join(' ')}
          // En linea y no en la hoja de estilos: la clase de cada pantalla podria ganarle, y con otra
          // tipografia los puntos del input y los de la capa dejarian de coincidir.
          style={{
            ...style,
            paddingRight: 36,
            ...(visible ? {} : { fontFamily: 'var(--font-mono)' }),
          }}
          aria-describedby={capsLock === true ? 'caps-lock-hint' : rest['aria-describedby']}
          onChange={handleChange}
          onScroll={follow}
          onKeyDown={(event) => {
            trackCapsLock(event)
            onKeyDown?.(event)
          }}
          onKeyUp={(event) => {
            trackCapsLock(event)
            follow()
            onKeyUp?.(event)
          }}
          onFocus={(event) => {
            setFocused(true)
            onFocus?.(event)
          }}
          onBlur={(event) => {
            setCapsLock(null)
            setFocused(false)
            onBlur?.(event)
          }}
        />
        {!visible && (
          <span ref={maskRef} className={styles.mask} aria-hidden="true">
            <span ref={textRef} className={styles.maskText}>{display}</span>
          </span>
        )}
        <button
          type="button"
          className={styles.toggle}
          onClick={() => setVisible((current) => !current)}
          aria-label={visible ? 'Ocultar contraseña' : 'Mostrar contraseña'}
        >
          {visible ? <EyeOff size={15} strokeWidth={2.25} /> : <Eye size={15} strokeWidth={2.25} />}
        </button>
      </div>
      {capsLock === true && (
        <span id="caps-lock-hint" className={styles.caps} role="status">
          <ArrowBigUp size={14} strokeWidth={2.5} aria-hidden="true" />
          Bloq Mayús activado
        </span>
      )}
    </>
  )
}
