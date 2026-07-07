import { useRef, useState, useCallback, useEffect } from 'react'
import { resolveMediaUrl } from '../../../shared/lib/mediaUrl'

interface Props {
  images: string[]
  alt?: string
}

export function ImageCarousel({ images, alt = '' }: Props) {
  const [currentIndex, setCurrentIndex] = useState(0)
  const scrollRef = useRef<HTMLDivElement>(null)
  const count = images.length

  const scrollToIndex = useCallback(
    (index: number) => {
      const el = scrollRef.current
      if (!el) return
      const child = el.children[index] as HTMLElement | undefined
      if (child) {
        child.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'start' })
      }
    },
    [],
  )

  const goTo = useCallback(
    (index: number) => {
      const clamped = Math.max(0, Math.min(index, count - 1))
      setCurrentIndex(clamped)
      scrollToIndex(clamped)
    },
    [count, scrollToIndex],
  )

  const prev = () => goTo(currentIndex - 1)
  const next = () => goTo(currentIndex + 1)

  // Sync index on scroll-snap settle
  useEffect(() => {
    const el = scrollRef.current
    if (!el) return

    let timer: ReturnType<typeof setTimeout>
    const handleScroll = () => {
      clearTimeout(timer)
      timer = setTimeout(() => {
        const scrollLeft = el.scrollLeft
        const width = el.clientWidth
        if (width === 0) return
        const idx = Math.round(scrollLeft / width)
        setCurrentIndex(Math.max(0, Math.min(idx, count - 1)))
      }, 60)
    }

    el.addEventListener('scroll', handleScroll, { passive: true })
    return () => {
      clearTimeout(timer)
      el.removeEventListener('scroll', handleScroll)
    }
  }, [count])

  if (count === 0) return null

  return (
    <div className="relative w-full select-none">
      {/* Scroll-snap container */}
      <div
        ref={scrollRef}
        className="flex overflow-x-auto snap-x snap-mandatory scrollbar-hide"
        style={{ scrollbarWidth: 'none', msOverflowStyle: 'none', WebkitOverflowScrolling: 'touch' }}
      >
        {images.map((src, i) => (
          <div key={i} className="flex-shrink-0 w-full snap-start flex items-center justify-center bg-gray-50">
            <img
              src={resolveMediaUrl(src) || ''}
              alt={alt ? `${alt} — ${i + 1}` : `Изображение ${i + 1}`}
              className="w-full h-auto max-h-[70vh] object-contain"
              draggable={false}
            />
          </div>
        ))}
      </div>

      {/* Prev / Next arrows (only when > 1 image) */}
      {count > 1 && (
        <>
          <button
            type="button"
            onClick={prev}
            disabled={currentIndex === 0}
            aria-label="Предыдущее"
            className="absolute top-1/2 left-2 -translate-y-1/2 w-9 h-9 flex items-center justify-center
                       rounded-full bg-white/80 backdrop-blur text-gray-800 shadow
                       hover:bg-white disabled:opacity-30 disabled:cursor-default transition-opacity"
          >
            ‹
          </button>
          <button
            type="button"
            onClick={next}
            disabled={currentIndex === count - 1}
            aria-label="Следующее"
            className="absolute top-1/2 right-2 -translate-y-1/2 w-9 h-9 flex items-center justify-center
                       rounded-full bg-white/80 backdrop-blur text-gray-800 shadow
                       hover:bg-white disabled:opacity-30 disabled:cursor-default transition-opacity"
          >
            ›
          </button>
        </>
      )}

      {/* Dot indicators (only when > 1 image) */}
      {count > 1 && (
        <div className="flex justify-center gap-1.5 mt-2">
          {images.map((_, i) => (
            <button
              key={i}
              type="button"
              onClick={() => goTo(i)}
              aria-label={`Перейти к изображению ${i + 1}`}
              className={`w-2 h-2 rounded-full transition-colors ${
                i === currentIndex ? 'bg-blue-600' : 'bg-gray-300 hover:bg-gray-400'
              }`}
            />
          ))}
        </div>
      )}
    </div>
  )
}
