# Spring Boot: память, Prometheus и Grafana

Учебное приложение на Java 21 / Spring Boot 4.1 с Actuator и Micrometer.
Нужны Docker Engine и Docker Compose v2, доступ к Maven Central и Gradle,
свободные порты 8080, 9090, 3000 и примерно 2 ГиБ свободной памяти.
Локальная установка Java и Gradle для запуска стека не требуется.

## Запуск одной командой

Из корня репозитория:

```bash
docker compose up -d --build
```

Приложение собирается в многоэтапном Dockerfile. Prometheus автоматически
получает конфигурацию сбора и правила алертов. Grafana автоматически загружает
источник данных и дашборд **Demo → Spring Boot Memory Demo**. Дополнительная
настройка через интерфейс не нужна. Первый запуск скачивает зависимости.

| Сервис | Адрес |
| --- | --- |
| Приложение | `http://localhost:8080` |
| Метрики Actuator | `http://localhost:8080/actuator/prometheus` |
| Prometheus, алерты | `http://localhost:9090/alerts` |
| Grafana, дашборд | `http://localhost:3000/d/memory-demo` |

Grafana доступна без входа с правами Viewer. Все опубликованные порты привязаны
к loopback: это локальный учебный стенд. Настройки находятся в
`grafana/provisioning/datasources/datasource.yml`,
`grafana/provisioning/dashboards/dashboard.yml` и
`grafana/dashboards/my-dashboard.json`.

## Методы и метрики

| Метод | Ответ / действие |
| --- | --- |
| `GET /api/ok` | HTTP 200 |
| `GET /api/not-found` | HTTP 404 |
| `GET /api/error` | HTTP 500, намеренная демонстрация ошибки |
| `GET /api/counted` | HTTP 200; увеличивает `demo_controller_calls_total` |
| `GET /api/memory` | Количество удерживаемых байтов |
| `DELETE /api/memory` | Останавливает выделение памяти и удаляет ссылки на массивы |

```bash
curl -i localhost:8080/api/ok
curl -i localhost:8080/api/not-found
curl -i localhost:8080/api/error
curl localhost:8080/api/counted
```

Алгоритм каждую секунду выделяет и заполняет массив размером до 16 МиБ,
сохраняя ссылку на него. Через примерно 16 секунд удерживается 256 МиБ.
Это ограниченная нагрузка, воспроизводимая без намеренного OutOfMemoryError:
heap ограничен 384 МиБ, память контейнера — 768 МиБ. Настройки
`DEMO_MEMORY_ENABLED`, `DEMO_MEMORY_LIMIT_MIB` (0–256),
`DEMO_MEMORY_INTERVAL_MS` позволяют изменить демонстрацию.
После удаления ссылок реальный heap освобождается при последующем GC;
метрика удерживаемой приложением памяти сразу становится нулевой.

Дашборд содержит:

- Используемую и максимальную память JVM heap (`jvm_memory_*_bytes`).
- Удерживаемую демонстрацией память (`demo_memory_retained_bytes`).
- Частоту HTTP-ответов по кодам (`http_server_requests_seconds_count`).
- Счётчик вызовов кастомного метода (`demo_controller_calls_total`).
- Состояние алерта (`ALERTS`).
- Время пауз сборщика мусора (`jvm_gc_pause_seconds_sum`).

HTTP-графики появляются после запросов к соответствующим методам;
частоты используют окно 1 минута. Prometheus собирает метрики каждые 5 секунд.

## Алерты и самопроверка

В `prometheus-alerts.yml` определены:

- `HighRetainedMemory`: удерживается более 192 МиБ в течение 15 секунд.
  После обычного запуска автоматически переходит в `firing` примерно за 30–40 секунд.
- `ApplicationDown`: Prometheus не может собрать метрики приложения 30 секунд.

Состояния алертов видны в Prometheus и на дашборде. Внешняя отправка уведомлений
не настроена; для неё потребуется Alertmanager и выбранный канал доставки.

Для автоматической проверки работающего стека нужен Python 3:

```bash
python3 scripts/smoke-test.py
```

Проверка выполняет запросы с кодами 200/404/500, проверяет сбор HTTP-, JVM- и
кастомных метрик, источник Grafana, дашборд и выполнение всех его запросов через
Grafana. Затем ожидает `HighRetainedMemory` в состоянии `firing`, освобождает
память через DELETE и проверяет снятие алерта. Проверка завершится ошибкой,
если ожидаемое поведение не наступит за установленный таймаут.

Для повторной демонстрации или повторного запуска проверки:

```bash
docker compose restart app
python3 scripts/smoke-test.py
```

Проверка конфигурации Prometheus:

```bash
docker compose exec prometheus promtool check config /etc/prometheus/prometheus.yml
```

Остановка стека: `docker compose down`. Метрики и данные Grafana здесь
эфемерны; дашборд и источник восстанавливаются из файлов при запуске.

## Разработка без Docker

С Java 21 JDK:

```bash
bash gradlew --no-daemon test bootJar
bash gradlew bootRun
```

Три интеграционных теста проверяют HTTP-коды и экспорт метрик, отключение
алгоритма, ограничение памяти и остановку выделения после освобождения.

В средах с HTTPS-прокси Java требует настроек `https.proxyHost`,
`https.proxyPort` и доверенного хранилища сертификатов. Docker-сборка принимает
необязательный `BUILD_JAVA_TOOL_OPTIONS`; файл `.docker-cacerts`, если он нужен,
используется только на этапе сборки и не попадает в конечный образ.
Проверка TLS остаётся включённой. Обычный запуск вне такой среды не требует
этих настроек.
