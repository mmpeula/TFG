import { useRef } from 'react'
import { Button } from '@/components/ui/Button'
import type { ImagePicker } from '@/hooks/useImagePicker'

/** Lo que el servidor acepta como foto (ver FileStorageService): imagenes raster, nada de SVG. */
const IMAGE_TYPES = 'image/png,image/jpeg,image/gif,image/webp'

/** «Cambiar foto» y, si hay una, «Quitar foto», con el selector de ficheros escondido. */
export function PhotoActions({ picker, hasPhoto }: { picker: ImagePicker; hasPhoto: boolean }) {
  const inputRef = useRef<HTMLInputElement>(null)

  return (
    <>
      <input
        ref={inputRef}
        type="file"
        accept={IMAGE_TYPES}
        hidden
        onChange={(event) => {
          picker.pick(event.target.files?.[0])
          // Asi, quitar la foto y volver a elegir la misma tambien avisa del cambio.
          event.target.value = ''
        }}
      />
      <Button variant="secondary" small onClick={() => inputRef.current?.click()}>
        Cambiar foto
      </Button>
      {hasPhoto && (
        <Button variant="ghost" small onClick={picker.remove}>
          Quitar foto
        </Button>
      )}
    </>
  )
}
