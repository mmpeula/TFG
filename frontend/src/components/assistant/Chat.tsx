import { useEffect, useRef, useState } from 'react'
import { Calendar, Clock, Flag, Lightbulb, ListChecks, RotateCcw, SendHorizontal, Users } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import styles from './Chat.module.css'
import { renderMarkdown } from '@/lib/markdown'
import { formatTime } from '@/lib/format'
import type { ChatTurn } from '@/types/api'
import assistantIcon from '@/assets/assistant-icon.png'

/** Solo la usa este componente: el estado del chat flotante define la suya (ver state/context). */
interface ChatMessage extends ChatTurn {
  /** Lo que el modelo tenia delante al responder; se muestra en pequeno. */
  context?: string
  error?: boolean
  createdAt: number
}

/** Lo que el asistente sabe hacer, para quien abre el chat por primera vez. */
const CAPABILITIES = [
  { icon: ListChecks, label: 'Generar y organizar tareas' },
  { icon: Flag, label: 'Priorizar el trabajo' },
  { icon: Clock, label: 'Estimar tiempos' },
  { icon: Lightbulb, label: 'Resolver dudas' },
  { icon: Calendar, label: 'Crear planes y sprints' },
  { icon: Users, label: 'Analizar el progreso' },
]

interface Props {
  messages: ChatMessage[]
  pending: boolean
  disabled?: boolean
  placeholder?: string
  onSend: (message: string) => void
  onReset: () => void
}

/**
 * La conversacion con el asistente.
 *
 * Es un chat corriente a proposito: es la unica interfaz que un estudiante ya
 * sabe usar sin que nadie se la explique.
 */
export function Chat({ messages, pending, disabled, placeholder, onSend, onReset }: Props) {
  const [value, setValue] = useState('')
  const endRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLTextAreaElement>(null)
  // Fija a cuando se monta el chat: calcular la hora en cada render seria una
  // llamada impura dentro del render, y ademas cambiaria sola cada vez.
  const [welcomeTime] = useState(() => formatTime(Date.now()))

  // Cada mensaje nuevo deja la conversacion abajo, como en cualquier chat.
  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' })
  }, [messages.length, pending])

  // Crece con lo escrito, hasta el tope que marca el CSS: nunca recorta una
  // linea, ni la del texto ni la del placeholder largo.
  useEffect(() => {
    const el = inputRef.current
    if (!el) return
    el.style.height = 'auto'
    el.style.height = `${el.scrollHeight}px`
  }, [value])

  function send() {
    const text = value.trim()
    if (!text || pending || disabled) return
    onSend(text)
    setValue('')
  }

  return (
    <div className={styles.chat}>
      <div className={styles.thread}>
        {messages.length === 0 && !pending && (
          <div className={[styles.message, styles.theirs].join(' ')}>
            <span className={styles.avatar} aria-hidden="true">
              <img src={assistantIcon} alt="" className={styles.avatarIcon} />
            </span>
            <div className={styles.column}>
              <div className={styles.bubble}>
                <p className={styles.welcomeGreeting}>¡Hola! 👋</p>
                <p className={styles.text}>Soy PlanifIA, tu asistente de proyectos.</p>
                <p className={styles.text}>Puedo ayudarte a:</p>
                <ul className={styles.capabilities}>
                  {CAPABILITIES.map(({ icon: Icon, label }) => (
                    <li key={label} className={styles.capability}>
                      <Icon size={14} strokeWidth={2.5} className={styles.capabilityIcon} />
                      {label}
                    </li>
                  ))}
                </ul>
                <p className={styles.text}>¿En que puedo ayudarte hoy?</p>
              </div>
              <span className={styles.timestamp}>{welcomeTime}</span>
            </div>
          </div>
        )}

        {messages.map((message, index) => (
          <div
            key={index}
            className={[
              styles.message,
              message.role === 'user' ? styles.mine : styles.theirs,
              message.error && styles.failed,
            ]
              .filter(Boolean)
              .join(' ')}
          >
            {message.role === 'assistant' && (
              <span className={styles.avatar} aria-hidden="true">
                <img src={assistantIcon} alt="" className={styles.avatarIcon} />
              </span>
            )}
            <div className={styles.column}>
              <div className={styles.bubble}>
                {message.role === 'assistant' ? (
                  <div className={styles.markdown}>{renderMarkdown(message.content)}</div>
                ) : (
                  <p className={styles.text}>{message.content}</p>
                )}
                {message.context && <span className={styles.context}>ha mirado: {message.context}</span>}
              </div>
              <span className={styles.timestamp}>{formatTime(message.createdAt)}</span>
            </div>
          </div>
        ))}

        {pending && (
          <div className={[styles.message, styles.theirs].join(' ')}>
            <span className={styles.avatar} aria-hidden="true">
              <img src={assistantIcon} alt="" className={styles.avatarIcon} />
            </span>
            <div className={styles.bubble}>
              <span className={styles.typing} aria-label="Escribiendo">
                <i />
                <i />
                <i />
              </span>
            </div>
          </div>
        )}

        <div ref={endRef} />
      </div>

      <div className={styles.composer}>
        <textarea
          ref={inputRef}
          className={styles.input}
          rows={2}
          value={value}
          disabled={disabled}
          placeholder={placeholder ?? 'Preguntale algo sobre este espacio de trabajo…'}
          onChange={(event) => setValue(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter' && !event.shiftKey) {
              event.preventDefault()
              send()
            }
          }}
        />
        {messages.length > 0 && (
          <Button variant="ghost" small onClick={onReset} aria-label="Empezar de cero">
            <RotateCcw size={15} strokeWidth={2.75} />
          </Button>
        )}
        <Button
          className={styles.sendButton}
          onClick={send}
          disabled={!value.trim() || pending || disabled}
          aria-label="Enviar"
        >
          <SendHorizontal size={15} strokeWidth={2.75} />
        </Button>
      </div>
      <p className={styles.disclaimer}>Respuestas generadas por IA · pueden contener errores</p>
    </div>
  )
}
