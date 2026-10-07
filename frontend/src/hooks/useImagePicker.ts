import { useEffect, useState } from 'react'

/**
 * Elegir una foto nueva o quitar la que hay, con su vista previa, antes de guardar.
 *
 * Lo usan el perfil y el espacio de trabajo: los dos suben la foto aparte (multipart, no
 * cabe en el JSON del resto del formulario) y solo cuando se pulsa Guardar.
 */
export function useImagePicker() {
  const [file, setFile] = useState<File | null>(null)
  const [preview, setPreview] = useState<string | null>(null)
  const [removed, setRemoved] = useState(false)

  // La vista previa es un objeto local: se libera al cambiar de fichero o al cerrar el
  // dialogo, o se acumularian URLs sin usar.
  useEffect(() => () => {
    if (preview) URL.revokeObjectURL(preview)
  }, [preview])

  return {
    /** La foto nueva elegida, pendiente de subir. */
    file,
    preview,
    /** Se ha pulsado «Quitar foto» y falta guardarlo. */
    removed,
    pick: (next: File | undefined) => {
      if (!next) return
      setFile(next)
      setPreview(URL.createObjectURL(next))
      setRemoved(false)
    },
    remove: () => {
      setFile(null)
      setPreview(null)
      setRemoved(true)
    },
    /** Ya esta en el servidor: si lo siguiente falla, un reintento no la vuelve a subir. */
    saved: () => {
      setFile(null)
      setRemoved(false)
    },
  }
}

export type ImagePicker = ReturnType<typeof useImagePicker>
