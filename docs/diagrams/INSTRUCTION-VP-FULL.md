# Инструкция: полная диаграмма компонентов в Visual Paradigm

Соответствует файлу `component-diagram-business.puml`.

---

## Состав

### Пакет `user-service` (2 компонента, 3 интерфейса)

| Компонент | Класс |
|-----------|--------|
| Управление пользователями | `UserService` |
| Социальный граф | `SocialService` |

| Интерфейс | Реализация |
|-----------|------------|
| `IUserProfiles` | Управление пользователями |
| `IFollows` | Социальный граф |
| `IUserEvents` | Оба (события outbox) |

### Пакет `content-service` (11 компонентов)

| Компонент | Класс |
|-----------|--------|
| Управление публикациями | `PostService` |
| Комментарии | `CommentService` |
| Лента | `FeedService` |
| Поиск | `SearchService` |
| Голосование | `PostUpvoteService` |
| Теги | `TagService` |
| Модерация контента | `AdminPostService`, `AdminCommentService` |
| Обогащение профилями | `ProfileCacheService`, `UserServiceClient` |
| Кэш подписок | `FollowsCacheService` |
| Блоклист модерации | `ModerationBlocklistService` |
| Счётчики активности | `CounterService` |
| Обработчик событий пользователя | `UserEventsConsumer` |

### Зависимости (кратко)

- Публикации, комментарии, лента, поиск → обогащение профилями, блоклист, счётчики (по необходимости).
- Лента → кэш подписок.
- Голосование → счётчики.
- Модерация контента → блоклист.
- Обработчик событий → кэш подписок, блоклист.
- Обогащение профилями → `IUserProfiles`.
- Кэш подписок → `IFollows`.
- Обработчик событий → `IUserEvents`.

---

## Пошагово в Visual Paradigm

### 1–2. Проект, Component Diagram, два пакета

Как в упрощённой инструкции: `user-service`, `content-service`.

### 3. user-service

- 2 компонента, 3 interface (lollipop) на границе пакета.
- Realization: User → IUserProfiles, IUserEvents; Social → IFollows, IUserEvents.

### 4. content-service

Разместите блоками:

- **Верх:** лента, поиск, теги  
- **Центр:** публикации, комментарии, голосование  
- **Право:** обогащение, кэш подписок, блоклист, счётчики, обработчик событий  
- **Низ:** модерация контента  

### 5. Внутренние Dependency

См. таблицу в `component-diagram-business.puml` или legend в файле.

### 6. Межсервисные связи

- Обогащение профилями → `IUserProfiles`
- Кэш подписок → `IFollows`
- Обработчик событий → `IUserEvents`

### 7. Экспорт

Layout → Export PNG. Подпись: *Диаграмма компонентов бизнес-логики (детализированная).*

---

Подробная схема связей — в PlantUML-файле. Для диплома чаще достаточно **упрощённой** версии (`INSTRUCTION-VP-SIMPLE.md`).
