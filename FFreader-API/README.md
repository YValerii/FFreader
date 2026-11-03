# FFreader-API

See the [root README](../README.md) for the full guide, including tests and known limitations.

Spring Boot 4 / Java 25 service that:

1. Accepts a fanfiction.net story URL (e.g. `https://www.fanfiction.net/s/14585136/1/For-Her`) via an **async** REST endpoint.
2. Uses a headless Selenium Chrome browser to load the page and extract:
   - Title (`b.xcontrast_txt`)
   - Description (`div.xcontrast_txt`)
   - Metadata: rating, language, genre, characters, chapter count, word count, reviews, favs, follows, published date, external id
3. Persists the story to **PostgreSQL**.
4. Determines chapter count from the `Chapters:` metadata field and/or the `#chap_select` dropdown.
5. Walks every chapter, saving each chapter's raw HTML to a temp extraction file named
   `{runUuid}_{storyName}_{chapterNumber}.txt`, and to the `chapters` table.
6. Navigates chapter-by-chapter (`/s/{id}/{chapter}/{slug}`) until all chapters are extracted.
7. Converts the extracted HTML into clean plain text and assembles a single, readable PDF per story.
8. Exposes a paginated list endpoint for all loaded fanfics.
9. Exposes a download endpoint for the generated PDF.
10. Tracks status per fanfic: `PENDING -> PARSING -> PARSED` (or `PARSED_PARTIALLY` / `FAILED`).
11. Parsing runs on a dedicated thread pool (`AsyncConfig`) so multiple fanfics parse in parallel.
12. Delete endpoint removes the fanfic row, its chapters, extraction files and generated PDF.

## Requirements

- Java 25
- PostgreSQL 18 (or `docker compose up -d postgres` from the repository root)
- Google Chrome installed locally (WebDriverManager downloads the matching driver automatically)

## Running

```bash
docker compose up -d postgres # from the repository root; starts Postgres on localhost:5432
./mvnw spring-boot:run        # starts the API on localhost:8080
```

## Endpoints

| Method | Path                       | Description                                   |
|--------|----------------------------|------------------------------------------------|
| POST   | `/api/fanfics`              | `{ "url": "https://www.fanfiction.net/s/..." }` — kicks off async parsing |
| GET    | `/api/fanfics?page=&size=`  | Paginated list of all loaded fanfics           |
| GET    | `/api/fanfics/{id}`         | Single fanfic + current status                 |
| GET    | `/api/fanfics/{id}/download`| Downloads the generated PDF (409 if not ready) |
| DELETE | `/api/fanfics/{id}`         | Deletes the fanfic, chapters, files and PDF     |

## Configuration

See `src/main/resources/application.yml` — datasource credentials, Selenium headless mode,
thread pool size, and storage directories for extraction `.txt` files and generated PDFs
are all configurable there or via environment variables (e.g. `FFREADER_SCRAPER_POOLSIZE`).
