import { useEffect, useRef, useState } from 'react'
import { Bot, RotateCcw, SendHorizontal } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import styles from './Chat.module.css'
import type { ChatTurn } from '@/types/api'

/** Solo la usa este componente: el estado del chat flotante define la suya (ver state/context). */
interface ChatMessage extends ChatTurn {
  /** Lo que el modelo tenia delante al responder; se muestra en pequeno. */
  context?: string
  error?: boolean
}

/** Preguntas de ejemplo: quien no sabe que pedirle, empieza por aqui. */
const SUGGESTIONS = [
  '¿Que deberia hacer primero?',
  'Resume las tareas pendientes',
  '¿Que tareas llevamos atrasadas?',
  '¿Quien tiene mas trabajo encima?',
  'Divide la tarea mas grande en pasos',
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
 * La conversacion con el copiloto.
 *
 * Es un chat corriente a proposito: es la unica interfaz que un estudiante ya
 * sabe usar sin que nadie se la explique.
 */
export function Chat({ messages, pending, disabled, placeholder, onSend, onReset }: Props) {
  const [value, setValue] = useState('')
  const endRef = useRef<HTMLDivElement>(null)

  // Cada mensaje nuevo deja la conversacion abajo, como en cualquier chat.
  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' })
  }, [messages.length, pending])

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
          <div className={styles.welcome}>
            <span className={styles.avatar} aria-hidden="true">
              <Bot size={18} strokeWidth={2.75} />
            </span>
            <div>
              <p className={styles.welcomeText}>
                Puedo mirar este workspace y responderte sobre el: que llevais hecho, que urge y como
                repartirlo. Pruebame:
              </p>
              <div className={styles.suggestions}>
                {SUGGESTIONS.map((suggestion) => (
                  <button
                    key={suggestion}
                    className={styles.suggestion}
                    disabled={disabled}
                    onClick={() => onSend(suggestion)}
                  >
                    {suggestion}
                  </button>
                ))}
              </div>
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
                <Bot size={16} strokeWidth={2.75} />
              </span>
            )}
            <div className={styles.bubble}>
              <p className={styles.text}>{message.content}</p>
              {message.context && <span className={styles.context}>ha mirado: {message.context}</span>}
            </div>
          </div>
        ))}

        {pending && (
          <div className={[styles.message, styles.theirs].join(' ')}>
            <span className={styles.avatar} aria-hidden="true">
              <Bot size={16} strokeWidth={2.75} />
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
          className={styles.input}
          rows={1}
          value={value}
          disabled={disabled}
          placeholder={placeholder ?? 'Preguntale algo sobre este workspace…'}
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
        <Button onClick={send} disabled={!value.trim() || pending || disabled} aria-label="Enviar">
          <SendHorizontal size={15} strokeWidth={2.75} />
        </Button>
      </div>
    </div>
  )
}
