import { useEffect } from 'react'
import { useMutation } from '@tanstack/react-query'
import axios from 'axios'
import { MessageCircleQuestion, X } from 'lucide-react'
import { Chat } from './Chat'
import styles from './HelpChat.module.css'
import { workspaces as workspacesApi } from '@/api/endpoints'
import { toErrorMessage } from '@/lib/api-client'
import { useHelpChat } from '@/state/context'
import type { ChatTurn } from '@/types/api'

/**
 * Chat de ayuda del workspace: un boton flotante que se despliega en un panel
 * para preguntarle al copiloto sobre el trabajo.
 *
 * Flotante y no una pantalla propia a proposito: se pregunta «que hago primero?»
 * mirando el tablero, no yendo a otro sitio a preguntarlo. Por eso el panel se
 * abre encima de lo que haya debajo y la conversacion sobrevive al cambio de
 * seccion (vive en HelpChatProvider). Automatizar tareas es otra cosa: eso es el
 * Copiloto, que si tiene pestana y solo la ve el responsable.
 */
export function HelpChat({ workspaceId }: { workspaceId: number }) {
  const { open, toggle, close, messagesOf, append, reset } = useHelpChat()
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
      return workspacesApi.ask(workspaceId, message, history)
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
        ? `${toErrorMessage(err)} El modelo corre en tu maquina: comprueba que Ollama esta arrancado.`
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
            <span className={styles.title}>Pregunta sobre este workspace</span>
            <button className={styles.close} onClick={close} aria-label="Cerrar el chat">
              <X size={15} strokeWidth={2.75} />
            </button>
          </header>
          <Chat
            messages={messages}
            pending={ask.isPending}
            onSend={send}
            onReset={() => reset(workspaceId)}
          />
        </div>
      )}

      <button
        className={styles.bubble}
        onClick={toggle}
        aria-expanded={open}
        aria-label={open ? 'Cerrar el chat de ayuda' : 'Abrir el chat de ayuda'}
      >
        {open ? <X size={20} strokeWidth={2.5} /> : <MessageCircleQuestion size={20} strokeWidth={2.5} />}
      </button>
    </div>
  )
}
