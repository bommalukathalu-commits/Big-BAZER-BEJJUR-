package com.example.ui.navigation

import android.app.Application
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.ImageLoader
import com.example.R
import com.example.data.repository.AdminOpsRepository
import com.example.data.repository.CartAndOrderRepository
import com.example.data.repository.StoreRepository
import com.example.data.util.AppLanguage
import com.example.data.util.AppThemeMode
import com.example.data.util.AppUpdateChecker
import com.example.data.util.AppUpdateInfo
import com.example.data.util.CloudImageFetcherFactory
import com.example.data.util.PreferencesManager
import com.example.data.util.UpdateCheckResult
import com.example.ui.auth.SignInScreen
import com.example.ui.auth.signOutUser
import com.example.ui.components.APP_SHARE_LINK
import com.example.ui.components.LocalAppImageLoader
import com.example.ui.components.ShareQrDialog
import com.example.ui.components.ShareTarget
import com.example.ui.i18n.getStrings
import com.example.ui.screens.AccountScreen
import com.example.ui.screens.AdminPanelScreen
import com.example.ui.screens.CartScreen
import com.example.ui.screens.CatalogScreen
import com.example.ui.screens.CheckoutScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.OrdersScreen
import com.example.ui.screens.ProductDetailScreen
import com.example.ui.viewmodel.BigBazerViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

enum class MainTab { HOME, CATALOG, CART, ORDERS, ACCOUNT }

sealed interface OverlayRoute {
    data object None : OverlayRoute
    data class ProductDetail(val productId: String) : OverlayRoute
    data object Checkout : OverlayRoute
    data object AdminPanel : OverlayRoute
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth -> trySend(auth.currentUser) }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun BigBazerAppRoot(
    language: AppLanguage,
    themeMode: AppThemeMode,
    prefsManager: PreferencesManager
) {
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val db = remember(context) {
        FirebaseFirestore.getInstance(context.applicationContext.getString(R.string.firestore_database_id))
    }
    val imageLoader = remember(context, db) {
        ImageLoader.Builder(context.applicationContext)
            .components { add(CloudImageFetcherFactory(context.applicationContext, db)) }
            .crossfade(true)
            .build()
    }

    CompositionLocalProvider(LocalAppImageLoader provides imageLoader) {
        val user = currentUser
        if (user == null) {
            SignInScreen(
                language = language,
                onLanguageChange = { lang -> scope.launch { prefsManager.setLanguage(lang) } },
                onAuthSuccess = {}
            )
        } else {
            key(user.uid) {
                AuthenticatedStoreApp(
                    currentUserId = user.uid,
                    language = language,
                    themeMode = themeMode,
                    onLanguageChange = { lang -> scope.launch { prefsManager.setLanguage(lang) } },
                    onThemeChange = { mode -> scope.launch { prefsManager.setThemeMode(mode) } },
                    onSignOut = {
                        val cm = CredentialManager.create(context)
                        signOutUser(cm, scope)
                    }
                )
            }
        }
    }
}

@Composable
private fun AuthenticatedStoreApp(
    currentUserId: String,
    language: AppLanguage,
    themeMode: AppThemeMode,
    onLanguageChange: (AppLanguage) -> Unit,
    onThemeChange: (AppThemeMode) -> Unit,
    onSignOut: () -> Unit
) {
    val vm: BigBazerViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as Application
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                BigBazerViewModel(
                    storeRepo = StoreRepository(db),
                    cartRepo = CartAndOrderRepository(db),
                    adminRepo = AdminOpsRepository(db),
                    currentUserId = currentUserId,
                    appContext = app.applicationContext
                )
            }
        }
    )

    val strings = getStrings(language)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val statusMessage by vm.statusMessage.collectAsStateWithLifecycle()
    val isAdmin by vm.isAdmin.collectAsStateWithLifecycle()
    val isBusy by vm.isBusy.collectAsStateWithLifecycle()
    val userProfile by vm.userProfile.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    val banners by vm.banners.collectAsStateWithLifecycle()
    val coupons by vm.coupons.collectAsStateWithLifecycle()
    val shopSettings by vm.shopSettings.collectAsStateWithLifecycle()
    val cartItems by vm.cartItems.collectAsStateWithLifecycle()
    val checkoutSummary by vm.checkoutSummary.collectAsStateWithLifecycle()
    val appliedCoupon by vm.appliedCoupon.collectAsStateWithLifecycle()
    val addresses by vm.addresses.collectAsStateWithLifecycle()
    val wishlist by vm.wishlist.collectAsStateWithLifecycle()
    val recentlyViewedIds by vm.recentlyViewedIds.collectAsStateWithLifecycle()
    val notifications by vm.notifications.collectAsStateWithLifecycle()
    val userOrders by vm.userOrders.collectAsStateWithLifecycle()
    val userPayments by vm.userPayments.collectAsStateWithLifecycle()
    val userTickets by vm.userTickets.collectAsStateWithLifecycle()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val selectedCategoryId by vm.selectedCategoryId.collectAsStateWithLifecycle()

    // Admin streams
    val analytics by vm.adminAnalytics.collectAsStateWithLifecycle()
    val allOrders by vm.allOrders.collectAsStateWithLifecycle()
    val allPayments by vm.allPayments.collectAsStateWithLifecycle()
    val allCustomers by vm.allCustomers.collectAsStateWithLifecycle()
    val allTickets by vm.allTickets.collectAsStateWithLifecycle()
    val deletedProducts by vm.deletedProducts.collectAsStateWithLifecycle()
    val backupRecords by vm.backupRecords.collectAsStateWithLifecycle()
    val auditLogs by vm.auditLogs.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(MainTab.HOME) }
    var overlay by remember { mutableStateOf<OverlayRoute>(OverlayRoute.None) }
    var shareTarget by remember { mutableStateOf<ShareTarget?>(null) }
    var availableUpdate by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var isCheckingUpdate by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        when (val res = AppUpdateChecker.checkForUpdate()) {
            is UpdateCheckResult.UpdateAvailable -> {
                availableUpdate = res.info
                showUpdateDialog = true
            }
            else -> Unit
        }
    }

    val openAppShareDialog = {
        val storeTitle = shopSettings.shopName.ifBlank { "BIG BAZER BEJJUR" }
        shareTarget = ShareTarget(
            title = storeTitle,
            subtitle = if (language == AppLanguage.TELUGU)
                "బిగ్ బజార్ బెజ్జూర్ యాప్ డౌన్‌లోడ్ చేయడానికి ఈ QR కోడ్ స్కాన్ చేయండి"
            else
                "Scan this QR code to download $storeTitle APK",
            qrContent = APP_SHARE_LINK,
            shareMessage = "Shop at $storeTitle! Download the Android app here: $APP_SHARE_LINK"
        )
    }

    if (shareTarget != null) {
        ShareQrDialog(
            target = shareTarget!!,
            language = language,
            onDismiss = { shareTarget = null }
        )
    }

    if (showUpdateDialog && availableUpdate != null) {
        val update = availableUpdate!!
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text("${strings.updateAvailableTitle} (${update.versionTag})") },
            text = {
                Text(
                    buildString {
                        append(strings.updateAvailableBody)
                        append("\n\n${strings.currentVersionLabel}: ${AppUpdateChecker.currentVersionLabel()}")
                        if (update.releaseNotes.isNotBlank()) {
                            append("\n\n${update.releaseNotes.take(240)}")
                        }
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUpdateDialog = false
                        AppUpdateChecker.openUpdateDownload(context, update)
                    },
                    modifier = Modifier.testTag("update_now_dialog_btn")
                ) {
                    Text(strings.updateNow)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) {
                    Text(strings.later)
                }
            }
        )
    }

    LaunchedEffect(statusMessage) {
        val msg = statusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            vm.clearStatusMessage()
        }
    }

    if (overlay != OverlayRoute.None) {
        BackHandler { overlay = OverlayRoute.None }
    }

    when (val route = overlay) {
        is OverlayRoute.ProductDetail -> {
            val product = products.firstOrNull { it.id == route.productId }
            if (product != null) {
                ProductDetailScreen(
                    product = product,
                    allProducts = products,
                    language = language,
                    isWishlisted = wishlist.any { it.productId == product.id },
                    wishlistIds = wishlist.map { it.productId }.toSet(),
                    reviewsFlow = vm.observeReviews(product.id),
                    onBack = { overlay = OverlayRoute.None },
                    onRecordViewed = vm::recordRecentlyViewed,
                    onToggleWishlist = vm::toggleWishlist,
                    onAddToCart = { prod, qty, color, size, buyNow ->
                        vm.addToCart(prod, qty, color, size) {
                            if (buyNow) {
                                currentTab = MainTab.CART
                                overlay = OverlayRoute.Checkout
                            }
                        }
                    },
                    onSubmitReview = { rating, comment -> vm.submitReview(product.id, rating, comment) },
                    onProductClick = { pid -> overlay = OverlayRoute.ProductDetail(pid) },
                    onShareProduct = { prod ->
                        val pName = prod.localizedName(language == AppLanguage.TELUGU)
                        val storeTitle = shopSettings.shopName.ifBlank { "BIG BAZER BEJJUR" }
                        shareTarget = ShareTarget(
                            title = pName,
                            subtitle = "₹${prod.sellingPrice.toInt()} • $storeTitle",
                            qrContent = APP_SHARE_LINK,
                            shareMessage = "Check out $pName for ₹${prod.sellingPrice.toInt()} on $storeTitle! Download app: $APP_SHARE_LINK"
                        )
                    }
                )
            } else {
                overlay = OverlayRoute.None
            }
            return
        }
        OverlayRoute.Checkout -> {
            CheckoutScreen(
                language = language,
                addresses = addresses,
                summary = checkoutSummary,
                shopSettings = shopSettings,
                isBusy = isBusy,
                defaultName = userProfile?.displayName.orEmpty(),
                defaultPhone = userProfile?.phone.orEmpty(),
                onBack = { overlay = OverlayRoute.None },
                onSaveAddress = vm::saveAddress,
                onPlaceOrder = vm::placeOrder,
                onUpdateOrderPayment = vm::updateOrderPaymentOutcome,
                onFormatWhatsAppMessage = vm::formatWhatsAppMessage,
                onDoneToOrders = {
                    overlay = OverlayRoute.None
                    currentTab = MainTab.ORDERS
                }
            )
            return
        }
        OverlayRoute.AdminPanel -> {
            if (isAdmin) {
                AdminPanelScreen(
                    analytics = analytics,
                    products = products,
                    categories = categories,
                    banners = banners,
                    coupons = coupons,
                    orders = allOrders,
                    payments = allPayments,
                    customers = allCustomers,
                    tickets = allTickets,
                    deletedProducts = deletedProducts,
                    backupRecords = backupRecords,
                    auditLogs = auditLogs,
                    shopSettings = shopSettings,
                    isBusy = isBusy,
                    onBack = { overlay = OverlayRoute.None },
                    onUploadImage = vm::uploadSingleProductImage,
                    onSaveProduct = vm::saveProduct,
                    onSoftDeleteProduct = vm::softDeleteProduct,
                    onSaveCategory = vm::saveCategory,
                    onInitDefaultCategories = vm::initializeDefaultCategories,
                    onSaveBanner = vm::saveBanner,
                    onDeleteBanner = vm::deleteBanner,
                    onSaveCoupon = vm::saveCoupon,
                    onUpdateOrderStatus = vm::updateOrderStatusAdmin,
                    onProcessRefund = vm::processRefundAdmin,
                    onUpdateTicket = vm::updateTicketAdmin,
                    onBroadcastNotification = vm::broadcastNotificationAdmin,
                    onRestoreProduct = vm::restoreProduct,
                    onPermanentDeleteProduct = vm::permanentlyDeleteProduct,
                    onRunBackup = vm::runBackupNow,
                    onRestoreFromBackup = vm::restoreFromBackup,
                    onUpdateShopSettings = vm::updateShopSettings
                )
                return
            } else {
                overlay = OverlayRoute.None
            }
        }
        OverlayRoute.None -> Unit
    }

    val cartBadgeCount = cartItems.sumOf { it.quantity }.toInt()
    val navItems = listOf(
        Triple(MainTab.HOME, strings.navHome, Icons.Default.Home),
        Triple(MainTab.CATALOG, strings.navCategories, Icons.Default.Category),
        Triple(MainTab.CART, strings.navCart, Icons.Default.ShoppingCart),
        Triple(MainTab.ORDERS, strings.navOrders, Icons.AutoMirrored.Filled.ReceiptLong),
        Triple(MainTab.ACCOUNT, strings.navAccount, Icons.Default.Person)
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        navItems.forEach { (tab, label, icon) ->
                            NavigationBarItem(
                                selected = currentTab == tab,
                                onClick = { currentTab = tab },
                                icon = {
                                    if (tab == MainTab.CART && cartBadgeCount > 0) {
                                        BadgedBox(badge = { Badge { Text("$cartBadgeCount") } }) {
                                            Icon(icon, contentDescription = label)
                                        }
                                    } else {
                                        Icon(icon, contentDescription = label)
                                    }
                                },
                                label = { Text(label, maxLines = 1) },
                                modifier = Modifier.testTag("nav_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail {
                        navItems.forEach { (tab, label, icon) ->
                            NavigationRailItem(
                                selected = currentTab == tab,
                                onClick = { currentTab = tab },
                                icon = {
                                    if (tab == MainTab.CART && cartBadgeCount > 0) {
                                        BadgedBox(badge = { Badge { Text("$cartBadgeCount") } }) {
                                            Icon(icon, contentDescription = label)
                                        }
                                    } else {
                                        Icon(icon, contentDescription = label)
                                    }
                                },
                                label = { Text(label, maxLines = 1) }
                            )
                        }
                    }
                }

                when (currentTab) {
                    MainTab.HOME -> HomeScreen(
                        language = language,
                        searchQuery = searchQuery,
                        categories = categories,
                        products = products,
                        banners = banners,
                        coupons = coupons,
                        wishlist = wishlist,
                        recentlyViewedIds = recentlyViewedIds,
                        notifications = notifications,
                        onLanguageToggle = {
                            onLanguageChange(if (language == AppLanguage.TELUGU) AppLanguage.ENGLISH else AppLanguage.TELUGU)
                        },
                        onSearchChange = vm::setSearchQuery,
                        onSearchSubmit = { currentTab = MainTab.CATALOG },
                        onCategoryClick = { catId ->
                            vm.selectCategory(catId)
                            currentTab = MainTab.CATALOG
                        },
                        onProductClick = { pid -> overlay = OverlayRoute.ProductDetail(pid) },
                        onToggleWishlist = vm::toggleWishlist,
                        onQuickAdd = { prod -> vm.addToCart(prod) },
                        onApplyCoupon = { code ->
                            vm.applyCouponCode(code)
                            currentTab = MainTab.CART
                        },
                        onOpenNotifications = { currentTab = MainTab.ACCOUNT },
                        onShareApp = openAppShareDialog,
                        onViewAllCatalog = {
                            vm.selectCategory(null)
                            currentTab = MainTab.CATALOG
                        },
                        availableUpdate = availableUpdate,
                        onUpdateApp = {
                            val upd = availableUpdate
                            if (upd != null) AppUpdateChecker.openUpdateDownload(context, upd)
                        }
                    )
                    MainTab.CATALOG -> CatalogScreen(
                        language = language,
                        searchQuery = searchQuery,
                        selectedCategoryId = selectedCategoryId,
                        categories = categories,
                        products = products,
                        wishlist = wishlist,
                        onSearchChange = vm::setSearchQuery,
                        onCategorySelect = vm::selectCategory,
                        onProductClick = { pid -> overlay = OverlayRoute.ProductDetail(pid) },
                        onToggleWishlist = vm::toggleWishlist,
                        onQuickAdd = { prod -> vm.addToCart(prod) }
                    )
                    MainTab.CART -> CartScreen(
                        language = language,
                        cartItems = cartItems,
                        summary = checkoutSummary,
                        appliedCoupon = appliedCoupon,
                        availableCoupons = coupons,
                        shopSettings = shopSettings,
                        onUpdateQty = vm::updateCartQuantity,
                        onRemoveItem = vm::removeFromCart,
                        onApplyCoupon = vm::applyCouponCode,
                        onRemoveCoupon = vm::removeCoupon,
                        onProceedToCheckout = { overlay = OverlayRoute.Checkout },
                        onBrowseStore = { currentTab = MainTab.CATALOG }
                    )
                    MainTab.ORDERS -> OrdersScreen(
                        language = language,
                        orders = userOrders,
                        payments = userPayments,
                        shopSettings = shopSettings,
                        onCancelOrder = vm::cancelOrder,
                        onRetryUpiPayment = vm::updateOrderPaymentOutcome,
                        onFormatWhatsAppMessage = vm::formatWhatsAppMessage
                    )
                    MainTab.ACCOUNT -> AccountScreen(
                        language = language,
                        themeMode = themeMode,
                        userProfile = userProfile,
                        isAdmin = isAdmin,
                        addresses = addresses,
                        wishlist = wishlist,
                        notifications = notifications,
                        tickets = userTickets,
                        shopSettings = shopSettings,
                        onLanguageChange = onLanguageChange,
                        onThemeChange = onThemeChange,
                        onUpdateProfile = vm::updateProfile,
                        onSaveAddress = vm::saveAddress,
                        onDeleteAddress = vm::deleteAddress,
                        onMarkNotificationRead = vm::markNotificationRead,
                        onSubmitTicket = vm::submitSupportTicket,
                        onOpenProduct = { pid -> overlay = OverlayRoute.ProductDetail(pid) },
                        onOpenAdminPanel = { if (isAdmin) overlay = OverlayRoute.AdminPanel },
                        onShareApp = openAppShareDialog,
                        availableUpdate = availableUpdate,
                        isCheckingUpdate = isCheckingUpdate,
                        onCheckForUpdates = {
                            if (!isCheckingUpdate) {
                                isCheckingUpdate = true
                                scope.launch {
                                    when (val res = AppUpdateChecker.checkForUpdate()) {
                                        is UpdateCheckResult.UpdateAvailable -> {
                                            availableUpdate = res.info
                                            showUpdateDialog = true
                                        }
                                        UpdateCheckResult.UpToDate -> {
                                            availableUpdate = null
                                            vm.showMessage(strings.upToDateMessage)
                                        }
                                        is UpdateCheckResult.Error -> {
                                            vm.showMessage(res.message)
                                        }
                                    }
                                    isCheckingUpdate = false
                                }
                            }
                        },
                        onSignOut = onSignOut
                    )
                }
            }
        }
    }
}
