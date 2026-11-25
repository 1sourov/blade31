package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.i18n.BladeStrings
import com.example.core.i18n.SupportedLanguages
import com.example.core.i18n.bladeStringsForCode
import com.example.data.local.UserSettingsPrefs
import com.example.data.model.UserProfile
import com.example.ui.theme.BladeCrimson
import com.example.ui.theme.BladeCyan
import com.example.ui.theme.BladeEmerald
import com.example.ui.theme.TextWhite
import java.io.File
import java.util.Locale

enum class SettingsSheetSection {
    MANAGE_ACCOUNT,
    PRIVACY,
    CONTENT_PREFERENCES,
    NOTIFICATIONS,
    ACTIVITY_CENTRE,
    ADS,
    APP_LANGUAGE,
    SCREEN_TIME,
    FAMILY_PAIRING,
    AUDIENCE_CONTROL,
    DISPLAY_ACCESSIBILITY,
    FREE_UP_SPACE,
    DATA_SAVER,
    DEVELOPER
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDetailSheet(
    section: SettingsSheetSection,
    currentUser: UserProfile?,
    isDarkMode: Boolean,
    languageCode: String,
    userSettings: UserSettingsPrefs,
    cacheSizeBytes: Long,
    strings: BladeStrings,
    onDismiss: () -> Unit,
    onEditProfile: () -> Unit,
    onToggleAccountPrivacy: (Boolean) -> Unit,
    onUpdateSettings: ((UserSettingsPrefs) -> UserSettingsPrefs) -> Unit,
    onToggleDarkMode: (Boolean) -> Unit,
    onSelectLanguage: (String) -> Unit,
    onClearWatchHistory: () -> Unit,
    onClearAppCache: () -> Unit,
    onSignOut: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (section) {
                SettingsSheetSection.MANAGE_ACCOUNT -> {
                    SheetHeader(
                        title = strings.manageAccount,
                        subtitle = strings.manageAccountSubtitle
                    )
                    if (currentUser != null) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = currentUser.displayName.ifBlank { currentUser.username },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (currentUser.isVerified) {
                                        Spacer(Modifier.width(6.dp))
                                        Icon(
                                            Icons.Filled.CheckCircle,
                                            contentDescription = strings.verifiedCreator,
                                            tint = BladeEmerald,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "@${currentUser.username}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (currentUser.isPrivate) strings.privateAccount else strings.publicAccount,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Button(
                            onClick = {
                                onDismiss()
                                onEditProfile()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("settings_edit_profile_button")
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(strings.editProfile)
                        }
                        OutlinedButton(
                            onClick = {
                                onDismiss()
                                onSignOut()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("settings_manage_signout_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(strings.signOut)
                        }
                    }
                }

                SettingsSheetSection.PRIVACY -> {
                    SheetHeader(
                        title = strings.privacyTitle,
                        subtitle = strings.privacySubtitle
                    )
                    SettingSwitchRow(
                        title = strings.privateAccount,
                        subtitle = strings.privateAccountDesc,
                        checked = currentUser?.isPrivate == true,
                        testTag = "privacy_private_account_switch",
                        onCheckedChange = { enabled ->
                            onToggleAccountPrivacy(enabled)
                        }
                    )
                    SettingSwitchRow(
                        title = strings.allowComments,
                        subtitle = strings.allowCommentsDesc,
                        checked = userSettings.allowComments,
                        testTag = "privacy_allow_comments_switch",
                        onCheckedChange = { enabled ->
                            onUpdateSettings { it.copy(allowComments = enabled) }
                        }
                    )
                    SettingSwitchRow(
                        title = strings.suggestAccount,
                        subtitle = strings.suggestAccountDesc,
                        checked = userSettings.suggestAccountToOthers,
                        testTag = "privacy_suggest_account_switch",
                        onCheckedChange = { enabled ->
                            onUpdateSettings { it.copy(suggestAccountToOthers = enabled) }
                        }
                    )
                }

                SettingsSheetSection.CONTENT_PREFERENCES -> {
                    SheetHeader(
                        title = strings.contentPreferences,
                        subtitle = strings.contentPreferencesSubtitle
                    )
                    SettingSwitchRow(
                        title = strings.restrictedMode,
                        subtitle = strings.restrictedModeDesc,
                        checked = userSettings.restrictedMode,
                        testTag = "content_restricted_mode_switch",
                        onCheckedChange = { enabled ->
                            onUpdateSettings { it.copy(restrictedMode = enabled) }
                        }
                    )
                    OutlinedButton(
                        onClick = {
                            onClearWatchHistory()
                            onShowMessage(strings.refreshForYouFeed)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("content_refresh_feed_button")
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(strings.refreshForYouFeed)
                    }
                }

                SettingsSheetSection.NOTIFICATIONS -> {
                    SheetHeader(
                        title = strings.notificationsTitle,
                        subtitle = strings.notificationsSubtitle
                    )
                    SettingSwitchRow(
                        title = strings.likes,
                        subtitle = strings.notifyLikesDesc,
                        checked = userSettings.notifyLikes,
                        testTag = "notify_likes_switch",
                        onCheckedChange = { v -> onUpdateSettings { it.copy(notifyLikes = v) } }
                    )
                    SettingSwitchRow(
                        title = strings.comments,
                        subtitle = strings.notifyCommentsDesc,
                        checked = userSettings.notifyComments,
                        testTag = "notify_comments_switch",
                        onCheckedChange = { v -> onUpdateSettings { it.copy(notifyComments = v) } }
                    )
                    SettingSwitchRow(
                        title = strings.followers,
                        subtitle = strings.notifyFollowersDesc,
                        checked = userSettings.notifyFollowers,
                        testTag = "notify_followers_switch",
                        onCheckedChange = { v -> onUpdateSettings { it.copy(notifyFollowers = v) } }
                    )
                    SettingSwitchRow(
                        title = strings.directMessage,
                        subtitle = strings.notifyMessagesDesc,
                        checked = userSettings.notifyMessages,
                        testTag = "notify_messages_switch",
                        onCheckedChange = { v -> onUpdateSettings { it.copy(notifyMessages = v) } }
                    )
                }

                SettingsSheetSection.ACTIVITY_CENTRE -> {
                    SheetHeader(
                        title = strings.activityCentre,
                        subtitle = strings.activityCentreSubtitle
                    )
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Notifications, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = strings.activityCentreInfo,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Button(
                        onClick = {
                            onClearWatchHistory()
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("activity_clear_history_button")
                    ) {
                        Text(strings.clearWatchHistory)
                    }
                }

                SettingsSheetSection.ADS -> {
                    SheetHeader(
                        title = strings.adsTitle,
                        subtitle = strings.adsSubtitle
                    )
                    SettingSwitchRow(
                        title = strings.personalizedAds,
                        subtitle = strings.personalizedAdsDesc,
                        checked = userSettings.personalizedAds,
                        testTag = "ads_personalized_switch",
                        onCheckedChange = { v -> onUpdateSettings { it.copy(personalizedAds = v) } }
                    )
                }

                SettingsSheetSection.APP_LANGUAGE -> {
                    var languageQuery by remember { mutableStateOf("") }
                    SheetHeader(
                        title = strings.appLanguage,
                        subtitle = strings.selectLanguageSubtitle
                    )
                    OutlinedTextField(
                        value = languageQuery,
                        onValueChange = { languageQuery = it },
                        placeholder = { Text(strings.searchLanguageHint) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (languageQuery.isNotBlank()) {
                                IconButton(onClick = { languageQuery = "" }) {
                                    Icon(Icons.Filled.Close, contentDescription = strings.clear)
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("language_search_input")
                    )
                    val filteredLanguages = remember(languageQuery) {
                        val q = languageQuery.trim().lowercase()
                        if (q.isEmpty()) {
                            SupportedLanguages
                        } else {
                            SupportedLanguages.filter {
                                it.nativeName.lowercase().contains(q) ||
                                    it.englishName.lowercase().contains(q) ||
                                    it.code.lowercase().contains(q)
                            }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        filteredLanguages.forEach { lang ->
                            val isSelected = languageCode.equals(lang.code, ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) {
                                    BladeCrimson.copy(alpha = 0.14f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                },
                                border = if (isSelected) {
                                    androidx.compose.foundation.BorderStroke(1.2.dp, BladeCrimson)
                                } else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        onSelectLanguage(lang.code)
                                        val updatedStrings = bladeStringsForCode(lang.code)
                                        onShowMessage("${updatedStrings.languageChangedToastPrefix} ${lang.nativeName}")
                                        onDismiss()
                                    }
                                    .testTag("lang_${lang.code}_item")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = lang.nativeName,
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                            ),
                                            color = if (isSelected) BladeCrimson else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = lang.englishName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = strings.done,
                                            tint = BladeCrimson,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                SettingsSheetSection.SCREEN_TIME -> {
                    SheetHeader(
                        title = strings.screenTime,
                        subtitle = strings.screenTimeSubtitle
                    )
                    Text(
                        text = strings.dailyScreenTimeLimit,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val options = listOf(0, 30, 60, 90, 120)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        options.forEach { mins ->
                            FilterChip(
                                selected = userSettings.dailyScreenTimeMinutes == mins,
                                onClick = {
                                    onUpdateSettings { it.copy(dailyScreenTimeMinutes = mins) }
                                },
                                label = { Text(if (mins == 0) strings.offLabel else "${mins}m") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BladeCrimson,
                                    selectedLabelColor = TextWhite
                                ),
                                modifier = Modifier.testTag("screen_time_${mins}_chip")
                            )
                        }
                    }
                    SettingSwitchRow(
                        title = strings.sleepReminders,
                        subtitle = strings.sleepRemindersDesc,
                        checked = userSettings.sleepRemindersEnabled,
                        testTag = "screen_time_sleep_switch",
                        onCheckedChange = { v -> onUpdateSettings { it.copy(sleepRemindersEnabled = v) } }
                    )
                }

                SettingsSheetSection.FAMILY_PAIRING -> {
                    SheetHeader(
                        title = strings.familyPairing,
                        subtitle = strings.familyPairingSubtitle
                    )
                    SettingSwitchRow(
                        title = strings.safeViewingGuardrails,
                        subtitle = strings.safeViewingGuardrailsDesc,
                        checked = userSettings.familyRestrictedMode,
                        testTag = "family_restricted_switch",
                        onCheckedChange = { v -> onUpdateSettings { it.copy(familyRestrictedMode = v) } }
                    )
                    SettingSwitchRow(
                        title = strings.restrictDirectMessages,
                        subtitle = strings.restrictDirectMessagesDesc,
                        checked = userSettings.familyRestrictDms,
                        testTag = "family_restrict_dms_switch",
                        onCheckedChange = { v -> onUpdateSettings { it.copy(familyRestrictDms = v) } }
                    )
                }

                SettingsSheetSection.AUDIENCE_CONTROL -> {
                    SheetHeader(
                        title = strings.audienceControl,
                        subtitle = strings.audienceControlSubtitle
                    )
                    Text(
                        text = strings.defaultVideoVisibility,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf(
                            "Public" to strings.privacyPublic,
                            "Friends" to strings.privacyFriends,
                            "Private" to strings.privacyPrivate
                        ).forEach { (optionKey, optionLabel) ->
                            FilterChip(
                                selected = userSettings.defaultPostPrivacy.equals(optionKey, ignoreCase = true),
                                onClick = {
                                    onUpdateSettings { it.copy(defaultPostPrivacy = optionKey) }
                                },
                                label = { Text(optionLabel) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BladeCrimson,
                                    selectedLabelColor = TextWhite
                                ),
                                modifier = Modifier.testTag("audience_privacy_${optionKey.lowercase()}_chip")
                            )
                        }
                    }
                    SettingSwitchRow(
                        title = strings.allowDownloads,
                        subtitle = strings.allowDownloadsDesc,
                        checked = userSettings.allowDownloads,
                        testTag = "audience_downloads_switch",
                        onCheckedChange = { v -> onUpdateSettings { it.copy(allowDownloads = v) } }
                    )
                    SettingSwitchRow(
                        title = strings.allowReactionsDuets,
                        subtitle = strings.allowReactionsDuetsDesc,
                        checked = userSettings.allowReactions,
                        testTag = "audience_reactions_switch",
                        onCheckedChange = { v -> onUpdateSettings { it.copy(allowReactions = v) } }
                    )
                }

                SettingsSheetSection.DISPLAY_ACCESSIBILITY -> {
                    SheetHeader(
                        title = strings.displayAccessibility,
                        subtitle = strings.displayAccessibilitySubtitle
                    )
                    SettingSwitchRow(
                        title = strings.darkMode,
                        subtitle = strings.darkModeDesc,
                        checked = isDarkMode,
                        testTag = "settings_dark_mode_switch",
                        onCheckedChange = onToggleDarkMode
                    )
                    SettingSwitchRow(
                        title = strings.autoCaptions,
                        subtitle = strings.autoCaptionsDesc,
                        checked = userSettings.autoCaptions,
                        testTag = "display_auto_captions_switch",
                        onCheckedChange = { v -> onUpdateSettings { it.copy(autoCaptions = v) } }
                    )
                    SettingSwitchRow(
                        title = strings.reduceMotion,
                        subtitle = strings.reduceMotionDesc,
                        checked = userSettings.reduceMotion,
                        testTag = "display_reduce_motion_switch",
                        onCheckedChange = { v -> onUpdateSettings { it.copy(reduceMotion = v) } }
                    )
                }

                SettingsSheetSection.FREE_UP_SPACE -> {
                    SheetHeader(
                        title = strings.freeUpSpace,
                        subtitle = strings.freeUpSpaceSubtitle
                    )
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(strings.cachedMediaLabel, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = formatCacheSizeMb(cacheSizeBytes),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Button(
                        onClick = onClearAppCache,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("settings_clear_cache_button")
                    ) {
                        Icon(Icons.Filled.CleaningServices, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(strings.clearCache)
                    }
                }

                SettingsSheetSection.DATA_SAVER -> {
                    SheetHeader(
                        title = strings.dataSaver,
                        subtitle = strings.dataSaverSubtitle
                    )
                    SettingSwitchRow(
                        title = strings.enableDataSaver,
                        subtitle = strings.enableDataSaverDesc,
                        checked = userSettings.dataSaverEnabled,
                        testTag = "data_saver_switch",
                        onCheckedChange = { v -> onUpdateSettings { it.copy(dataSaverEnabled = v) } }
                    )
                }

                SettingsSheetSection.DEVELOPER -> {
                    DeveloperDetailsSheetContent(
                        strings = strings,
                        onClose = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
private fun DeveloperDetailsSheetContent(
    strings: BladeStrings,
    onClose: () -> Unit
) {
    val devName = stringResource(R.string.developer_name_value)
    val devAge = stringResource(R.string.developer_age_value)
    val devLocation = stringResource(R.string.developer_location_value)

    SheetHeader(
        title = strings.developerTitle,
        subtitle = strings.developerSubtitle
    )

    Surface(
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        BladeCrimson.copy(alpha = 0.42f),
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                        BladeCyan.copy(alpha = 0.36f)
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            )
            .testTag("developer_info_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            BladeCrimson.copy(alpha = 0.12f),
                            BladeCyan.copy(alpha = 0.05f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.0f)
                        )
                    )
                )
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 156.dp, height = 192.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        width = 2.dp,
                        brush = Brush.linearGradient(listOf(BladeCrimson, BladeCyan)),
                        shape = RoundedCornerShape(22.dp)
                    )
            ) {
                Image(
                    painter = painterResource(id = R.drawable.developer_sourov_photo),
                    contentDescription = "Developer portrait of $devName",
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("developer_photo")
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = devName,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.2.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Surface(
                    shape = CircleShape,
                    color = BladeCrimson.copy(alpha = 0.14f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Code,
                            contentDescription = null,
                            tint = BladeCrimson,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = strings.leadAppDeveloper,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = BladeCrimson
                        )
                    }
                }
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 0.75.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                shape = RoundedCornerShape(18.dp)
            )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            DeveloperDetailRow(
                icon = Icons.Filled.Person,
                label = strings.developerNameLabel,
                value = devName
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
            )
            DeveloperDetailRow(
                icon = Icons.Filled.Cake,
                label = strings.developerAgeLabel,
                value = devAge
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
            )
            DeveloperDetailRow(
                icon = Icons.Filled.LocationOn,
                label = strings.developerLocationLabel,
                value = devLocation
            )
        }
    }

    OutlinedButton(
        onClick = onClose,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("developer_sheet_close")
    ) {
        Text(strings.done)
    }
}

@Composable
private fun DeveloperDetailRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(BladeCrimson.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = BladeCrimson,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SheetHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

fun calculateCacheSizeBytes(context: Context): Long {
    fun dirSize(file: File?): Long {
        if (file == null || !file.exists()) return 0L
        if (file.isFile) return file.length()
        return file.listFiles()?.sumOf { dirSize(it) } ?: 0L
    }
    return dirSize(context.cacheDir) + dirSize(context.externalCacheDir)
}

fun clearAppCacheFiles(context: Context): Long {
    fun deleteChildren(dir: File?) {
        if (dir == null || !dir.exists() || !dir.isDirectory) return
        dir.listFiles()?.forEach { child ->
            runCatching { child.deleteRecursively() }
        }
    }
    deleteChildren(context.cacheDir)
    deleteChildren(context.externalCacheDir)
    return calculateCacheSizeBytes(context)
}

fun formatCacheSizeMb(bytes: Long): String {
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return String.format(Locale.US, "%.1f MB", mb.coerceAtLeast(0.0))
}
