# HomeHub

**Home Services Booking & Management REST API**

HomeHub is a Java Spring Boot application that connects homeowners with home-maintenance service providers. Project 3 extends the original booking platform with additional booking workflows, database concurrency controls, background email processing, persistent notifications, and expanded tests. Homeowners can manage their properties, discover services, check provider availability, and book appointments. Providers can create service listings, publish availability, and manage booking requests. Administrators manage platform users, provider approvals, and service categories.

> **Project type:** Backend REST API | **Base URL:** `http://localhost:8070` | **API documentation:** `http://localhost:8070/swagger-ui/index.html`


## Project Information

**Project title:** HomeHub

**Purpose:** To provide one platform where homeowners can request home services and providers can manage appointments without conflicting bookings. The system also supports administrators in maintaining a trustworthy service marketplace.

**Target users:** Homeowners, home-service providers, and platform administrators.

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

### Project 3 feature additions

- Booking reschedule requests with provider approval or rejection and advance-notice validation.
- Normal bookings with a minimum advance booking period; emergency same-day bookings with an additional surcharge.
- Provider time-off blocking and emergency-booking preferences.
- Booking waitlists with time-limited offers and expiration handling.
- Favorite services for homeowners.
- Provider service reports, required before marking applicable bookings as completed.
- Homeowner assessments written by providers with restricted visibility.
- Reviews for completed services.
- Booking status history and persistent notification history alongside SSE.
- Booking reminders, unanswered-booking expiration, cancellation rules, and service-report deadlines.
- Transactional provider-level locking to guard overlapping booking requests.
- Asynchronous email dispatch using a managed thread pool after transaction commit.

## Project 3: What Changed

Project 2 established authentication, service discovery, provider availability, bookings, email/SSE notifications, and administration. Project 3 extends the same backend and database rather than replacing the existing application.

| Area | Project 3 extension |
|---|---|
| Booking lifecycle | Rescheduling, emergency bookings, expiration, cancellation policies, and history |
| Provider scheduling | Time off, emergency-booking management, and waitlist offers |
| After-service workflows | Service reports, homeowner assessments, and service reviews |
| Homeowner convenience | Favorites, reminders, and persistent notification history |
| Concurrency | Transactional provider locking and overlapping-slot checks |
| Multithreading | Background email processing with `@Async` and a thread pool |
| Quality | Additional concurrency-focused and asynchronous-email tests |

## Technology Stack

| Area | Technology |
|---|---|
| Language | Java 17 |
| Security | Spring Security, JWT, BCrypt |
| Database | PostgreSQL |
| Validation | Jakarta Bean Validation |
| Email | Spring Mail / SMTP, `@Async`, `ThreadPoolTaskExecutor` |
| Real-time updates | Spring MVC Server-Sent Events (`SseEmitter`) |
| API documentation | Springdoc OpenAPI / Swagger UI |
| Concurrency | Spring transactions, database pessimistic locking, `ExecutorService` (tests) |
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

### Project 3 processing paths

- **Booking requests:** HTTP request → controller → transactional booking service → provider lock → availability and overlap checks → repository → PostgreSQL → response.
- **Email delivery:** successful database commit → asynchronous email task → `homeHubExecutor` worker thread → SMTP server. Email delivery does not hold up the booking HTTP response.
- **Real-time events:** booking-related services publish SSE notifications and persist notification records for later retrieval.

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
| `BookingRescheduleRequest` | Proposed appointment changes and provider decisions |
| `ServiceReport` | Work performed, materials used, and recommendations |
| `HomeownerAssessment` | Provider-authored assessment with restricted access |
| `ServiceReview` | Homeowner feedback after completed services |
| `BookingWaitlist` | Waitlist requests, offers, and offer expiration |
| `ServiceFavorite` | Saved service offerings for homeowners |
| `BookingHistory` | Status transitions, actors, and timestamps |

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

### Additional Project 3 business rules

1. Normal bookings must be made at least **24 hours** in advance; eligible emergency bookings may be made on the same day with a **20% surcharge**.
2. Booking start/end times must fit the provider’s availability and must not overlap blocked time off or active bookings.
3. Competing requests for a provider’s overlapping time slot must be serialized through provider-level database locking and rechecked inside the transaction.
4. Reschedule requests require provider approval and are subject to the **24-hour notice** rule.
5. Service reports must be submitted before the relevant booking can transition to `COMPLETED`.
6. Waitlist offers have expiration rules; unanswered bookings and overdue reports are subject to deadline-handling workflows.
7. Homeowner assessments are private and should be available only to appropriately authorized providers after a qualifying booking.
8. Inactive users must be rejected even when they present an otherwise valid JWT.

## API Endpoints

The following routes describe the implemented HomeHub API, including Project 3. See Swagger UI for request schemas and validation details. Protected routes require `Authorization: Bearer <JWT>`. Endpoint paths are relative to each section's base path; an empty endpoint means the base path itself. Role checks also enforce ownership and booking-state rules where applicable.

### Authentication - `/api/auth`

| Method | Endpoint | Description | Access |
|---|---|---|---|
| POST | `/register` | Register homeowner/provider | Public |
| GET | `/verify?token=...` | Verify registered email | Public |
| POST | `/login` | Login and receive JWT | Public |
| POST | `/forgot-password` | Request password reset email | Public |
| POST | `/reset-password` | Reset password using token | Public |

### Users - `/api/users`

| Method | Endpoint | Description | Access |
|---|---|---|---|
| GET | `/profile` | Get own profile | Authenticated |
| PUT | `/profile` | Update own profile | Authenticated |
| PUT | `/change-password` | Change password | Authenticated |
| POST | `/profile-picture` | Upload profile picture | Authenticated |

### Homes - `/api/homes`

| Method | Endpoint | Description | Access |
|---|---|---|---|
| POST | `` | Create home | HOMEOWNER |
| GET | `` | List own active homes; paginate | HOMEOWNER |
| GET | `/{homeId}` | Get one active home | HOMEOWNER owner |
| PUT | `/{homeId}` | Update own active home | HOMEOWNER owner |
| DELETE | `/{homeId}` | Soft-delete own home | HOMEOWNER owner |

### Providers - `/api/providers`

| Method | Endpoint | Description | Access |
|---|---|---|---|
| POST | `` | Create provider profile | PROVIDER |
| GET | `/profile` | View own provider profile | PROVIDER |
| PUT | `/profile` | Update own business profile | PROVIDER |
| GET | `/{providerId}` | View approved provider details and portfolio | Authenticated; active provider account |
| PUT | `/portfolio` | Update own portfolio description and emergency settings | PROVIDER |

### Services - `/api/services`

| Method | Endpoint | Description | Access |
|---|---|---|---|
| GET | `` | Browse eligible active services; filter by category/provider, sort, paginate | Authenticated |
| GET | `/mine` | List own services, including inactive offerings | PROVIDER |
| GET | `/{serviceId}` | Get service details; hidden offerings restricted to owner/admin | Authenticated |
| GET | `/search` | Search by name, category, provider and price range; paginate | Authenticated |
| POST | `` | Create service offering with optional emergency setting | Approved PROVIDER |
| PUT | `/{serviceId}` | Update own service and emergency setting | PROVIDER owner |
| DELETE | `/{serviceId}` | Deactivate own service | PROVIDER owner |

### Favorites - `/api/favorites`

| Method | Endpoint | Description | Access |
|---|---|---|---|
| POST | `/{serviceId}` | Save a service as a favorite | HOMEOWNER |
| GET | `` | List own favorites | HOMEOWNER |
| DELETE | `/{favoriteId}` | Remove own favorite using its favorite ID | HOMEOWNER owner |

### Reviews — `/api/reviews`

| Method | Endpoint | Description | Access |
|---|---|---|---|
| POST | `` | Review own completed booking | HOMEOWNER owner |
| GET | `/mine` | List own submitted reviews; paginate | HOMEOWNER |
| GET | `/provider/{providerId}` | View provider reviews without private booking details | Authenticated |

### Categories - `/api/categories`

| Method | Endpoint | Description | Access |
|---|---|---|---|
| GET | `` | List active categories; paginate | Authenticated |
| GET | `/{categoryId}` | Get category | Authenticated |
| POST | `` | Create category | ADMIN |
| PUT | `/{categoryId}` | Update category | ADMIN |
| DELETE | `/{categoryId}` | Deactivate category | ADMIN |

### Availability - `/api/availability`

| Method | Endpoint | Description | Access |
|---|---|---|---|
| GET | `?providerId=1&date=2026-10-15` | Get provider availability for a date | Authenticated |
| GET | `?providerId=1&date=2026-10-15&timeOff=true` | Get own blocked time-off periods (same endpoint, optional filter) | PROVIDER owner |
| POST | `` | Create own availability slot | PROVIDER |
| PUT | `/{availabilityId}` | Update own availability or time-off period | PROVIDER owner |
| DELETE | `/{availabilityId}` | Deactivate own availability or remove own time off | PROVIDER owner |
| POST | `/time-off` | Block own time off without overlapping active bookings | PROVIDER |

### Bookings - `/api/booking` (singular)

| Method | Endpoint | Description | Access |
|---|---|---|---|
| POST | `` | Create normal/emergency booking; validate availability and pricing | HOMEOWNER owner |
| GET | `` | List own/assigned bookings; filter by status and paginate | Authenticated; current-account scope |
| GET | `/available-times?serviceId=1&date=2026-10-15&bookingType=NORMAL` | Discover available start times; NORMAL is the default, EMERGENCY supported | Authenticated |
| GET | `/{bookingId}` | View authorized booking | Booking owner / assigned PROVIDER / ADMIN |
| GET | `/{bookingId}/history` | View recorded booking audit history; paginate | Booking owner / assigned PROVIDER / ADMIN |
| PUT | `/{bookingId}/status` | Apply a valid booking status transition; confirmation must precede start/deadline | Assigned PROVIDER / ADMIN |
| DELETE | `/{bookingId}` | Cancel booking under existing cancellation rules | Booking owner / ADMIN |

### Reschedule Requests - `/api/reschedules`

| Method | Endpoint | Description | Access |
|---|---|---|---|
| POST | `/booking/{bookingId}` | Request a new appointment with at least 24 hours notice | HOMEOWNER booking owner |
| GET | `` | List own/assigned requests; filter by status/bookingId and paginate | HOMEOWNER / PROVIDER participants |
| GET | `/{id}` | View an authorized reschedule request | HOMEOWNER / PROVIDER participants |
| PUT | `/{id}/decision` | Approve or reject request using the approve boolean | Assigned PROVIDER |

### Waitlists - `/api/waitlist`

| Method | Endpoint | Description | Access |
|---|---|---|---|
| POST | `` | Join an occupied appointment waitlist | HOMEOWNER |
| GET | `` | List own waitlist entries and offers with expiration times | HOMEOWNER |
| DELETE | `/{id}` | Withdraw own waiting/offered entry | HOMEOWNER owner |
| POST | `/{id}/claim` | Accept own unexpired offer and create a normal pending booking | HOMEOWNER owner |

### Service Reports - `/api/reports`

| Method | Endpoint | Description | Access |
|---|---|---|---|
| GET | `/booking/{bookingId}` | View a private service report | Booking owner / assigned PROVIDER |
| POST | `/booking/{bookingId}` | Submit required report after appointment end for a confirmed/report-due booking | Assigned PROVIDER |

### Homeowner Assessments - `/api/assessments`

| Method | Endpoint | Description | Access |
|---|---|---|---|
| POST | `/booking/{bookingId}` | Write a private homeowner assessment after booking completion | Assigned PROVIDER |
| GET | `/booking/{bookingId}` | View prior homeowner assessments while reviewing a pending booking | Assigned PROVIDER |

### Administration - `/api/admin`

| Method | Endpoint | Description | Access |
|---|---|---|---|
| GET | `/users` | List users with account/role information; paginate | ADMIN |
| DELETE | `/users/{userId}` | Deactivate user | ADMIN |
| GET | `/providers` | List provider applications; filter by status and paginate | ADMIN |
| GET | `/providers/{providerId}` | Inspect provider business and applicant details | ADMIN |
| PUT | `/providers/{providerId}/approve` | Approve provider | ADMIN |

### Notifications - `/api/notifications`

| Method | Endpoint | Description | Access |
|---|---|---|---|
| GET | `/stream` | Subscribe to own SSE booking events | Authenticated |
| GET | `` | Get own notification history | Authenticated |
| GET | `/unread-count` | Get own unread notification count | Authenticated |
| PATCH | `/{id}/read` | Mark own notification as read | Authenticated recipient |

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

## Concurrency, Race Conditions, and Thread Safety

### The concurrency-sensitive scenario

Two homeowners can send booking requests at almost the same instant for the **same provider and overlapping time range**. Without synchronization, both transactions might read the slot as available before either inserts a booking. Both could then succeed, producing a double booking.

### Database consistency solution

`BookingService.createBooking()` runs in a Spring-managed `@Transactional` boundary. It acquires a provider-level database lock through `ProviderProfileRepository` before checking overlapping bookings and saving a new booking. Overlap validation checks active statuses such as `PENDING`, `CONFIRMED`, and `AWAITING_REPORT`. The lock makes competing transactions for that provider wait their turn, the later transaction must recheck the database after the first transaction finishes.

The time-overlap rule is: `newStart < existingEnd && newEnd > existingStart`. Adjacent appointments, where one ends exactly when the other begins, do not overlap.

**Why this approach:** A database transaction and provider-level pessimistic lock coordinate requests across HTTP threads and application instances sharing the same database. A Java-only `synchronized` block would not reliably protect multiple application instances. Provider-level locking also protects a provider's schedule across their service offerings, rather than only one booking row.

**Failure without protection:** Both users could receive successful responses for the same slot. **Expected behavior with protection:** One request creates the booking; the other is rejected with the application's booking-conflict error.

### Concurrent REST verification - pending

A Mockito-based two-thread test has passed.

### Multithreading and background email processing

`EmailService.sendEmailAsync()` uses `@Async("homeHubExecutor")`. `ExecutorConfig` configures a `ThreadPoolTaskExecutor` with **3 core threads**, **5 maximum threads**, a **50-task queue**, and thread prefix `HomeHub-`. Email work is dispatched after the relevant transaction commits, so failed or rolled-back bookings do not trigger success emails. The request thread can return without waiting for SMTP delivery.

This is production multithreading: several email tasks may run on separate worker threads. Separately, the concurrency test uses `ExecutorService`, `CountDownLatch`, and two test threads to simulate competing bookings.

### Asynchronous and concurrency test coverage

- `concurrentBookingsAllowOnlyOneWinner()` - two competing test threads, one accepted booking.
- `overlappingBookingIsRejected()` - rejects an overlapping time range.
- `backToBackBookingsAreAllowed()` - permits adjacent time slots.
- `providerLockIsAcquiredBeforeCheckingBookingConflicts()` - checks lock ordering.
- Asynchronous email tests cover background execution, multiple emails, no dispatch after rollback, and dispatch after commit.


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


### Run the application

1. Create a PostgreSQL database and configure the application's database URL, username, and password in the relevant Spring configuration/environment.
2. Configure the required JWT and SMTP secrets below. Never commit real credentials.
3. From the project root, run `./mvnw spring-boot:run` or `.\mvnw.cmd spring-boot:run` .
4. Open Swagger UI at `http://localhost:8070/swagger-ui/index.html` and test the workflow.


### Sample workflow

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
18. **Prevent double booking:** As a homeowner, I want the system to prevent overlapping bookings for the same provider so that my appointment is not affected by another homeowner booking the same time slot.
19. **Handle concurrent bookings:** As a homeowner, I want the system to handle simultaneous booking requests safely so that only one homeowner can reserve an available provider time slot.
20. **Request booking reschedule:** As a homeowner, I want to request a different booking date or time so that I can adjust my appointment when my plans change.
21. **Manage reschedule requests:** As a provider, I want to approve or reject reschedule requests so that I can manage changes to my schedule.
22. **Book emergency services:** As a homeowner, I want to request an emergency same-day service so that I can receive urgent assistance when needed.
23. **Manage emergency bookings:** As a provider, I want to control whether I accept emergency bookings so that I can manage urgent requests according to my availability.
24. **Manage provider time off:** As a provider, I want to block unavailable dates and times so that homeowners cannot book appointments when I am unavailable.
25. **Join booking waitlist:** As a homeowner, I want to join a waitlist for an unavailable time slot so that I can be notified when an appointment becomes available.
26. **Manage waitlist offers:** As a homeowner, I want to receive and respond to time-limited waitlist offers so that I can reserve a newly available appointment.
27. **Save favorite services:** As a homeowner, I want to save my favorite services so that I can easily find and book them again.
28. **Submit service report:** As a provider, I want to submit a report after performing a service so that the homeowner can review the work completed.
29. **View service reports:** As a homeowner, I want to view reports for my bookings so that I can understand the work performed and any recommendations.
30. **Assess homeowners:** As a provider, I want to write an assessment of a homeowner so that I can document my experience for future service interactions.
31. **View homeowner assessments:** As a provider, I want to view relevant homeowner assessments after receiving a booking so that I can prepare for the service.
32. **Review completed services:** As a homeowner, I want to rate and review completed services so that I can share feedback about the provider.
33. **View booking history:** As a user, I want to view booking status changes so that I can track the progress and history of an appointment.
34. **View notification history:** As a user, I want to view my previous notifications so that I can review important updates I may have missed.
35. **Receive booking reminders:** As a homeowner, I want to receive reminders before my appointment so that I do not forget my scheduled service.
36. **Expire unanswered bookings:** As a homeowner, I want unanswered booking requests to expire after the provider response deadline so that I am not left waiting indefinitely.
37. **Apply cancellation policy:** As a homeowner, I want to know the cancellation rules for my booking so that I understand any restrictions or consequences before cancelling.
38. **Track service report deadlines:** As a provider, I want to receive notifications about overdue service reports so that I can complete the required documentation on time.
39. **Receive email notifications:** As a user, I want to receive booking-related emails without delaying my booking request so that I can receive timely updates while the application remains responsive.
40. **Track booking changes:** As an admin, I want booking status changes and important actions recorded so that I can review activity and investigate issues when necessary.


## Project Planning and Deliverables

- **postman:** https://israa-husain-3161197.postman.co/workspace/e9f859ee-39ab-4169-9f84-551111843b6a
- **User stories:** Included above.
- **API documentation:** Swagger UI at `/swagger-ui/index.html` when the app is running.
- **Version control:** Git branches and descriptive commits used throughout development.

### Development approach

The project was built incrementally: design models and relationships; implement repositories and services; add controllers and authentication; implement booking rules and notifications; then test, secure API responses, and document the system.

## Challenges and Future Improvements

### Challenges addressed

- Designing a booking flow that remains consistent when requests arrive concurrently.
- Selecting database-level provider locking instead of relying on in-memory synchronization.
- Sending email after transaction commit so rolled-back bookings do not send misleading messages.
- Using a bounded thread pool to keep email delivery off HTTP request threads.
- Expanding the booking lifecycle while preserving role-based access and notification behavior.

- Enforcing role-based permissions across homeowner, provider, and admin workflows.
- Sending real emails for account verification and booking updates.
- Returning safe DTO responses rather than exposing nested JPA entities.
- Supporting flexible search filters alongside pagination and sorting.
- Rate limit and SSE.

### Possible future improvements

- Add a web or mobile frontend.
- Add more detailed OpenAPI examples and a shareable Postman collection.
- Add payment processing instead of only listing service prices.


## Project 3 Planning and Daily Progress

### October 21, 2026 - Project Planning and Foundation

**Focus:** Define the requirements and prepare the backend for additional functionality.

- Reviewed the existing HomeHub Project 2 implementation and identified the features required.
- Planned the new workflows: booking rescheduling, waitlists, service reports, reviews, favorites, homeowner assessments, and persistent notifications.
- Added booking type, reschedule status, and waitlist status enums.
- Added the initial models and repository queries for the new features.
- Strengthened JWT authentication to reject deactivated users.
- Implemented booking rules for normal and emergency appointments, including 24-hour notice and a 20% emergency surcharge.
- Introduced provider-level locking to prevent competing booking operations.
- Added provider time-off support and booking conflict queries.
- Prepared repository queries for scheduled tasks, notifications, and FIFO waitlist processing.

**Outcome:** Established the data models, business rules, and concurrency foundation.

### October 22, 2026 - Core Feature Development

**Focus:** Implement the main workflows and integrate them with the existing booking system.

- Implemented booking reschedule requests with provider approval and rejection.
- Added validation for appointment availability and provider time off.
- Developed the waitlist workflow, including joining, claiming offers, and expiration.
- Added FIFO ordering and provider-wide waitlist processing.
- Implemented service favorites for homeowners.
- Added service reports and restricted access to authorized users.
- Implemented ratings and reviews for completed services.
- Added homeowner assessments and history retrieval.
- Implemented provider portfolio updates.
- Improved current-user validation and account management.
- Added and refined the booking scheduler.
- Improved pessimistic locking, booking status transitions, and waitlist integration.
- Isolated waitlist expiration processing into separate transactions to reduce lock contention.
- Updated booking reminders when appointments are rescheduled.

**Outcome:** Implemented the primary business workflows and improved their integration with booking and availability management.

### October 25, 2026 - API Integration, Security, and Asynchronous Processing

**Focus:** Complete API integration, strengthen validation, and introduce background processing.

- Added REST endpoints for rescheduling, waitlists, homeowner assessments, favorites, service reports, reviews, provider portfolios, and notification history.
- Added notification unread counts and mark-as-read functionality.
- Added provider time-off endpoints.
- Improved booking and waitlist concurrency validation.
- Prevented deleting availability when existing bookings would be affected.
- Strengthened service report authorization.
- Added file upload size limits and image signature validation.
- Disabled unnecessary error stack traces in API responses.
- Configured asynchronous email processing using a thread pool.
- Updated email delivery to occur after successful transaction commits.
- Added scheduled booking reminders and expiration tasks.
- Organized and committed the new entities, repositories, services, and controllers.
- Improved Swagger documentation with API descriptions and response information.

**Outcome:** Expanded API coverage, improved security and validation, and integrated asynchronous processing.

### October 27, 2026 - Concurrency Testing and Email Logging

**Focus:** Verify concurrency behavior and improve visibility into asynchronous execution.

- Verified provider-level database locks protecting booking and waitlist operations.
- Reviewed how Spring handles concurrent requests using request-processing threads.
- Tested booking concurrency scenarios, including overlapping and back-to-back bookings.
- Tested asynchronous email execution and transaction-aware email dispatch.
- Reviewed the use of `ExecutorService`, thread pools, and `@Async`.
- Added logging for asynchronous email processing, thread execution, SMTP duration, and failures.

**Outcome:** Improved understanding and test coverage of concurrency and asynchronous execution.

### October 28, 2026 - Application Logging, Frontend, Testing, and Code Quality

**Focus:** Improve application observability, resolve frontend and backend issues, validate business rules, and prepare the project for testing and version control.

**Application logging and code quality**

- Expanded logging for booking creation, cancellation, and status updates.
- Added logging for reschedule requests, provider decisions, and booking conflicts.
- Added waitlist logging for joining, withdrawing, claiming offers, and offer expiration.
- Added provider lock logs to assist with concurrency debugging.
- Improved logging comments and message clarity.
- Discussed which service classes require logging and which classes do not.
- Made additional code-quality improvements.

**Frontend and backend bug fixes**

- Fixed service browsing errors caused by nullable search parameters in PostgreSQL queries.
- Improved backend handling of historical bookings containing null booking types or prices.
- Updated the frontend to display missing historical prices appropriately.
- Fixed booking detail errors affecting homeowner and administrator views.
- Improved the service search field layout and placeholder text.
- Removed unnecessary decorative elements from the login interface.
- Improved frontend error handling and session recovery.

**Booking workflow improvements**

- Separated pending booking requests from upcoming confirmed appointments.
- Added a dedicated Pending tab for homeowners.
- Updated Upcoming to display only confirmed bookings with future appointment times.
- Prevented providers and administrators from confirming appointments that have already started or passed.
- Preserved response-deadline validation and provider-level locking.
- Improved frontend approval-action visibility and backend HTTP 422 error responses.

**Testing and application verification**

- Ran isolated backend tests covering booking validation, authorization, concurrency, and API responses.
- Verified the frontend production build.
- Rebuilt and restarted the backend using the existing development configuration.
- Verified service browsing, search, and historical booking endpoints.
- Confirmed the application was connected to the correct PostgreSQL database.
- Verified that all application tables remained unchanged after the controlled restart.
- Confirmed the frontend and backend were running on ports 5173 and 8070.

**Outcome:** Improved HomeHub's reliability, booking validation, frontend usability, test coverage, and code organization. Completed local Git commits and prepared a safer, more comprehensive demonstration-data strategy for future testing.
---

## Development Summary

1. **Planning and design:** Define the additional requirements, entities, and workflows.
2. **Feature implementation:** Develop rescheduling, waitlists, reviews, favorites, reports, assessments, and notifications.
3. **Business rule validation:** Enforce booking policies, authorization, provider availability, and scheduling restrictions.
4. **Concurrency management:** Protect booking operations using transactional provider-level pessimistic locking.
5. **Multithreading:** Process email delivery asynchronously using Spring-managed background threads.
6. **Testing:** Validate business rules, concurrency scenarios, and asynchronous execution.
7. **Logging:** Track important operations, failures, and concurrency behavior.
8. **Documentation:** Improve Swagger documentation and maintain the project README.
