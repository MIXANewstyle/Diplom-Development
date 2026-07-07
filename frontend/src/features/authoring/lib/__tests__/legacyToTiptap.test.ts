import { describe, it, expect } from 'vitest'
import { legacyToTiptapDoc } from '../legacyToTiptap'
import type { EditorBlock } from '../../../feed/types'

describe('legacyToTiptapDoc', () => {
  it('handles null/empty input by returning an empty doc', () => {
    const emptyDoc = { type: 'doc', content: [{ type: 'paragraph' }] }
    expect(legacyToTiptapDoc(null)).toEqual(emptyDoc)
    expect(legacyToTiptapDoc('')).toEqual(emptyDoc)
    expect(legacyToTiptapDoc(undefined as any)).toEqual(emptyDoc)
  })

  it('converts a plain string into paragraphs (best effort fallback)', () => {
    const result = legacyToTiptapDoc('Hello\n\nWorld')
    expect(result).toEqual({
      type: 'doc',
      content: [
        { type: 'paragraph', content: [{ type: 'text', text: 'Hello' }] },
        { type: 'paragraph', content: [] }, // Empty line
        { type: 'paragraph', content: [{ type: 'text', text: 'World' }] }
      ]
    })
  })

  it('passes through an already valid TipTap doc', () => {
    const doc = {
      type: 'doc',
      content: [{ type: 'paragraph', content: [{ type: 'text', text: 'TipTap' }] }]
    }
    // as string
    expect(legacyToTiptapDoc(JSON.stringify(doc))).toEqual(doc)
    // as object
    expect(legacyToTiptapDoc(doc as any)).toEqual(doc)
  })

  it('converts Editor.js blocks object', () => {
    const legacy = {
      time: 123,
      version: '2.28.0',
      blocks: [
        { type: 'header', data: { level: 2, text: 'Title' } } as EditorBlock,
        { type: 'paragraph', data: { text: 'Text with **bold**' } } as EditorBlock,
        { type: 'list', data: { style: 'unordered', items: ['A', 'B'] } } as EditorBlock,
        { type: 'quote', data: { text: 'Quote line 1\nQuote line 2' } } as EditorBlock
      ]
    }

    const expected = {
      type: 'doc',
      content: [
        { type: 'heading', attrs: { level: 2 }, content: [{ type: 'text', text: 'Title' }] },
        { type: 'paragraph', content: [{ type: 'text', text: 'Text with **bold**' }] },
        {
          type: 'bulletList',
          content: [
            { type: 'listItem', content: [{ type: 'paragraph', content: [{ type: 'text', text: 'A' }] }] },
            { type: 'listItem', content: [{ type: 'paragraph', content: [{ type: 'text', text: 'B' }] }] }
          ]
        },
        {
          type: 'blockquote',
          content: [
            { type: 'paragraph', content: [{ type: 'text', text: 'Quote line 1' }] },
            { type: 'paragraph', content: [{ type: 'text', text: 'Quote line 2' }] }
          ]
        }
      ]
    }

    // as object
    expect(legacyToTiptapDoc(legacy)).toEqual(expected)
    // as string
    expect(legacyToTiptapDoc(JSON.stringify(legacy))).toEqual(expected)
  })

  it('handles invalid JSON string by falling back to plain text', () => {
    const invalidJson = '{"blocks":['
    const result = legacyToTiptapDoc(invalidJson)
    expect(result).toEqual({
      type: 'doc',
      content: [
        { type: 'paragraph', content: [{ type: 'text', text: '{"blocks":[' }] }
      ]
    })
  })
})
