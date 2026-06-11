import type { EditorContent, EditorBlock } from '../../feed/types'

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
  if (b.type === 'header') return '#'.repeat(b.data.level || 2) + ' ' + b.data.text
  if (b.type === 'paragraph') return b.data.text
  if (b.type === 'list') {
    if (b.data.style === 'ordered') {
      return b.data.items.map((it, idx) => `${idx + 1}. ${it}`).join('\n')
    } else {
      return b.data.items.map(it => `- ${it}`).join('\n')
    }
  }
  if (b.type === 'quote') {
    return b.data.text.split('\n').map(line => `> ${line}`).join('\n')
  }
  return ''
}

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
