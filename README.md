# Система бронирования отелей
Это пример распределённого приложения на Spring Boot/Cloud. Есть несколько сервисов:
- **API Gateway** — проксирует запросы к другим сервисам, работает с JWT.  (Порт: 8080)
- **Booking Service** — регистрация пользователей, бронирования, хранение истории. 
- **Hotel Service** — управление отелями и комнатами, статистика популярности.  
- **Eureka Server** — для обнаружения сервисов.  (Порт: 8761)

В качестве БД была выбрана H2.


## Основные функции:

- Регистрация и вход пользователей (JWT).  
- Создание бронирований с двухшаговой логикой: PENDING → CONFIRMED/CANCELLED.  
- Идемпотентность запросов по `requestId`.  
- Повторы с экспоненциальной задержкой при вызовах других сервисов.  
- Подсказки по выбору номера (по популярности).  
- CRUD для пользователей (для админа) и отелей/комнат.  
- Агрегация популярности номеров.  
- Логирование `X-Correlation-Id` для сквозной трассировки.  

## Основные эндпоинты
- Аутентификация:
  - POST `/auth/register` — регистрация (администратору добавить `"admin": true`)
  - POST `/auth/login` — авторизация и получение токена
- Бронирование:
  - GET `/bookings` — список броней
  - POST `/bookings` — создать бронирование
  - GET `/bookings/suggestions` — подсказки по комнатам
- Редактирование пользователей (требуется вход под admin):
  - GET `/admin/users`, GET `/admin/users/{id}`, PUT `/admin/users/{id}`, DELETE `/admin/users/{id}`
- Редактирование номеров и отелей (требуется вход под admin):
  - GET `/hotels`, GET `/hotels/{id}`, POST `/hotels`, PUT `/hotels/{id}`, DELETE `/hotels/{id}` (админ)
  - GET `/rooms/{id}`, POST `/rooms`, PUT `/rooms/{id}`, DELETE `/rooms/{id}` (админ)
  - POST `/rooms/{id}/hold` — удержание 
  - POST `/rooms/{id}/confirm` — подтверждение удержания
  - POST `/rooms/{id}/release` — освобождение удержания

## Как запустить:
Необходимо выполнить следующие команды:
```bash
mvn -pl eureka-server spring-boot:run
mvn -pl api-gateway spring-boot:run
mvn -pl hotel-service spring-boot:run
mvn -pl booking-service spring-boot:run
```

Совет: можно запустить все модули в отдельных окнах. После старта сервисы зарегистрируются в Eureka (`http://localhost:8761`).

## Примеры запросов
1. Регистрация пользователя
```bash
curl -X POST http://localhost:8080/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"user1","password":"pass"}'
```
2. Авторизация и получение JWT-токена
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"user1","password":"pass"}' | jq -r .access_token)
```
3. Создание отеля и комнаты (требуется вход под admin)
```bash
curl -X POST http://localhost:8080/hotels \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Hotel A","city":"Moscow","address":"Red Square, 1"}'

curl -X POST http://localhost:8080/rooms \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"number":"101","capacity":2,"available":true}'
```
4. Получение подсказок
```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/bookings/suggestions
```
5. Создать бронирование
```bash
curl -X POST http://localhost:8080/bookings \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"roomId":1, "startDate":"2025-10-20", "endDate":"2025-10-22", "requestId":"req-123"}'
```

## Тестирование:
```bash
mvn test
```

## H2 консоль и Swagger:
- H2 консоль: http://localhost:8080/h2-console
- Swagger: http://localhost:8080/swagger-ui.html