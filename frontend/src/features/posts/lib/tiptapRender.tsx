import React from 'react'

interface TiptapNode {
  type: string
  attrs?: Record<string, any>
  content?: TiptapNode[]
  marks?: TiptapMark[]
  text?: string
}

interface TiptapMark {
  type: string
  attrs?: Record<string, any>
}

function isSafeHref(href: unknown): boolean {
  if (typeof href !== 'string') return false
  return href.startsWith('http://') || href.startsWith('https://')
}

function renderMarks(text: string, marks: TiptapMark[], key: string): React.ReactNode {
  let result: React.ReactNode = text

  for (const mark of marks) {
    switch (mark.type) {
      case 'bold':
        result = React.createElement('strong', { key: `${key}-b` }, result)
        break
      case 'italic':
        result = React.createElement('em', { key: `${key}-i` }, result)
        break
      case 'underline':
        result = React.createElement('u', { key: `${key}-u` }, result)
        break
      case 'strike':
        result = React.createElement('s', { key: `${key}-s` }, result)
        break
      case 'code':
        result = React.createElement('code', {
          key: `${key}-c`,
          className: 'bg-gray-100 text-pink-600 px-1.5 py-0.5 rounded text-sm font-mono',
        }, result)
        break
      case 'link': {
        const href = mark.attrs?.href
        if (isSafeHref(href)) {
          result = React.createElement('a', {
            key: `${key}-a`,
            href,
            target: '_blank',
            rel: 'noopener noreferrer',
            className: 'text-blue-600 underline hover:text-blue-800',
          }, result)
        }
        break
      }
      // Unknown marks: ignored (result stays as-is)
    }
  }

  return result
}

function renderNode(node: TiptapNode, key: string): React.ReactNode {
  if (node.type === 'text') {
    if (node.marks && node.marks.length > 0) {
      return renderMarks(node.text || '', node.marks, key)
    }
    return React.createElement(React.Fragment, { key }, node.text || '')
  }

  if (node.type === 'hardBreak') {
    return React.createElement('br', { key })
  }

  const children = node.content?.map((child, i) => renderNode(child, `${key}-${i}`)) ?? []

  switch (node.type) {
    case 'doc':
      return React.createElement(React.Fragment, { key }, ...children)

    case 'paragraph':
      return React.createElement('p', { key, className: 'mb-3 leading-relaxed' }, ...children)

    case 'heading': {
      const level = Math.min(Math.max(node.attrs?.level ?? 2, 1), 6)
      const classes =
        level === 1 ? 'text-2xl font-bold mt-6 mb-3' :
        level === 2 ? 'text-xl font-semibold mt-5 mb-2' :
        'text-lg font-semibold mt-4 mb-2'
      const Tag = `h${level}` as keyof React.JSX.IntrinsicElements
      return React.createElement(Tag, { key, className: classes }, ...children)
    }

    case 'bulletList':
      return React.createElement('ul', { key, className: 'pl-6 mb-3 space-y-1 list-disc' }, ...children)

    case 'orderedList':
      return React.createElement('ol', { key, className: 'pl-6 mb-3 space-y-1 list-decimal' }, ...children)

    case 'listItem':
      return React.createElement('li', { key }, ...children)

    case 'blockquote':
      return React.createElement('blockquote', {
        key,
        className: 'border-l-4 pl-4 italic text-gray-600 mb-3',
      }, ...children)

    case 'codeBlock':
      return React.createElement('pre', {
        key,
        className: 'bg-gray-100 rounded p-3 mb-3 overflow-x-auto font-mono text-sm',
      }, React.createElement('code', null, ...children))

    default:
      // Unknown node: render children (graceful degradation)
      return children.length > 0
        ? React.createElement(React.Fragment, { key }, ...children)
        : null
  }
}

/**
 * Render a TipTap JSON document to React elements.
 * Never uses dangerouslySetInnerHTML — XSS-safe by construction.
 */
export function renderTiptapDoc(doc: any): React.ReactNode {
  if (!doc || typeof doc !== 'object' || doc.type !== 'doc') return null
  return renderNode(doc as TiptapNode, 'tt')
}
