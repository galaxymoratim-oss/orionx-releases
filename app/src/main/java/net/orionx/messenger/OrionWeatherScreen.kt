package net.orionx.messenger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.FilterDrama
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.orionx.messenger.weather.ORION_WEATHER_CITIES
import net.orionx.messenger.weather.WeatherLook
import net.orionx.messenger.weather.WeatherReport
import net.orionx.messenger.weather.OrionWeatherApi
import net.orionx.messenger.weather.weatherCondition
import net.orionx.messenger.weather.weatherLook

private fun lookIcon(look: WeatherLook): ImageVector = when (look) {
    WeatherLook.SUN -> Icons.Outlined.WbSunny
    WeatherLook.PARTLY -> Icons.Outlined.FilterDrama
    WeatherLook.CLOUD -> Icons.Outlined.WbCloudy
    WeatherLook.RAIN -> Icons.Outlined.WaterDrop
    WeatherLook.SNOW -> Icons.Outlined.Cloud
    WeatherLook.STORM -> Icons.Outlined.Thunderstorm
    WeatherLook.FOG -> Icons.Outlined.Cloud
}

/** «погода»: city chips, the big current card, three metrics, the hourly strip and five days. */
@Composable
internal fun XWeatherScreen(onBack: () -> Unit) {
    var cityIndex by remember { mutableIntStateOf(0) }
    var report by remember { mutableStateOf<WeatherReport?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(cityIndex) {
        loading = true
        error = null
        runCatching { withContext(Dispatchers.IO) { OrionWeatherApi.load(ORION_WEATHER_CITIES[cityIndex]) } }
            .onSuccess { report = it }
            .onFailure {
                // Отмена (быстрое переключение городов) не должна показывать ошибку.
                if (it is kotlinx.coroutines.CancellationException) throw it
                report = null
                error = it.message
            }
        loading = false
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        XScreenHeader(title = xSettingText("app_weather"), onBack = onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                ORION_WEATHER_CITIES.forEachIndexed { index, city ->
                    val active = index == cityIndex
                    Box(
                        Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (active) XText else XSurface)
                            .clickable { cityIndex = index },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            city.name,
                            style = XType.sectionLabel.copy(fontSize = 11.sp, letterSpacing = .02.em),
                            color = if (active) XInverse else XText,
                            maxLines = 1,
                        )
                    }
                }
            }

            val current = report
            when {
                // При смене города старый отчёт ещё показан — добавляем индикатор,
                // чтобы переход не выглядел зависанием.
                loading && current == null -> XCard {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = XAccent, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
                    }
                }

                loading -> XCard {
                    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = XAccent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    }
                }

                current == null -> XCard {
                    Text(
                        xSettingText("weather_failed"),
                        style = XType.bodyStrong,
                        color = XMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(28.dp),
                    )
                }

                else -> {
                    XCard {
                        Column(
                            Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(lookIcon(weatherLook(current.code)), null, tint = XText, modifier = Modifier.size(58.dp))
                            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Top) {
                                Text(
                                    "${current.temperature}",
                                    style = XType.metric.copy(fontSize = 62.sp, letterSpacing = (-.05).em),
                                    color = XText,
                                )
                                // line-height 1.2 in the canvas drops the degree sign just below
                                // the cap line rather than floating it at the very top.
                                Text(
                                    "°",
                                    style = XType.metric.copy(fontSize = 26.sp),
                                    color = XMuted,
                                    modifier = Modifier.padding(top = 7.dp),
                                )
                            }
                            Text(
                                weatherCondition(current.code),
                                style = XType.meta.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                                color = XText,
                            )
                            Row(
                                Modifier.padding(top = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("макс ${current.high}°", style = XType.meta.copy(fontWeight = FontWeight.Bold), color = XMuted)
                                Box(Modifier.size(3.dp).clip(RoundedCornerShape(50)).background(XMuted))
                                Text("мин ${current.low}°", style = XType.meta.copy(fontWeight = FontWeight.Bold), color = XMuted)
                                Box(Modifier.size(3.dp).clip(RoundedCornerShape(50)).background(XMuted))
                                Text("ощущается ${current.feelsLike}°", style = XType.meta.copy(fontWeight = FontWeight.Bold), color = XMuted)
                            }
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        XWeatherMetric(Icons.Outlined.Air, "${current.windKph} км/ч", xSettingText("wind"), Modifier.weight(1f))
                        XWeatherMetric(Icons.Outlined.WaterDrop, "${current.humidity} %", xSettingText("humidity"), Modifier.weight(1f))
                        XWeatherMetric(Icons.Outlined.Compress, "${current.pressureMm} мм", xSettingText("pressure"), Modifier.weight(1f))
                    }

                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        current.hours.forEachIndexed { index, hour ->
                            val highlight = index == 0
                            Column(
                                Modifier
                                    .width(60.dp)
                                    .xShadow(RoundedCornerShape(20.dp))
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (highlight) XText else XSurface)
                                    .padding(horizontal = 6.dp, vertical = 13.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    hour.label,
                                    style = XType.meta.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Bold),
                                    color = if (highlight) XOnMuted else XMuted,
                                    maxLines = 1,
                                )
                                Icon(
                                    lookIcon(weatherLook(hour.code)),
                                    null,
                                    tint = if (highlight) XInverse else XText,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    "${hour.temperature}°",
                                    style = XType.rowTitle.copy(fontSize = 14.sp),
                                    color = if (highlight) XInverse else XText,
                                )
                            }
                        }
                    }

                    val allHigh = current.days.maxOfOrNull { it.high } ?: 0
                    val allLow = current.days.minOfOrNull { it.low } ?: 0
                    val span = (allHigh - allLow).coerceAtLeast(1).toFloat()
                    XCard(shape = XShape.card) {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            current.days.forEachIndexed { index, day ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Text(day.label, style = XType.rowTitle.copy(fontSize = 12.sp), color = XText, modifier = Modifier.width(26.dp))
                                    Icon(lookIcon(weatherLook(day.code)), null, tint = XMuted, modifier = Modifier.size(19.dp))
                                    Text(
                                        "${day.low}°",
                                        style = XType.meta.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                                        color = XMuted,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.width(26.dp),
                                    )
                                    Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(XSurfaceRaised)) {
                                        val start = (day.low - allLow) / span
                                        val width = ((day.high - day.low) / span).coerceAtLeast(.06f)
                                        Box(
                                            Modifier
                                                .fillMaxWidth(start + width)
                                                .fillMaxHeight()
                                                .padding(start = 0.dp),
                                        ) {
                                            Box(
                                                Modifier
                                                    .fillMaxHeight()
                                                    .fillMaxWidth(width / (start + width))
                                                    .align(Alignment.CenterEnd)
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(
                                                        Brush.horizontalGradient(listOf(Color(0xFF8FB4FF), Color(0xFFFF9FC4)))
                                                    ),
                                            )
                                        }
                                    }
                                    Text(
                                        "${day.high}°",
                                        style = XType.rowTitle.copy(fontSize = 12.sp),
                                        color = XText,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.width(26.dp),
                                    )
                                }
                                if (index != current.days.lastIndex) {
                                    Box(Modifier.fillMaxWidth().height(1.dp).background(XBorder))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun XWeatherMetric(icon: ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .xShadow(RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(XSurface)
            .padding(horizontal = 8.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = XMuted, modifier = Modifier.size(19.dp))
        Text(value, style = XType.rowTitle.copy(fontSize = 13.sp), color = XText, maxLines = 1)
        Text(
            label.uppercase(),
            style = XType.sectionLabel.copy(fontSize = 8.5.sp, letterSpacing = .06.em),
            color = XMuted,
            maxLines = 1,
        )
    }
}
