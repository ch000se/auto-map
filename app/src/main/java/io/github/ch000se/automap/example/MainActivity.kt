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
import io.github.ch000se.automap.example.models.Address
import io.github.ch000se.automap.example.models.Order
import io.github.ch000se.automap.example.models.Product
import io.github.ch000se.automap.example.models.Tag
import io.github.ch000se.automap.example.models.User
import io.github.ch000se.automap.example.models.toOrderDto
import io.github.ch000se.automap.example.models.toProductDto
import io.github.ch000se.automap.example.models.toUserDto

/**
 * Minimal Android sample screen that executes generated AutoMap mappers and displays their output.
 */
class MainActivity : ComponentActivity() {

    /**
     * Initializes the Compose UI and renders sample mapping cases.
     *
     * @param savedInstanceState Previously saved Android state, if the activity is being recreated.
     */
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

    private fun buildCases(): List<Pair<String, String>> = listOf(
        "1. Direct mapping (User -> UserDto, password ignored)" to
            User(1L, "Alice", "a@x.io", passwordHash = "secret").toUserDto().toString(),

        "2. Rename + named & lambda converters (Product -> ProductDto)" to
            Product(2L, name = "Widget", priceInCents = 4999L, displayPrice = 4999L)
                .toProductDto { cents -> "$%.2f".format(cents / 100.0) }
                .toString(),

        "3. Nested mapping + list element mapping (Order -> OrderDto)" to
            Order(
                id = 100L,
                address = Address("Main St 1", "Kyiv"),
                tags = listOf(Tag("priority"), Tag("gift")),
            ).toOrderDto().toString(),
    )
}

@Composable
@Suppress("FunctionName")
private fun ExamplesScreen(cases: List<Pair<String, String>>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = "AutoMap examples",
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
