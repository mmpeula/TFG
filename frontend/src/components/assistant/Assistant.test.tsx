import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { renderApp } from '@/test/app'
import { HttpError, server } from '@/test/server'

async function abrirChat(path = '/workspaces/100/tablero') {
  const app = renderApp(path, { as: 'lucia' })
  await app.user.click(await screen.findByRole('button', { name: 'Abrir el chat de ayuda' }))
  return { ...app, chat: screen.getByRole('dialog', { name: 'Chat de ayuda' }) }
}

describe('Assistant', () => {
  it('saluda y explica lo que sabe hacer', async () => {
    const { chat } = await abrirChat()
    expect(within(chat).getByText('¡Hola! 👋')).toBeInTheDocument()
    expect(within(chat).getByText('Priorizar el trabajo')).toBeInTheDocument()
    expect(within(chat).getByRole('button', { name: 'Enviar' })).toBeDisabled()
  })

  it('pregunta, ensena la respuesta en markdown y lo que ha mirado', async () => {
    const { user, chat } = await abrirChat()
    await user.type(within(chat).getByRole('textbox'), '¿qué hago primero?{Enter}')
    expect(within(chat).getByText('¿qué hago primero?', { selector: 'p' })).toBeInTheDocument()
    expect(await within(chat).findByText('¿qué hago primero?', { selector: 'strong' })).toBeInTheDocument()
    expect(within(chat).getByText('ha mirado: 4 tareas, 4 personas')).toBeInTheDocument()

    await user.type(within(chat).getByRole('textbox'), 'y luego')
    await user.click(within(chat).getByRole('button', { name: 'Enviar' }))
    await within(chat).findByText('y luego', { selector: 'strong' })
    // La segunda pregunta lleva la conversacion anterior.
    expect(server.callsTo('post', '/workspaces/100/ai/chat')[1].body).toEqual({
      message: 'y luego',
      history: [
        { role: 'user', content: '¿qué hago primero?' },
        { role: 'assistant', content: 'Empieza por **¿qué hago primero?**.' },
        { role: 'user', content: 'y luego' },
      ],
      taskId: undefined,
    })
  })

  it('«Empezar de cero» borra la conversacion', async () => {
    const { user, chat } = await abrirChat()
    await user.type(within(chat).getByRole('textbox'), 'hola{Enter}')
    await within(chat).findByText('hola', { selector: 'strong' })
    await user.click(within(chat).getByRole('button', { name: 'Empezar de cero' }))
    expect(within(chat).getByText('¡Hola! 👋')).toBeInTheDocument()
  })

  it('con DeepSeek caido lo dice y sugiere revisar la conexion', async () => {
    server.on('post', '/workspaces/:id/ai/chat', () => { throw new HttpError(503, 'El asistente no está disponible.') })
    const { user, chat } = await abrirChat()
    await user.type(within(chat).getByRole('textbox'), 'hola{Enter}')
    expect(await within(chat).findByText('El asistente no está disponible. Revisa la conexión con DeepSeek y su API key.')).toBeInTheDocument()
  })

  it('otros errores se ensenan tal cual', async () => {
    server.on('post', '/workspaces/:id/ai/chat', () => { throw new HttpError(400, 'Pregunta demasiado larga') })
    const { user, chat } = await abrirChat()
    await user.type(within(chat).getByRole('textbox'), 'hola{Enter}')
    expect(await within(chat).findByText('Pregunta demasiado larga')).toBeInTheDocument()
  })

  it('con una tarea abierta pregunta sobre ella', async () => {
    const app = renderApp({ pathname: '/workspaces/100/tablero', state: { openTaskId: 502 } }, { as: 'lucia' })
    await screen.findByRole('dialog', { name: 'Implementar el algoritmo voraz' })
    await app.user.click(screen.getByRole('button', { name: 'Abrir el chat de ayuda' }))
    const chat = screen.getByRole('dialog', { name: 'Chat de ayuda' })
    expect(within(chat).getByText('Sobre «Implementar el algoritmo voraz»')).toBeInTheDocument()
    await app.user.type(within(chat).getByPlaceholderText('Pregúntale algo sobre «Implementar el algoritmo voraz»…'), 'cómo la parto{Enter}')
    await waitFor(() => expect(server.callsTo('post', '/workspaces/100/ai/chat')).toHaveLength(1))
    expect(server.callsTo('post', '/workspaces/100/ai/chat')[0].body).toMatchObject({ taskId: 502 })
  })

  it('se minimiza, se cierra y se vuelve a abrir con la conversacion', async () => {
    const { user, chat } = await abrirChat()
    await user.type(within(chat).getByRole('textbox'), 'hola{Enter}')
    await within(chat).findByText('hola', { selector: 'strong' })
    await user.click(within(chat).getByRole('button', { name: 'Minimizar el chat' }))
    expect(screen.queryByRole('dialog', { name: 'Chat de ayuda' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Abrir el chat de ayuda' }))
    expect(screen.getByText('hola', { selector: 'strong' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Cerrar el chat' }))
    await user.click(screen.getByRole('button', { name: 'Abrir el chat de ayuda' }))
    await user.click(screen.getByRole('button', { name: 'Cerrar el chat de ayuda' }))
    expect(screen.queryByRole('dialog', { name: 'Chat de ayuda' })).not.toBeInTheDocument()
  })

  it('la docente no tiene asistente', async () => {
    renderApp('/workspaces/100/tablero', { as: 'carmen' })
    await screen.findByText('Leer el enunciado')
    expect(screen.queryByRole('button', { name: 'Abrir el chat de ayuda' })).not.toBeInTheDocument()
  })
})
