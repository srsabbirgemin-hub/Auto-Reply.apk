package com.example.ui.components

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.data.local.ReplyStatus
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusCyan
import com.example.ui.theme.StatusEmerald
import com.example.ui.theme.StatusIndigo
import com.example.ui.theme.StatusRose
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatusPill(
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.14f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ReplyStatusBadge(status: ReplyStatus, modifier: Modifier = Modifier) {
    val (label, color) = when (status) {
        ReplyStatus.SENT -> "AI Replied" to StatusEmerald
        ReplyStatus.COMMAND_SENT -> "Command Replied" to StatusIndigo
        ReplyStatus.SMART_ACTION_SENT -> "Smart Action" to StatusCyan
        ReplyStatus.QUIET_HOURS_SENT -> "Quiet Hours" to StatusIndigo
        ReplyStatus.MANUAL_SENT -> "Sent Manually" to StatusEmerald
        ReplyStatus.PENDING_APPROVAL -> "Needs Approval" to StatusAmber
        ReplyStatus.SKIPPED_DISABLED -> "Skipped (Paused)" to MaterialTheme.colorScheme.onSurfaceVariant
        ReplyStatus.SKIPPED_MUTED_CONTACT -> "Muted Contact" to MaterialTheme.colorScheme.onSurfaceVariant
        ReplyStatus.SKIPPED_GROUP_CHAT -> "Skipped (Group)" to MaterialTheme.colorScheme.onSurfaceVariant
        ReplyStatus.SKIPPED_COOLDOWN -> "Cooldown Active" to StatusAmber
        ReplyStatus.UNSUPPORTED_NO_REPLY_ACTION -> "No Reply Action" to StatusAmber
        ReplyStatus.FAILED_NO_API_KEY -> "Missing API Key" to StatusRose
        ReplyStatus.FAILED_API_ERROR -> "Reply Failed" to StatusRose
        ReplyStatus.RECEIVED -> "Received" to StatusCyan
    }
    StatusPill(label = label, color = color, modifier = modifier)
}

@Composable
fun AppBadgeAvatar(
    appName: String,
    iconDrawable: Drawable?,
    size: Dp = 42.dp,
    modifier: Modifier = Modifier
) {
    val imageBitmap = remember(iconDrawable) {
        runCatching {
            iconDrawable?.toBitmap(width = 96, height = 96)?.asImageBitmap()
        }.getOrNull()
    }
    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap,
            contentDescription = "$appName icon",
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )
    } else {
        val initials = appName.trim().take(2).uppercase().ifBlank { "MS" }
        val bg = remember(appName) {
            val palette = listOf(StatusCyan, StatusEmerald, StatusIndigo, StatusAmber)
            palette[(appName.hashCode().and(0x7fffffff)) % palette.size]
        }
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(12.dp))
                .background(bg.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                style = MaterialTheme.typography.titleSmall,
                color = bg,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

fun formatShortTimestamp(timestampMs: Long): String {
    if (timestampMs <= 0L) return ""
    val now = System.currentTimeMillis()
    val diff = now - timestampMs
    val format = if (diff < 24 * 60 * 60 * 1000L) {
        SimpleDateFormat("h:mm a", Locale.getDefault())
    } else {
        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    }
    return format.format(Date(timestampMs))
}
