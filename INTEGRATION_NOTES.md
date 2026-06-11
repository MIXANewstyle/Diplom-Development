# Integration Notes

## Post Search
- The backend search (`GET /api/v1/posts/search`) utilizes PostgreSQL full-text search (`tsvector`).
- **Important**: Search requires **published** posts to return results. If the catalog is empty or only contains drafts, search will return no results. Create and publish posts via the authoring workspace (05b) to test the search functionality.
