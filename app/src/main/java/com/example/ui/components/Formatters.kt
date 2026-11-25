package com.example.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.core.camera.VideoFilterCatalog
import com.example.core.i18n.bladeStrings
import com.example.ui.theme.BladeCountCrisp
import com.example.ui.theme.BladeCyan
import com.example.ui.theme.BladeRed
import com.example.ui.theme.BladeRedAccent
import com.example.ui.theme.BladeRedDark
import com.example.ui.theme.TextWhite

fun formatCompactCount(count: Int): String = when {
    count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000f).replace(".0M", "M")
    count >= 1_000 -> String.format("%.1fK", count / 1_000f).replace(".0K", "K")
    else -> count.toString()
}

fun formatDurationMs(durationMs: Long): String {
    val totalSeconds = (durationMs.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

fun filterOverlayColor(filterName: String): Color =
    VideoFilterCatalog.swatchOverlayColor(filterName)

@Composable
fun BladeAvatar(
    avatarUrl: String,
    username: String,
    size: Dp = 44.dp,
    borderColor: Color = TextWhite,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .border(1.5.dp, borderColor.copy(alpha = 0.85f), CircleShape)
            .background(
                Brush.linearGradient(listOf(BladeRedDark, BladeRed, BladeRedAccent))
            ),
        contentAlignment = Alignment.Center
    ) {
        if (avatarUrl.isNotBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = username,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val initial = username.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "B"
            Text(
                text = initial,
                color = TextWhite,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.42f).sp
            )
        }
    }
}

@Composable
fun VerifiedBadge(modifier: Modifier = Modifier, size: Dp = 15.dp) {
    Icon(
        imageVector = Icons.Filled.CheckCircle,
        contentDescription = "Verified",
        tint = BladeRed,
        modifier = modifier.size(size)
    )
}

@Composable
fun BladeBrandMark(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = R.drawable.blade_logo_round),
            contentDescription = "Blade Logo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .border(1.dp, BladeRed, CircleShape)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "BLADE",
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp
            ),
            color = BladeRed
        )
    }
}

@Composable
fun VideoRailActionButton(
    icon: ImageVector,
    tint: Color,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    labelColor: Color = BladeCountCrisp,
    onLabelClick: (() -> Unit)? = null,
    labelTestTag: String? = null
) {
    Column(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 1.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.padding(top = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = iconModifier.size(25.dp)
            )
        }
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .then(if (labelTestTag != null) Modifier.testTag(labelTestTag) else Modifier)
                .width(44.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(7.dp))
                .then(if (onLabelClick != null) Modifier.clickable(onClick = onLabelClick) else Modifier)
                .padding(horizontal = 3.dp, vertical = 1.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = labelColor,
                maxLines = 1,
                softWrap = false,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.1.sp,
                    fontFeatureSettings = "tnum",
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.65f),
                        offset = Offset(0f, 1f),
                        blurRadius = 3f
                    )
                )
            )
        }
    }
}

@Composable
fun ExpandableVideoTitle(
    title: String,
    resetKey: Any,
    style: TextStyle,
    color: Color = TextWhite,
    collapsedMaxLines: Int = 2,
    modifier: Modifier = Modifier,
    toggleTestTag: String? = null
) {
    val strings = bladeStrings()
    var expanded by remember(resetKey, title) { mutableStateOf(false) }
    var hasOverflow by remember(resetKey, title) { mutableStateOf(false) }

    Column(
        modifier = modifier.animateContentSize(
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
        )
    ) {
        Text(
            text = title,
            color = color,
            style = style,
            maxLines = if (expanded) Int.MAX_VALUE else collapsedMaxLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { layoutResult ->
                if (!expanded) {
                    hasOverflow = layoutResult.hasVisualOverflow
                }
            }
        )
        if (expanded || hasOverflow) {
            Text(
                text = if (expanded) strings.hide else strings.seeMore,
                color = color.copy(alpha = 0.92f),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.1.sp,
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.65f),
                        offset = Offset(0f, 1f),
                        blurRadius = 3f
                    )
                ),
                modifier = Modifier
                    .then(if (toggleTestTag != null) Modifier.testTag(toggleTestTag) else Modifier)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { expanded = !expanded }
                    .padding(top = 3.dp, bottom = 3.dp, end = 12.dp)
            )
        }
    }
}

@Composable
fun ExpandableVideoCaption(
    caption: String,
    resetKey: Any,
    style: TextStyle,
    color: Color = TextWhite,
    hashtagColor: Color = BladeCyan,
    textShadow: Shadow? = null,
    collapsedMaxLines: Int = 3,
    onHashtagClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    toggleTestTag: String? = null
) {
    val strings = bladeStrings()
    var expanded by remember(resetKey, caption) { mutableStateOf(false) }
    var hasOverflow by remember(resetKey, caption) { mutableStateOf(false) }

    val styledText = remember(caption, color, hashtagColor, textShadow, onHashtagClick) {
        buildAnnotatedString {
            val words = caption.split(" ")
            words.forEachIndexed { idx, word ->
                if (word.startsWith("#") && word.length > 1) {
                    val tagStyle = SpanStyle(
                        color = hashtagColor,
                        fontWeight = FontWeight.SemiBold,
                        shadow = textShadow
                    )
                    if (onHashtagClick != null) {
                        pushLink(
                            LinkAnnotation.Clickable(
                                tag = "HASHTAG_$idx",
                                styles = TextLinkStyles(style = tagStyle),
                                linkInteractionListener = { onHashtagClick(word) }
                            )
                        )
                        append(word)
                        pop()
                    } else {
                        pushStyle(tagStyle)
                        append(word)
                        pop()
                    }
                } else {
                    pushStyle(SpanStyle(color = color, shadow = textShadow))
                    append(word)
                    pop()
                }
                if (idx < words.lastIndex) append(" ")
            }
        }
    }

    Column(
        modifier = modifier.animateContentSize(
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
        )
    ) {
        Text(
            text = styledText,
            style = style,
            maxLines = if (expanded) Int.MAX_VALUE else collapsedMaxLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { layoutResult ->
                if (!expanded) {
                    hasOverflow = layoutResult.hasVisualOverflow
                }
            }
        )
        if (expanded || hasOverflow) {
            Text(
                text = if (expanded) strings.hide else strings.seeMore,
                color = color.copy(alpha = 0.92f),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.1.sp,
                    shadow = textShadow ?: Shadow(
                        color = Color.Black.copy(alpha = 0.65f),
                        offset = Offset(0f, 1f),
                        blurRadius = 3f
                    )
                ),
                modifier = Modifier
                    .then(if (toggleTestTag != null) Modifier.testTag(toggleTestTag) else Modifier)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { expanded = !expanded }
                    .padding(top = 3.dp, bottom = 3.dp, end = 12.dp)
            )
        }
    }
}
