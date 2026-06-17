import React from 'react'
import type { EditorBlock } from '../../feed/types'

const INLINE_PATTERN = /(\*\*.*?\*\*|__.*?__|~~.*?~~)/g

export function parseInline(text: string): React.ReactNode[] {
  const parts = text.split(INLINE_PATTERN)
  return parts.map((part, i) => {
    if (part.startsWith('**') && part.endsWith('**') && part.length >= 4) {
      return <strong key={i}>{parseInline(part.slice(2, -2))}</strong>
    }
    if (part.startsWith('__') && part.endsWith('__') && part.length >= 4) {
      return <u key={i}>{parseInline(part.slice(2, -2))}</u>
    }
    if (part.startsWith('~~') && part.endsWith('~~') && part.length >= 4) {
      return <s key={i}>{parseInline(part.slice(2, -2))}</s>
    }
    return <React.Fragment key={i}>{part}</React.Fragment>
  })
}

export function renderBlock(block: EditorBlock, index: number) {
  switch (block.type) {
    case 'paragraph':
      return (
        <p key={index} className="mb-3 leading-relaxed">
          {parseInline(block.data.text)}
        </p>
      )
    case 'header': {
      const level = block.data.level || 2
      const classes =
        level === 1 ? 'text-2xl font-bold mt-6 mb-3' :
        level === 2 ? 'text-xl font-semibold mt-5 mb-2' :
        'text-lg font-semibold mt-4 mb-2'
      const Tag = `h${Math.min(Math.max(level, 1), 6)}` as keyof JSX.IntrinsicElements
      return (
        <Tag key={index} className={classes}>
          {parseInline(block.data.text)}
        </Tag>
      )
    }
    case 'list': {
      const ListTag = block.data.style === 'ordered' ? 'ol' : 'ul'
      return (
        <ListTag key={index} className={`pl-6 mb-3 space-y-1 ${block.data.style === 'ordered' ? 'list-decimal' : 'list-disc'}`}>
          {block.data.items.map((item, i) => (
            <li key={i}>{parseInline(item)}</li>
          ))}
        </ListTag>
      )
    }
    case 'quote':
      return (
        <blockquote key={index} className="border-l-4 pl-4 italic text-gray-600 mb-3 whitespace-pre-wrap">
          {parseInline(block.data.text)}
        </blockquote>
      )
    default:
      return null
  }
}
