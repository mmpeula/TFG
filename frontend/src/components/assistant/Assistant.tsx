import { useEffect } from 'react'
import { useMutation } from '@tanstack/react-query'
import axios from 'axios'
import { Bot, MessageCircleQuestion, X } from 'lucide-react'
import { Chat } from './Chat'
import styles from './Assistant.module.css'
import { workspaces as workspacesApi } from '@/api/endpoints'
import { toErrorMessage } from '@/lib/api-client'
import { useAssistant } from '@/state/context'
import type { ChatTurn } from '@/types/api'

/**
 * Chat de ayuda del workspace: un boton flotante que se despliega en un panel
 * para preguntarle al asistente sobre el trabajo.
 *
 * Flotante y no una pantalla propia a proposito: se pregunta «que hago primero?»
 * mirando el tablero, no yendo a otro sitio a preguntarlo. Por eso el panel se
 * abre encima de lo que haya debajo y la conversacion sobrevive al cambio de
 * seccion (vive en AssistantProvider). Automatizar tareas es otra cosa: eso es el
 * Generar plan, que si tiene pestana y solo la ve el responsable.
 */
export function Assistant({ workspaceId }: { workspaceId: number }) {
  const { open, toggle, close, messagesOf, append, reset, activeTask } = useAssistant()
  const messages = messagesOf(workspaceId)

  // Escape cierra el panel, como cualquier cosa que se abre por encima.
  useEffect(() => {
    if (!open) return
    function onKey(event: KeyboardEvent) {
      if (event.key === 'Escape') close()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [open, close])

  const ask = useMutation({
    mutationFn: (message: string) => {
      const history: ChatTurn[] = messages.map(({ role, content }) => ({ role, content }))
      return workspacesApi.ask(workspaceId, message, history, activeTask?.id)
    },
    onSuccess: (response) => {
      append(workspaceId, {
        role: 'assistant',
        content: response.answer,
        context: response.contextSummary,
      })
    },
    onError: (err) => {
      const status = axios.isAxiosError(err) ? err.response?.status : undefined
      const content = status === 503
        ? `${toErrorMessage(err)} Revisa la conexion con DeepSeek y su API key.`
        : toErrorMessage(err)
      append(workspaceId, { role: 'assistant', content, error: true })
    },
  })

  function send(message: string) {
    append(workspaceId, { role: 'user', content: message })
    ask.mutate(message)
  }

  return (
    <div className={styles.dock}>
      {open && (
        <div className={styles.panel} role="dialog" aria-label="Chat de ayuda">
          <header className={styles.head}>
            <span className={styles.mark} aria-hidden="true">
              <Bot size={14} strokeWidth={2.75} />
            </span>
            <span className={styles.titles}>
              <span className={styles.title}>Asistente</span>
              <span className={styles.hint}>
                {activeTask ? `Sobre «${activeTask.title}»` : 'Responde sobre este espacio de trabajo'}
              </span>
            </span>
            <button className={styles.close} onClick={close} aria-label="Cerrar el chat">
              <X size={15} strokeWidth={2.75} />
            </button>
          </header>
          <div className={styles.body}>
            <Chat
              messages={messages}
              pending={ask.isPending}
              placeholder={activeTask ? `Preguntale algo sobre «${activeTask.title}»…` : undefined}
              onSend={send}
              onReset={() => reset(workspaceId)}
            />
          </div>
        </div>
      )}

      <button
        className={styles.bubble}
        onClick={toggle}
        aria-expanded={open}
        aria-haspopup="dialog"
        aria-label={open ? 'Cerrar el chat de ayuda' : 'Abrir el chat de ayuda'}
      >
        {open ? <X size={19} strokeWidth={2.5} /> : <MessageCircleQuestion size={19} strokeWidth={2.5} />}
        {!open && messages.length > 0 && <span className={styles.pip} aria-hidden="true" />}
      </button>
    </div>
  )
}
