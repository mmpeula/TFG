import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { PasswordInput } from '@/components/ui/PasswordInput'
import { AuthLayout, AuthSubmit } from './AuthLayout'
import styles from './Auth.module.css'
import { auth } from '@/api/endpoints'
import { useAuth } from '@/state/context'
import { ROUTES, homeForRole } from '@/app/routes'

export function LoginScreen() {
  const navigate = useNavigate()
  const { signIn, isAuthenticated } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  const mutation = useMutation({
    mutationFn: auth.login,
    onSuccess: (response) => {
      signIn(response)
      navigate(homeForRole(response.user.role), { replace: true })
    },
  })

  if (isAuthenticated) return <Navigate to={ROUTES.home} replace />

  return (
    <AuthLayout
      claim="Gestión colaborativa de proyectos universitarios"
      title="Acceder"
      subtitle="Entra con tu cuenta para ver tus tableros."
      foot={<>¿No tienes cuenta? <Link className={styles.link} to={ROUTES.register}>Regístrate</Link></>}
    >
      <form
        className={styles.form}
        onSubmit={(event) => {
          event.preventDefault()
          mutation.mutate({ email, password })
        }}
      >
        <div>
          <label className={styles.label} htmlFor="email">Email</label>
          <input
            id="email" type="email" autoComplete="email" required className={styles.control}
            value={email} onChange={(event) => setEmail(event.target.value)}
            placeholder="tu.correo@uni.es"
          />
        </div>

        <div>
          <label className={styles.label} htmlFor="password">Contraseña</label>
          <PasswordInput
            id="password" autoComplete="current-password" required className={styles.control}
            value={password} onChange={(event) => setPassword(event.target.value)}
          />
        </div>

        <AuthSubmit error={mutation.error} pending={mutation.isPending}>Entrar</AuthSubmit>
      </form>
    </AuthLayout>
  )
}
