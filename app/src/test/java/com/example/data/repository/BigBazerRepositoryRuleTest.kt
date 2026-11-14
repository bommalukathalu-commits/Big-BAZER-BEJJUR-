package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.Category
import com.example.data.model.CustomerAddress
import com.example.data.model.Product
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class BigBazerRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun customerCartAndOrder_persistsAndEnforcesIsolation() = runBlocking {
        val aliceUid = signInTestUser("alice_${UUID.randomUUID().toString().take(6)}@test.com")
        val cartRepo = CartAndOrderRepository(firestore)

        val sampleProduct = Product(
            id = "prod_test_${UUID.randomUUID().toString().take(6)}",
            sku = "SKU-101",
            nameEn = "Bangles Set",
            nameTe = "గాజుల సెట్",
            categoryId = "cat_bangles",
            originalPrice = 200.0,
            sellingPrice = 150.0,
            stock = 10L
        )

        val addRes = withTimeout(5000L) { cartRepo.addToCart(sampleProduct, quantity = 2) }
        assertTrue("Expected addToCart to succeed: ${addRes.exceptionOrNull()}", addRes.isSuccess)

        val cartItems = withTimeout(4000L) {
            cartRepo.observeCart(aliceUid).first { it.isNotEmpty() }
        }
        assertEquals(1, cartItems.size)
        assertEquals(2L, cartItems.first().quantity)

        val orderRes = withTimeout(5000L) {
            cartRepo.placeOrder(
                items = cartItems,
                productsById = emptyMap(),
                address = CustomerAddress(
                    userId = aliceUid,
                    fullName = "Alice",
                    phone = "9876543210",
                    houseStreet = "Main Road",
                    village = "Bejjur",
                    mandal = "Bejjur",
                    district = "Komaram Bheem Asifabad",
                    pinCode = "504299"
                ),
                paymentMethod = "COD",
                appliedCoupon = null,
                subtotal = 300.0,
                discountAmount = 0.0,
                deliveryCharge = 0.0,
                totalAmount = 300.0
            )
        }
        assertTrue("Expected placeOrder to succeed: ${orderRes.exceptionOrNull()}", orderRes.isSuccess)

        val orders = withTimeout(4000L) {
            cartRepo.observeUserOrders(aliceUid).first { it.isNotEmpty() }
        }
        assertEquals("NEW", orders.first().orderStatus)

        // Cross-user isolation check: Bob cannot read Alice's orders or cart
        signInTestUser("bob_${UUID.randomUUID().toString().take(6)}@test.com")
        var denied = false
        try {
            withTimeout(4000L) {
                cartRepo.observeUserOrders(aliceUid).first()
            }
        } catch (e: Exception) {
            val ffe = e.unwrapFirestoreException()
            denied = ffe?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
        }
        assertTrue("Cross-user order read must fail with PERMISSION_DENIED", denied)
    }

    @Test
    fun customerCannotCreateCategoryOrAccessRecycleBin() = runBlocking {
        signInTestUser("customer_${UUID.randomUUID().toString().take(6)}@test.com")
        val storeRepo = StoreRepository(firestore)
        val adminRepo = AdminOpsRepository(firestore)

        val catResult = withTimeout(5000L) {
            storeRepo.saveCategory(Category(nameEn = "Unauthorized", nameTe = "అనధికార", iconKey = "toys", sortOrder = 1L))
        }
        assertTrue("Non-admin category creation must fail", catResult.isFailure)
        val catErr = catResult.exceptionOrNull()?.unwrapFirestoreException()
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, catErr?.code)

        var recycleDenied = false
        try {
            withTimeout(4000L) {
                adminRepo.observeDeletedProducts().first()
            }
        } catch (e: Exception) {
            val ffe = e.unwrapFirestoreException()
            recycleDenied = ffe?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
        }
        assertTrue("Non-admin recycle bin read must fail with PERMISSION_DENIED", recycleDenied)
    }

    @Test
    fun unauthenticatedUser_cannotObserveActiveProducts() = runBlocking {
        auth.signOut()
        val storeRepo = StoreRepository(firestore)
        var denied = false
        try {
            withTimeout(4000L) {
                storeRepo.observeActiveProducts().first()
            }
        } catch (e: Exception) {
            val ffe = e.unwrapFirestoreException()
            denied = ffe?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
        }
        assertTrue("Unauthenticated product observation must fail with PERMISSION_DENIED", denied)
    }
}
