# HomeHub

**Home Services Booking & Management REST API**

HomeHub is a Java Spring Boot application that connects homeowners with home-maintenance service providers. Homeowners can manage their properties, discover services, check provider availability, and book appointments. Providers can create service listings, publish availability, and manage booking requests. Administrators manage platform users, provider approvals, and service categories.

> **Project type:** Backend REST API | **Base URL:** `http://localhost:8070` | **API documentation:** `http://localhost:8070/swagger-ui/index.html`


## Project Information

**Project title:** HomeHub

**Purpose:** To provide one platform where homeowners can request home services and providers can manage appointments without conflicting bookings. The system also supports administrators in maintaining a trustworthy service marketplace.

**Target users:** Homeowners, home-service providers, and platform administrators.

**Scope:** Spring Boot backend with REST endpoints; no frontend. Requests can be sent through Postman or Swagger UI.

## Features

- Account registration, email verification, login, and JWT authentication.
- Forgot-password email and password reset; authenticated password change.
- Profile management and profile-picture upload.
- Provider profile registration and administrator approval.
- Home creation, retrieval, editing, and soft deletion(CRUD).
- Category and service-offering management.
- Provider availability management and soft deletion of availability slots.
- Booking creation, viewing, confirmation, completion, and cancellation.
- Prevention of bookings outside provider availability, in the past, or overlapping existing bookings.
- Service filtering by category, sorting, and pagination.
- Advanced service search by name, category, minimum price, and maximum price.
- Email booking notifications and Server-Sent Events (SSE) notification endpoint.
- Audit records for important operations and application logging.
- Validation, centralized exception handling, and appropriate HTTP responses.
- Rate limiting on selected public authentication endpoints.
- Initial category seed data and automated unit tests.
- Swagger/OpenAPI documentation.

## Technology Stack

| Area | Technology |
|---|---|
| Language | Java 17 |
| Backend | Spring Boot, Spring Web, Spring Data JPA |
| Security | Spring Security, JWT, BCrypt |
| Database | PostgreSQL |
| Validation | Jakarta Bean Validation |
| Email | Spring Mail / SMTP |
| Real-time updates | Spring MVC Server-Sent Events (`SseEmitter`) |
| API documentation | Springdoc OpenAPI / Swagger UI |
| Tests | JUnit 5, Mockito |
| Build | Maven |
| Development tools | IntelliJ IDEA, Postman, pgAdmin, Git, GitHub |
| Boilerplate reduction | Lombok |

## Architecture

HomeHub uses a layered architecture:

```text
Postman / Swagger / API Client
              |
              v
     Spring Security + JWT
              |
              v
          Controller
              |
              v
           Service  ------> Email / SSE / Audit logging
              |
              v
          Repository
              |
              v
        PostgreSQL
```

- **Controller:** Maps HTTP requests, validates request DTOs, and returns responses.
- **Service:** Applies business rules and coordinates application operations.
- **Repository:** Accesses PostgreSQL through Spring Data JPA.
- **Model:** Defines JPA entities and relationships.
- **DTO:** Defines request and response data without exposing unnecessary entity fields.
- **Security / configuration:** Controls authentication, authorization, profiles, and application configuration.
- **Exception handling:** Returns structured errors instead of exposing internal stack traces.

### Main package structure

```text
src/main/java/com/ga/HomeHub/
├── config/
├── controller/
├── dto/
├── exception/
├── model/
│   └── enums/
├── repository/
├── security/       
├── seed/
└── service/
```

## User Roles

| Role | Main permissions |
|---|---|
| `HOMEOWNER` | Manage own homes, browse services, create/view/cancel own bookings, manage own profile |
| `PROVIDER` | Create a provider profile, manage own service offerings and availability, view assigned bookings, update booking status |
| `ADMIN` | Approve providers, manage categories, deactivate users, perform permitted administrative actions |

Users must authenticate before accessing protected endpoints. Provider service creation requires an approved provider profile. Users cannot self-register as `ADMIN`.

## Database Design

### Main entities

| Entity | Purpose |
|---|---|
| `User` | Account details, role, verification state, and account status |
| `ProviderProfile` | Provider business information and approval status |
| `Category` | Home-service categories |
| `ServiceOffering` | Service name, description, price, duration, category, and provider |
| `Home` | A homeowner's property and service address |
| `Availability` | Dates and time ranges offered by a provider |
| `Booking` | Appointment, customer, home, service, date, times, and status |
| `Notification` / SSE workflow | User notifications and real-time delivery |
| `AuditLog` | Record of important actions |
| Email verification / password reset tokens | Account verification and password recovery |

### Core relationships

- One homeowner (`User`) can own many `Home` records.
- One `User` with provider role can have a `ProviderProfile`.
- One `ProviderProfile` can offer many `ServiceOffering` records.
- One `Category` can contain many `ServiceOffering` records.
- One `ProviderProfile` can define many `Availability` slots.
- One homeowner can create many `Booking` records.
- Each `Booking` belongs to one homeowner, one home, and one service offering.
- A service offering links its bookings to the corresponding provider.
- Audit logs reference the user who performed an important action.

**ERD:** [Add link to exported ERD diagram here]

## Business Rules

1. An account must have a verified email address before it can log in.
2. Users cannot register themselves as administrators.
3. Deactivated users must not be allowed to authenticate.
4. A provider must have an **APPROVED** profile before creating service offerings.
5. A homeowner can book only a home belonging to their account.
6. A booking cannot be made for a past date.
7. A booking must fit within the provider's available date and time range.
8. Only an active service from an approved provider can be booked.
9. A homeowner can cancel their own booking; completed bookings cannot be cancelled.
10. Booking status follows allowed transitions: `PENDING → CONFIRMED/CANCELLED` and `CONFIRMED → COMPLETED/CANCELLED`.
11. Home and availability removal use soft deletion rather than immediately deleting the database row.
12. Users and providers may only perform operations authorized for their roles and resources.
13. Search price ranges must be valid; the minimum cannot exceed the maximum.

## API Endpoints

The following routes describe the implemented HomeHub API. See Swagger UI for request schemas and additional details. Protected routes require `Authorization: Bearer <JWT>`.

### Authentication — `/api/auth`

| Method | Endpoint | Description | Access |
|---|---|---|---|
| POST | `/register` | Register homeowner/provider | Public |
| GET | `/verify?token=...` | Verify registered email | Public |
| POST | `/login` | Login and receive JWT | Public |
| POST | `/forgot-password` | Request password reset email | Public |
| POST | `/reset-password` | Reset password using token | Public |

### Users — `/api/users`

| Method | Endpoint | Description | Access |
|---|---|---|---|
| GET | `/profile` | Get own profile | Authenticated |
| PUT | `/profile` | Update own profile | Authenticated |
| PUT | `/password` | Change password | Authenticated |
| POST | `/profile-picture` | Upload profile picture | Authenticated |


### Homes — `/api/homes`

| Method | Endpoint | Description | Access |
|---|---|---|---|
| POST | `/homes` | Create home | HOMEOWNER |
| GET | `/homes` | List own homes  | Authenticated owner |
| GET | `/{homeId}` | Get one home | Authorized owner |
| PUT | `/{homeId}` | Update home | Authorized owner |
| DELETE | `/{homeId}` | Soft-delete home | Authorized owner |

### Providers — `/api/providers`

| Method | Endpoint | Description | Access |
|---|---|---|---|
| POST | `/profile` | Create provider profile | PROVIDER |
| GET | `/profile` | View own provider profile | PROVIDER |

### Services — `/api/services`

| Method | Endpoint | Description | Access |
|---|---|---|---|
| GET | `` | Browse active services; filter, sort, paginate | Authenticated |
| GET | `/{serviceId}` | Get service details | Authenticated |
| GET | `/search` | Advanced service search | Authenticated |
| POST | `` | Create service offering | Approved PROVIDER |
| PUT | `/{serviceId}` | Update own service | PROVIDER |
| DELETE | `/{serviceId}` | Deactivate own service | PROVIDER |

### Categories — `/api/categories`

| Method | Endpoint | Description | Access |
|---|---|---|---|
| GET | `` | List categories | Authenticated |
| GET | `/{categoryId}` | Get category | Authenticated |
| POST | `` | Create category | ADMIN |
| PUT | `/{categoryId}` | Update category | ADMIN |
| DELETE | `/{categoryId}` | Deactivate category | ADMIN |

### Availability — `/api/availability`

| Method | Endpoint | Description | Access |
|---|---|---|---|
| GET | `/?providerId=1&date=2026-10-15` | Get provider availability | Authenticated |
| POST | `/` | Create availability slot | PROVIDER |
| DELETE | `/{availabilityId}` | Deactivate availability slot | PROVIDER |

### Bookings — `/api/booking` (singular)

| Method | Endpoint | Description | Access |
|---|---|---|---|
| POST | `` | Create booking | HOMEOWNER |
| GET | `` | List own bookings, optionally by status | Authenticated |
| GET | `/{bookingId}` | View authorized booking | Authorized user, if mapped |
| PUT | `/{bookingId}/status` | Confirm, cancel, or complete booking | PROVIDER / ADMIN |
| DELETE | `/{bookingId}` | Cancel booking | Homeowner / authorized admin |

### Administration — `/api/admin`

| Method | Endpoint | Description | Access |
|---|---|---|---|
| GET | `/users` | List users | ADMIN |
| DELETE | `/users/{userId}` | Deactivate user | ADMIN |
| PUT | `/providers/{providerId}/approve` | Approve provider | ADMIN |

### Notifications — `/api/notifications`

| Method | Endpoint | Description | Access |
|---|---|---|---|
| GET | `/stream` | Subscribe to SSE booking events | Authenticated |

**Swagger UI:** `http://localhost:8070/swagger-ui/index.html`  
**OpenAPI JSON:** `http://localhost:8070/v3/api-docs`

## Filtering, Sorting, and Pagination

Service listing supports Spring Data `Pageable`:

```http
GET /api/services?categoryId=1
GET /api/services?page=0&size=2&sort=price,asc
GET /api/services?page=0&size=2&sort=price,desc
```

Advanced search supports optional filters together:

```http
GET /api/services/search?name=cleaning&categoryId=3&minPrice=10&maxPrice=30
GET /api/services/search?minPrice=10&maxPrice=50&page=0&size=10&sort=price,asc
```

Pagination responses include `content`, `totalElements`, `totalPages`, `number`, `size`, and other metadata.

## Authentication and Security

1. Register a `HOMEOWNER` or `PROVIDER` account.
2. Follow the email verification link.
3. Login to receive a JWT.
4. For protected endpoints, set the request header:

```http
Authorization: YOUR_JWT_TOKEN
```

Security features include:

- Password hashing with BCrypt; passwords are never returned in API responses.
- Email verification before login.
- JWT validation and role-based method authorization.
- "401 Unauthorized" for missing or invalid JWTs; "403 Forbidden" for insufficient permissions.
- Account deactivation and provider approval controls.
- Validation of request bodies and centralized error responses.
- Rate limiting on login, registration, and password-recovery endpoints.
- Sensitive configuration loaded from environment variables.
- Response DTOs for services, providers, and bookings to avoid exposing nested user records.


## Notifications and Audit Logging

### Email notifications

- Provider receives an email when a homeowner creates a booking.
- Homeowner receives an email when the provider changes booking status, including confirmation.
- Provider receives a notification when a booking is cancelled.

### Server-Sent Events (SSE)

Authenticated clients can open a long-lived connection to:

```http
GET /api/notifications/stream
Accept: text/event-stream
Authorization: Bearer YOUR_JWT_TOKEN
```

The application uses `SseEmitter` to push booking-related events to connected users.

### Audit and application logs

Important operations such as service creation, booking creation/cancellation, status updates, provider approval, and user deactivation are recorded. Application logs help troubleshoot errors without exposing raw passwords or JWTs.

## Setup and Installation

### Prerequisites

- JDK **17**
- PostgreSQL
- Maven
- IntelliJ IDEA
- Postman
- SMTP account for outgoing email


-Configure environment variables:
In **IntelliJ IDEA → Run → Edit Configurations → Environment variables**, set:

```text
DB_PASSWORD=your_postgresql_password
JWT_SECRET=your_long_random_jwt_signing_secret
MAIL_USERNAME=your_smtp_username
MAIL_PASSWORD=your_smtp_password
```


### sample workflow

1. Register a homeowner and provider; verify both email addresses.
2. Create a provider profile and approve it using an admin account.
3. Create a category/service offering and provider availability.
4. Create a home using the homeowner account.
5. Book a service; confirm it as the provider.
6. Test a conflicting booking and cancel the original booking.

**Admin provisioning:** Admin self-registration is disabled. Use the project's existing admin seed/provisioning mechanism.

## Seed Data

At startup, `DataSeeder` checks whether categories already exist. When the category table is empty, it creates:

| Category | Description |
|---|---|
| Plumbing | Plumbing repair and installation services |
| Electrical | Electrical repair and installation services |
| Cleaning | Home cleaning services |
| AC Maintenance | Air conditioning maintenance and repair |
| Painting | Indoor and outdoor painting services |

The seed process avoids inserting these categories again on subsequent restarts when data already exists.

## Testing

### Automated unit tests

The project has **19 JUnit/Mockito unit tests** that passed in the most recent reported run. They cover:

- Authentication: unverified/inactive users and duplicate registration.
- Registration rules: admin self-registration prohibited.
- Request validation: invalid email and short password.
- Booking rules: past dates, unauthorized homes, inactive services, unavailable slots, and double bookings.
- Booking authorization: owner restrictions and provider-only status changes.
- Booking lifecycle: cancellation, confirmation, completion.


### Manual Postman verification

| Postman request name | Expected / observed result |
|---|---|
| `Create Provider Availability` | **201 Created** |
| `Create Home` | **201 Created** |
| `Create Booking` | **201 Created**, status `PENDING` |
| `Provider Confirm Booking` | **200 OK**, status `CONFIRMED` |
| `Prevent Double Booking` | **422**, overlapping booking rejected |
| `Homeowner Cancel Booking` | **200 OK**, status `CANCELLED` |
| `Get My Bookings` | **200 OK**, paginated safe response |
| `Get Service By ID` | **200 OK**, safe `ServiceResponse` |
| `Get My Provider Profile` | **200 OK**, safe `ProviderResponse` |
| `Get Services Without Token` | **401 Unauthorized** |
| `Get Services With Invalid Token` | **401 Unauthorized** |
| Service sort / pagination | Correct ascending order and page metadata |
| Service category filtering | Matching category results |
| Advanced service search | Correct matching service |


## HTTP Status Codes and Error Handling

| Status | Meaning | Example |
|---|---|---|
| `200 OK` | Successful retrieval/update | Get booking, confirm booking |
| `201 Created` | New resource created | Register, create booking |
| `204 No Content` | Successful action with no response body | Selected delete/deactivate endpoints |
| `400 Bad Request` | Invalid request or validation | Missing required field, invalid price range |
| `401 Unauthorized` | Missing or invalid authentication | Missing/invalid JWT |
| `403 Forbidden` | Insufficient permission | Wrong role, inaccessible resource |
| `404 Not Found` | Resource does not exist | Unknown provider profile or booking |
| `409 Conflict` | Duplicate/conflicting resource | Duplicate email |
| `422 Unprocessable Entity` | Booking business-rule failure | Provider already booked |
| `429 Too Many Requests` | Authentication rate limit exceeded | Repeated login attempts |
| `500 Internal Server Error` | Unexpected server failure | Generic server error |

A typical structured error looks like:

```json
{
  "timestamp": "2026-10-08T05:20:34",
  "status": 422,
  "error": "UNPROCESSABLE_ENTITY",
  "message": "Provider is booked during this time",
  "path": "/api/booking"
}
```

The global exception handler avoids exposing internal stack traces in API responses.

## User Stories

These user stories are preserved from the original project plan, with wording corrected for readability.

1. **Register account:** As a new user, I want to register an account so that I can use HomeHub.
2. **Verify email:** As a registered user, I want to verify my email so that I can access HomeHub features.
3. **Login:** As a verified user, I want to log in so that I can securely access my account.
4. **Manage profile:** As a user, I want to view and update my profile so that my information stays current.
5. **Manage homes:** As a homeowner, I want to manage my homes so that I can choose where a booked service should be performed.
6. **Browse services:** As a homeowner, I want to browse and search available services so that I can find the home service I need.
7. **Manage provider profile:** As a provider, I want to manage my profile so that homeowners can learn about my business.
8. **Manage services:** As a provider, I want to manage the services I offer so that homeowners can book them.
9. **Manage availability:** As a provider, I want to define my availability so that homeowners know when I can provide a service.
10. **Create booking:** As a homeowner, I want to book a service for one of my homes so that the provider can perform the requested service.
11. **Manage booking status:** As a provider, I want to update booking statuses so that homeowners know their appointment progress.
12. **Cancel booking:** As a homeowner, I want to cancel a booking so that an unwanted time slot becomes available again.
13. **Reset password:** As a user, I want to reset a forgotten password so that I can regain access to my account.
14. **Manage users:** As an admin, I want to deactivate users so that inappropriate accounts cannot access HomeHub.
15. **Manage categories:** As an admin, I want to manage service categories so that services remain organized.
16. **Receive notifications:** As a user, I want to receive notifications when booking events occur so that I know when my booking changes.
17. **View audit activity:** As an admin, I want important system actions recorded so that activity can be reviewed when necessary.

## Project Planning and Deliverables

- **postman:** https://israa-husain-3161197.postman.co/workspace/e9f859ee-39ab-4169-9f84-551111843b6a
- **User stories:** Included above.
- **API documentation:** Swagger UI at `/swagger-ui/index.html` when the app is running.
- **Test evidence:** 19 passing unit tests in the last verified run, plus Postman workflow tests.
- **Version control:** Git branches and descriptive commits used throughout development.

### Development approach

The project was built incrementally: design models and relationships; implement repositories and services; add controllers and authentication; implement booking rules and notifications; then test, secure API responses, and document the system.

## Challenges and Future Improvements

### Challenges addressed

- Enforcing role-based permissions across homeowner, provider, and admin workflows.
- Sending real emails for account verification and booking updates.
- Returning safe DTO responses rather than exposing nested JPA entities.
- Supporting flexible search filters alongside pagination and sorting.
- Rate limit and SSE.

### Possible future improvements

- Add a web or mobile frontend.
- Add persistent notification history and unread/read tracking if not yet exposed.
- Add more detailed OpenAPI examples and a shareable Postman collection.
- Add payment instead of just listing the price.