import { AxiosError, AxiosHeaders } from 'axios'
import { describe, expect, it, vi } from 'vitest'
import { apiClient, getStoredToken, setStoredToken, toErrorMessage, UNAUTHORIZED_EVENT } from './api-client'
import { HttpError, server } from '@/test/server'

const config = { headers: new AxiosHeaders() }
const conRespuesta = (data: unknown, status = 400) =>
  new AxiosError('Request failed', 'ERR_BAD_REQUEST', config, {}, { data, status, statusText: '', headers: {}, config })

describe('toErrorMessage', () => {
  it('de los errores de validacion ensena solo los mensajes, sin repetirlos', () => {
    const error = conRespuesta({
      message: 'Datos no válidos',
      fieldErrors: [
        { field: 'title', message: 'El título es obligatorio' },
        { field: 'name', message: 'El título es obligatorio' },
        { field: 'dueDate', message: 'La fecha no es válida' },
      ],
    })
    expect(toErrorMessage(error)).toBe('El título es obligatorio · La fecha no es válida')
  })

  it('el mensaje del servidor si lo hay', () => {
    expect(toErrorMessage(conRespuesta({ message: 'No tienes permiso' }, 403))).toBe('No tienes permiso')
  })

  it('distingue el tiempo agotado y la falta de conexion', () => {
    expect(toErrorMessage(new AxiosError('timeout', 'ECONNABORTED', config))).toBe('El servidor ha tardado demasiado en responder')
    expect(toErrorMessage(new AxiosError('Network Error', 'ERR_NETWORK', config)))
      .toBe('No se ha podido conectar con el servidor. Comprueba la conexión.')
  })

  it('una respuesta sin mensaje usa el de axios', () => {
    expect(toErrorMessage(conRespuesta({}, 500))).toBe('Request failed')
  })

  it('errores que no son de axios', () => {
    expect(toErrorMessage(new Error('Algo raro'))).toBe('Algo raro')
    expect(toErrorMessage('cadena')).toBe('Error desconocido')
  })
})

describe('token de sesion', () => {
  it('se guarda, se lee y se borra', () => {
    setStoredToken('abc')
    expect(getStoredToken()).toBe('abc')
    setStoredToken(null)
    expect(getStoredToken()).toBeNull()
  })

  it('sin almacenamiento (modo incognito estricto) no rompe', () => {
    const get = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('bloqueado') })
    const set = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('bloqueado') })
    expect(getStoredToken()).toBeNull()
    expect(() => setStoredToken('abc')).not.toThrow()
    get.mockRestore()
    set.mockRestore()
  })
})

describe('interceptores', () => {
  it('manda el token en cada peticion', async () => {
    setStoredToken('tok-2')
    const yo = await apiClient.get('/auth/me')
    expect(yo.data.email).toBe('pablo@uni.es')
  })

  it('un 401 borra la sesion y avisa a la aplicacion', async () => {
    setStoredToken('tok-2')
    server.on('get', '/workspaces', () => { throw new HttpError(401, 'Sesión caducada') })
    const aviso = vi.fn()
    window.addEventListener(UNAUTHORIZED_EVENT, aviso)
    await expect(apiClient.get('/workspaces')).rejects.toThrow()
    window.removeEventListener(UNAUTHORIZED_EVENT, aviso)
    expect(aviso).toHaveBeenCalledOnce()
    expect(getStoredToken()).toBeNull()
  })

  it('un 401 al entrar no es una sesion caducada', async () => {
    setStoredToken('tok-2')
    await expect(apiClient.post('/auth/login', { email: 'x@uni.es', password: 'mala' })).rejects.toThrow()
    expect(getStoredToken()).toBe('tok-2')
  })
})
