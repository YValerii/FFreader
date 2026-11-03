# FFreader

FFreader turns [fanfiction.net](https://www.fanfiction.net) stories into downloadable PDFs.
Paste a story link into the web UI and the backend scrapes every chapter in the background,
stores the story in PostgreSQL and builds a single readable PDF.

## Repository layout

| Path | Description | Stack |
|------|-------------|-------|
| [`FFreader-API/`](FFreader-API) | REST API, scraper and PDF generator | Java 25, Spring Boot 4.1, Selenium, jsoup, PDFBox, Flyway |
| [`FFreader-UI/`](FFreader-UI) | Web interface | Angular 22, Angular Material |

## How it works

```
Browser (Angular, :4200) --REST--> API (Spring Boot, :8080) --> PostgreSQL (:5432)
                                     |
                                     +--> headless Chrome --> fanfiction.net
                                     +--> extraction .txt files and generated PDFs on disk
```

1. `POST /api/fanfics` validates the URL, saves the story as `PENDING` and returns `202 Accepted`.
2. A background job on the `fanficTaskExecutor` thread pool reads the story metadata, then loads
   each chapter in headless Chrome. Every page load uses its own browser, which is closed afterwards.
   A chapter is retried up to 10 times before the job gives up.
3. Each chapter is saved to the `chapters` table and to an extraction file on disk.
4. The saved chapters are converted to plain text and assembled into one PDF. If only some
   chapters were loaded, a PDF is still built from those.
5. The UI polls the list every 4 seconds while any story on the current page is in progress, and
   enables the download button once the PDF is ready.

Submitting a story that already exists parses it again and replaces its chapters and PDF. If that
story is still being parsed, the request returns its current state and no second job starts.

### Story statuses

| Status | Meaning | PDF available |
|--------|---------|---------------|
| `PENDING` | Queued, waiting for a worker thread | No |
| `PARSING` | Scraping chapters and building the PDF | No |
| `PARSED` | All chapters loaded and the PDF was generated | Yes |
| `PARSED_PARTIALLY` | Some chapters were loaded, or PDF generation failed; see `errorMessage` | If it was generated |
| `FAILED` | No chapters were loaded; see `errorMessage` | No |

## Quick start with Docker

The only requirement is Docker. From the repository root:

```bash
docker compose up --build -d
```

Then open <http://localhost:8081>. The first build takes a few minutes.

To install dependencies, rebuild everything and restart the containers in one step, run
`bash deploy.sh`. Add `--local` to also build on your machine and run the API unit tests, which needs
JDK 25 and a supported Node version. Stories and PDFs are kept, but a restart interrupts any story
that is being parsed.

Compose starts PostgreSQL, then the API once the database is healthy, then the UI once the API is
healthy. The UI serves the app through nginx and forwards `/api` to the API. PostgreSQL is also
published, on `127.0.0.1:5432` only, for local development.

| Command | What it does |
|---------|--------------|
| `docker compose ps` | Show service status |
| `docker compose logs -f api` | Follow the API logs |
| `docker compose down` | Stop everything and keep your stories |
| `docker compose down -v` | Stop everything and **delete all stories and PDFs** |

To rebuild after changing the code, run `docker compose up --build -d` again.

Compose reads these optional settings from the environment or a `.env` file next to
`docker-compose.yml`:

| Variable | Default | Description |
|----------|---------|-------------|
| `FFREADER_PORT` | `8081` | Port the app is available on |
| `FFREADER_DB_PORT` | `5432` | Port PostgreSQL is published on, on this machine only |
| `POSTGRES_PASSWORD` | `ffreader` | Database password; only takes effect when the database is first created |

The API image runs Google Chrome, which is only published for Linux on amd64. On ARM machines it
runs under emulation, which hasn't been tested.

## Prerequisites for local development

To work on the code without Docker for the apps, you need:

| Tool | Version | Notes |
|------|---------|-------|
| JDK | 25 | `JAVA_HOME` must point to it. The API compiles for Java 25 |
| Node.js | 26 LTS | Angular 22 requires `^22.22.3`, `^24.15.0` or `>=26`; **Node 25 is not supported**. See `FFreader-UI/.nvmrc` |
| Docker | Any recent version | Runs PostgreSQL 18. A local PostgreSQL also works |
| Google Chrome | Latest | WebDriverManager downloads the matching driver when the API starts |

Maven doesn't need to be installed: the API includes the Maven wrapper (`mvnw`), which downloads
Maven 3.10 on first use. On Windows PowerShell, run it as `.\mvnw`.

The API needs internet access at startup to resolve the Chrome driver, and while scraping.

## Running locally

Run each part in its own terminal, starting from the repository root. This uses the same database
as the full Docker setup above. PDFs are stored separately, so downloading a story that the other
setup parsed returns `409` until you submit it again.

**1. Start the database**

```bash
docker compose up -d postgres
```

This starts PostgreSQL on `localhost:5432` with database, user and password all set to `ffreader`.
The API creates the schema itself through Flyway when it starts. Give the database a few seconds on
its first run before starting the API.

**2. Start the API**

```bash
cd FFreader-API
./mvnw spring-boot:run
```

The API listens on <http://localhost:8080>. To check it:

```bash
curl "http://localhost:8080/api/fanfics?page=0&size=5"
```

**3. Start the UI**

```bash
cd FFreader-UI
npm ci
npm start
```

Open <http://localhost:4200>, paste a story URL such as
`https://www.fanfiction.net/s/14585136/1/For-Her` and click **Add fanfic**.

**Stopping**

Press `Ctrl+C` in the API and UI terminals, then stop the database:

```bash
docker compose down      # keeps the data; add -v to delete all stories and PDFs as well
```

## Building

```bash
# API: runs the unit tests and produces FFreader-API/target/ffreader-api.jar
cd FFreader-API
./mvnw clean package
java -jar target/ffreader-api.jar

# UI: produces static files in FFreader-UI/dist/ffreader-ui
cd FFreader-UI
npm ci
npm run build
```

The production UI calls the API at the relative path `/api`, so it must be served from the same
origin as the API, for example behind a reverse proxy that forwards `/api` to port 8080 and serves
`dist/ffreader-ui` for everything else. To use another URL, change
[`environment.prod.ts`](FFreader-UI/src/environments/environment.prod.ts). `npm start` uses
[`environment.ts`](FFreader-UI/src/environments/environment.ts), which points to
`http://localhost:8080/api`.

## Testing

```bash
cd FFreader-API
./mvnw test                                 # unit tests; also part of ./mvnw package
docker compose up -d postgres
./mvnw test -Dtest=FanficReparseIT          # parse pipeline on real PostgreSQL, browser mocked
./mvnw test -Dtest=ChromeDriverIT           # starts and closes real Chrome on a local page
```

Neither integration test contacts fanfiction.net. The UI has no automated tests, so `npm test` does
nothing useful yet.

## API reference

| Method | Path | Description | Responses |
|--------|------|-------------|-----------|
| `POST` | `/api/fanfics` | Queue a story; body: `{ "url": "https://www.fanfiction.net/s/..." }` | `202`, `400` invalid URL, `503` queue full |
| `GET` | `/api/fanfics?page=&size=` | List stories, newest first (default page size 10) | `200` |
| `GET` | `/api/fanfics/{id}` | Get one story and its status | `200`, `404` |
| `GET` | `/api/fanfics/{id}/download` | Download the PDF | `200`, `404`, `409` not ready |
| `DELETE` | `/api/fanfics/{id}` | Delete a story with its chapters, extraction files and PDF | `204`, `404`, `409` still parsing |

Errors are returned as JSON with `timestamp`, `status`, `error` and `message` fields.

## Configuration

API settings are in
[`application.yml`](FFreader-API/src/main/resources/application.yml). Each one can be overridden
with an environment variable.

| Property | Environment variable | Default | Description |
|----------|----------------------|---------|-------------|
| `spring.datasource.url` | `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/ffreader` | Database URL |
| `spring.datasource.username` | `SPRING_DATASOURCE_USERNAME` | `ffreader` | Database user |
| `spring.datasource.password` | `SPRING_DATASOURCE_PASSWORD` | `ffreader` | Database password |
| `server.port` | `SERVER_PORT` | `8080` | API port |
| `ffreader.scraper.headless` | `FFREADER_SCRAPER_HEADLESS` | `true` | Set to `false` to watch the browser |
| `ffreader.scraper.page-load-timeout-seconds` | `FFREADER_SCRAPER_PAGELOADTIMEOUTSECONDS` | `120` | Page load timeout |
| `ffreader.scraper.pool-size` | `FFREADER_SCRAPER_POOLSIZE` | `10` | Core worker threads; the maximum is twice this value |
| `ffreader.storage.extraction-dir` | `FFREADER_STORAGE_EXTRACTIONDIR` | `/tmp/ffreader/extractions` | Extraction files |
| `ffreader.storage.pdf-dir` | `FFREADER_STORAGE_PDFDIR` | `/tmp/ffreader/pdfs` | Generated PDFs |
| `ffreader.cors.allowed-origins` | `FFREADER_CORS_ALLOWEDORIGINS` | `http://localhost:4200` | Comma-separated list of allowed origins |

On Windows, `/tmp/...` resolves against the current drive, for example `E:\tmp\ffreader`.

## PDF text support

PDFs embed the fonts in `FFreader-API/src/main/resources/fonts`, choosing the first one that has each
character: DejaVu Sans (Latin, Cyrillic, Greek and more), then Noto Sans SC (Chinese, Japanese), then
Noto Sans KR (Korean). The large fonts are only read when a story needs them. These fonts add about
19 MB to the jar.

## Known limitations

- **Some scripts don't render properly.** Characters no bundled font covers, such as most emoji and
  Indic scripts, appear as `?`. Right-to-left and connected scripts such as Arabic and Hebrew aren't
  shaped or reordered, so they will not read correctly.
- **Scraping depends on the site.** Bot protection or markup changes on fanfiction.net can make
  jobs end as `FAILED` or `PARSED_PARTIALLY`.
- **There is no authentication.** Run it locally only.
