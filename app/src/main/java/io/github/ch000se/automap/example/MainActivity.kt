package io.github.ch000se.automap.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.ch000se.automap.example.models.Account
import io.github.ch000se.automap.example.models.ApiStatus
import io.github.ch000se.automap.example.models.Article
import io.github.ch000se.automap.example.models.Catalog
import io.github.ch000se.automap.example.models.Category
import io.github.ch000se.automap.example.models.City
import io.github.ch000se.automap.example.models.Config
import io.github.ch000se.automap.example.models.Contact
import io.github.ch000se.automap.example.models.Coord
import io.github.ch000se.automap.example.models.CustomId
import io.github.ch000se.automap.example.models.Device
import io.github.ch000se.automap.example.models.Event
import io.github.ch000se.automap.example.models.Location
import io.github.ch000se.automap.example.models.Metrics
import io.github.ch000se.automap.example.models.Order
import io.github.ch000se.automap.example.models.Permission
import io.github.ch000se.automap.example.models.Player
import io.github.ch000se.automap.example.models.Product
import io.github.ch000se.automap.example.models.ProductSnapshot
import io.github.ch000se.automap.example.models.Profile
import io.github.ch000se.automap.example.models.RawUser
import io.github.ch000se.automap.example.models.Report
import io.github.ch000se.automap.example.models.Role
import io.github.ch000se.automap.example.models.Scoreboard
import io.github.ch000se.automap.example.models.Store
import io.github.ch000se.automap.example.models.SystemMessage
import io.github.ch000se.automap.example.models.Tag
import io.github.ch000se.automap.example.models.User
import io.github.ch000se.automap.example.models.UserDto
import io.github.ch000se.automap.example.models.Venue
import io.github.ch000se.automap.example.models.toAccountDto
import io.github.ch000se.automap.example.models.toAddressDto
import io.github.ch000se.automap.example.models.toArticleDto
import io.github.ch000se.automap.example.models.toCatalogDto
import io.github.ch000se.automap.example.models.toCategoryDto
import io.github.ch000se.automap.example.models.toConfigDto
import io.github.ch000se.automap.example.models.toContactDto
import io.github.ch000se.automap.example.models.toDeviceDto
import io.github.ch000se.automap.example.models.toEventDto
import io.github.ch000se.automap.example.models.toMetricsDto
import io.github.ch000se.automap.example.models.toOrderDto
import io.github.ch000se.automap.example.models.toPermissionDto
import io.github.ch000se.automap.example.models.toPlayerDto
import io.github.ch000se.automap.example.models.toProductDto
import io.github.ch000se.automap.example.models.toProductSnapshotDto
import io.github.ch000se.automap.example.models.toProfileDto
import io.github.ch000se.automap.example.models.toReportDto
import io.github.ch000se.automap.example.models.toRoleDto
import io.github.ch000se.automap.example.models.toSanitizedUserDto
import io.github.ch000se.automap.example.models.toScoreboardDto
import io.github.ch000se.automap.example.models.toStoreDto
import io.github.ch000se.automap.example.models.toSystemMessageDto
import io.github.ch000se.automap.example.models.toTagDto
import io.github.ch000se.automap.example.models.toUser
import io.github.ch000se.automap.example.models.toUserDto
import io.github.ch000se.automap.example.models.toVenueDto

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ExamplesScreen(buildCases())
                }
            }
        }
    }

    @Suppress("LongMethod")
    private fun buildCases(): List<Pair<String, String>> = listOf(

        // ── Case 1: Direct mapping ──────────────────────────────────────────────
        "1. Direct mapping\nUser → UserDto" to
                User(1L, "Alice", "alice@example.com")
                    .toUserDto()
                    .toString(),

        // ── Case 1b: Reverse mapping ────────────────────────────────────────────
        "1b. Reverse mapping (reverse = true)\nUserDto → User" to
                UserDto(2L, "Bob", "bob@example.com")
                    .toUser()
                    .toString(),

        // ── Case 2: Field rename ────────────────────────────────────────────────
        "2. Field rename (source = \"fullName\")\nContact → ContactDto" to
                Contact(3L, "John Doe", "+380501234567")
                    .toContactDto()
                    .toString(),

        // ── Case 3: Constant field ──────────────────────────────────────────────
        "3. Constant field (constant = \"\\\"1.0\\\"\")\nSystemMessage → SystemMessageDto" to
                SystemMessage(10L, "Server restarted")
                    .toSystemMessageDto()
                    .toString(),

        // ── Case 4: Nullable with default ───────────────────────────────────────
        "4a. Nullable with default (null input)\nProfile → ProfileDto" to
                Profile(4L, bio = null, avatarUrl = null)
                    .toProfileDto()
                    .toString(),

        "4b. Nullable with default (filled input)\nProfile → ProfileDto" to
                Profile(5L, bio = "Developer", avatarUrl = "https://example.com/me.png")
                    .toProfileDto()
                    .toString(),

        // ── Case 5: Rename + nullable default ──────────────────────────────────
        "5. Rename + defaultIfNull combined\nPlayer(null nick) → PlayerDto" to
                Player(6L, rawNickname = null, score = 1500)
                    .toPlayerDto()
                    .toString(),

        // ── Case 6: Auto type conversion ────────────────────────────────────────
        "6. Auto type conversion (Int→Long, Float→String)\nMetrics → MetricsDto" to
                Metrics("latency", count = 42, ratio = 0.75f)
                    .toMetricsDto()
                    .toString(),

        // ── Case 7: Explicit convert ────────────────────────────────────────────
        "7. Explicit convert (source rename + convert = \"toInt\")\nProductSnapshot → ProductSnapshotDto" to
                ProductSnapshot(name = "Widget", priceRaw = 1999L)
                    .toProductSnapshotDto()
                    .toString(),

        // ── Case 8: Auto-toString ────────────────────────────────────────────────
        "8. Auto-toString (CustomId → String)\nEvent → EventDto" to
                Event(id = CustomId(99L), title = "Launch")
                    .toEventDto()
                    .toString(),

        // ── Case 9: Ignore field ─────────────────────────────────────────────────
        "9. Ignore field (ignore = true, target has default)\nReport → ReportDto" to
                Report(7L, "Q1 Results", internalNote = "CONFIDENTIAL")
                    .toReportDto()
                    .toString(),

        // ── Case 10: Custom resolver as lambda ──────────────────────────────────
        "10. Custom resolver (lambda, compile-safe)\nProduct → ProductDto" to
                Product(8L, "Gadget", priceInCents = 4999L)
                    .toProductDto(
                        resolveDisplayPrice = { "$%.2f".format(it.priceInCents / 100.0) },
                    )
                    .toString(),

        // ── Case 11: Before/after hooks ─────────────────────────────────────────
        "11. Before/after hooks\nRawUser → SanitizedUserDto" to
                RawUser("  Alice  ", "  ALICE@EXAMPLE.COM  ")
                    .toSanitizedUserDto(
                        beforeMap = { it.copy(name = it.name.trim(), email = it.email.trim()) },
                        afterMap = { it.copy(email = it.email.lowercase()) },
                    )
                    .toString(),

        // ── Case 12: Enum auto-match by name ────────────────────────────────────
        "12. Enum auto-match by name\nAccount(ApiStatus) → AccountDto(UserStatus)" to
                Account(9L, ApiStatus.SUSPENDED)
                    .toAccountDto()
                    .toString(),

        // ── Case 13: Nested object with @AutoMap (lambda delegate) ──────────────
        "13. Nested with @AutoMap (lambda delegate)\nOrder → OrderDto" to
                Order(100L, io.github.ch000se.automap.example.models.Address("Main St 1", "Kyiv"))
                    .toOrderDto(
                        addressMapper = { it.toAddressDto() },
                    )
                    .toString(),

        // ── Case 14: Inline nested — 1 level, no @AutoMap ───────────────────────
        "14. Inline nested — 1 level, no @AutoMap\nVenue → VenueDto" to
                Venue("Arena", Coord(x = 48, y = 37))
                    .toVenueDto()
                    .toString(),

        // ── Case 15: Deep inline nested — 2 levels, no @AutoMap anywhere ────────
        "15. Deep inline nested — 2 levels, no @AutoMap\nStore → StoreDto" to
                Store("Main Store", Location("Khreshchatyk 1", City("Kyiv", "01001")))
                    .toStoreDto()
                    .toString(),

        // ── Case 16: List<T> collection (@AutoMap element) ──────────────────────
        "16. List<T> with @AutoMap elements\nArticle → ArticleDto" to
                Article(
                    11L,
                    "Hello KSP",
                    listOf(Tag("kotlin", "#7F52FF"), Tag("android", "#3DDC84")),
                )
                    .toArticleDto(
                        tagMapper = { it.toTagDto() },
                    )
                    .toString(),

        // ── Case 17: Set<T> collection (@AutoMap element) ───────────────────────
        "17. Set<T> with @AutoMap elements\nRole → RoleDto" to
                Role("admin", setOf(Permission("read"), Permission("write")))
                    .toRoleDto(
                        permissionMapper = { it.toPermissionDto() },
                    )
                    .toString(),

        // ── Case 18: Map<K, V> value mapping ────────────────────────────────────
        "18. Map<K, V> value mapping\nCatalog → CatalogDto" to
                Catalog(
                    "Electronics",
                    mapOf("phones" to Category("Phones"), "laptops" to Category("Laptops")),
                )
                    .toCatalogDto(
                        categoryMapper = { it.toCategoryDto() },
                    )
                    .toString(),

        // ── Case 19: Collection of primitives transform ──────────────────────────
        "19. List<Int> → List<String> (no lambda)\nScoreboard → ScoreboardDto" to
                Scoreboard("TopPlayers", listOf(1000, 850, 720))
                    .toScoreboardDto()
                    .toString(),

        // ── Case 20: Builder pattern — companion operator invoke ─────────────────
        "20. Builder pattern (companion invoke)\nConfig → ConfigDto" to
                Config("db.example.com", 5432)
                    .toConfigDto()
                    .toString(),

        // ── Case 21: Non-data class source ──────────────────────────────────────
        "21. Non-data class source (open class)\nDevice → DeviceDto" to
                Device(12L, "Pixel 9")
                    .toDeviceDto()
                    .toString(),
    )
}

@Composable
private fun ExamplesScreen(cases: List<Pair<String, String>>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = "AutoMap — all mapping scenarios",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        cases.forEachIndexed { index, (label, result) ->
            Text(text = label, style = MaterialTheme.typography.titleSmall)
            Text(
                text = result,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            if (index < cases.lastIndex) {
                HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp))
            }
        }
    }
}