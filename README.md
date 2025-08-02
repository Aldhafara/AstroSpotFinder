# AstroSpotFinder

![Build](https://github.com/Aldhafara/AstroSpotFinder/actions/workflows/ci.yml/badge.svg)

![License](https://img.shields.io/github/license/Aldhafara/AstroSpotFinder)

![Last Commit](https://img.shields.io/github/last-commit/Aldhafara/AstroSpotFinder)

AstroSpotFinder is a microservice that finds the darkest spots for night sky observation within a specified radius from
a starting point. It uses light pollution maps and an iterative search algorithm, providing a fast and accurate API for
planning astro-expeditions.

## Table of Contents

- [Features](#features)
- [Required (or Recommended) Microservices](#required-or-recommended-microservices)
- [Configuration](#configuration)
- [How to Run](#how-to-run)
- [How to Run with Docker](#how-to-run-with-docker)
- [API Documentation (Swagger/OpenAPI)](#api-documentation-swaggeropenapi)
- [API - Endpoints](#api-endpoints)
- [API - Request Parameters](#api-request-parameters)
- [API Response Format](#api-response-format)
- [Caching](#caching)
- [Rate Limiting](#rate-limiting)
- [Error Handling](#error-handling)
- [Example Usage](#example-usage)
- [How to Test](#how-to-test)
- [Troubleshooting](#troubleshooting)
- [License](#license)
- [TODO / Roadmap](#todo--roadmap)

## Features

Currently under development. Available now:

- `/status` health-check endpoint (server status, uptime, timestamp)
- `/astrospots/best` REST endpoint - finds the best astro observations spots in a radius around specified coordinates
- Recursive and asynchronous search algorithm leveraging parameter objects (SearchParams, SearchArea, SearchContext) for
  flexible, precise queries
- Caching of light pollution data responses for improved performance and reduced external calls

## Required (or Recommended) Microservices

AstroSpotFinder is designed to work with several supporting microservices to provide the most accurate astronomical
site recommendations. For best results, the following services should be available:

- [LightPollutionService](https://github.com/Aldhafara/LightPollutionService)  
  Provides sky darkness and light pollution data for a given location.
- [WeatherForecastLite](https://github.com/Aldhafara/WeatherForecastLite)  
  Supplies nighttime cloud cover and temperature forecasts.

**Fallback/Dummy support:**
AstroSpotFinder allows manual selection of a dummy (fallback) implementation for each required service via configuration.
The dummy implementation can be enabled for development, demo or test scenarios by setting the appropriate property
(e.g. `lightpollutionservice.provider=dummy`).
**Note:** Fallback services are **not switched in automatically** if the external real service becomes unavailable at runtime.
The choice of real or dummy service is determined only at application startup (based on configuration).

You can find links to the sample implementations in the table below:

| Service Name          | Description                             | Repository                                         | Config Property           |
|-----------------------|-----------------------------------------|----------------------------------------------------|---------------------------|
| LightPollutionService | Sky darkness/light pollution data       | https://github.com/Aldhafara/LightPollutionService | lightpollutionservice.url |
| WeatherForecastLite   | Nighttime weather forecast (cloud/temp) | https://github.com/Aldhafara/WeatherForecastLite   | weatherforecastlite.url   |

**Config Property** indicates the name of the configuration key in the application.properties file
(or other configuration file) under which the URL of the corresponding running microservice should be provided.

## Configuration

Before running the application, you need an `application.properties` file with your local configuration (paths, API
keys, etc.).

1. Copy the example to create your own config:

```bash
   cp src/main/resources/example-application.properties src/main/resources/application.properties
```

2. Edit `src/main/resources/application.properties` and fill in the required values for your environment.

**Choosing additional service implementation**
You can select which additional service implementation (real or dummy) AstroSpotFinder will use by setting the following
property (on LightPollutionService example):

```text
# Use 'dummy' to enable the built-in fallback service (suitable for development and testing)
# Use 'real' (or leave unset) to use the production service and provide its URL
lightpollutionservice.provider=dummy

# If you use the real backend, specify the endpoint URL:
lightpollutionservice.url=https://your-lightpollutionservice.url
```

- If `lightpollutionservice.provider=dummy` - the dummy fallback service will be used (no external requests).
- If `lightpollutionservice.provider=real` or the property is unset - the real LightPollutionService will be used (you
  must set `lightpollutionservice.url` to the backend address).

**Note:**
Switching between dummy/real services is always determined at application startup time. There is no automatic failover.
Proceed similarly for other additional services. See [Required (or Recommended) Microservices](#required-or-recommended-microservices)

## How to Run

1. Clone the repository:

```bash
git clone https://github.com/Aldhafara/AstroSpotFinder.git
```

2. Prepare your configuration:

```bash
   cp src/main/resources/example-application.properties src/main/resources/application.properties
```

(then edit as needed)

3. Start the application:

```bash
./mvnw spring-boot:run
```

4. By default, the application will be available at:

```
http://localhost:8080
```

## How to Run with Docker

1. Build the JAR:

```bash
./mvnw clean package
```

2. (Optional) Prepare your application.properties if you want to override the config.

3. Build the image:

```bash
docker build -t astrospotfinder .
```

4. Run (with local `application.properties` mounted):

```bash
docker run -p 8080:8080 -v $(pwd)/src/main/resources/application.properties:/app/application.properties  astrospotfinder
```

5. (or, with Docker Compose)

```bash
docker compose up --build
```

Once running, the application will be available at:

```
http://localhost:8080
```

## API Documentation (Swagger/OpenAPI)

Once the application is running, interactive API documentation will be available at:

```
http://localhost:8080/swagger-ui.html
```

You can explore, test, and understand all endpoints directly from your browser.

## API Endpoints

| Endpoint         | Type | Description                                                                            | Status |
|------------------|------|----------------------------------------------------------------------------------------|--------|
| /status          | GET  | Server status, uptime, timestamp                                                       | ✅      |
| /astrospots/best | GET  | Finds the best astro observation spots based on location and radius (recursive search) | ✅      |

## API Request Parameters

### for /astrospots/best

| Parameter   | Type   | Description                                  |
|-------------|--------|----------------------------------------------|
| latitude    | double | Latitude of the search center                |
| longitude   | double | Longitude of the search center               |
| radiusKm    | double | Radius around center in kilometers to search |

**Example requests:**

```
GET /astrospots/best?latitude=52.2298&longitude=21.0117&radiusKm=30
```

### for /status

**Example requests:**

```
GET /status
```

## API Response Format

### Example `/astrospots/best` Response

Returns a list of LocationConditions objects representing the best spots, sorted by light pollution brightness.

```json
[
  {
    "coordinate": {
      "latitude": 52.26864169801801,
      "longitude": 20.896780418018018
    },
    "brightness": 12
  },
  //other points
]
```

### Example `/status` Response

```json
{
  "status": "UP",
  "uptime": 5025112,
  "uptimePretty": "1h 23m 45s",
  "timestamp": "2025-07-14T18:52:00Z"
}
```

**Description of key fields:**

- `status` - server status
- `uptime` - server uptime in ms
- `uptimePretty` - server uptime in easy to read form
- `timestamp` - timestamp of request

## Caching

- Light pollution data responses are cached internally by AstroSpotService using Spring Cache.
- Cache keys are based on coordinate parameters.
- Cached entries are not stored for failed requests (e.g., HTTP 429 errors cause exceptions and do not populate the cache).
- This caching reduces redundant calls to the external LightPollutionService for improved performance.

## Rate Limiting

- Planned: endpoint protection (e.g., /template-endpoint)-limit 20 requests/min/IP.
- Exceeding the limit: HTTP 429.

## Error Handling

Planned features. Not yet implemented.

- 503 - if data unavailable (e.g., file read error)
- 422 - invalid input parameters (latitude, longitude)
- All errors in clear JSON format:

```json
{
  "error": "Invalid parameter: latitude",
  "timestamp": "2025-07-14T18:52:00Z"
}
```

## Example Usage

```bash
curl "http://localhost:8080/astrospots/best?latitude=52.2298&longitude=21.0117&radiusKm=30"
```

## How to Test

Run tests:

```bash
./mvnw test
```

## Troubleshooting

- If you see errors about missing configuration, make sure `src/main/resources/application.properties` exists and is
  correctly filled.
- For Docker users, you can mount your configuration file as a volume if not building it into the image directly.
- Example Error:  
  `Could not resolve placeholder...`  
  Make sure your application.properties contains all needed variables.

## License

MIT

## TODO / Roadmap

- [X] /astrospots/best
- [X] Result caching
- [ ] Input parameter validation
- [X] Global error handler
- [ ] API documentation (Swagger/OpenAPI)
- [ ] Integration tests (MockMvc)
- [X] Example deployment (Docker/Kubernetes)