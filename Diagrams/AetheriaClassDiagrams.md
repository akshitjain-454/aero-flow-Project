# Aero Flow — Class Diagrams

---

## Diagram 1: Enumerations

```mermaid
classDiagram
    class BookingStatus {
        CREATED
        CONFIRMED
        CANCELLED
    }

    class FlightStatus {
        SCHEDULED
        DELAYED
        ARRIVED
        CANCELLED
    }

    class PaymentStatus {
        PENDING
        COMPLETED
        FAILED
        REFUNDED
    }

    class PaymentMethod {
        CARD
        PAYPAL
        BANK_TRANSFER
    }

    class UserRole {
        USER
        ADMIN
    }

    class SeatClass {
        ECONOMY
        BUSINESS
        FIRST
    }

    class ComplaintStatus {
        OPEN
        IN_REVIEW
        RESOLVED
        CLOSED
    }

    class FlightInfoRequestStatus {
        PENDING
        APPROVED
        REJECTED
    }

    class FlightInfoRequestType {
        PASSENGER_INFO
        FLIGHT_CHANGE
        BOTH
    }
```

---

## Diagram 2: Core Data Models

```mermaid
classDiagram
    class User {
        +Int id
        +String? firstname
        +String? lastname
        +String email
        +String passwordHash
        +UserRole role
        +Int loyaltyPoints
        +Int redeemedLoyaltyPoints
        +LocalDateTime createdAt
    }

    class Booking {
        +Int id
        +String bookingReference
        +Int userId
        +Int flightId
        +Int? returnFlightId
        +BookingStatus status
        +LocalDateTime createdAt
    }

    class Flight {
        +Int id
        +String flightCode
        +Int departureAirportId
        +Int arrivalAirportId
        +Int aircraftId
        +LocalDateTime departureTime
        +LocalDateTime arrivalTime
        +BigDecimal minPrice
        +FlightStatus status
    }

    class Aircraft {
        +Int id
        +String type
        +Int numOfSeats
    }

    class Airport {
        +Int id
        +String name
        +String code
        +String city
        +String country
    }

    class Passenger {
        +Int id
        +Int bookingId
        +String firstname
        +String? lastname
        +String? passportCode
    }

    class Payment {
        +Int id
        +Int bookingId
        +BigDecimal amount
        +PaymentStatus paymentStatus
        +PaymentMethod paymentMethod
        +String transactionId
        +LocalDateTime createdAt
        +BigDecimal? refundAmount
        +LocalDateTime? refundDate
    }

    class Complaint {
        +Int id
        +Int userId
        +String message
        +ComplaintStatus status
        +LocalDateTime createdAt
        +String? adminReply
        +LocalDateTime? repliedAt
        +Int? repliedByUserId
    }

    class Seat {
        +Int id
        +Int aircraftId
        +String seatNumber
        +SeatClass seatClass
    }

    class TicketAssignment {
        +Int id
        +Int passengerId
        +Int flightSeatId
        +BigDecimal ticketPrice
        +String seatNumber
    }

    class FlightSeat {
        +Int id
        +Int flightId
        +Int seatId
    }

    User "1" --> "*" Booking : creates
    User "1" --> "*" Complaint : submits
    Booking "1" --> "*" Passenger : contains
    Booking "1" --> "*" Payment : has
    Booking "1" --> "1" Flight : outbound
    Booking "0..1" --> "1" Flight : return
    Flight "1" --> "*" FlightSeat : has
    Flight "1" --> "1" Aircraft : uses
    Flight "1" --> "1" Airport : departs from
    Flight "1" --> "1" Airport : arrives at
    Aircraft "1" --> "*" Seat : has
    Passenger "1" --> "*" TicketAssignment : assigned
    FlightSeat "1" --> "0..1" TicketAssignment : has
```

---

## Diagram 3: Core Models and Enum Dependencies

```mermaid
classDiagram
    class Booking {
        +BookingStatus status
    }
    class Flight {
        +FlightStatus status
    }
    class Payment {
        +PaymentStatus paymentStatus
        +PaymentMethod paymentMethod
    }
    class User {
        +UserRole role
    }
    class Seat {
        +SeatClass seatClass
    }
    class Complaint {
        +ComplaintStatus status
    }

    class BookingStatus {
        CREATED
        CONFIRMED
        CANCELLED
    }
    class FlightStatus {
        SCHEDULED
        DELAYED
        ARRIVED
        CANCELLED
    }
    class PaymentStatus {
        PENDING
        COMPLETED
        FAILED
        REFUNDED
    }
    class PaymentMethod {
        CARD
        PAYPAL
        BANK_TRANSFER
    }
    class UserRole {
        USER
        ADMIN
    }
    class SeatClass {
        ECONOMY
        BUSINESS
        FIRST
    }
    class ComplaintStatus {
        OPEN
        IN_REVIEW
        RESOLVED
        CLOSED
    }

    Booking --> BookingStatus : status
    Flight --> FlightStatus : status
    Payment --> PaymentStatus : paymentStatus
    Payment --> PaymentMethod : paymentMethod
    User --> UserRole : role
    Seat --> SeatClass : seatClass
    Complaint --> ComplaintStatus : status
```

---

## Diagram 4a: Response / DTO Models (Flight and Booking)

```mermaid
classDiagram
    class SeatAvailability {
        +Int flightSeatId
        +String seatNumber
        +SeatClass seatClass
        +Boolean available
    }

    class FlightInfo {
        +String flightCode
        +String departureAirport
        +String departureAirportCode
        +String arrivalAirport
        +String arrivalAirportCode
        +LocalDateTime departureTime
        +BigDecimal priceFrom
    }

    class BookingInfo {
        +String bookingReference
        +String flightCode
        +String? returnFlightCode
        +BookingStatus bookingStatus
        +Long numOfPassengers
        +String departureAirportNameCode
        +String arrivalAirportNameCode
        +String? returnDepartureAirportNameCode
        +String? returnArrivalAirportNameCode
        +LocalDateTime departureTime
        +LocalDateTime? returnDepartureTime
        +FlightStatus flightStatus
        +BigDecimal? amountPaid
    }

    class TicketInfo {
        +String passengerName
        +String bookingReference
        +String seatNumber
        +SeatClass seatClass
        +String departureAirportNameCode
        +String arrivalAirportNameCode
        +LocalDateTime dateTime
    }
```

---

## Diagram 4b: Response / DTO Models (Reports and Requests)

```mermaid
classDiagram
    class BookingsPerFlightReport {
        +String flightCode
        +Int bookingCount
        +String aircraftType
    }

    class FlightAvailabilitySummary {
        +String flightCode
        +Int totalSeats
        +Int bookedSeats
        +Int availableSeats
    }

    class ComplaintSummary {
        +Int id
        +String message
        +ComplaintStatus status
        +LocalDateTime createdAt
        +String? adminReply
        +String? responderName
    }

    class FlightInfoRequestSummary {
        +String bookingReference
        +String customerName
        +String email
        +String currentFlightCode
        +String? requestedFlightCode
        +FlightInfoRequestType requestType
        +FlightInfoRequestStatus status
        +String? newFirstname
        +String? newLastname
        +String? newPassportCode
        +String? message
        +String? adminReply
    }
```

---

## Diagram 5: Sessions and Notification Service

```mermaid
classDiagram
    class UserSession {
        +Int userId
        +UserRole role
        +String initials
    }

    class VerificationSession {
        +String email
        +String otp
        +Boolean verified
    }

    class NotificationEvent {
        +Int userId
        +String message
        +String type
        +Long sentAt
    }

    class NotificationService {
        +send(event: NotificationEvent)
        +events SharedFlow~NotificationEvent~
    }

    NotificationService --> NotificationEvent : emits
    UserSession --> UserRole : role
```

---

## Diagram 6a: Repositories (User and Booking)

```mermaid
classDiagram
    class UserRepository {
        +createUser(user: User)
        +deleteUser(userId: Int)
        +updateNameForUser(userId: Int, firstname: String?, lastname: String?)
        +changePasswordForUser(userId: Int, newPasswordHash: String)
        +getUserByEmail(email: String) User?
        +getUserById(id: Int) User?
        +getInitialsByUser(user: User) String
        +sendEmail(email: String, subject: String, body: String)
        +resultRowToUser(row: ResultRow) User
    }

    class BookingRepository {
        +createBooking(userId: Int, flightId: Int, returnFlightId: Int?) Booking
        +createPayment(bookingId: Int, amount: BigDecimal, paymentMethod: PaymentMethod) Payment
        +addPassenger(bookingId: Int, firstname: String, lastname: String, passportCode: String?) Passenger
        +ticketAssignment(passengerId: Int, flightSeatId: Int, ticketPrice: BigDecimal, seatNumber: String) TicketAssignment
        +confirmBooking(booking: Booking) Int
        +cancelBooking(bookingReference: String) Booking?
        +deleteOldSeatSelectionsByBookingReference(bookingReference: String)
        +deleteBookingByReference(bookingReference: String)
        +getSeatsByFlightId(flightId: Int) List~SeatAvailability~
        +getSelectedSeatsByFlightIdAndPassengers(flightId: Int, passengers: List~Passenger~) List~SelectedSeat~
        +getPassengersByBookingId(bookingId: Int) List~Passenger~
        +getBookingsByUserId(userId: Int) List~Booking~
        +getBookingByReference(bookingReference: String) Booking?
        +getBookingPricePriceByBookingId(bookingId: Int) BigDecimal
        +getTicketInfoByPassengerAndBooking(passenger: Passenger, booking: Booking) TicketInfo
        +getReturnTicketInfoByPassengerAndBooking(passenger: Passenger, booking: Booking) TicketInfo
        +getBookingInfoByBooking(booking: Booking) BookingInfo
        +getSeatClassByFlightSeatId(flightSeatId: Int) SeatClass?
        +getSeatNumberByFlightSeatId(flightSeatId: Int) String?
        +calculatePrice(flightPrice: BigDecimal, seatClass: SeatClass?, date: LocalDate?) BigDecimal
        +getLoyaltyPointsByUserId(userId: Int) Int
        +getRedeemedLoyaltyPointsByUserId(userId: Int) Int
        +addLoyaltyPointsByUserIdAndBookingAmount(userId: Int, amount: BigDecimal) Int
        +useUsersLoyaltyPoints(userId: Int, price: BigDecimal) BigDecimal
        +createFlightInfoRequest(userId: Int, bookingReference: String, requestType: FlightInfoRequestType, ...) Boolean
        +getFlightInfoRequestsByUserId(userId: Int) List~FlightInfoRequestSummary~
        +generateBookingReference() String
    }
```

---

## Diagram 6b: Repositories (Flight, Admin and Complaint)

```mermaid
classDiagram
    class FlightRepository {
        +searchFlights(fromCodes: List~String~, toCodes: List~String~, departureDate: LocalDate, numPassengers: Int, departureFlexibility: Int) List~FlightInfo~
        +getAirportById(id: Int) Airport?
        +getFlightByFlightCode(flightCode: String) Flight?
        +getFlightByFlightId(flightId: Int) Flight?
        +getAirportBySearch(search: String) List~Airport~
        +resultRowToAirport(row: ResultRow) Airport
        +resultRowToFlight(row: ResultRow) Flight
    }

    class AdminRepository {
        +getBookingsPerFlightReport() List~BookingsPerFlightReport~
        +getBookingsPerFlightByFlightCode(flightCode: String) BookingsPerFlightReport?
        +getMostPopularRoutesReport() List~PopularRouteReport~
        +getPeakBookingTimesReport() List~PeakBookingTimeReport~
        +getFlightAvailabilityReport() List~FlightAvailabilitySummary~
        +getCancelledBookings(startDate: LocalDate?, endDate: LocalDate?) List~Booking~
        +getCancelledFlights(startDate: LocalDate?, endDate: LocalDate?) List~Flight~
        +updateFlightSchedule(flightId: Int, ...)
        +updateFlightStatus(flightId: Int, newStatus: FlightStatus)
        +getAllFlightChanges(startDate: LocalDate?, endDate: LocalDate?, flightCode: String?) List~FlightChangeLog~
        +getAllFlightInfoRequests() List~FlightInfoRequestSummary~
        +approveFlightInfoRequest(requestId: Int, handledByUserId: Int, adminReply: String?)
        +rejectFlightInfoRequest(requestId: Int, handledByUserId: Int, adminReply: String)
    }

    class ComplaintRepository {
        +createComplaint(userId: Int, message: String) Int
        +getComplaintsByUserId(userId: Int) List~Complaint~
        +getAllComplaints() List~ComplaintSummary~
        +getComplaintById(complaintId: Int) Complaint?
        +updateComplaintStatus(complaintId: Int, status: ComplaintStatus)
        +handleComplaint(complaintId: Int, adminReply: String, repliedByUserId: Int)
        +resultRowToComplaint(row: ResultRow) Complaint
    }
```

---

## Diagram 7: Routes and Their Repository Dependencies

```mermaid
classDiagram
    class UserRoutes {
        +registerRoutes()
        +loginRoutes()
        +overviewRoutes()
    }

    class BookingRoutes {
        +createBookingRoutes()
        +passengerRoutes()
        +seatSelectionRoutes()
        +paymentRoutes()
        +confirmationRoutes()
        +ticketRoutes()
        +cancelRoutes()
    }

    class FlightRoutes {
        +searchRoutes()
        +airportRoutes()
    }

    class ComplaintRoutes {
        +complaintRoutes()
        +submitRoutes()
        +myComplaintsRoutes()
    }

    class AdminRoutes {
        +adminDashboardRoutes()
        +complaintManagementRoutes()
        +flightInfoRequestRoutes()
        +reportRoutes()
        +flightManagementRoutes()
    }

    class NotificationRoutes {
        +streamRoutes()
    }

    class UserRepository
    class BookingRepository
    class FlightRepository
    class ComplaintRepository
    class AdminRepository
    class NotificationService

    UserRoutes --> UserRepository : uses
    BookingRoutes --> BookingRepository : uses
    FlightRoutes --> FlightRepository : uses
    ComplaintRoutes --> ComplaintRepository : uses
    AdminRoutes --> AdminRepository : uses
    AdminRoutes --> NotificationService : sends notifications
    NotificationRoutes --> NotificationService : streams events
```

---

## Diagram 8: Full Architecture — Layer Dependencies

```mermaid
classDiagram
    class DataLayer {
        Enums
        Models
        Tables
        DatabaseFactory
    }

    class RepositoryLayer {
        UserRepository
        BookingRepository
        FlightRepository
        AdminRepository
        ComplaintRepository
    }

    class ApplicationLayer {
        Routes
        Sessions
        NotificationService
    }

    ApplicationLayer --> RepositoryLayer : calls
    RepositoryLayer --> DataLayer : queries
    ApplicationLayer --> DataLayer : uses models and enums
```
