package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.AppNotification
import com.example.data.model.Banner
import com.example.data.model.Category
import com.example.data.model.Coupon
import com.example.data.model.Product
import com.example.data.model.ShopSettings
import com.example.data.model.WishlistItem
import com.example.data.util.AppLanguage
import com.example.data.util.AppUpdateInfo
import com.example.ui.components.CloudProductImage
import com.example.ui.components.ProductCard
import com.example.ui.components.categoryAccentColor
import com.example.ui.components.categoryIllustrationRes
import com.example.ui.i18n.getStrings

@Composable
fun HomeScreen(
    language: AppLanguage,
    searchQuery: String,
    categories: List<Category>,
    products: List<Product>,
    banners: List<Banner>,
    coupons: List<Coupon>,
    wishlist: List<WishlistItem>,
    recentlyViewedIds: List<String>,
    notifications: List<AppNotification>,
    onLanguageToggle: () -> Unit,
    onSearchChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onCategoryClick: (String) -> Unit,
    onProductClick: (String) -> Unit,
    onToggleWishlist: (Product) -> Unit,
    onQuickAdd: (Product) -> Unit,
    onApplyCoupon: (String) -> Unit,
    onOpenNotifications: () -> Unit,
    onShareApp: () -> Unit,
    onViewAllCatalog: () -> Unit,
    onBudgetClick: (Int?) -> Unit = {},
    availableUpdate: AppUpdateInfo? = null,
    onUpdateApp: () -> Unit = {},
    shopSettings: ShopSettings = ShopSettings(),
    onQuickTagClick: (String) -> Unit = {},
    onOpenOrders: () -> Unit = {},
    onOpenWishlist: () -> Unit = {}
) {
    val context = LocalContext.current
    val isTelugu = language == AppLanguage.TELUGU
    val strings = getStrings(language)
    val unreadCount = notifications.count { !it.isRead }
    val wishlistIds = wishlist.map { it.productId }.toSet()

    val newArrivals = products.filter { it.isNewArrival }.ifEmpty { products.take(8) }
    val newArrivalIds = newArrivals.map { it.id }.toSet()
    val trending = products.filter { it.isTrending }
        .let { list -> if (products.size <= 2) list.filterNot { it.id in newArrivalIds } else list }
    val bestSellers = products.filter { it.isBestSeller }
        .let { list -> if (products.size <= 2) list.filterNot { it.id in newArrivalIds } else list }
    val recentlyViewed = recentlyViewedIds.mapNotNull { id -> products.firstOrNull { it.id == id } }
    val flashDeals = remember(products) {
        products
            .filter { it.isAvailable && it.stock > 0 && it.computedDiscountPercent() >= 10 }
            .sortedByDescending { it.computedDiscountPercent() }
            .take(8)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Brand Top Bar + Search
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "B",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = strings.appName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Bejjur • Komaram Bheem Asifabad",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier
                                .clickable(onClick = onLanguageToggle)
                                .testTag("home_lang_toggle")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Translate,
                                    contentDescription = strings.languageLabel,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (isTelugu) "EN" else "తెలుగు",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        IconButton(
                            onClick = onShareApp,
                            modifier = Modifier.testTag("home_share_qr_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode2,
                                contentDescription = strings.shareAppQr,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = onOpenNotifications,
                            modifier = Modifier.testTag("notifications_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (unreadCount > 0) {
                                        Badge { Text("$unreadCount") }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = strings.notifications
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text(strings.searchPlaceholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = {
                        IconButton(onClick = onSearchSubmit) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_search_input")
                )

                Spacer(Modifier.height(10.dp))

                val quickTags = listOf(
                    "Toys" to (if (isTelugu) "బొమ్మలు" else "Toys"),
                    "Gifts" to (if (isTelugu) "గిఫ్ట్స్" else "Gifts"),
                    "Bangles" to (if (isTelugu) "గాజులు" else "Bangles"),
                    "Ladies" to (if (isTelugu) "లేడీస్" else "Ladies"),
                    "Kitchen" to (if (isTelugu) "వంటగది" else "Kitchen"),
                    "School" to (if (isTelugu) "స్కూల్" else "School")
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(quickTags) { (queryTerm, label) ->
                        AssistChip(
                            onClick = { onQuickTagClick(queryTerm) },
                            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                            modifier = Modifier.testTag("quick_tag_${queryTerm.lowercase()}")
                        )
                    }
                }
            }
        }

        if (availableUpdate != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clickable(onClick = onUpdateApp)
                        .testTag("home_update_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = strings.updateAvailableTitle,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${strings.updateAvailableTitle} (${availableUpdate.versionTag})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = strings.updateNow,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // Hero & Festival Banners
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    BannerCard(
                        title = if (isTelugu) "బిగ్ బజార్ బెజ్జూర్ మెగా సేల్" else "BIG BAZER BEJJUR Mega Store",
                        subtitle = if (isTelugu) "ఆటబొమ్మలు, గాజులు, గిఫ్ట్స్ & గృహోపకరణాలు" else "Toys, Gifts, Bangles, Kitchen & Essentials",
                        drawableRes = R.drawable.img_hero_banner,
                        remoteUrl = null,
                        onClick = onViewAllCatalog
                    )
                }
                item {
                    BannerCard(
                        title = if (isTelugu) "పండుగ ప్రత్యేక ఆఫర్లు" else "Festive Gift & Beauty Deals",
                        subtitle = if (isTelugu) "₹499 పైబడిన ఆర్డర్లకు ఉచిత డెలివరీ" else "Free Delivery in Bejjur on orders ₹499+",
                        drawableRes = R.drawable.img_deals_banner,
                        remoteUrl = null,
                        onClick = onViewAllCatalog
                    )
                }
                items(banners.filter { it.isActive }, key = { it.id }) { banner ->
                    BannerCard(
                        title = if (isTelugu) banner.titleTe else banner.titleEn,
                        subtitle = if (isTelugu) banner.subtitleTe else banner.subtitleEn,
                        drawableRes = R.drawable.img_deals_banner,
                        remoteUrl = banner.imageUrl.takeIf { it.isNotBlank() },
                        onClick = {
                            if (banner.actionCategoryId.isNotBlank()) onCategoryClick(banner.actionCategoryId)
                            else onViewAllCatalog()
                        }
                    )
                }
            }
        }

        // 15+ Categories Strip
        item {
            Column {
                SectionHeader(title = strings.shopByCategory, actionLabel = strings.viewAll, onAction = onViewAllCatalog)
                Spacer(Modifier.height(10.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(categories.filter { it.isActive }, key = { it.id }) { category ->
                        val accent = categoryAccentColor(category.id, category.iconKey)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(86.dp)
                                .clickable { onCategoryClick(category.id) }
                                .testTag("home_category_${category.id}")
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = accent.copy(alpha = 0.14f),
                                border = BorderStroke(1.2.dp, accent.copy(alpha = 0.35f)),
                                tonalElevation = 2.dp,
                                modifier = Modifier.size(68.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Image(
                                        painter = painterResource(
                                            id = categoryIllustrationRes(category.id, category.iconKey)
                                        ),
                                        contentDescription = category.localizedName(isTelugu),
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.size(44.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = category.localizedName(isTelugu),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Active Offers & Coupons
        val activeCoupons = coupons.filter { it.isActive && it.usedCount < it.usageLimit }
        if (activeCoupons.isNotEmpty()) {
            item {
                Column {
                    SectionHeader(title = strings.specialOffers, actionLabel = null, onAction = {})
                    Spacer(Modifier.height(8.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(activeCoupons, key = { it.id }) { coupon ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .width(220.dp)
                                    .clickable { onApplyCoupon(coupon.code) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocalOffer,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = coupon.code,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Text(
                                            text = if (isTelugu) coupon.titleTe else coupon.titleEn,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Min ₹${coupon.minPurchase.toInt()}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Budget Store Quick Deals (Under ₹99, ₹199, ₹499)
        if (products.isNotEmpty()) {
            item {
                Column {
                    SectionHeader(title = strings.budgetStore, actionLabel = strings.viewAll, onAction = onViewAllCatalog)
                    Spacer(Modifier.height(8.dp))
                    val budgetTiers = listOf(
                        Triple(99, if (isTelugu) "₹99 లోపు" else "Under ₹99", Color(0xFF047857)),
                        Triple(199, if (isTelugu) "₹199 లోపు" else "Under ₹199", Color(0xFF1D4ED8)),
                        Triple(499, if (isTelugu) "₹499 లోపు" else "Under ₹499", Color(0xFFB45309)),
                        Triple(null, if (isTelugu) "అన్ని వస్తువులు" else "All Store Items", Color(0xFF7C3AED))
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(budgetTiers) { (cap, label, accent) ->
                            val count = if (cap == null) products.size else products.count { it.sellingPrice <= cap }
                            Card(
                                colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.12f)),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .width(136.dp)
                                    .clickable { onBudgetClick(cap) }
                                    .testTag("budget_card_${cap ?: "all"}")
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = accent
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = if (isTelugu) "$count వస్తువులు" else "$count items",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Today's Top Discounts (Zero extra Firestore reads)
        if (flashDeals.isNotEmpty()) {
            item {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = Color(0xFFD97706),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = strings.flashDeals,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        TextButton(onClick = onViewAllCatalog) {
                            Text(strings.viewAll)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(flashDeals, key = { "deal_${it.id}" }) { deal ->
                            val discount = deal.computedDiscountPercent()
                            val savedAmt = (deal.originalPrice - deal.sellingPrice).toInt().coerceAtLeast(0)
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                modifier = Modifier
                                    .width(160.dp)
                                    .clickable { onProductClick(deal.id) }
                                    .testTag("flash_deal_${deal.id}")
                            ) {
                                Column {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(126.dp)
                                    ) {
                                        CloudProductImage(
                                            imageUrl = deal.primaryImageUrl(),
                                            contentDescription = deal.localizedName(isTelugu),
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                        )
                                        Surface(
                                            color = Color(0xFFDC2626),
                                            shape = RoundedCornerShape(bottomEnd = 12.dp),
                                            modifier = Modifier.align(Alignment.TopStart)
                                        ) {
                                            Text(
                                                text = "$discount% OFF",
                                                color = Color.White,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = deal.localizedName(isTelugu),
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "₹${deal.sellingPrice.toInt()}",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            if (deal.originalPrice > deal.sellingPrice) {
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    text = "₹${deal.originalPrice.toInt()}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    textDecoration = TextDecoration.LineThrough
                                                )
                                            }
                                        }
                                        if (savedAmt > 0) {
                                            Text(
                                                text = if (isTelugu) "₹$savedAmt ఆదా" else "Save ₹$savedAmt",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF059669)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (products.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storefront,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = strings.noProductsYet,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            if (newArrivals.isNotEmpty()) {
                productGridSection(
                    sectionKey = "new_arrivals",
                    title = strings.newArrivals,
                    products = newArrivals,
                    isTelugu = isTelugu,
                    wishlistIds = wishlistIds,
                    onViewAll = onViewAllCatalog,
                    onProductClick = onProductClick,
                    onToggleWishlist = onToggleWishlist,
                    onQuickAdd = onQuickAdd
                )
            }
            if (trending.isNotEmpty()) {
                productGridSection(
                    sectionKey = "trending",
                    title = strings.trendingNow,
                    products = trending,
                    isTelugu = isTelugu,
                    wishlistIds = wishlistIds,
                    onViewAll = onViewAllCatalog,
                    onProductClick = onProductClick,
                    onToggleWishlist = onToggleWishlist,
                    onQuickAdd = onQuickAdd
                )
            }
            if (bestSellers.isNotEmpty()) {
                productGridSection(
                    sectionKey = "best_sellers",
                    title = strings.bestSellers,
                    products = bestSellers,
                    isTelugu = isTelugu,
                    wishlistIds = wishlistIds,
                    onViewAll = onViewAllCatalog,
                    onProductClick = onProductClick,
                    onToggleWishlist = onToggleWishlist,
                    onQuickAdd = onQuickAdd
                )
            }
            if (recentlyViewed.isNotEmpty() && products.size > 1) {
                productGridSection(
                    sectionKey = "recent",
                    title = strings.recentlyViewed,
                    products = recentlyViewed,
                    isTelugu = isTelugu,
                    wishlistIds = wishlistIds,
                    onViewAll = onViewAllCatalog,
                    onProductClick = onProductClick,
                    onToggleWishlist = onToggleWishlist,
                    onQuickAdd = onQuickAdd
                )
            }
        }

        // Bejjur Store Promise & Quick Customer Actions Card
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = strings.bejjurPromiseTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocalShipping,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${strings.bejjurPromiseSubtitle} • ${if (isTelugu) "₹${shopSettings.freeDeliveryThreshold.toInt()}+ పై ఉచిత డెలివరీ" else "Free Delivery ₹${shopSettings.freeDeliveryThreshold.toInt()}+"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenWishlist,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("home_wishlist_shortcut")
                        ) {
                            Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("${strings.myWishlistShortcut} (${wishlist.size})", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = onOpenOrders,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("home_orders_shortcut")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.myOrdersShortcut, maxLines = 1)
                        }
                    }
                    val cleanWa = shopSettings.whatsappNumber.filter { it.isDigit() }
                    if (cleanWa.length >= 10) {
                        OutlinedButton(
                            onClick = {
                                val msg = if (isTelugu) {
                                    "నమస్తే ${shopSettings.shopName}! నాకు ఒక వస్తువు గురించి వివరాలు కావాలి."
                                } else {
                                    "Hello ${shopSettings.shopName}! I would like help finding an item."
                                }
                                val url = "https://wa.me/$cleanWa?text=${java.net.URLEncoder.encode(msg, "UTF-8")}"
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("home_whatsapp_help_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(strings.chatStoreHelp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BannerCard(
    title: String,
    subtitle: String,
    drawableRes: Int,
    remoteUrl: String?,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(310.dp)
            .height(165.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
    ) {
        if (remoteUrl != null) {
            CloudProductImage(
                imageUrl = remoteUrl,
                contentDescription = title,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Image(
                painter = painterResource(id = drawableRes),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent)
                    )
                )
                .padding(16.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "B",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(0.75f)
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, actionLabel: String?, onAction: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        if (actionLabel != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

private fun LazyListScope.productGridSection(
    sectionKey: String,
    title: String,
    products: List<Product>,
    isTelugu: Boolean,
    wishlistIds: Set<String>,
    onViewAll: () -> Unit,
    onProductClick: (String) -> Unit,
    onToggleWishlist: (Product) -> Unit,
    onQuickAdd: (Product) -> Unit
) {
    item(key = "${sectionKey}_header") {
        SectionHeader(
            title = title,
            actionLabel = if (isTelugu) "అన్నీ" else "See All",
            onAction = onViewAll
        )
    }
    val rows = products.chunked(2)
    items(rows.size, key = { idx -> "${sectionKey}_row_$idx" }) { rowIdx ->
        val pair = rows[rowIdx]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            pair.forEach { product ->
                ProductCard(
                    product = product,
                    isTelugu = isTelugu,
                    isWishlisted = product.id in wishlistIds,
                    onClick = { onProductClick(product.id) },
                    onToggleWishlist = { onToggleWishlist(product) },
                    onQuickAdd = { onQuickAdd(product) },
                    modifier = Modifier.weight(1f)
                )
            }
            if (pair.size == 1) {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}
