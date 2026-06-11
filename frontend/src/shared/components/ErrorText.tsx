interface ErrorTextProps {
  error?: string | null
  className?: string
}

export function ErrorText({ error, className = '' }: ErrorTextProps) {
  if (!error) return null

  return (
    <p className={`text-red-500 text-sm mt-1 ${className}`}>
      {error}
    </p>
  )
}
