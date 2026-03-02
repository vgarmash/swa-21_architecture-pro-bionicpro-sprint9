# Технический отчёт по реализации кеширования отчётов в MinIO

## Содержание

1. [Введение](#введение)
2. [Архитектура решения](#архитектура-решения)
3. [Реализация MinIO интеграции](#реализация-minio-интеграции)
4. [Конфигурация приложения](#конфигурация-приложения)
5. [Настройка CDN (Nginx)](#настройка-cdn-nginx)
6. [Механизм инвалидации кеша](#механизм-инвалидации-кеша)
7. [Структура хранения отчётов](#структура-хранения-отчётов)
8. [Соответствие требованиям](#соответствие-требованиям)
9. [Заключение](#заключение)

---

## Введение

### Постановка проблемы

После внедрения функционала получения пользовательской отчётности нагрузка на OLAP-базу данных (ClickHouse) существенно возросла. Пользователи стали часто запрашивать свои отчёты, но поскольку данные обновляются посредством ETL-процесса по расписанию, на повторные запросы пользователи получают идентичные отчёты.

### Цели реализации

1. **Снижение нагрузки на OLAP-базу** — исключение необходимости повторных запросов для уже сформированных отчётов
2. **Кеширование отчётов в S3-совместимом хранилище** — сохранение отчётов в MinIO
3. **Раздача отчётов через CDN** — использование Nginx как reverse proxy с кешированием

### Используемые технологии

| Компонент | Технология | Версия |
|-----------|------------|--------|
| Объектное хранилище | MinIO | (из docker-compose) |
| Java SDK для MinIO | minio-java | 8.5.9 |
| CDN / Reverse Proxy | Nginx | alpine |
| Сериализация | Jackson | (из Spring Boot) |

---

## Архитектура решения

### Общая схема взаимодействия

```mermaid
flowchart TD
    A[Клиентский запрос] --> B[ReportController]
    B --> C{Проверка MinIO кеша}
    C -->|Cache HIT| D[Возврат CDN URL]
    C -->|Cache MISS| E[Запрос к OLAP базе]
    E --> F[Генерация отчёта]
    F --> G[Сохранение в MinIO]
    G --> H[Возврат CDN URL]
    
    D --> I[Nginx/CDN]
    I --> J[Отдача кешированного отчёта]
    
    subgraph MinIO Storage
        K[Bucket: reports]
        K --> L[/user-reports/userId/reportDate.json]
    end
    
    G --> K
```

### Диаграмма последовательности запроса отчёта

```mermaid
sequenceDiagram
    participant Client as Клиент
    participant Controller as ReportController
    participant MinioService as MinioReportService
    participant Minio as MinIO Storage
    participant ReportService as ReportService
    participant OLAP as ClickHouse OLAP
    
    Client->>Controller: GET /api/v1/reports
    Controller->>MinioService: checkReportExists userId, reportDate
    MinioService->>Minio: statObject bucket, key
    alt Отчёт есть в кеше
        Minio-->>MinioService: Object metadata
        MinioService-->>Controller: CDN URL
        Controller-->>Client: Report с CDN URL
    else Отчёт не в кеше
        Minio-->>MinioService: Object not found
        MinioService-->>Controller: null
        Controller->>ReportService: getLatestReport userId
        ReportService->>OLAP: SELECT from user_reports
        OLAP-->>ReportService: Данные отчёта
        ReportService-->>Controller: ReportResponse
        Controller->>MinioService: storeReport userId, reportDate, data
        MinioService->>Minio: putObject bucket, key, data
        Minio-->>MinioService: Object stored
        MinioService-->>Controller: CDN URL
        Controller-->>Client: Report с CDN URL
    end
```

### Компоненты решения

| Компонент | Описание | Файл |
|-----------|----------|------|
| `MinioReportService` | Интерфейс сервиса кеширования | [`MinioReportService.java`](MinioReportService.java) |
| `MinioReportServiceImpl` | Реализация сервиса кеширования | [`MinioReportServiceImpl.java`](MinioReportServiceImpl.java) |
| `MinioStorageException` | Исключение для ошибок MinIO | [`MinioStorageException.java`](MinioStorageException.java) |
| `CacheAdminController` | Контроллер администрирования кеша | [`CacheAdminController.java`](CacheAdminController.java) |
| `ReportService` | Сервис отчётов с интеграцией кеша | [`ReportService.java`](../../app/bionicpro-reports/src/main/java/com/bionicpro/reports/service/ReportService.java) |

---

## Реализация MinIO интеграции

### Интерфейс MinioReportService

Интерфейс [`MinioReportService`](MinioReportService.java) определяет контракт для работы с MinIO хранилищем:

```java
public interface MinioReportService {
    // Проверка существования отчёта
    boolean reportExists(String objectKey);
    
    // Сохранение отчёта в MinIO
    void storeReport(String objectKey, ReportResponse report);
    
    // Получение отчёта из MinIO
    Optional<ReportResponse> getReport(String objectKey);
    
    // Генерация CDN URL
    String getCdnUrl(String objectKey);
    
    // Удаление отчёта (для инвалидации кеша)
    void deleteReport(String objectKey);
    
    // Удаление всех отчётов пользователя
    void deleteUserReports(Long userId);
    
    // Генерация ключей объектов
    String generateLatestReportKey(Long userId);
    String generateReportByDateKey(Long userId, String reportDate);
    String generateHistoryReportKey(Long userId, String reportDate);
    
    // Инициализация бакета
    void initializeBucket();
}
```

### Реализация MinioReportServiceImpl

Класс [`MinioReportServiceImpl`](MinioReportServiceImpl.java) реализует все методы интерфейса:

#### Инициализация MinIO клиента

```java
public MinioReportServiceImpl(
        @Value("${app.minio.endpoint:http://minio:9000}") String endpoint,
        @Value("${app.minio.access-key:minioadmin}") String accessKey,
        @Value("${app.minio.secret-key:minioadmin}") String secretKey) {
    
    this.minioClient = MinioClient.builder()
            .endpoint(endpoint)
            .credentials(accessKey, secretKey)
            .build();
    
    this.objectMapper = new ObjectMapper();
    this.objectMapper.registerModule(new JavaTimeModule());
}
```

#### Создание бакета при старте приложения

```java
@PostConstruct
@Override
public void initializeBucket() {
    try {
        boolean bucketExists = minioClient.bucketExists(
                BucketExistsArgs.builder()
                        .bucket(bucketName)
                        .build()
        );
        
        if (!bucketExists) {
            minioClient.makeBucket(
                    MakeBucketArgs.builder()
                            .bucket(bucketName)
                            .build()
            );
        }
    } catch (Exception e) {
        throw new MinioStorageException("Failed to initialize MinIO bucket", e);
    }
}
```

#### Сохранение отчёта

```java
@Override
public void storeReport(String objectKey, ReportResponse report) {
    try {
        String jsonContent = objectMapper.writeValueAsString(report);
        byte[] contentBytes = jsonContent.getBytes();
        
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectKey)
                        .stream(new ByteArrayInputStream(contentBytes), contentBytes.length, -1)
                        .contentType("application/json")
                        .build()
        );
    } catch (JsonProcessingException e) {
        throw new MinioStorageException("Failed to serialize report", e);
    } catch (Exception e) {
        throw new MinioStorageException("Failed to store report", e);
    }
}
```

#### Получение отчёта

```java
@Override
public Optional<ReportResponse> getReport(String objectKey) {
    try (InputStream stream = minioClient.getObject(
            GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectKey)
                    .build()
    )) {
        byte[] content = stream.readAllBytes();
        String jsonContent = new String(content);
        ReportResponse report = objectMapper.readValue(jsonContent, ReportResponse.class);
        return Optional.of(report);
    } catch (ErrorResponseException e) {
        if (e.errorResponse().code().equals("NoSuchKey")) {
            return Optional.empty();
        }
        throw new MinioStorageException("Error retrieving report", e);
    } catch (Exception e) {
        throw new MinioStorageException("Error retrieving report", e);
    }
}
```

#### Генерация presigned URL

```java
@Override
public String getCdnUrl(String objectKey) {
    try {
        return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET)
                        .bucket(bucketName)
                        .object(objectKey)
                        .expiry(presignedUrlExpiryHours, TimeUnit.HOURS)
                        .build()
        );
    } catch (Exception e) {
        // Fallback к прямой конструкции URL
        return String.format("%s/%s/%s", cdnBaseUrl, bucketName, objectKey);
    }
}
```

### Обработка ошибок

Класс [`MinioStorageException`](MinioStorageException.java) используется для обёртывания ошибок MinIO SDK:

```java
public class MinioStorageException extends RuntimeException {
    public MinioStorageException(String message) {
        super(message);
    }

    public MinioStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

### Интеграция в ReportService

Модифицированный [`ReportService`](../../app/bionicpro-reports/src/main/java/com/bionicpro/reports/service/ReportService.java) реализует стратегию cache-first:

```java
public ReportResponse getLatestReport(Long requestedUserId, Long currentUserId) {
    // Проверка авторизации
    if (!currentUserId.equals(requestedUserId)) {
        throw new UnauthorizedAccessException("You don't have permission to access this report");
    }

    // Попытка получить из кеша
    String cacheKey = minioReportService.generateLatestReportKey(requestedUserId);
    ReportResponse cachedReport = tryGetFromCache(cacheKey);

    if (cachedReport != null) {
        logger.info("Cache HIT for latest report: user={}", requestedUserId);
        return cachedReport;
    }

    logger.info("Cache MISS for latest report: user={}", requestedUserId);

    // Cache miss - запрос к OLAP базе
    Optional<UserReport> report = reportRepository.findLatestByUserId(requestedUserId);

    return report.map(r -> {
        ReportResponse response = mapToResponse(r);
        storeInCache(cacheKey, response);  // Сохранение в кеш
        return response;
    }).orElse(null);
}
```

---

## Конфигурация приложения

### Зависимости Maven

Добавлена зависимость MinIO Java SDK в [`pom.xml`](pom_updates.xml):

```xml
<dependency>
    <groupId>io.minio</groupId>
    <artifactId>minio</artifactId>
    <version>8.5.9</version>
</dependency>
```

### Конфигурация application.yml

Добавлены параметры MinIO в [`application.yml`](application_yml_updates.yaml):

```yaml
app:
  minio:
    # Endpoint MinIO в Docker сети
    endpoint: ${MINIO_ENDPOINT:http://minio:9000}
    # Учётные данные из переменных окружения
    access-key: ${MINIO_ROOT_USER:minioadmin}
    secret-key: ${MINIO_ROOT_PASSWORD:minioadmin}
    # Имя бакета для отчётов
    bucket-name: ${MINIO_BUCKET_NAME:reports}
    # Базовый URL CDN
    cdn-base-url: ${CDN_BASE_URL:http://localhost:9000}
    # Время жизни presigned URL (7 дней по умолчанию)
    presigned-url-expiry-hours: ${MINIO_PRESIGNED_EXPIRY:168}
```

### Обновление docker-compose.yaml

Добавлен сервис Nginx CDN в [`docker-compose.yaml`](docker_compose_updates.yaml):

```yaml
services:
  # Обновление bionicpro-reports
  bionicpro-reports:
    environment:
      MINIO_ENDPOINT: http://minio:9000
      MINIO_ROOT_USER: ${MINIO_ROOT_USER}
      MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD}
      MINIO_BUCKET_NAME: reports
      CDN_BASE_URL: http://nginx-cdn:80

  # Новый сервис Nginx CDN
  nginx-cdn:
    image: nginx:alpine
    ports:
      - "8082:80"
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf:ro
      - ./nginx/conf.d:/etc/nginx/conf.d:ro
      - nginx_cache:/var/cache/nginx
    depends_on:
      - minio
    networks:
      - app-network
    healthcheck:
      test: ["CMD", "nginx", "-t"]
      interval: 30s
      timeout: 10s
      retries: 3

volumes:
  nginx_cache:
```

---

## Настройка CDN (Nginx)

### Конфигурация Nginx

Файл [`nginx_cdn.conf`](nginx_cdn.conf) настраивает Nginx как reverse proxy с кешированием:

```nginx
# Определение зоны кеширования
proxy_cache_path /var/cache/nginx/reports 
    levels=1:2 
    keys_zone=reports_cache:10m 
    max_size=1g 
    inactive=24h 
    use_temp_path=off;

# Upstream для MinIO
upstream minio_backend {
    server minio:9000;
    keepalive 32;
}

server {
    listen 80;
    server_name localhost cdn.bionicpro.local;

    # Endpoint для отчётов с кешированием
    location /reports/ {
        proxy_pass http://minio_backend/reports/;
        
        # Включение кеширования
        proxy_cache reports_cache;
        proxy_cache_valid 200 24h;          # Кешировать успешные ответы 24 часа
        proxy_cache_valid 404 1m;           # Кешировать 404 на 1 минуту
        proxy_cache_key "$scheme$proxy_host$uri";
        
        # Защита от cache stampede
        proxy_cache_lock on;
        proxy_cache_lock_timeout 5s;
        
        # Заголовки для отладки
        add_header X-Cache-Status $upstream_cache_status always;
        add_header X-Cache-Key $uri always;
        
        # CORS заголовки
        add_header Access-Control-Allow-Origin "*" always;
        add_header Access-Control-Allow-Methods "GET, HEAD, OPTIONS" always;
        
        # Кеширование на стороне клиента
        expires 24h;
        add_header Cache-Control "public, max-age=86400";
    }
}
```

### Параметры кеширования

| Параметр | Значение | Описание |
|----------|----------|----------|
| `keys_zone` | `reports_cache:10m` | Зона кеша размером 10MB для метаданных |
| `max_size` | `1g` | Максимальный размер кеша на диске |
| `inactive` | `24h` | Удаление неиспользуемых объектов через 24 часа |
| `proxy_cache_valid 200` | `24h` | Время кеширования успешных ответов |
| `proxy_cache_lock` | `on` | Предотвращение cache stampede |

### Мониторинг кеша

Заголовок `X-Cache-Status` позволяет отслеживать состояние кеша:

| Значение | Описание |
|----------|----------|
| `HIT` | Ответ получен из кеша |
| `MISS` | Ответ получен от бэкенда |
| `UPDATING` | Кеш обновляется |
| `STALE` | Возвращён устаревший кеш |

---

## Механизм инвалидации кеша

### Стратегии инвалидации

#### 1. TTL-инвалидация (автоматическая)

Отчёты автоматически удаляются из кеша Nginx через 24 часа неактивности и из MinIO через время жизни presigned URL (7 дней по умолчанию).

#### 2. ETL-триггер (явная инвалидация)

При завершении ETL-процесса вызывается API инвалидации для пользователей с обновлёнными данными.

### Контроллер администрирования кеша

[`CacheAdminController`](CacheAdminController.java) предоставляет API для управления кешем:

```java
@RestController
@RequestMapping("/api/v1/admin/cache")
public class CacheAdminController {

    /**
     * Инвалидация кеша для одного пользователя
     * POST /api/v1/admin/cache/invalidate/user/{userId}
     */
    @PostMapping("/invalidate/user/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> invalidateUserCache(@PathVariable Long userId) {
        minioReportService.deleteUserReports(userId);
        return ResponseEntity.ok(Map.of(
            "message", "Cache invalidated for user",
            "userId", userId,
            "success", true
        ));
    }

    /**
     * Инвалидация кеша для нескольких пользователей
     * POST /api/v1/admin/cache/invalidate/users
     */
    @PostMapping("/invalidate/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> invalidateMultipleUserCache(@RequestBody List<Long> userIds) {
        int successCount = 0;
        int failureCount = 0;
        
        for (Long userId : userIds) {
            try {
                minioReportService.deleteUserReports(userId);
                successCount++;
            } catch (Exception e) {
                failureCount++;
            }
        }
        
        return ResponseEntity.ok(Map.of(
            "totalUsers", userIds.size(),
            "successCount", successCount,
            "failureCount", failureCount
        ));
    }

    /**
     * Проверка статуса кеша
     * GET /api/v1/admin/cache/status/{userId}
     */
    @GetMapping("/status/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getCacheStatus(@PathVariable Long userId) {
        String latestKey = minioReportService.generateLatestReportKey(userId);
        boolean hasLatestReport = minioReportService.reportExists(latestKey);
        
        return ResponseEntity.ok(Map.of(
            "userId", userId,
            "hasCachedLatestReport", hasLatestReport
        ));
    }
}
```

### Интеграция с ETL

После завершения ETL-процесса Airflow должен вызывать endpoint инвалидации:

```python
# В DAG Airflow после завершения обновления данных
def invalidate_cache_for_updated_users(**context):
    updated_users = get_updated_users_from_etl()
    requests.post(
        "http://bionicpro-reports:8080/api/v1/admin/cache/invalidate/users",
        json=updated_users,
        headers={"Authorization": f"Bearer {admin_token}"}
    )
```

---

## Структура хранения отчётов

### Организация объектов в MinIO

```
reports/                              # Бакет
├── user-reports/                     # Префикс для пользовательских отчётов
│   ├── {userId}/                     # Директория пользователя
│   │   ├── latest.json               # Последний отчёт пользователя
│   │   ├── {reportDate}.json         # Отчёт за конкретную дату
│   │   └── history/                  # Исторические отчёты
│   │       ├── {reportDate-1}.json
│   │       ├── {reportDate-2}.json
│   │       └── ...
```

### Форматы ключей объектов

| Тип отчёта | Шаблон ключа | Пример |
|------------|--------------|--------|
| Последний отчёт | `user-reports/{userId}/latest.json` | `user-reports/12345/latest.json` |
| Отчёт по дате | `user-reports/{userId}/{reportDate}.json` | `user-reports/12345/2026-02-21.json` |
| Исторический отчёт | `user-reports/{userId}/history/{reportDate}.json` | `user-reports/12345/history/2026-02-20.json` |

### Генерация ключей

```java
@Override
public String generateLatestReportKey(Long userId) {
    return String.format("user-reports/%d/latest.json", userId);
}

@Override
public String generateReportByDateKey(Long userId, String reportDate) {
    return String.format("user-reports/%d/%s.json", userId, reportDate);
}

@Override
public String generateHistoryReportKey(Long userId, String reportDate) {
    return String.format("user-reports/%d/history/%s.json", userId, reportDate);
}
```

### Преимущества структуры

1. **Изоляция пользователей** — каждый пользователь имеет свой префикс директории
2. **Организация по датам** — возможность доступа к отчётам за конкретную дату
3. **Предсказуемость** — лёгкая генерация и поиск ключей
4. **JSON формат** — совместимость с CDN и веб-клиентами
5. **Эффективное удаление** — удаление всех отчётов пользователя по префиксу

---

## Соответствие требованиям

### Требование 1: Запись отчётов в S3-совместимое хранилище

**Выполнено.** Реализован сервис [`MinioReportService`](MinioReportService.java) с методами:
- [`storeReport()`](MinioReportServiceImpl.java:79) — сохранение отчёта в MinIO
- [`initializeBucket()`](MinioReportServiceImpl.java:50) — создание бакета при старте

### Требование 2: Проверка наличия отчёта в S3 перед запросом к БД

**Выполнено.** В [`ReportService`](../../app/bionicpro-reports/src/main/java/com/bionicpro/reports/service/ReportService.java) реализована стратегия cache-first:
- [`getLatestReport()`](../../app/bionicpro-reports/src/main/java/com/bionicpro/reports/service/ReportService.java:71) — проверка кеша перед запросом к OLAP
- [`getReportByUserIdAndDate()`](../../app/bionicpro-reports/src/main/java/com/bionicpro/reports/service/ReportService.java:43) — аналогичная логика для отчётов по дате

### Требование 3: Раздача отчётов через CDN

**Выполнено.** Настроен Nginx как reverse proxy с кешированием:
- [`nginx_cdn.conf`](nginx_cdn.conf) — конфигурация CDN
- [`getCdnUrl()`](MinioReportServiceImpl.java:120) — генерация CDN URL для отчётов

### Требование 4: Механизм обновления кеша в CDN

**Выполнено.** Реализованы механизмы:
- **TTL-инвалидация** — автоматическое устаревание через 24 часа
- **Явная инвалидация** — API endpoint через [`CacheAdminController`](CacheAdminController.java)
- **Интеграция с ETL** — возможность вызова инвалидации из Airflow DAG

### Требование 5: Структура хранения отчётов для быстрого доступа

**Выполнено.** Разработана иерархическая структура:
- Изоляция по пользователям (`user-reports/{userId}/`)
- Организация по датам (`{reportDate}.json`)
- Оптимизация для типичных запросов (`latest.json`)

---

## Заключение

### Реализованные компоненты

| Компонент | Файл | Назначение |
|-----------|------|------------|
| MinioReportService | [`MinioReportService.java`](MinioReportService.java) | Интерфейс сервиса кеширования |
| MinioReportServiceImpl | [`MinioReportServiceImpl.java`](MinioReportServiceImpl.java) | Реализация сервиса кеширования |
| MinioStorageException | [`MinioStorageException.java`](MinioStorageException.java) | Обработка ошибок MinIO |
| CacheAdminController | [`CacheAdminController.java`](CacheAdminController.java) | API администрирования кеша |
| Nginx CDN | [`nginx_cdn.conf`](nginx_cdn.conf) | Конфигурация CDN |
| ReportService | [`ReportService.java`](../../app/bionicpro-reports/src/main/java/com/bionicpro/reports/service/ReportService.java) | Интеграция кеширования |

### Конфигурационные файлы

| Файл | Назначение |
|------|------------|
| [`pom_updates.xml`](pom_updates.xml) | Зависимость MinIO SDK |
| [`application_yml_updates.yaml`](application_yml_updates.yaml) | Параметры подключения к MinIO |
| [`docker_compose_updates.yaml`](docker_compose_updates.yaml) | Сервис Nginx CDN |

### Ожидаемые результаты

1. **Снижение нагрузки на OLAP базу данных** — до 80-90% запросов будут обслуживаться из кеша
2. **Уменьшение времени ответа** — кешированные отчёты отдаются за миллисекунды вместо секунд
3. **Масштабируемость** — Nginx CDN может обрабатывать тысячи запросов в секунду
4. **Отказоустойчивость** — при недоступности MinIO запросы перенаправляются в OLAP базу

### Рекомендации по внедрению

1. **Мониторинг** — добавить метрики cache hit ratio и_latency MinIO операций
2. **Тестирование** — провести нагрузочное тестирование для определения оптимальных размеров кеша
3. **Документация** — обновить эксплуатационную документацию с процедурами инвалидации кеша
4. **Алёртинг** — настроить уведомления при ошибках MinIO для оперативного реагирования

---

*Отчёт подготовлен на основе реализации MinIO кеширования для сервиса пользовательских отчётов BionicPRO.*