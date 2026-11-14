package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AppNotification
import com.example.data.model.Banner
import com.example.data.model.Category
import com.example.data.model.Coupon
import com.example.data.model.Product
import com.example.data.model.WishlistItem
import com.example.data.util.AppLanguage
import com.example.ui.components.CloudProductImage
import com.example.ui.components.ProductCard
import com.example.ui.components.categoryIconFor
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
    onViewAllCatalog: () -> Unit
) {
    val isTelugu = language == AppLanguage.TELUGU
    val strings = getStrings(language)
    val unreadCount = notifications.count { !it.isRead }
    val wishlistIds = wishlist.map { it.productId }.toSet()

    val newArrivals = products.filter { it.isNewArrival }.ifEmpty { products.take(6) }
    val trending = products.filter { it.isTrending }.ifEmpty { products.take(6) }
    val bestSellers = products.filter { it.isBestSeller }.ifEmpty { products.sortedByDescending { it.ratingAverage }.take(6) }
    val recentlyViewed = recentlyViewedIds.mapNotNull { id -> products.firstOrNull { it.id == id } }
    val recommendations = products.filter { it.isFeaturedOffer || it.computedDiscountPercent() >= 10 }.ifEmpty { products.take(6) }

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
                                Icon(
                                    imageVector = Icons.Default.Storefront,
                                    contentDescription = strings.appName,
                                    tint = MaterialTheme.colorScheme.onPrimary
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
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(84.dp)
                                .clickable { onCategoryClick(category.id) }
                                .testTag("home_category_${category.id}")
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                tonalElevation = 2.dp,
                                modifier = Modifier.size(60.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = categoryIconFor(category.iconKey),
                                        contentDescription = category.localizedName(isTelugu),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = category.localizedName(isTelugu),
                                style = MaterialTheme.typography.labelSmall,
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
            item {
                ProductHorizontalSection(
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
            item {
                ProductHorizontalSection(
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
            item {
                ProductHorizontalSection(
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
            item {
                ProductHorizontalSection(
                    title = strings.recommendedForYou,
                    products = recommendations,
                    isTelugu = isTelugu,
                    wishlistIds = wishlistIds,
                    onViewAll = onViewAllCatalog,
                    onProductClick = onProductClick,
                    onToggleWishlist = onToggleWishlist,
                    onQuickAdd = onQuickAdd
                )
            }
            if (recentlyViewed.isNotEmpty()) {
                item {
                    ProductHorizontalSection(
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
                .padding(16.dp),
            contentAlignment = Alignment.BottomStart
        ) {
            Column(modifier = Modifier.fillMaxWidth(0.72f)) {
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

@Composable
private fun ProductHorizontalSection(
    title: String,
    products: List<Product>,
    isTelugu: Boolean,
    wishlistIds: Set<String>,
    onViewAll: () -> Unit,
    onProductClick: (String) -> Unit,
    onToggleWishlist: (Product) -> Unit,
    onQuickAdd: (Product) -> Unit
) {
    Column {
        SectionHeader(title = title, actionLabel = if (isTelugu) "అన్నీ" else "See All", onAction = onViewAll)
        Spacer(Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(products, key = { it.id }) { product ->
                ProductCard(
                    product = product,
                    isTelugu = isTelugu,
                    isWishlisted = product.id in wishlistIds,
                    onClick = { onProductClick(product.id) },
                    onToggleWishlist = { onToggleWishlist(product) },
                    onQuickAdd = { onQuickAdd(product) },
                    modifier = Modifier.width(172.dp)
                )
            }
        }
    }
}
