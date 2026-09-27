import { useEffect, useRef, useState, type ReactNode } from 'react'
import styles from './Controls.module.css'

/** Control segmentado (Tablero / Lista / Calendario, pestanas de estado en movil…). */
export function Segmented<T extends string>({
  options,
  value,
  onChange,
  ariaLabel,
}: {
  options: { value: T; label: string }[]
  value: T
  onChange: (value: T) => void
  ariaLabel?: string
}) {
  return (
    <div className={styles.segmented} role="tablist" aria-label={ariaLabel}>
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          role="tab"
          aria-selected={option.value === value}
          className={[styles.segment, option.value === value && styles.segmentActive]
            .filter(Boolean)
            .join(' ')}
          onClick={() => onChange(option.value)}
        >
          {option.label}
        </button>
      ))}
    </div>
  )
}

export function Chip({
  children,
  active,
  onClick,
}: {
  children: ReactNode
  active?: boolean
  onClick?: () => void
}) {
  return (
    <button
      type="button"
      className={[styles.chip, active && styles.chipActive].filter(Boolean).join(' ')}
      onClick={onClick}
      aria-pressed={active}
    >
      {children}
    </button>
  )
}

export function Dropdown({
  trigger,
  children,
  align = 'right',
}: {
  trigger: (props: { open: boolean; toggle: () => void }) => ReactNode
  children: (close: () => void) => ReactNode
  align?: 'left' | 'right'
}) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    function onDown(event: MouseEvent) {
      if (!ref.current?.contains(event.target as Node)) setOpen(false)
    }
    function onKey(event: KeyboardEvent) {
      if (event.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  return (
    <div className={styles.dropdown} ref={ref}>
      {trigger({ open, toggle: () => setOpen((value) => !value) })}
      {open && (
        <div className={[styles.menu, align === 'left' && styles.menuLeft].filter(Boolean).join(' ')}>
          {children(() => setOpen(false))}
        </div>
      )}
    </div>
  )
}

export function DropdownItem({
  children,
  onClick,
  active,
}: {
  children: ReactNode
  onClick?: () => void
  active?: boolean
}) {
  return (
    <button
      type="button"
      className={[styles.item, active && styles.itemActive].filter(Boolean).join(' ')}
      onClick={onClick}
    >
      {children}
    </button>
  )
}

/** Tooltip que aparece a los 400 ms de hover. */
export function Tooltip({ label, children }: { label: string; children: ReactNode }) {
  return (
    <span className={styles.tooltipWrap}>
      {children}
      <span className={styles.tooltip} role="tooltip">
        {label}
      </span>
    </span>
  )
}
