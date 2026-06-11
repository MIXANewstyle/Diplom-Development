# Integration Notes

## Post Search
- The backend search (`GET /api/v1/posts/search`) utilizes PostgreSQL full-text search (`tsvector`).
- **Important**: Search requires **published** posts to return results. If the catalog is empty or only contains drafts, search will return no results. Create and publish posts via the authoring workspace (05b) to test the search functionality.

## Follower Count
- Displaying a public follower count on author pages requires a new backend endpoint (currently none exists). Out of scope for current iteration.
