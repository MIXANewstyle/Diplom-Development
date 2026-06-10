import { EditorContent, EditorBlock } from '../../feed/types'

export function editorContentToTextarea(content: EditorContent | string | null): string {
  if (!content) return ''
  if (typeof content === 'string') {
    try {
      const parsed = JSON.parse(content) as EditorContent
      if (parsed.blocks) return parsed.blocks.map(blockToText).join('\n\n')
    } catch {
      return content // Fallback to raw string if it's not valid JSON
    }
  } else if (content.blocks) {
    return content.blocks.map(blockToText).join('\n\n')
  }
  return ''
}

function blockToText(b: EditorBlock): string {
  if (b.type === 'header') return '# ' + b.data.text
  if (b.type === 'paragraph') return b.data.text
  if (b.type === 'list') {
    const prefix = b.data.style === 'ordered' ? '1. ' : '- '
    return b.data.items.map((it) => prefix + it).join('\n')
  }
  return ''
}

export function textareaToEditorContentStr(text: string): string {
  if (!text) return ''
  const lines = text.split(/\n\n+/)
  const blocks: EditorBlock[] = lines.filter(l => l.trim()).map(line => {
    line = line.trim()
    if (line.startsWith('# ')) {
      return { type: 'header', data: { text: line.substring(2), level: 2 } }
    }
    // Simplistic handling, treats everything else as paragraphs
    return { type: 'paragraph', data: { text: line } }
  })

  return JSON.stringify({
    time: Date.now(),
    blocks,
    version: '2.28.0'
  })
}
