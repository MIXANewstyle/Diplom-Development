import axios from 'axios'
import type { ApiErrorResponse } from '../types/api'

const ERROR_MESSAGE_MAP: Record<string, string> = {
  'You already have an active subscription': 'У вас уже есть активная подписка',
  'Free trial has already been used': 'Пробный период уже использован',
  'Invalid credentials': 'Неверный email или пароль',
  'Email already taken': 'Этот email уже занят',
  'User not found': 'Пользователь не найден',
  'Already following': 'Вы уже подписаны на этого автора',
  'Not following': 'Вы не подписаны на этого автора',
  'Promo code is invalid or exhausted': 'Промокод недействителен или исчерпан',
  'Cannot upvote your own post': 'Нельзя оценивать собственный пост',
  'Cannot upvote a post that is not published': 'Оценивать можно только опубликованные посты',
  'LLM rate limit exceeded. Try again in a moment.': 'Лимит запросов к ИИ исчерпан — подождите минуту и попробуйте снова.',
}

export function getErrorMessage(error: unknown): string {
  if (!error) {
    return ''
  }

  if (axios.isAxiosError<ApiErrorResponse>(error)) {
    // If we have a network error or timeout
    if (!error.response) {
      return 'Не удалось связаться с сервером. Попробуйте ещё раз.'
    }

    const { data } = error.response
    if (!data) {
      return 'Произошла ошибка. Попробуйте ещё раз.'
    }

    // If there are validation field errors, return the first one or join them
    if (data.fieldErrors && Object.keys(data.fieldErrors).length > 0) {
      const firstKey = Object.keys(data.fieldErrors)[0]
      return data.fieldErrors[firstKey]
    }

    // Map known backend messages to Russian
    if (data.message) {
      const matchedKey = Object.keys(ERROR_MESSAGE_MAP).find(k => data.message.startsWith(k))
      if (matchedKey) {
        return ERROR_MESSAGE_MAP[matchedKey]
      }
      
      // If the message is reasonably short, surface it, otherwise generic fallback
      if (data.message.length < 100) {
        return `Произошла ошибка: ${data.message}`
      }
      return 'Произошла ошибка. Попробуйте ещё раз.'
    }
  }

  // Fallback for non-axios errors or unknown shapes
  if (error instanceof Error && error.message) {
    if (error.message.length < 100 && !error.message.includes('Network Error')) {
        return `Произошла ошибка: ${error.message}`
    }
  }
  
  return 'Произошла ошибка. Попробуйте ещё раз.'
}
