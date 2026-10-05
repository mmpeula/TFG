import { Fragment } from 'react'

/** Signos tras los que se puede saltar de linea aunque no haya espacio: flechas, barras, guion bajo… */
const SEPARADORES = /([→←↔⇒⇐/\\_|>·]+)/

/**
 * Texto que, si no cabe, salta de linea despues de una flecha o una barra en vez
 * de partir una palabra por la mitad.
 *
 * «percepción→decisión→acción→validación» es, para el navegador, una sola
 * palabra: o se sale de la caja o (con overflow-wrap) la corta donde caiga,
 * «valid|ación». Con un <wbr> detras de cada separador puede saltar justo ahi,
 * que es donde lo haria una persona. El texto copiado queda igual.
 */
export function BreakableText({ children }: { children: string }) {
  const partes = children.split(SEPARADORES)
  if (partes.length === 1) return <>{children}</>
  return (
    <>
      {partes.map((parte, index) =>
        // Las partes impares son los separadores (por el grupo del split).
        index % 2 === 1 ? (
          <Fragment key={index}>
            {parte}
            <wbr />
          </Fragment>
        ) : (
          <Fragment key={index}>{parte}</Fragment>
        ),
      )}
    </>
  )
}
