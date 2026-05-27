# Frontend

Каркас фронтенда для платформы ментального здоровья.

## Стек

- Vite + React + TypeScript
- Tailwind CSS v3
- React Router
- TanStack Query v5
- axios
- Zustand

## Требования

- Node.js 18+
- API Gateway на `http://localhost:8080`

## Установка

```bash
npm install
```

## Запуск

```bash
npm run dev
```

Приложение будет доступно на [http://localhost:5173](http://localhost:5173).

## Сборка

```bash
npm run build
```

## Переменные окружения

Создайте `.env.local` (или используйте уже существующий):

```
VITE_API_BASE_URL=http://localhost:8080
```

Все запросы с фронта идут через API Gateway.

## Структура

```
src/
  app/          — роутер и провайдеры
  features/     — фичи (auth, feed, posts и т.д.)
  pages/        — страницы-заглушки
  shared/       — api, stores, types, ui, lib
```
