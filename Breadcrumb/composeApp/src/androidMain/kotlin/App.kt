import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview

import breadcrumb.composeapp.generated.resources.Res
import breadcrumb.composeapp.generated.resources.compose_multiplatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

// https://github.com/JetBrains/compose-multiplatform/blob/a6961385ccf0dee7b6d31e3f73d2c8ef91005f1a/examples/nav_cupcake/composeApp/src/commonMain/kotlin/org/jetbrains/nav_cupcake/CupcakeScreen.kt#L89
enum class Screens {
    Start,
}

@Composable
@Preview
fun App(
    routeHandler: RouteHandler,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screens.Start.name,
    ) {
        composable(route = Screens.Start.name) {
            Start(
                startViewModel = viewModel { StartViewModel(routeHandler) }
            )
        }
    }
}

class StartViewModel(private val routeHandler: RouteHandler) : ViewModel() {

    fun sendRoute() {
        viewModelScope.launch(Dispatchers.IO) {
            routeHandler.sendRoute(
                listOf(
                    Point(-27.297514, 152.753860, 0.0f), Point(-27.297509, 152.753848, 0.0f),
                    Point(-27.297438, 152.753839, 0.0f), Point(-27.297400, 152.753827, 0.0f),
                    Point(-27.297367, 152.753817, 0.0f), Point(-27.297353, 152.753816, 0.0f),
                    Point(-27.297332, 152.753811, 0.0f), Point(-27.297309, 152.753806, 0.0f),
                    Point(-27.297289, 152.753797, 0.0f), Point(-27.297277, 152.753793, 0.0f),
                    Point(-27.297266, 152.753791, 0.0f), Point(-27.297260, 152.753789, 0.0f),
                    Point(-27.297251, 152.753783, 0.0f), Point(-27.297243, 152.753779, 0.0f),
                    Point(-27.296854, 152.753722, 0.0f), Point(-27.296445, 152.753782, 0.0f),
                    Point(-27.296069, 152.754457, 0.0f), Point(-27.295623, 152.755619, 0.0f),
                    Point(-27.295187, 152.757002, 0.0f), Point(-27.295528, 152.758083, 0.0f),
                    Point(-27.295601, 152.759104, 0.0f), Point(-27.295467, 152.760068, 0.0f),
                    Point(-27.295026, 152.762026, 0.0f), Point(-27.294955, 152.763041, 0.0f),
                    Point(-27.294894, 152.764648, 0.0f), Point(-27.294592, 152.766201, 0.0f),
                    Point(-27.294732, 152.767209, 0.0f), Point(-27.296218, 152.767723, 0.0f),
                    Point(-27.297393, 152.768442, 0.0f), Point(-27.298084, 152.768516, 0.0f),
                    Point(-27.299137, 152.769156, 0.0f), Point(-27.300144, 152.769483, 0.0f),
                    Point(-27.301310, 152.770309, 0.0f), Point(-27.301524, 152.771727, 0.0f),
                    Point(-27.300998, 152.772259, 0.0f), Point(-27.300814, 152.772659, 0.0f),
                    Point(-27.299807, 152.773792, 0.0f), Point(-27.299565, 152.774460, 0.0f),
                    Point(-27.299743, 152.774257, 0.0f), Point(-27.299872, 152.773632, 0.0f),
                    Point(-27.300726, 152.772599, 0.0f), Point(-27.301410, 152.771877, 0.0f),
                    Point(-27.301647, 152.770617, 0.0f), Point(-27.303042, 152.770881, 0.0f),
                    Point(-27.303328, 152.770991, 0.0f), Point(-27.302099, 152.770411, 0.0f),
                    Point(-27.301988, 152.770390, 0.0f), Point(-27.300927, 152.770159, 0.0f),
                    Point(-27.299082, 152.769110, 0.0f), Point(-27.297414, 152.768498, 0.0f),
                    Point(-27.295843, 152.767761, 0.0f), Point(-27.294908, 152.764974, 0.0f),
                    Point(-27.294980, 152.762144, 0.0f), Point(-27.295515, 152.759859, 0.0f),
                    Point(-27.295467, 152.757536, 0.0f), Point(-27.295663, 152.755540, 0.0f),
                    Point(-27.297046, 152.753708, 0.0f)
                )
            )
        }
    }

}

@Composable
@Preview
fun Start(startViewModel: StartViewModel) {
    MaterialTheme {
        var showContent by remember { mutableStateOf(false) }

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Button(onClick = {
                startViewModel.sendRoute()
            }) {
                Text("Click me!")
            }
            AnimatedVisibility(showContent) {
                val greeting = remember { Greeting().greet() }
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(painterResource(Res.drawable.compose_multiplatform), null)
                    Text("Compose: $greeting")
                }
            }
        }
    }
}