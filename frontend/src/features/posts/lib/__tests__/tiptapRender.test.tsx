import { describe, it, expect } from 'vitest'
import { render } from '@testing-library/react'
import { renderTiptapDoc } from '../tiptapRender'
describe('renderTiptapDoc', () => {
  it('returns null for invalid docs', () => {
    expect(renderTiptapDoc(null)).toBeNull()
    expect(renderTiptapDoc('string')).toBeNull()
    expect(renderTiptapDoc({})).toBeNull()
    expect(renderTiptapDoc({ type: 'paragraph' })).toBeNull() // Not a doc
  })

  it('renders paragraph and text nodes', () => {
    const doc = {
      type: 'doc',
      content: [
        {
          type: 'paragraph',
          content: [{ type: 'text', text: 'Hello world' }]
        }
      ]
    }
    const { container } = render(<>{renderTiptapDoc(doc)}</>)
    expect(container.innerHTML).toBe('<p class="mb-3 leading-relaxed">Hello world</p>')
  })

  it('renders headings with correct levels', () => {
    const doc = {
      type: 'doc',
      content: [
        { type: 'heading', attrs: { level: 1 }, content: [{ type: 'text', text: 'H1' }] },
        { type: 'heading', attrs: { level: 2 }, content: [{ type: 'text', text: 'H2' }] }
      ]
    }
    const { container } = render(<>{renderTiptapDoc(doc)}</>)
    expect(container.querySelector('h1')).toHaveTextContent('H1')
    expect(container.querySelector('h2')).toHaveTextContent('H2')
  })

  it('renders bold, italic, underline, strike, code marks', () => {
    const doc = {
      type: 'doc',
      content: [
        {
          type: 'paragraph',
          content: [
            { type: 'text', text: 'B', marks: [{ type: 'bold' }] },
            { type: 'text', text: 'I', marks: [{ type: 'italic' }] },
            { type: 'text', text: 'U', marks: [{ type: 'underline' }] },
            { type: 'text', text: 'S', marks: [{ type: 'strike' }] },
            { type: 'text', text: 'C', marks: [{ type: 'code' }] }
          ]
        }
      ]
    }
    const { container } = render(<>{renderTiptapDoc(doc)}</>)
    expect(container.querySelector('strong')).toHaveTextContent('B')
    expect(container.querySelector('em')).toHaveTextContent('I')
    expect(container.querySelector('u')).toHaveTextContent('U')
    expect(container.querySelector('s')).toHaveTextContent('S')
    expect(container.querySelector('code')).toHaveTextContent('C')
  })

  it('renders nested marks properly', () => {
    const doc = {
      type: 'doc',
      content: [
        {
          type: 'paragraph',
          content: [
            { type: 'text', text: 'BoldItalic', marks: [{ type: 'bold' }, { type: 'italic' }] }
          ]
        }
      ]
    }
    const { container } = render(<>{renderTiptapDoc(doc)}</>)
    const em = container.querySelector('em')
    expect(em?.textContent).toBe('BoldItalic')
    // One wraps the other
    expect(container.innerHTML).toContain('<strong><em>BoldItalic</em></strong>')
  })

  it('renders secure links and drops javascript: links', () => {
    const doc = {
      type: 'doc',
      content: [
        {
          type: 'paragraph',
          content: [
            { type: 'text', text: 'Safe', marks: [{ type: 'link', attrs: { href: 'https://example.com' } }] },
            { type: 'text', text: 'Evil', marks: [{ type: 'link', attrs: { href: 'javascript:alert(1)' } }] }
          ]
        }
      ]
    }
    const { container } = render(<>{renderTiptapDoc(doc)}</>)
    const links = container.querySelectorAll('a')
    expect(links.length).toBe(1)
    expect(links[0].getAttribute('href')).toBe('https://example.com')
    expect(links[0].textContent).toBe('Safe')

    // Evil is rendered as plain text, no <a> tag
    expect(container.textContent).toContain('SafeEvil')
  })

  it('handles unknown nodes and marks gracefully', () => {
    const doc = {
      type: 'doc',
      content: [
        {
          type: 'unknownNode',
          content: [
            {
              type: 'text',
              text: 'Fallback',
              marks: [{ type: 'unknownMark' }]
            }
          ]
        }
      ]
    }
    const { container } = render(<>{renderTiptapDoc(doc)}</>)
    // Should render the child text even if node/mark is unknown
    expect(container.textContent).toBe('Fallback')
    expect(container.innerHTML).toBe('Fallback') // No wrapper element
  })
})
