package com.flightbooking.repositories

import com.flightbooking.enums.UserRole
import com.flightbooking.models.User
import com.flightbooking.tables.UserTable
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime

class UserRepositoryTest : StringSpec({

    lateinit var repo: UserRepository
    var databaseFile: Path? = null

    beforeTest {
        databaseFile = Files.createTempFile("user-repository-test", ".sqlite")

        Database.connect(
            url = "jdbc:sqlite:${databaseFile!!.toAbsolutePath()}",
            driver = "org.sqlite.JDBC",
        )

        transaction {
            SchemaUtils.create(UserTable)
        }

        repo = UserRepository()
    }

    afterTest {
        transaction {
            SchemaUtils.drop(UserTable)
        }

        databaseFile?.let {
            Files.deleteIfExists(it)
        }
    }

    fun createTestUser(): User {
        return User(
            id = 0,
            firstname = "John",
            lastname = "Doe",
            email = "john@test.com",
            passwordHash = "hash123",
            role = UserRole.ADMIN,
            loyaltyPoints = 10,
            redeemedLoyaltyPoints = 0,
            createdAt = LocalDateTime.now(),
        )
    }

    "should create and fetch user by email" {
        val user = createTestUser()
        repo.createUser(user)

        val fetched = repo.getUserByEmail(user.email)

        fetched.shouldNotBeNull()
        fetched.firstname shouldBe "John"
        fetched.lastname shouldBe "Doe"
    }

    "should fetch user by id" {
        val user = createTestUser()
        repo.createUser(user)

        val created = repo.getUserByEmail(user.email)!!
        val fetched = repo.getUserById(created.id)

        fetched.shouldNotBeNull()
        fetched.id shouldBe created.id
    }

    "should return null for non-existent email" {
        repo.getUserByEmail("nobody@test.com").shouldBeNull()
    }

    "should return null for non-existent id" {
        repo.getUserById(99999).shouldBeNull()
    }

    "should update user name" {
        val user = createTestUser()
        repo.createUser(user)

        val created = repo.getUserByEmail(user.email)!!
        repo.updateNameForUser(created.id, "Alice", "Smith")

        val updated = repo.getUserById(created.id)
        updated?.firstname shouldBe "Alice"
        updated?.lastname shouldBe "Smith"
    }

    "should allow updating name to null" {
        val user = createTestUser()
        repo.createUser(user)

        val created = repo.getUserByEmail(user.email)!!
        repo.updateNameForUser(created.id, null, null)

        val updated = repo.getUserById(created.id)
        updated?.firstname.shouldBeNull()
        updated?.lastname.shouldBeNull()
    }

    "should change password" {
        val user = createTestUser()
        repo.createUser(user)

        val created = repo.getUserByEmail(user.email)!!
        repo.changePasswordForUser(created.id, "newHash")

        val updated = repo.getUserById(created.id)
        updated?.passwordHash shouldBe "newHash"
    }

    "should delete user" {
        val user = createTestUser()
        repo.createUser(user)

        val created = repo.getUserByEmail(user.email)!!
        repo.deleteUser(created.id)

        repo.getUserById(created.id).shouldBeNull()
    }

    "should not throw when deleting non-existent user" {
        repo.deleteUser(99999)
    }

    "should return correct initials when both names present" {
        val user = createTestUser()
        repo.getInitialsByUser(user) shouldBe "JD"
    }

    "should return lastname initial when firstname is missing" {
        val user = createTestUser().copy(firstname = null)
        repo.getInitialsByUser(user) shouldBe "D"
    }

    "should return firstname initial when lastname is missing" {
        val user = createTestUser().copy(lastname = null)
        repo.getInitialsByUser(user) shouldBe "J"
    }

    "should fallback to email initials when no names present" {
        val user = createTestUser().copy(firstname = null, lastname = null)
        repo.getInitialsByUser(user) shouldBe "JO"
    }

    "should persist loyalty points on create" {
        val user = createTestUser().copy(loyaltyPoints = 100, redeemedLoyaltyPoints = 25)
        repo.createUser(user)

        val fetched = repo.getUserByEmail(user.email)
        fetched?.loyaltyPoints shouldBe 100
        fetched?.redeemedLoyaltyPoints shouldBe 25
    }

    "should persist role on create" {
        val user = createTestUser()
        repo.createUser(user)

        val fetched = repo.getUserByEmail(user.email)
        fetched.shouldNotBeNull()
        fetched.role shouldBe UserRole.ADMIN
    }
})
