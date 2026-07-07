import type { EditorContent, EditorBlock } from '../../feed/types'

/**
 * Detects whether content is a TipTap JSON document.
 */
export function isTiptapDoc(obj: any): boolean {
  return obj && typeof obj === 'object' && obj.type === 'doc' && Array.isArray(obj.content)
}

/**
 * Convert legacy Editor.js blocks (or string containing them) to TipTap JSON doc.
 * Inline markers (**bold**, __underline__, ~~strike~~) are NOT converted —
 * they are left as literal characters (accepted trade-off).
 */
export function legacyToTiptapDoc(content: EditorContent | string | null): object {
  const emptyDoc = { type: 'doc', content: [{ type: 'paragraph' }] }
  if (!content) return emptyDoc

  let blocks: EditorBlock[] = []

  if (typeof content === 'string') {
    try {
      const parsed = JSON.parse(content)
      if (parsed && parsed.type === 'doc') return parsed // already TipTap
      if (parsed?.blocks) blocks = parsed.blocks
      else return emptyDoc
    } catch {
      // Plain text: wrap each line in a paragraph
      const lines = content.split(/\r?\n/)
      return {
        type: 'doc',
        content: lines.map(line => ({
          type: 'paragraph',
          content: line ? [{ type: 'text', text: line }] : [],
        })),
      }
    }
  } else {
    if (isTiptapDoc(content)) return content
    if (content.blocks) blocks = content.blocks
    else return emptyDoc
  }

  if (blocks.length === 0) return emptyDoc

  return {
    type: 'doc',
    content: blocks.map(convertBlock),
  }
}

function textNode(text: string): object[] {
  return text ? [{ type: 'text', text }] : []
}

function convertBlock(block: EditorBlock): object {
  switch (block.type) {
    case 'paragraph':
      return { type: 'paragraph', content: textNode(block.data.text) }

    case 'header':
      return {
        type: 'heading',
        attrs: { level: Math.min(Math.max(block.data.level || 2, 1), 3) },
        content: textNode(block.data.text),
      }

    case 'list': {
      const listType = block.data.style === 'ordered' ? 'orderedList' : 'bulletList'
      return {
        type: listType,
        content: block.data.items.map(item => ({
          type: 'listItem',
          content: [{ type: 'paragraph', content: textNode(item) }],
        })),
      }
    }

    case 'quote':
      return {
        type: 'blockquote',
        content: block.data.text.split('\n').map(line => ({
          type: 'paragraph',
          content: textNode(line),
        })),
      }

    default:
      return { type: 'paragraph', content: [] }
  }
}
