package com.flightbooking.repositories

import com.flightbooking.models.User
import com.flightbooking.tables.UserTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import kotlin.test.*

class UserRepositoryTest {
    private val repo = UserRepository()

    @Before
    fun setup() {
        Database.connect(
            url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.create(UserTable)
        }
    }

    @After
    fun teardown() {
        transaction {
            SchemaUtils.drop(UserTable)
        }
    }

    private fun createTestUser(): User {
        return User(
            id = 0,
            firstname = "John",
            lastname = "Doe",
            email = "john@test.com",
            passwordHash = "hash123",
            role = "USER",
            loyaltyPoints = 10,
            redeemedLoyaltyPoints = 0,
            createdAt = LocalDateTime.now(),
        )
    }

    // ✅ CREATE + GET
    @Test
    fun `should create and fetch user by email`() {
        val user = createTestUser()
        repo.createUser(user)
        val fetched = repo.getUserByEmail(user.email)
        assertNotNull(fetched)
        assertEquals("John", fetched?.firstname)
        assertEquals("Doe", fetched?.lastname)
    }

    // ✅ GET BY ID
    @Test
    fun `should fetch user by id`() {
        val user = createTestUser()
        repo.createUser(user)
        val created = repo.getUserByEmail(user.email)!!
        val fetched = repo.getUserById(created.id)
        assertNotNull(fetched)
        assertEquals(created.id, fetched?.id)
    }

    // ✅ RETURN NULL FOR NON-EXISTENT EMAIL
    @Test
    fun `should return null for non-existent email`() {
        val result = repo.getUserByEmail("nobody@test.com")
        assertNull(result)
    }

    // ✅ RETURN NULL FOR NON-EXISTENT ID
    @Test
    fun `should return null for non-existent id`() {
        val result = repo.getUserById(99999)
        assertNull(result)
    }

    // ✅ UPDATE NAME
    @Test
    fun `should update user name`() {
        val user = createTestUser()
        repo.createUser(user)
        val created = repo.getUserByEmail(user.email)!!
        repo.updateNameForUser(created.id, "Alice", "Smith")
        val updated = repo.getUserById(created.id)
        assertEquals("Alice", updated?.firstname)
        assertEquals("Smith", updated?.lastname)
    }

    @Test
    fun `should allow updating name to null`() {
        val user = createTestUser()
        repo.createUser(user)
        val created = repo.getUserByEmail(user.email)!!
        repo.updateNameForUser(created.id, null, null)
        val updated = repo.getUserById(created.id)
        assertNull(updated?.firstname)
        assertNull(updated?.lastname)
    }

    @Test
    fun `should change password`() {
        val user = createTestUser()
        repo.createUser(user)
        val created = repo.getUserByEmail(user.email)!!
        repo.changePasswordForUser(created.id, "newHash")
        val updated = repo.getUserById(created.id)
        assertEquals("newHash", updated?.passwordHash)
    }

    @Test
    fun `should delete user`() {
        val user = createTestUser()
        repo.createUser(user)
        val created = repo.getUserByEmail(user.email)!!
        repo.deleteUser(created.id)
        val deleted = repo.getUserById(created.id)
        assertNull(deleted)
    }

    @Test
    fun `should not throw when deleting non-existent user`() {
        assertDoesNotThrow {
            repo.deleteUser(99999)
        }
    }

    @Test
    fun `should return correct initials when both names present`() {
        val user = createTestUser()
        val initials = repo.getInitialsByUser(user)
        assertEquals("JD", initials)
    }

    @Test
    fun `should return lastname initial when firstname is missing`() {
        val user = createTestUser().copy(firstname = null)
        val initials = repo.getInitialsByUser(user)
        assertEquals("D", initials)
    }

    @Test
    fun `should return firstname initial when lastname is missing`() {
        val user = createTestUser().copy(lastname = null)
        val initials = repo.getInitialsByUser(user)
        assertEquals("J", initials)
    }

    // ✅ INITIALS - fallback to email when no names
    @Test
    fun `should fallback to email initials when no names present`() {
        val user = createTestUser().copy(firstname = null, lastname = null)
        val initials = repo.getInitialsByUser(user)
        assertEquals("JO", initials) // from "john@test.com"
    }

    // ✅ LOYALTY POINTS - persisted correctly on create
    @Test
    fun `should persist loyalty points on create`() {
        val user = createTestUser().copy(loyaltyPoints = 100, redeemedLoyaltyPoints = 25)
        repo.createUser(user)
        val fetched = repo.getUserByEmail(user.email)
        assertEquals(100, fetched?.loyaltyPoints)
        assertEquals(25, fetched?.redeemedLoyaltyPoints)
    }

    @Test
    fun `should persist role on create`() {
        val user = createTestUser().copy(role = "ADMIN")
        repo.createUser(user)
        val fetched = repo.getUserByEmail(user.email)
        assertEquals("ADMIN", fetched?.role)
    }
}
