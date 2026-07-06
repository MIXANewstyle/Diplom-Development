import { describe, it, expect } from 'vitest'
import { parseInline } from '../markdown'
import React from 'react'

// Helper to extract text and tags for easier asserting
function renderNodesToString(nodes: React.ReactNode[]): string {
  return nodes.map(node => {
    if (!React.isValidElement(node)) return String(node)
    
    const props = node.props as { children: React.ReactNode }
    if (node.type === React.Fragment) {
      return String(props.children)
    }
    return `<${node.type}>${props.children}</${node.type}>`
  }).join('')
}

describe('parseInline', () => {
  it('handles empty string', () => {
    expect(renderNodesToString(parseInline(''))).toBe('')
  })

  it('parses bold alone', () => {
    expect(renderNodesToString(parseInline('hello **world**'))).toBe('hello <strong>world</strong>')
  })

  it('parses underline alone', () => {
    expect(renderNodesToString(parseInline('hello __world__'))).toBe('hello <u>world</u>')
  })

  it('parses strikethrough alone', () => {
    expect(renderNodesToString(parseInline('hello ~~world~~'))).toBe('hello <s>world</s>')
  })

  it('parses all three in one line', () => {
    expect(renderNodesToString(parseInline('**a** __b__ ~~c~~'))).toBe('<strong>a</strong> <u>b</u> <s>c</s>')
  })

  it('leaves unmatched markers as literal', () => {
    expect(renderNodesToString(parseInline('**bold __under~~'))).toBe('**bold __under~~')
    expect(renderNodesToString(parseInline('__lonely'))).toBe('__lonely')
  })

  it('does not nest (MVP behavior)', () => {
    // According to MVP, inner markers inside bold are rendered literal
    expect(renderNodesToString(parseInline('**a __b__ a**'))).toBe('<strong>a __b__ a</strong>')
  })
})
