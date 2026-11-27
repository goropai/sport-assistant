package com.sportassistant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sportassistant.data.Category

/** One place that decides which icon means what, so the whole app speaks the same visual language. */
object AppIcons {
    val Today = Icons.Rounded.Home
    val Walk = Icons.Rounded.DirectionsWalk
    val Food = Icons.Rounded.Restaurant
    val Progress = Icons.Rounded.BarChart
    val Settings = Icons.Rounded.Settings

    val Camera = Icons.Rounded.PhotoCamera
    val Gallery = Icons.Rounded.PhotoLibrary
    val Manual = Icons.Rounded.Edit
    val Ai = Icons.Rounded.AutoAwesome
    val Recalculate = Icons.Rounded.Refresh
    val Delete = Icons.Rounded.Close
    val Add = Icons.Rounded.Add
    val Remove = Icons.Rounded.Remove
    val Library = Icons.Rounded.MenuBook
    val Nose = Icons.Rounded.Face
    val Posture = Icons.Rounded.AccessibilityNew
    val Watch = Icons.Rounded.Watch
    val HeartRate = Icons.Rounded.Favorite
    val Goal = Icons.Rounded.Flag
    val Tip = Icons.Rounded.Lightbulb
    val Warning = Icons.Rounded.Warning
    val Celebrate = Icons.Rounded.Celebration
    val Done = Icons.Rounded.CheckCircle
    val NotDone = Icons.Rounded.RadioButtonUnchecked
    val Locked = Icons.Rounded.Lock

    // Achievements
    val BadgeFirst = Icons.Rounded.Flag
    val BadgeStreak3 = Icons.Rounded.Bolt
    val BadgeStreak7 = Icons.Rounded.LocalFireDepartment
    val BadgeWorkouts = Icons.Rounded.FitnessCenter
    val BadgePoints = Icons.Rounded.EmojiEvents

    fun forCategory(c: Category): ImageVector = when (c) {
        Category.MOBILITY -> Icons.Rounded.Sync
        Category.STRETCH -> Icons.Rounded.SelfImprovement
        Category.STRENGTH -> Icons.Rounded.FitnessCenter
        Category.CORE -> Icons.Rounded.AccessibilityNew
        Category.CARDIO -> Icons.Rounded.DirectionsRun
        Category.EYES -> Icons.Rounded.Visibility
    }
}

/** An icon followed by text, for labels and notes. */
@Composable
fun IconText(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
    tint: Color = MaterialTheme.colorScheme.primary,
    iconSize: Dp = 20.dp,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
        Spacer(Modifier.width(8.dp))
        Text(text, style = style, color = color)
    }
}

/** Content of a button: icon + label, tinted like the button text. */
@Composable
fun ButtonLabel(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = LocalContentColor.current)
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

/** A large icon on a soft round tint, used where a picture is wanted but no illustration exists. */
@Composable
fun IconBadge(icon: ImageVector, size: Dp, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary) {
    Box(
        modifier.size(size).clip(CircleShape).background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}
