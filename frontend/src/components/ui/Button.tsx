import type { ButtonHTMLAttributes, ReactNode } from 'react'
import styles from './Button.module.css'

type Variant = 'primary' | 'accent' | 'secondary' | 'ghost' | 'destructive'

interface Props extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant
  small?: boolean
  block?: boolean
  children: ReactNode
}

export function Button({ variant = 'primary', small, block, children, className, ...rest }: Props) {
  const classes = [styles.button, styles[variant], small && styles.small, block && styles.block, className]
    .filter(Boolean)
    .join(' ')
  return (
    <button type="button" className={classes} {...rest}>
      {children}
    </button>
  )
}
