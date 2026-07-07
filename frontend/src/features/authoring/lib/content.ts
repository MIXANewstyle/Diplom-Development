import type { EditorBlock } from '../../feed/types'

/**
 * Convert plain-text (legacy textarea content) into Editor.js-style block JSON string.
 * Still used by PostView as a last-resort fallback for posts stored as plain strings.
 */
export function textareaToEditorContentStr(text: string): string {
  if (!text) return ''
  const lines = text.split(/\r?\n/)
  const blocks: EditorBlock[] = []
  
  let currentBlockType: 'paragraph' | 'list' | 'quote' | null = null
  let currentLines: string[] = []
  let listStyle: 'ordered' | 'unordered' = 'unordered'

  const flushBlock = () => {
    if (currentLines.length === 0) return
    
    if (currentBlockType === 'list') {
      blocks.push({ type: 'list', data: { style: listStyle, items: [...currentLines] } })
    } else if (currentBlockType === 'quote') {
      blocks.push({ type: 'quote', data: { text: currentLines.join('\n') } })
    } else if (currentBlockType === 'paragraph') {
      blocks.push({ type: 'paragraph', data: { text: currentLines.join('\n') } })
    }
    currentLines = []
    currentBlockType = null
  }

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    const trimmed = line.trim()

    if (trimmed === '') {
      flushBlock()
      continue
    }

    // Headers
    const headerMatch = line.match(/^(#{1,3})\s+(.*)$/)
    if (headerMatch) {
      flushBlock()
      blocks.push({ type: 'header', data: { level: headerMatch[1].length, text: headerMatch[2] } })
      continue
    }

    // Unordered List
    if (trimmed.startsWith('- ') || trimmed.startsWith('* ')) {
      if (currentBlockType !== 'list' || listStyle !== 'unordered') {
        flushBlock()
        currentBlockType = 'list'
        listStyle = 'unordered'
      }
      currentLines.push(trimmed.substring(2))
      continue
    }

    // Ordered List
    const orderedMatch = trimmed.match(/^\d+\.\s+(.*)$/)
    if (orderedMatch) {
      if (currentBlockType !== 'list' || listStyle !== 'ordered') {
        flushBlock()
        currentBlockType = 'list'
        listStyle = 'ordered'
      }
      currentLines.push(orderedMatch[1])
      continue
    }

    // Quote
    if (trimmed.startsWith('> ')) {
      if (currentBlockType !== 'quote') {
        flushBlock()
        currentBlockType = 'quote'
      }
      currentLines.push(trimmed.substring(2))
      continue
    }

    // Paragraph
    if (currentBlockType !== 'paragraph' && currentBlockType !== null) {
      flushBlock()
    }
    currentBlockType = 'paragraph'
    currentLines.push(line)
  }
  
  flushBlock()

  return JSON.stringify({
    time: Date.now(),
    blocks,
    version: '2.28.0'
  })
}
