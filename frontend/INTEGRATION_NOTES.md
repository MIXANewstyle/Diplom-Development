# Integration Notes

## A. Capture the real response shapes

Based on inspection of the actual backend service code (Spring REST controllers and DTOs), here are the exact JSON shapes returned by the live backend.

- `POST /api/v1/users/register`
  ```json
  {
    "id": "uuid",
    "email": "string",
    "username": "string"
  }
  ```
  *(Matches frontend `RegisterResponse`)*

- `POST /api/v1/users/login`
  ```json
  {
    "token": "string"
  }
  ```
  *(Matches frontend `LoginResponse`)*

- `GET /api/v1/feed?sort=newest`
  ```json
  {
    "items": [
      {
        "id": "uuid",
        "authorId": "uuid",
        "authorUsername": "string",
        "authorAvatarUrl": "string",
        "title": "string",
        "content": "string", 
        "coverImageUrl": "string",
        "status": "string",
        "publishedAt": "string",
        "updatedAt": "string",
        "viewsCount": 0,
        "upvotesCount": 0,
        "commentsCount": 0,
        "tags": [
          {
            "id": "uuid",
            "name": "string"
          }
        ],
        "keywords": ["string"],
        "version": 0
      }
    ],
    "nextCursor": "string"
  }
  ```
  *(Matches frontend `FeedResponse`. Note: `content` is an escaped JSON string containing Editor.js blocks because the database uses `JSONB` but the DTO maps it to `String`.)*

- `GET /api/v1/tags?page=0&size=50`
  ```json
  {
    "content": [
      {
        "id": "uuid",
        "name": "string"
      }
    ],
    "pageable": { ... },
    "totalPages": 0,
    "totalElements": 0,
    "last": true,
    "first": true,
    "size": 50,
    "number": 0,
    "numberOfElements": 0,
    "empty": false
  }
  ```
  *(Matches frontend `Page<Tag>` shape exactly)*

- `GET /api/v1/posts/{id}`
  - Returns the single post object exactly as shaped in the `items` array of the `/feed` response.
  - **Critically:** The `content` field is a plain string. However, since the database `content` column is `JSONB`, this string contains stringified JSON of the Editor.js blocks (e.g., `"{\"blocks\": [{\"type\": \"paragraph\", ...}]}"`).

- `GET /api/v1/posts/{id}/comments` and `GET /api/v1/comments/{id}/replies`
  ```json
  {
    "items": [
      {
        "id": "uuid",
        "postId": "uuid",
        "authorId": "uuid",
        "authorUsername": "string",
        "authorAvatarUrl": "string",
        "parentId": "uuid",
        "content": "string",
        "deleted": false,
        "createdAt": "string",
        "updatedAt": "string",
        "repliesCount": 0
      }
    ],
    "nextCursor": "string"
  }
  ```
  *(Matches frontend `CommentsResponse` exactly)*

- `POST /api/v1/posts/{id}/upvote`
  ```json
  {
    "upvoted": true,
    "upvotesCount": 1
  }
  ```
  *(Matches frontend `UpvoteResponse` exactly)*

## Backend issues to confirm with the team
- **Content Serialization Strategy:** The database schema correctly maps the `content` field to `JSONB`. However, the Java DTO `PostResponse` maps `content` to a `String` rather than a structured object or `JsonNode`. As a result, Jackson serializes the `content` into an escaped JSON string instead of an embedded JSON object. The frontend has been adapted to intercept and parse this string with `JSON.parse()`, but the backend team might want to consider adding `@JsonRawValue` or changing the DTO field type to `JsonNode` for a cleaner API contract.

## Chat Service Integration
- **LLM API Key Requirement**: The `POST /api/v1/rooms/{roomId}/turns` endpoint (which submits a user turn and synchronously returns both the user's turn and the assistant's reply for solo rooms) requires a valid LLM API key configured on the backend (`chat-service`). Specifically, the `LLM_API_KEY` environment variable must be set. If it is missing or invalid, the request will fail at the AI step (often with a 5xx or "LLM unavailable" error), although room creation and persistence will still work.
- **Turn Roles**: The actual `role` string values returned in `TurnResponse` are `"USER"` and `"ASSISTANT"`. There may also be a `"SYSTEM"` role used internally, but the UI currently distinguishes primarily between `"USER"` and everything else.
