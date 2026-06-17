export type InlineMark = 'bold' | 'underline' | 'strikethrough'

const MARKERS: Record<InlineMark, { open: string; close: string }> = {
  bold: { open: '**', close: '**' },
  underline: { open: '__', close: '__' },
  strikethrough: { open: '~~', close: '~~' },
}

export function wrapSelectionWithMark(
  text: string,
  start: number,
  end: number,
  mark: InlineMark,
): { text: string; newStart: number; newEnd: number } {
  const { open, close } = MARKERS[mark]
  const selected = text.slice(start, end)

  if (
    selected.startsWith(open) &&
    selected.endsWith(close) &&
    selected.length >= open.length + close.length
  ) {
    const inner = selected.slice(open.length, -close.length)
    return {
      text: text.slice(0, start) + inner + text.slice(end),
      newStart: start,
      newEnd: start + inner.length,
    }
  }

  if (
    start >= open.length &&
    end + close.length <= text.length &&
    text.slice(start - open.length, start) === open &&
    text.slice(end, end + close.length) === close
  ) {
    return {
      text: text.slice(0, start - open.length) + selected + text.slice(end + close.length),
      newStart: start - open.length,
      newEnd: end - open.length,
    }
  }

  const wrapped = open + selected + close
  return {
    text: text.slice(0, start) + wrapped + text.slice(end),
    newStart: start,
    newEnd: start + wrapped.length,
  }
}
