import { useState, type InputHTMLAttributes } from 'react'
import { Eye, EyeOff } from 'lucide-react'
import styles from './PasswordInput.module.css'

interface Props extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> {
  /** Clase del input de cada pantalla (Auth.module.css, SettingsScreen.module.css...). */
  className?: string
}

/** Campo de contrasena con un boton para ensenarla y volver a ocultarla. */
export function PasswordInput({ className, style, ...rest }: Props) {
  const [visible, setVisible] = useState(false)

  return (
    <div className={styles.wrap}>
      <input
        {...rest}
        type={visible ? 'text' : 'password'}
        className={className ? `${className} ${styles.input}` : styles.input}
        style={{ ...style, paddingRight: 36 }}
      />
      <button
        type="button"
        className={styles.toggle}
        onClick={() => setVisible((current) => !current)}
        aria-label={visible ? 'Ocultar contraseña' : 'Mostrar contraseña'}
      >
        {visible ? <EyeOff size={15} strokeWidth={2.25} /> : <Eye size={15} strokeWidth={2.25} />}
      </button>
    </div>
  )
}
