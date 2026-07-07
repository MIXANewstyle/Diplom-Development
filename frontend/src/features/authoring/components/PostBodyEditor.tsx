import { useEditor, EditorContent } from '@tiptap/react'
import { BubbleMenu } from '@tiptap/react/menus'
import StarterKit from '@tiptap/starter-kit'
import Placeholder from '@tiptap/extension-placeholder'
import { useEffect, useState, useCallback } from 'react'
import {
  Bold, Italic, Underline, Strikethrough, Code, Link as LinkIcon, X,
} from 'lucide-react'
import { legacyToTiptapDoc } from '../lib/legacyToTiptap'
import type { EditorContent as FeedEditorContent } from '../../feed/types'

interface Props {
  initialContent: string | FeedEditorContent | null
  onChange: (json: object) => void
}

export function PostBodyEditor({ initialContent, onChange }: Props) {
  const [linkInputVisible, setLinkInputVisible] = useState(false)
  const [linkUrl, setLinkUrl] = useState('')

  const editor = useEditor({
    extensions: [
      StarterKit.configure({
        heading: { levels: [1, 2, 3] },
        // Link is bundled in v3 StarterKit — configure it here
        link: {
          openOnClick: false,
          autolink: true,
          protocols: ['http', 'https'],
          HTMLAttributes: {
            class: 'text-blue-600 underline hover:text-blue-800',
            rel: 'noopener noreferrer',
            target: '_blank',
          },
        },
      }),
      Placeholder.configure({
        placeholder: 'Текст поста… Выдели текст, чтобы отформатировать',
      }),
    ],
    content: legacyToTiptapDoc(initialContent),
    onUpdate: ({ editor: e }) => {
      onChange(e.getJSON())
    },
    editorProps: {
      attributes: {
        class: 'prose-editor focus:outline-none min-h-[16rem] p-3',
      },
    },
  })

  // Sync initial content on mount if it changes (e.g. loading an existing post)
  const initialRef = useCallback(() => initialContent, [initialContent])
  useEffect(() => {
    if (editor && initialRef()) {
      const doc = legacyToTiptapDoc(initialRef())
      // Only reset if the editor is truly empty and content is not
      if (editor.isEmpty && doc && (doc as any).content?.length > 0) {
        editor.commands.setContent(doc)
      }
    }
  }, [editor, initialRef])

  if (!editor) return null

  const toggleLink = () => {
    if (editor.isActive('link')) {
      editor.chain().focus().unsetLink().run()
      setLinkInputVisible(false)
      return
    }
    setLinkUrl('')
    setLinkInputVisible(true)
  }

  const applyLink = () => {
    const trimmed = linkUrl.trim()
    if (trimmed && (trimmed.startsWith('http://') || trimmed.startsWith('https://'))) {
      editor.chain().focus().setLink({ href: trimmed }).run()
    } else if (!trimmed) {
      editor.chain().focus().unsetLink().run()
    }
    setLinkInputVisible(false)
    setLinkUrl('')
  }

  const btnClass = (active: boolean) =>
    `p-1.5 rounded transition-colors ${
      active
        ? 'bg-blue-600 text-white'
        : 'text-gray-700 hover:bg-gray-100'
    }`

  return (
    <div className="border border-gray-300 rounded focus-within:ring-2 focus-within:ring-blue-500 focus-within:border-blue-500">
      {editor && (
        <BubbleMenu
          editor={editor}
        >
          <div className="flex items-center gap-0.5 bg-white border border-gray-300 rounded-lg shadow-lg p-1">
            <button
              type="button"
              title="Жирный (Ctrl+B)"
              onMouseDown={e => e.preventDefault()}
              onClick={() => editor.chain().focus().toggleBold().run()}
              className={btnClass(editor.isActive('bold'))}
            >
              <Bold className="w-4 h-4" />
            </button>
            <button
              type="button"
              title="Курсив (Ctrl+I)"
              onMouseDown={e => e.preventDefault()}
              onClick={() => editor.chain().focus().toggleItalic().run()}
              className={btnClass(editor.isActive('italic'))}
            >
              <Italic className="w-4 h-4" />
            </button>
            <button
              type="button"
              title="Подчёркнутый (Ctrl+U)"
              onMouseDown={e => e.preventDefault()}
              onClick={() => editor.chain().focus().toggleUnderline().run()}
              className={btnClass(editor.isActive('underline'))}
            >
              <Underline className="w-4 h-4" />
            </button>
            <button
              type="button"
              title="Зачёркнутый (Ctrl+Shift+S)"
              onMouseDown={e => e.preventDefault()}
              onClick={() => editor.chain().focus().toggleStrike().run()}
              className={btnClass(editor.isActive('strike'))}
            >
              <Strikethrough className="w-4 h-4" />
            </button>
            <button
              type="button"
              title="Код"
              onMouseDown={e => e.preventDefault()}
              onClick={() => editor.chain().focus().toggleCode().run()}
              className={btnClass(editor.isActive('code'))}
            >
              <Code className="w-4 h-4" />
            </button>
            <div className="w-px h-5 bg-gray-300 mx-0.5" />
            <button
              type="button"
              title="Ссылка"
              onMouseDown={e => e.preventDefault()}
              onClick={toggleLink}
              className={btnClass(editor.isActive('link'))}
            >
              <LinkIcon className="w-4 h-4" />
            </button>

            {linkInputVisible && (
              <>
                <input
                  type="url"
                  value={linkUrl}
                  onChange={e => setLinkUrl(e.target.value)}
                  onKeyDown={e => {
                    if (e.key === 'Enter') { e.preventDefault(); applyLink() }
                    if (e.key === 'Escape') { setLinkInputVisible(false); editor.chain().focus().run() }
                  }}
                  placeholder="https://…"
                  autoFocus
                  className="ml-1 px-2 py-1 text-sm border border-gray-300 rounded w-48 outline-none focus:border-blue-500"
                />
                <button
                  type="button"
                  onMouseDown={e => e.preventDefault()}
                  onClick={() => { setLinkInputVisible(false); editor.chain().focus().run() }}
                  className="p-1 text-gray-400 hover:text-gray-600"
                >
                  <X className="w-3.5 h-3.5" />
                </button>
              </>
            )}
          </div>
        </BubbleMenu>
      )}

      <EditorContent editor={editor} />
    </div>
  )
}
