import com.flightbooking.enums.ComplaintStatus
import com.flightbooking.enums.UserRole
import com.flightbooking.repositories.ComplaintRepository
import com.flightbooking.tables.ComplaintTable
import com.flightbooking.tables.UserTable
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime

class ComplaintRepositoryTest : StringSpec({

    lateinit var repository: ComplaintRepository
    var databaseFile: Path? = null

    beforeTest {
        databaseFile = Files.createTempFile("complaint-repository-test", ".sqlite")

        Database.connect(
            url = "jdbc:sqlite:${databaseFile!!.toAbsolutePath()}",
            driver = "org.sqlite.JDBC"
        )

        transaction {
            SchemaUtils.create(UserTable, ComplaintTable)
        }

        repository = ComplaintRepository()
    }

    afterTest {
        transaction {
            SchemaUtils.drop(ComplaintTable, UserTable)
        }

        databaseFile?.let {
            Files.deleteIfExists(it)
        }
    }

    fun createTestUser(
        email: String,
        role: UserRole = UserRole.USER,
        firstname: String = "Test",
        lastname: String = "User"
    ): Int = transaction {
        UserTable.insert {
            it[UserTable.firstname] = firstname
            it[UserTable.lastname] = lastname
            it[UserTable.email] = email
            it[UserTable.passwordHash] = "hashed-password"
            it[UserTable.role] = role
            it[UserTable.loyaltyPoints] = 0
            it[UserTable.redeemedLoyaltyPoints] = 0
            it[UserTable.createdAt] = LocalDateTime.now()
        } get UserTable.id
    }

    "Create complaint stores message with OPEN status" {
        // Arrange
        val userId = createTestUser("customer1@test.com")
        // Act
        val complaint = repository.createComplaint(
            userId = userId,
            message = "My flight was delayed"
        )
        // Assert
        complaint.userId shouldBe userId
        complaint.message shouldBe "My flight was delayed"
        complaint.status shouldBe ComplaintStatus.OPEN
        complaint.adminReply shouldBe null
        complaint.repliedAt shouldBe null
        complaint.repliedByUserId shouldBe null
    }

    "Create complaint stores special character message without crashing" {
        // Arrange: create a test user and prepare a complaint message with special characters
        val userId = createTestUser("special@test.com")
        val message = "Delayed flight! @#$%^&*() 中文 test 123"
        // Act: create a complaint using the special character message
        val complaint = repository.createComplaint(
            userId = userId,
            message = message
        )
        // Assert: the complaint should be saved exactly as submitted
        complaint.userId shouldBe userId
        complaint.message shouldBe message
        complaint.status shouldBe ComplaintStatus.OPEN
    }

    "Create complaint stores script-like input without crashing" {
        // Arrange: create a test user and prepare a script-like complaint message
        val userId = createTestUser("xss@test.com")
        val message = "<script>alert('xss')</script>"
        // Act: create a complaint using the script-like message
        val complaint = repository.createComplaint(
            userId = userId,
            message = message
        )
        // Assert: the repository should store the input as a normal string value
        complaint.userId shouldBe userId
        complaint.message shouldBe message
        complaint.status shouldBe ComplaintStatus.OPEN
    }
    "Get complaint by id returns existing complaint" {
        // Arrange
        val userId = createTestUser("customer2@test.com")
        val createdComplaint = repository.createComplaint(
            userId = userId,
            message = "Lost baggage issue"
        )
        // Act
        val foundComplaint = repository.getComplaintById(createdComplaint.id)
        // Assert
        foundComplaint shouldNotBe null
        foundComplaint!!.id shouldBe createdComplaint.id
        foundComplaint.userId shouldBe userId
        foundComplaint.message shouldBe "Lost baggage issue"
        foundComplaint.status shouldBe ComplaintStatus.OPEN
    }

    "Get complaint by id returns null for unknown id" {
        // Act
        val result = repository.getComplaintById(99999)
        // Assert
        result shouldBe null
    }

    "Get complaints by user id returns only that user's complaints" {
        // Arrange
        val userOneId = createTestUser("customer3@test.com")
        val userTwoId = createTestUser("customer4@test.com")
        repository.createComplaint(
            userId = userOneId,
            message = "First user complaint"
        )
        repository.createComplaint(
            userId = userTwoId,
            message = "Second user complaint"
        )
        // Act
        val userOneComplaints = repository.getComplaintsByUserId(userOneId)
        // Assert
        userOneComplaints.size shouldBe 1
        userOneComplaints[0].userId shouldBe userOneId
        userOneComplaints[0].message shouldBe "First user complaint"
    }

    "Get complaints by user id returns empty list when user has no complaints" {
        // Arrange: create a user who has not submitted any complaints
        val userId = createTestUser("nocomplaints@test.com")
        // Act: retrieve complaints for that user
        val complaints = repository.getComplaintsByUserId(userId)
        // Assert: the repository should return an empty list
        complaints.size shouldBe 0
    }

    "Update complaint status changes status" {
        // Arrange
        val userId = createTestUser("customer5@test.com")
        val complaint = repository.createComplaint(
            userId = userId,
            message = "Refund has not arrived"
        )
        // Act
        val updatedComplaint = repository.updateComplaintStatus(
            id = complaint.id,
            newStatus = ComplaintStatus.IN_REVIEW
        )
        // Assert
        updatedComplaint shouldNotBe null
        updatedComplaint!!.id shouldBe complaint.id
        updatedComplaint.status shouldBe ComplaintStatus.IN_REVIEW
    }

    "Update complaint status returns null for unknown id" {
        // Act
        val result = repository.updateComplaintStatus(
            id = 99999,
            newStatus = ComplaintStatus.RESOLVED
        )
        // Assert
        result shouldBe null
    }

    "Handle complaint stores admin reply and updated status" {
        // Arrange
        val customerId = createTestUser("customer6@test.com")
        val adminId = createTestUser(
            email = "admin1@test.com",
            role = UserRole.ADMIN,
            firstname = "Admin",
            lastname = "User"
        )
        val complaint = repository.createComplaint(
            userId = customerId,
            message = "The booking page crashed"
        )
        // Act
        val handledComplaint = repository.handleComplaint(
            id = complaint.id,
            newStatus = ComplaintStatus.RESOLVED,
            reply = "We have fixed this issue.",
            adminUserId = adminId
        )
        // Assert
        handledComplaint shouldNotBe null
        handledComplaint!!.id shouldBe complaint.id
        handledComplaint.status shouldBe ComplaintStatus.RESOLVED
        handledComplaint.adminReply shouldBe "We have fixed this issue."
        handledComplaint.repliedByUserId shouldBe adminId
        handledComplaint.repliedAt shouldNotBe null
    }

    "Handle complaint with blank reply updates status without saving admin reply" {
        // Arrange
        val customerId = createTestUser("customer7@test.com")
        val adminId = createTestUser(
            email = "admin2@test.com",
            role = UserRole.ADMIN
        )
        val complaint = repository.createComplaint(
            userId = customerId,
            message = "Seat selection did not work"
        )
        // Act
        val handledComplaint = repository.handleComplaint(
            id = complaint.id,
            newStatus = ComplaintStatus.IN_REVIEW,
            reply = "   ",
            adminUserId = adminId
        )
        // Assert
        handledComplaint shouldNotBe null
        handledComplaint!!.status shouldBe ComplaintStatus.IN_REVIEW
        handledComplaint.adminReply shouldBe null
        handledComplaint.repliedByUserId shouldBe null
        handledComplaint.repliedAt shouldBe null
    }

    "Handle complaint returns null when complaint id does not exist" {
        // Arrange: create an admin user
        val adminId = createTestUser(
            email = "admin-not-found-test@test.com",
            role = UserRole.ADMIN
        )
        // Act: try to handle a complaint that does not exist
        val result = repository.handleComplaint(
            id = 99999,
            newStatus = ComplaintStatus.RESOLVED,
            reply = "Handled",
            adminUserId = adminId
        )
        // Assert: the repository should return null instead of crashing
        result shouldBe null
    }

    "Get all complaints excludes CLOSED complaints" {
        // Arrange
        val userId = createTestUser("customer8@test.com")
        val openComplaint = repository.createComplaint(
            userId = userId,
            message = "Visible complaint"
        )
        val closedComplaint = repository.createComplaint(
            userId = userId,
            message = "Closed complaint"
        )
        repository.updateComplaintStatus(
            id = closedComplaint.id,
            newStatus = ComplaintStatus.CLOSED
        )
        // Act
        val complaints = repository.getAllComplaints()
        val complaintIds = complaints.map { it.id }
        // Assert
        complaints.size shouldBe 1
        complaintIds.contains(openComplaint.id) shouldBe true
        complaintIds.contains(closedComplaint.id) shouldBe false
    }

    "Get all complaints includes customer details and admin reply details" {
        // Arrange
        val customerId = createTestUser(
            email = "customer9@test.com",
            firstname = "Alice",
            lastname = "Brown"
        )
        val adminId = createTestUser(
            email = "admin3@test.com",
            role = UserRole.ADMIN,
            firstname = "Admin",
            lastname = "Manager"
        )
        val complaint = repository.createComplaint(
            userId = customerId,
            message = "Payment confirmation email was missing"
        )
        repository.handleComplaint(
            id = complaint.id,
            newStatus = ComplaintStatus.RESOLVED,
            reply = "The confirmation email has been resent.",
            adminUserId = adminId
        )
        // Act
        val complaints = repository.getAllComplaints()
        val summary = complaints.single()
        // Assert
        summary.id shouldBe complaint.id
        summary.userId shouldBe customerId
        summary.firstname shouldBe "Alice"
        summary.lastname shouldBe "Brown"
        summary.email shouldBe "customer9@test.com"
        summary.message shouldBe "Payment confirmation email was missing"
        summary.status shouldBe ComplaintStatus.RESOLVED
        summary.adminReply shouldBe "The confirmation email has been resent."
        summary.repliedByUserId shouldBe adminId
        summary.repliedByName shouldBe "Admin Manager"
        summary.repliedAt shouldNotBe null
    }
})