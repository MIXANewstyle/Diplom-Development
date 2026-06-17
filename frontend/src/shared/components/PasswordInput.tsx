import { useState, type ComponentPropsWithoutRef } from 'react'
import { Eye, EyeOff } from 'lucide-react'

type PasswordInputProps = Omit<ComponentPropsWithoutRef<'input'>, 'type'> & {
  value: string
  onChange: (e: React.ChangeEvent<HTMLInputElement>) => void
}

export function PasswordInput({
  value,
  onChange,
  id,
  name,
  required,
  placeholder,
  autoComplete,
  'aria-invalid': ariaInvalid,
  className = '',
  ...rest
}: PasswordInputProps) {
  const [visible, setVisible] = useState(false)

  return (
    <div className="relative">
      <input
        id={id}
        name={name}
        type={visible ? 'text' : 'password'}
        required={required}
        value={value}
        onChange={onChange}
        placeholder={placeholder}
        autoComplete={autoComplete}
        aria-invalid={ariaInvalid}
        className={`w-full border rounded p-2 pr-10 ${className}`}
        {...rest}
      />
      <button
        type="button"
        tabIndex={0}
        onClick={() => setVisible((v) => !v)}
        aria-label={visible ? 'Скрыть пароль' : 'Показать пароль'}
        className="absolute right-0 top-0 flex h-full min-w-10 items-center justify-center text-gray-500 hover:text-gray-700"
      >
        {visible ? <EyeOff size={20} aria-hidden /> : <Eye size={20} aria-hidden />}
      </button>
    </div>
  )
}
