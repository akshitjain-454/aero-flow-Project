import com.flightbooking.enums.BookingStatus
import com.flightbooking.enums.FlightStatus
import com.flightbooking.enums.PaymentMethod
import com.flightbooking.enums.PaymentStatus
import com.flightbooking.enums.SeatClass
import com.flightbooking.enums.UserRole
import com.flightbooking.repositories.BookingRepository
import com.flightbooking.tables.AircraftTable
import com.flightbooking.tables.AirportTable
import com.flightbooking.tables.BookingTable
import com.flightbooking.tables.FlightSeatTable
import com.flightbooking.tables.FlightTable
import com.flightbooking.tables.PassengerTable
import com.flightbooking.tables.PaymentTable
import com.flightbooking.tables.SeatTable
import com.flightbooking.tables.TicketAssignmentTable
import com.flightbooking.tables.UserTable
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldHaveLength
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalDateTime

class BookingRepositoryTest : StringSpec({

    lateinit var repository: BookingRepository
    var databaseFile: Path? = null
    var airportCounter = 0
    var flightCounter = 0

    beforeTest {
        airportCounter = 0
        flightCounter = 0
        databaseFile = Files.createTempFile("booking-repository-test", ".sqlite")

        Database.connect(
            url = "jdbc:sqlite:${databaseFile!!.toAbsolutePath()}",
            driver = "org.sqlite.JDBC",
        )

        transaction {

            SchemaUtils.create(
                UserTable,
                AirportTable,
                AircraftTable,
                SeatTable,
                FlightTable,
                FlightSeatTable,
                BookingTable,
                PassengerTable,
                TicketAssignmentTable,
                PaymentTable,
            )
        }

        repository = BookingRepository()
    }

    afterTest {
        transaction {
            SchemaUtils.drop(
                PaymentTable,
                TicketAssignmentTable,
                PassengerTable,
                BookingTable,
                FlightSeatTable,
                SeatTable,
                FlightTable,
                AircraftTable,
                AirportTable,
                UserTable,
            )
        }

        databaseFile?.let {
            Files.deleteIfExists(it)
        }
    }

    fun createTestUser(email: String = "test@example.com"): Int =
        transaction {
            UserTable.insert {
                it[UserTable.email] = email
                it[firstname] = "Test"
                it[lastname] = "User"
                it[passwordHash] = "hash"
                it[role] = UserRole.USER
                it[UserTable.loyaltyPoints] = 0
                it[UserTable.redeemedLoyaltyPoints] = 0
                it[createdAt] = LocalDateTime.now()
            } get UserTable.id
        }

    fun createTestAirport(): Int =
        transaction {
            AirportTable.insert {
                it[code] = "A%02d".format(airportCounter++)
                it[name] = "Test Airport"
                it[city] = "City"
                it[country] = "Country"
            } get AirportTable.id
        }

    fun createTestAircraft(): Int =
        transaction {
            AircraftTable.insert {
                it[type] = "Boeing"
                it[numOfSeats] = 100
            } get AircraftTable.id
        }

    fun createTestFlight(): Int =
        transaction {
            val dep = createTestAirport()
            val arr = createTestAirport()
            val aircraft = createTestAircraft()

            FlightTable.insert {
                it[flightCode] = "FL%05d".format(flightCounter++)
                it[departureAirportId] = dep
                it[arrivalAirportId] = arr
                it[aircraftId] = aircraft
                it[departureTime] = LocalDateTime.now().plusDays(7)
                it[arrivalTime] = LocalDateTime.now().plusDays(7).plusHours(2)
                it[minPrice] = BigDecimal("100.00")
                it[status] = FlightStatus.SCHEDULED
            } get FlightTable.id
        }

    fun createTestSeat(
        aircraftId: Int,
        seatNumber: String = "10A",
        seatClass: SeatClass = SeatClass.ECONOMY,
    ): Int =
        transaction {
            SeatTable.insert {
                it[SeatTable.aircraftId] = aircraftId
                it[SeatTable.seatNumber] = seatNumber
                it[SeatTable.seatClass] = seatClass
            } get SeatTable.id
        }

    fun createTestFlightSeat(
        flightId: Int,
        seatNumber: String = "10A",
        seatClass: SeatClass = SeatClass.ECONOMY,
    ): Int =
        transaction {
            val aircraftId =
                FlightTable
                    .select { FlightTable.id eq flightId }
                    .single()[FlightTable.aircraftId]

            val seatId = createTestSeat(aircraftId, seatNumber, seatClass)

            FlightSeatTable.insert {
                it[FlightSeatTable.flightId] = flightId
                it[FlightSeatTable.seatId] = seatId
            } get FlightSeatTable.id
        }

    "Create booking with valid data" {
        val userId = createTestUser()
        val flightId = createTestFlight()
        createTestFlightSeat(flightId = flightId)

        val booking = repository.createBooking(userId, flightId, null)

        booking.userId shouldBe userId
        booking.flightId shouldBe flightId
        booking.returnFlightId.shouldBeNull()
        booking.status shouldBe BookingStatus.CREATED
        booking.bookingReference shouldHaveLength 8
        booking.createdAt.shouldNotBeNull()
    }

    "Create booking with return flight" {
        val userId = createTestUser()
        val flightId = createTestFlight()
        val returnFlightId = createTestFlight()
        createTestFlightSeat(flightId = flightId)

        val booking = repository.createBooking(userId, flightId, returnFlightId)

        booking.returnFlightId shouldBe returnFlightId
    }

    "Generate unique booking references" {
        val userId = createTestUser()
        val flightId = createTestFlight()
        createTestFlightSeat(flightId = flightId)

        val b1 = repository.createBooking(userId, flightId, null)
        val b2 = repository.createBooking(userId, flightId, null)

        b1.bookingReference shouldNotBe b2.bookingReference
    }

    "Create payment with completed status" {
        val userId = createTestUser()
        val flightId = createTestFlight()
        createTestFlightSeat(flightId = flightId)
        val booking = repository.createBooking(userId, flightId, null)

        val payment =
            repository.createPayment(
                booking.id,
                BigDecimal("250.00"),
                PaymentMethod.CARD,
            )

        payment.bookingId shouldBe booking.id
        payment.paymentStatus shouldBe PaymentStatus.COMPLETED
        payment.transactionId shouldHaveLength 10
    }

    "Get booking by reference returns existing booking" {
        val userId = createTestUser()
        val flightId = createTestFlight()
        createTestFlightSeat(flightId = flightId)
        val booking = repository.createBooking(userId, flightId, null)

        val result = repository.getBookingByReference(booking.bookingReference)

        result.shouldNotBeNull()
        result.id shouldBe booking.id
    }

    "Get booking by reference returns null for invalid reference" {
        repository.getBookingByReference("INVALID123").shouldBeNull()
    }

    "Cancel booking updates status" {
        val userId = createTestUser()
        val flightId = createTestFlight()
        createTestFlightSeat(flightId = flightId)
        val booking = repository.createBooking(userId, flightId, null)

        val cancelled = repository.cancelBooking(booking.bookingReference)

        cancelled.shouldNotBeNull()
        cancelled.status shouldBe BookingStatus.CANCELLED
    }

    "Confirm booking updates status to confirmed" {
        val userId = createTestUser()
        val flightId = createTestFlight()
        createTestFlightSeat(flightId = flightId)
        val booking = repository.createBooking(userId, flightId, null)

        repository.confirmBooking(booking)

        val updated = repository.getBookingByReference(booking.bookingReference)
        updated?.status shouldBe BookingStatus.CONFIRMED
    }

    "Calculate price applies multipliers correctly" {
        val price =
            repository.calculatePrice(
                BigDecimal("100.00"),
                SeatClass.FIRST,
                LocalDate.now(),
            )

        price shouldBe BigDecimal("525.00")
    }

    "Get passengers by booking returns list" {
        val userId = createTestUser()
        val flightId = createTestFlight()
        createTestFlightSeat(flightId = flightId)
        val booking = repository.createBooking(userId, flightId, null)

        repository.addPassenger(booking.id, "John", "Doe", null)
        repository.addPassenger(booking.id, "Jane", "Doe", null)

        val passengers = repository.getPassengersByBookingId(booking.id)

        passengers shouldHaveSize 2
    }

    "Delete booking removes data" {
        val userId = createTestUser()
        val flightId = createTestFlight()
        createTestFlightSeat(flightId = flightId)
        val booking = repository.createBooking(userId, flightId, null)

        repository.deleteBookingByReference(booking.bookingReference)

        repository.getBookingByReference(booking.bookingReference).shouldBeNull()
    }

    "Get loyalty points by user id" {
        val userId = createTestUser()

        val points = repository.getLoyaltyPointsByUserId(userId)

        points shouldBe 0
    }

    "Add loyalty points by user id and booking amount" {
        val userId = createTestUser()

        val pointsAdded = repository.addLoyaltyPointsByUserIdAndBookingAmount(userId, BigDecimal("250.00"))
        val pointsAfter = repository.getLoyaltyPointsByUserId(userId)

        pointsAdded shouldBe 250
        pointsAfter shouldBe 250
    }

    "Use users loyalty points reduces price and zeroes points" {
        val userId = createTestUser()
        repository.addLoyaltyPointsByUserIdAndBookingAmount(userId, BigDecimal("200.00"))

        val discountedPrice = repository.useUsersLoyaltyPoints(userId, BigDecimal("100.00"))
        val pointsAfter = repository.getLoyaltyPointsByUserId(userId)
        val redeemedAfter = repository.getRedeemedLoyaltyPointsByUserId(userId)

        discountedPrice shouldBe BigDecimal("98.00")
        pointsAfter shouldBe 0
        redeemedAfter shouldBe 200
    }

    "Use users loyalty points does not go below zero" {
        val userId = createTestUser()
        repository.addLoyaltyPointsByUserIdAndBookingAmount(userId, BigDecimal("500.00"))

        val discountedPrice = repository.useUsersLoyaltyPoints(userId, BigDecimal("3.00"))

        discountedPrice shouldBe BigDecimal("0.00")
    }

    "Get redeemed loyalty points by user id" {
        val userId = createTestUser()
        repository.addLoyaltyPointsByUserIdAndBookingAmount(userId, BigDecimal("100.00"))
        repository.useUsersLoyaltyPoints(userId, BigDecimal("50.00"))

        val redeemed = repository.getRedeemedLoyaltyPointsByUserId(userId)

        redeemed shouldBe 100
    }

    "Ticket assignment creates record" {
        val userId = createTestUser()
        val flightId = createTestFlight()
        val flightSeatId = createTestFlightSeat(flightId = flightId)
        val booking = repository.createBooking(userId, flightId, null)
        val passenger = repository.addPassenger(booking.id, "John", "Doe", null)

        val assignment =
            repository.ticketAssignment(
                passengerId = passenger.id,
                flightSeatId = flightSeatId,
                ticketPrice = BigDecimal("199.99"),
                seatNumber = "10A",
            )

        assignment.passengerId shouldBe passenger.id
        assignment.flightSeatId shouldBe flightSeatId
        assignment.ticketPrice shouldBe BigDecimal("199.99")
        assignment.seatNumber shouldBe "10A"
    }

    "Delete old seat selections by booking reference removes ticket assignments" {
        val userId = createTestUser()
        val flightId = createTestFlight()
        val flightSeatId = createTestFlightSeat(flightId = flightId)
        val booking = repository.createBooking(userId, flightId, null)
        val passenger = repository.addPassenger(booking.id, "John", "Doe", null)
        repository.ticketAssignment(passenger.id, flightSeatId, BigDecimal("100.00"), "10A")

        repository.deleteOldSeatSelectionsByBookingReference(booking.bookingReference)

        val seats = repository.getSeatsByFlightId(flightId)
        seats.single().available shouldBe true
    }

    "Get seats by flight id returns availability" {
        val flightId = createTestFlight()
        val flightSeatId = createTestFlightSeat(flightId = flightId, seatNumber = "10A")

        val seats = repository.getSeatsByFlightId(flightId)

        seats shouldHaveSize 1
        seats.single().available shouldBe true
        seats.single().seatNumber shouldBe "10A"
    }

    "Get seats by flight id shows unavailable when assigned" {
        val userId = createTestUser()
        val flightId = createTestFlight()
        val flightSeatId = createTestFlightSeat(flightId = flightId)
        val booking = repository.createBooking(userId, flightId, null)
        val passenger = repository.addPassenger(booking.id, "John", "Doe", null)
        repository.ticketAssignment(passenger.id, flightSeatId, BigDecimal("100.00"), "10A")

        val seats = repository.getSeatsByFlightId(flightId)

        seats.single().available shouldBe false
    }

    "Get selected seats by flight id and passengers" {
        val userId = createTestUser()
        val flightId = createTestFlight()
        val flightSeatId = createTestFlightSeat(flightId = flightId)
        val booking = repository.createBooking(userId, flightId, null)
        val passenger = repository.addPassenger(booking.id, "John", "Doe", null)
        repository.ticketAssignment(passenger.id, flightSeatId, BigDecimal("100.00"), "10A")

        val passengers = repository.getPassengersByBookingId(booking.id)
        val selectedSeats = repository.getSelectedSeatsByFlightIdAndPassengers(flightId, passengers)

        selectedSeats shouldHaveSize 1
        selectedSeats.single().passenger.id shouldBe passenger.id
        selectedSeats.single().seatNumber shouldBe "10A"
    }

    "Get bookings by user id returns all bookings for user" {
        val userId = createTestUser()
        val flightId1 = createTestFlight()
        val flightId2 = createTestFlight()
        createTestFlightSeat(flightId = flightId1)
        createTestFlightSeat(flightId = flightId2)

        repository.createBooking(userId, flightId1, null)
        repository.createBooking(userId, flightId2, null)

        val bookings = repository.getBookingsByUserId(userId)

        bookings shouldHaveSize 2
        bookings.all { it.userId == userId } shouldBe true
    }

    "Get bookings by user id returns empty for unknown user" {
        val bookings = repository.getBookingsByUserId(999)

        bookings shouldHaveSize 0
    }

    "Get booking price by booking id sums ticket prices" {
        val userId = createTestUser()
        val flightId = createTestFlight()
        val flightSeatId1 = createTestFlightSeat(flightId = flightId, seatNumber = "10A")
        val flightSeatId2 = createTestFlightSeat(flightId = flightId, seatNumber = "10B")
        val booking = repository.createBooking(userId, flightId, null)
        val p1 = repository.addPassenger(booking.id, "John", "Doe", null)
        val p2 = repository.addPassenger(booking.id, "Jane", "Doe", null)
        repository.ticketAssignment(p1.id, flightSeatId1, BigDecimal("100.00"), "10A")
        repository.ticketAssignment(p2.id, flightSeatId2, BigDecimal("150.00"), "10B")

        val total = repository.getBookingPricePriceByBookingId(booking.id)

        total shouldBe BigDecimal("250.00")
    }
})

/**
*createFlightInfoRequest
*getFlightInfoRequestsByUserId
*getBookingInfoByBooking
*getTicketInfoByPassengerAndBooking
*getReturnTicketInfoByPassengerAndBooking
*getSeatClassByFlightSeatId
*getSeatNumberByFlightSeatId
* THESE FUNCTIONS REMAIN UNTESTED - tested manually in frontend instead due to the amount of dependencies
*/
