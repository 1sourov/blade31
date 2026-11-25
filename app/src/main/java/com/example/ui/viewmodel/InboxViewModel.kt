package com.example.ui.viewmodel

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ConversationItem
import com.example.data.model.MessageItem
import com.example.data.model.NotificationItem
import com.example.data.model.UiState
import com.example.data.remote.RealtimeChatEvent
import com.example.data.repository.BladeRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.util.UUID

data class InboxUiState(
    val notificationsState: UiState<List<NotificationItem>> = UiState.Loading,
    val conversationsState: UiState<List<ConversationItem>> = UiState.Loading,
    val selectedFilter: String = "All",
    val activeConversation: ConversationItem? = null,
    val messagesState: UiState<List<MessageItem>> = UiState.Loading,
    val isRealtimeConnected: Boolean = false,
    val isSendingMedia: Boolean = false,
    val stagedImageUri: Uri? = null,
    val unreadCount: Int = 0,
    val error: String? = null
)

class InboxViewModel(private val repository: BladeRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(InboxUiState())
    val uiState: StateFlow<InboxUiState> = _uiState.asStateFlow()

    private var chatPollingJob: Job? = null
    private var chatRealtimeJob: Job? = null
    private var inboxRealtimeJob: Job? = null
    private var activeChatId: String? = null
    private val inFlightReadConversations = mutableSetOf<String>()
    private val inFlightReadNotifications = mutableSetOf<String>()
    private val markReadJobs = mutableMapOf<String, Job>()
    private val activeReadTokens = mutableMapOf<String, Long>()
    private var nextReadToken = 0L

    private fun computeLocalUnreadCount(state: InboxUiState): Int {
        val unreadNotifs = (state.notificationsState as? UiState.Success)?.data
            ?.count { !it.isRead && it.id !in inFlightReadNotifications } ?: 0
        val unreadConvs = (state.conversationsState as? UiState.Success)?.data
            ?.sumOf { conv ->
                if (conv.id == activeChatId || conv.id in inFlightReadConversations) 0 else conv.unreadCount.coerceAtLeast(0)
            } ?: 0
        return (unreadNotifs + unreadConvs).coerceAtLeast(0)
    }

    private suspend fun refreshUnreadCountSuspend() {
        val rawServerCount = repository.getUnreadInboxCount()
        _uiState.update { state ->
            val hasPendingRead = activeChatId != null ||
                inFlightReadConversations.isNotEmpty() ||
                inFlightReadNotifications.isNotEmpty()
            val resolved = if (hasPendingRead) {
                val hasLoadedSlices =
                    state.notificationsState is UiState.Success && state.conversationsState is UiState.Success
                val cap = if (hasLoadedSlices) computeLocalUnreadCount(state) else state.unreadCount
                minOf(rawServerCount, cap).coerceAtLeast(0)
            } else {
                rawServerCount.coerceAtLeast(0)
            }
            state.copy(unreadCount = resolved)
        }
    }

    private suspend fun refreshConversationsSuspend() {
        repository.getConversations().fold(
            onSuccess = { list ->
                val adjusted = list.map { conv ->
                    if (conv.id == activeChatId || conv.id in inFlightReadConversations) {
                        conv.copy(unreadCount = 0)
                    } else {
                        conv
                    }
                }
                _uiState.update { state ->
                    val next = state.copy(conversationsState = UiState.Success(adjusted))
                    val localCount = computeLocalUnreadCount(next)
                    if (activeChatId != null || inFlightReadConversations.isNotEmpty()) {
                        next.copy(unreadCount = minOf(next.unreadCount, localCount).coerceAtLeast(0))
                    } else {
                        next
                    }
                }
            },
            onFailure = { err ->
                if (_uiState.value.conversationsState !is UiState.Success) {
                    _uiState.update { it.copy(conversationsState = UiState.Error(err.message ?: "Error")) }
                }
            }
        )
    }

    fun refreshUnreadCount() {
        viewModelScope.launch {
            refreshUnreadCountSuspend()
        }
    }

    fun clearUnreadCount() {
        _uiState.update { it.copy(unreadCount = 0) }
    }

    fun onNotificationClicked(notificationId: String) {
        inFlightReadNotifications.add(notificationId)
        _uiState.update { state ->
            val currentNotifs = (state.notificationsState as? UiState.Success)?.data
            val wasUnread = currentNotifs?.any { it.id == notificationId && !it.isRead } == true
            val updatedNotifs = currentNotifs?.map { notif ->
                if (notif.id == notificationId) notif.copy(isRead = true) else notif
            }
            val nextState = if (updatedNotifs != null) {
                state.copy(notificationsState = UiState.Success(updatedNotifs))
            } else {
                state
            }
            val newUnread = if (nextState.conversationsState is UiState.Success && updatedNotifs != null) {
                computeLocalUnreadCount(nextState)
            } else if (wasUnread) {
                (state.unreadCount - 1).coerceAtLeast(0)
            } else {
                state.unreadCount.coerceAtLeast(0)
            }
            nextState.copy(unreadCount = newUnread)
        }
        viewModelScope.launch {
            try {
                repository.markNotificationRead(notificationId)
            } finally {
                inFlightReadNotifications.remove(notificationId)
                refreshUnreadCountSuspend()
            }
        }
    }

    fun onConversationOpened(
        conversationId: String,
        knownMessageIds: Collection<String> = emptyList()
    ) {
        val token = ++nextReadToken
        activeReadTokens[conversationId] = token
        inFlightReadConversations.add(conversationId)
        _uiState.update { state ->
            val currentConvs = (state.conversationsState as? UiState.Success)?.data
            val clearedUnread = currentConvs?.firstOrNull { it.id == conversationId }?.unreadCount ?: 0
            val updatedConvs = currentConvs?.map { conv ->
                if (conv.id == conversationId) conv.copy(unreadCount = 0) else conv
            }
            val nextState = if (updatedConvs != null) {
                state.copy(conversationsState = UiState.Success(updatedConvs))
            } else {
                state
            }
            val nextUnread = if (nextState.notificationsState is UiState.Success && updatedConvs != null) {
                computeLocalUnreadCount(nextState)
            } else {
                (state.unreadCount - clearedUnread).coerceAtLeast(0)
            }
            nextState.copy(unreadCount = nextUnread)
        }
        markReadJobs[conversationId]?.cancel()
        val job = viewModelScope.launch {
            try {
                repository.markConversationRead(conversationId, knownMessageIds)
            } finally {
                if (activeReadTokens[conversationId] == token) {
                    activeReadTokens.remove(conversationId)
                    inFlightReadConversations.remove(conversationId)
                    refreshConversationsSuspend()
                    refreshUnreadCountSuspend()
                }
            }
        }
        markReadJobs[conversationId] = job
    }

    fun loadInbox() {
        viewModelScope.launch {
            // Immediately reflect that notifications in Inbox are being viewed
            _uiState.update { state ->
                val currentNotifs = (state.notificationsState as? UiState.Success)?.data
                val updatedNotifs = currentNotifs?.map { it.copy(isRead = true) }
                val nextState = state.copy(
                    notificationsState = updatedNotifs?.let { UiState.Success(it) } ?: state.notificationsState
                )
                if (nextState.conversationsState is UiState.Success) {
                    nextState.copy(unreadCount = computeLocalUnreadCount(nextState))
                } else {
                    nextState
                }
            }
            if (_uiState.value.notificationsState !is UiState.Success) {
                _uiState.update { it.copy(notificationsState = UiState.Loading) }
            }
            if (_uiState.value.conversationsState !is UiState.Success) {
                _uiState.update { it.copy(conversationsState = UiState.Loading) }
            }
            repository.getNotifications(markAsRead = true).fold(
                onSuccess = { list ->
                    _uiState.update { state ->
                        val next = state.copy(notificationsState = UiState.Success(list))
                        if (next.conversationsState is UiState.Success) {
                            next.copy(unreadCount = computeLocalUnreadCount(next))
                        } else next
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(notificationsState = UiState.Error(err.message ?: "Error")) }
                }
            )
            refreshConversationsSuspend()
            refreshUnreadCountSuspend()
        }
    }

    fun refreshConversationsOnly() {
        viewModelScope.launch {
            refreshConversationsSuspend()
            refreshUnreadCountSuspend()
        }
    }

    fun startInboxRealtime() {
        inboxRealtimeJob?.cancel()
        inboxRealtimeJob = viewModelScope.launch {
            repository.observeInboxRealtime().collect { event ->
                if (event is RealtimeChatEvent.InboxActivityChanged || event is RealtimeChatEvent.Connected) {
                    repository.getNotifications(markAsRead = false).onSuccess { list ->
                        val adjusted = list.map { notif ->
                            if (notif.id in inFlightReadNotifications) notif.copy(isRead = true) else notif
                        }
                        _uiState.update { it.copy(notificationsState = UiState.Success(adjusted)) }
                    }
                    refreshConversationsSuspend()
                    refreshUnreadCountSuspend()
                }
            }
        }
    }

    fun stopInboxRealtime() {
        inboxRealtimeJob?.cancel()
        inboxRealtimeJob = null
    }

    fun setFilter(filter: String) {
        _uiState.update { it.copy(selectedFilter = filter) }
    }

    fun openConversationWithUser(targetUserId: String, onOpened: (String) -> Unit) {
        viewModelScope.launch {
            repository.startOrGetConversation(targetUserId).fold(
                onSuccess = { conv ->
                    _uiState.update { it.copy(activeConversation = conv) }
                    onConversationOpened(conv.id)
                    onOpened(conv.id)
                },
                onFailure = { err ->
                    _uiState.update { it.copy(error = err.message) }
                }
            )
        }
    }

    fun startChatPolling(conversationId: String, currentUserId: String? = null) {
        if (activeChatId != conversationId) {
            activeChatId = conversationId
            _uiState.update {
                it.copy(
                    messagesState = UiState.Loading,
                    stagedImageUri = null,
                    isRealtimeConnected = false
                )
            }
        }
        onConversationOpened(conversationId)

        viewModelScope.launch {
            if (_uiState.value.activeConversation?.id != conversationId) {
                repository.getConversationById(conversationId)?.let { conv ->
                    _uiState.update { it.copy(activeConversation = conv) }
                }
            }
        }

        chatRealtimeJob?.cancel()
        chatRealtimeJob = viewModelScope.launch {
            while (isActive) {
                runCatching {
                    repository.observeConversationRealtime(conversationId).collect { event ->
                        when (event) {
                            is RealtimeChatEvent.Connected -> {
                                _uiState.update { it.copy(isRealtimeConnected = true) }
                                syncConversationOnce(conversationId, currentUserId)
                            }
                            is RealtimeChatEvent.Disconnected -> {
                                _uiState.update { it.copy(isRealtimeConnected = false) }
                            }
                            is RealtimeChatEvent.MessageInserted -> {
                                val normalized = event.message.normalized()
                                val fromOther = currentUserId == null || normalized.senderId != currentUserId
                                val displayMsg = if (fromOther) {
                                    normalized.copy(isRead = true, status = "read")
                                } else normalized
                                upsertSingleMessage(displayMsg)
                                if (fromOther) {
                                    onConversationOpened(conversationId, listOf(normalized.id))
                                }
                            }
                            is RealtimeChatEvent.MessageUpdated -> {
                                upsertSingleMessage(event.message.normalized())
                            }
                            else -> Unit
                        }
                    }
                }
                _uiState.update { it.copy(isRealtimeConnected = false) }
                delay(4000)
            }
        }

        chatPollingJob?.cancel()
        chatPollingJob = viewModelScope.launch {
            while (isActive) {
                syncConversationOnce(conversationId, currentUserId)
                val interval = if (_uiState.value.isRealtimeConnected) 10_000L else 2_500L
                delay(interval)
            }
        }
    }

    private suspend fun syncConversationOnce(conversationId: String, currentUserId: String?) {
        repository.getMessages(conversationId).fold(
            onSuccess = { serverMsgs ->
                val incomingFromOther = if (currentUserId != null) {
                    serverMsgs.filter { it.senderId != currentUserId }
                } else {
                    val otherId = _uiState.value.activeConversation?.otherUser?.id
                    if (!otherId.isNullOrBlank()) serverMsgs.filter { it.senderId == otherId } else emptyList()
                }
                val incomingIds = incomingFromOther.map { it.id }
                if (incomingIds.isNotEmpty()) {
                    repository.markMessagesSeenLocally(incomingIds)
                }
                val hasUnreadFromOther = incomingFromOther.any { !it.isRead || it.resolvedStatus != "read" }
                val incomingIdSet = incomingIds.toHashSet()
                val displayIncoming = if (incomingIdSet.isNotEmpty()) {
                    serverMsgs.map { msg ->
                        if (msg.id in incomingIdSet) msg.copy(isRead = true, status = "read") else msg
                    }
                } else serverMsgs

                val currentList = (_uiState.value.messagesState as? UiState.Success)?.data ?: emptyList()
                val merged = mergeMessages(currentList, displayIncoming)
                _uiState.update { it.copy(messagesState = UiState.Success(merged)) }

                if (hasUnreadFromOther) {
                    val token = ++nextReadToken
                    activeReadTokens[conversationId] = token
                    inFlightReadConversations.add(conversationId)
                    try {
                        repository.markConversationRead(conversationId, incomingIds)
                    } finally {
                        if (activeReadTokens[conversationId] == token) {
                            activeReadTokens.remove(conversationId)
                            inFlightReadConversations.remove(conversationId)
                        }
                    }
                    refreshConversationsSuspend()
                    refreshUnreadCountSuspend()
                    repository.getMessages(conversationId).getOrNull()?.let { refreshed ->
                        val latestList = (_uiState.value.messagesState as? UiState.Success)?.data ?: emptyList()
                        _uiState.update { it.copy(messagesState = UiState.Success(mergeMessages(latestList, refreshed))) }
                    }
                }
            },
            onFailure = { err ->
                if (_uiState.value.messagesState is UiState.Loading) {
                    _uiState.update { it.copy(messagesState = UiState.Error(err.message ?: "Failed to load messages")) }
                }
            }
        )
    }

    fun stopChatPolling() {
        val closingId = activeChatId
        activeChatId = null
        chatPollingJob?.cancel()
        chatPollingJob = null
        chatRealtimeJob?.cancel()
        chatRealtimeJob = null
        _uiState.update { it.copy(isRealtimeConnected = false) }
        viewModelScope.launch {
            if (closingId != null) {
                markReadJobs[closingId]?.join()
            }
            refreshConversationsSuspend()
            refreshUnreadCountSuspend()
        }
    }

    fun stageImage(uri: Uri?) {
        _uiState.update { it.copy(stagedImageUri = uri) }
    }

    fun sendMessage(conversationId: String, text: String, senderId: String = "") {
        val clean = text.trim()
        if (clean.isBlank()) return
        val clientId = UUID.randomUUID().toString()
        val optimistic = MessageItem(
            id = "local_$clientId",
            conversationId = conversationId,
            senderId = senderId,
            clientId = clientId,
            messageType = "text",
            content = clean,
            status = "sending",
            createdAt = Instant.now().toString()
        )
        upsertSingleMessage(optimistic)

        viewModelScope.launch {
            sendWithRetry(
                conversationId = conversationId,
                content = clean,
                clientId = clientId,
                messageType = "text",
                mediaUrl = "",
                mediaDurationMs = 0L
            ).fold(
                onSuccess = { serverMsg ->
                    upsertSingleMessage(serverMsg)
                },
                onFailure = { err ->
                    markLocalMessageFailed(clientId)
                    _uiState.update { it.copy(error = err.message ?: "Message failed to send") }
                }
            )
        }
    }

    fun sendImageMessage(
        contentResolver: ContentResolver,
        conversationId: String,
        senderId: String,
        imageUri: Uri,
        caption: String = ""
    ) {
        val clientId = UUID.randomUUID().toString()
        val cleanCaption = caption.trim()
        val optimistic = MessageItem(
            id = "local_$clientId",
            conversationId = conversationId,
            senderId = senderId,
            clientId = clientId,
            messageType = "image",
            content = cleanCaption,
            mediaUrl = imageUri.toString(),
            status = "sending",
            createdAt = Instant.now().toString(),
            localPreviewUri = imageUri.toString()
        )
        _uiState.update { it.copy(stagedImageUri = null, isSendingMedia = true) }
        upsertSingleMessage(optimistic)

        viewModelScope.launch {
            val uploadRes = uploadImageWithRetry(contentResolver, imageUri)
            val uploadedUrl = uploadRes.getOrNull()
            if (uploadedUrl == null) {
                markLocalMessageFailed(clientId)
                _uiState.update {
                    it.copy(
                        isSendingMedia = false,
                        error = uploadRes.exceptionOrNull()?.message ?: "Failed to upload photo"
                    )
                }
                return@launch
            }

            sendWithRetry(
                conversationId = conversationId,
                content = cleanCaption,
                clientId = clientId,
                messageType = "image",
                mediaUrl = uploadedUrl,
                mediaDurationMs = 0L
            ).fold(
                onSuccess = { serverMsg ->
                    upsertSingleMessage(serverMsg.copy(localPreviewUri = imageUri.toString()))
                    _uiState.update { it.copy(isSendingMedia = false) }
                },
                onFailure = { err ->
                    markLocalMessageFailed(clientId)
                    _uiState.update {
                        it.copy(
                            isSendingMedia = false,
                            error = err.message ?: "Failed to send photo"
                        )
                    }
                }
            )
        }
    }

    fun sendVoiceMessage(
        contentResolver: ContentResolver,
        conversationId: String,
        senderId: String,
        voiceFile: File,
        durationMs: Long
    ) {
        val clientId = UUID.randomUUID().toString()
        val optimistic = MessageItem(
            id = "local_$clientId",
            conversationId = conversationId,
            senderId = senderId,
            clientId = clientId,
            messageType = "voice",
            content = "",
            mediaUrl = voiceFile.absolutePath,
            mediaDurationMs = durationMs,
            status = "sending",
            createdAt = Instant.now().toString(),
            localPreviewUri = voiceFile.absolutePath
        )
        _uiState.update { it.copy(isSendingMedia = true) }
        upsertSingleMessage(optimistic)

        viewModelScope.launch {
            val uploadRes = uploadVoiceWithRetry(contentResolver, voiceFile)
            val uploadedUrl = uploadRes.getOrNull()
            if (uploadedUrl == null) {
                markLocalMessageFailed(clientId)
                _uiState.update {
                    it.copy(
                        isSendingMedia = false,
                        error = uploadRes.exceptionOrNull()?.message ?: "Failed to upload voice message"
                    )
                }
                return@launch
            }

            sendWithRetry(
                conversationId = conversationId,
                content = "",
                clientId = clientId,
                messageType = "voice",
                mediaUrl = uploadedUrl,
                mediaDurationMs = durationMs
            ).fold(
                onSuccess = { serverMsg ->
                    upsertSingleMessage(serverMsg.copy(localPreviewUri = voiceFile.absolutePath))
                    _uiState.update { it.copy(isSendingMedia = false) }
                },
                onFailure = { err ->
                    markLocalMessageFailed(clientId)
                    _uiState.update {
                        it.copy(
                            isSendingMedia = false,
                            error = err.message ?: "Failed to send voice message"
                        )
                    }
                }
            )
        }
    }

    fun retryFailedMessage(contentResolver: ContentResolver, failedMsg: MessageItem) {
        val clientId = failedMsg.clientId.ifBlank { UUID.randomUUID().toString() }
        upsertSingleMessage(failedMsg.copy(clientId = clientId, status = "sending"))

        viewModelScope.launch {
            when {
                failedMsg.isImageMessage -> {
                    val uriStr = failedMsg.localPreviewUri ?: failedMsg.mediaUrl
                    val mediaUrl = if (uriStr.startsWith("http")) {
                        uriStr
                    } else {
                        val res = uploadImageWithRetry(contentResolver, Uri.parse(uriStr))
                        val url = res.getOrNull()
                        if (url == null) {
                            markLocalMessageFailed(clientId)
                            _uiState.update { it.copy(error = res.exceptionOrNull()?.message ?: "Retry failed") }
                            return@launch
                        }
                        url
                    }
                    sendWithRetry(
                        conversationId = failedMsg.conversationId,
                        content = failedMsg.content,
                        clientId = clientId,
                        messageType = "image",
                        mediaUrl = mediaUrl,
                        mediaDurationMs = 0L
                    ).fold(
                        onSuccess = { upsertSingleMessage(it.copy(localPreviewUri = failedMsg.localPreviewUri)) },
                        onFailure = { err ->
                            markLocalMessageFailed(clientId)
                            _uiState.update { it.copy(error = err.message) }
                        }
                    )
                }
                failedMsg.isVoiceMessage -> {
                    val mediaUrl = if (failedMsg.mediaUrl.startsWith("http")) {
                        failedMsg.mediaUrl
                    } else {
                        val file = File(failedMsg.localPreviewUri ?: failedMsg.mediaUrl)
                        val res = uploadVoiceWithRetry(contentResolver, file)
                        val url = res.getOrNull()
                        if (url == null) {
                            markLocalMessageFailed(clientId)
                            _uiState.update { it.copy(error = res.exceptionOrNull()?.message ?: "Retry failed") }
                            return@launch
                        }
                        url
                    }
                    sendWithRetry(
                        conversationId = failedMsg.conversationId,
                        content = "",
                        clientId = clientId,
                        messageType = "voice",
                        mediaUrl = mediaUrl,
                        mediaDurationMs = failedMsg.mediaDurationMs
                    ).fold(
                        onSuccess = { upsertSingleMessage(it.copy(localPreviewUri = failedMsg.localPreviewUri)) },
                        onFailure = { err ->
                            markLocalMessageFailed(clientId)
                            _uiState.update { it.copy(error = err.message) }
                        }
                    )
                }
                else -> {
                    sendWithRetry(
                        conversationId = failedMsg.conversationId,
                        content = failedMsg.content,
                        clientId = clientId,
                        messageType = "text",
                        mediaUrl = "",
                        mediaDurationMs = 0L
                    ).fold(
                        onSuccess = { upsertSingleMessage(it) },
                        onFailure = { err ->
                            markLocalMessageFailed(clientId)
                            _uiState.update { it.copy(error = err.message) }
                        }
                    )
                }
            }
        }
    }

    private suspend fun uploadImageWithRetry(contentResolver: ContentResolver, uri: Uri): Result<String> {
        val first = repository.uploadChatImage(contentResolver, uri)
        if (first.isSuccess) return first
        delay(1200)
        return repository.uploadChatImage(contentResolver, uri)
    }

    private suspend fun uploadVoiceWithRetry(contentResolver: ContentResolver, file: File): Result<String> {
        val first = repository.uploadChatVoice(contentResolver, file)
        if (first.isSuccess) return first
        delay(1200)
        return repository.uploadChatVoice(contentResolver, file)
    }

    private suspend fun sendWithRetry(
        conversationId: String,
        content: String,
        clientId: String,
        messageType: String,
        mediaUrl: String,
        mediaDurationMs: Long
    ): Result<MessageItem> {
        val first = repository.sendMessage(
            conversationId = conversationId,
            content = content,
            clientId = clientId,
            messageType = messageType,
            mediaUrl = mediaUrl,
            mediaDurationMs = mediaDurationMs
        )
        if (first.isSuccess) return first
        delay(1200)
        return repository.sendMessage(
            conversationId = conversationId,
            content = content,
            clientId = clientId,
            messageType = messageType,
            mediaUrl = mediaUrl,
            mediaDurationMs = mediaDurationMs
        )
    }

    private fun upsertSingleMessage(incoming: MessageItem) {
        _uiState.update { state ->
            val current = (state.messagesState as? UiState.Success)?.data ?: emptyList()
            val merged = mergeMessages(current, listOf(incoming))
            state.copy(messagesState = UiState.Success(merged))
        }
    }

    private fun markLocalMessageFailed(clientId: String) {
        _uiState.update { state ->
            val current = (state.messagesState as? UiState.Success)?.data ?: return@update state
            val updated = current.map { m ->
                if (m.clientId == clientId && m.id.startsWith("local_")) {
                    m.copy(status = "failed")
                } else m
            }
            state.copy(messagesState = UiState.Success(updated))
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        stopChatPolling()
        stopInboxRealtime()
    }

    companion object {
        internal fun computeLocalUnreadCount(
            state: InboxUiState,
            activeChatId: String? = null,
            inFlightReadConversations: Set<String> = emptySet(),
            inFlightReadNotifications: Set<String> = emptySet()
        ): Int {
            val unreadNotifs = (state.notificationsState as? UiState.Success)?.data
                ?.count { !it.isRead && it.id !in inFlightReadNotifications } ?: 0
            val unreadConvs = (state.conversationsState as? UiState.Success)?.data
                ?.sumOf { conv ->
                    if (conv.id == activeChatId || conv.id in inFlightReadConversations) 0 else conv.unreadCount.coerceAtLeast(0)
                } ?: 0
            return (unreadNotifs + unreadConvs).coerceAtLeast(0)
        }

        private fun statusRank(msg: MessageItem): Int = when (msg.resolvedStatus) {
            "read" -> 4
            "delivered" -> 3
            "sent" -> 2
            "sending" -> 1
            else -> 0
        }

        private fun pickFresherMessage(existing: MessageItem, candidate: MessageItem): MessageItem {
            // Prefer non-local server message over optimistic local message
            val mergedClientId = candidate.clientId.ifBlank { existing.clientId }
            val existingLocal = existing.id.startsWith("local_")
            val candidateLocal = candidate.id.startsWith("local_")
            if (existingLocal && !candidateLocal) {
                return candidate.copy(
                    clientId = mergedClientId,
                    localPreviewUri = candidate.localPreviewUri ?: existing.localPreviewUri
                )
            }
            if (!existingLocal && candidateLocal) {
                return existing.copy(
                    clientId = mergedClientId,
                    localPreviewUri = existing.localPreviewUri ?: candidate.localPreviewUri
                )
            }

            // Both are server (or both local): never downgrade read -> delivered -> sent due to stale poll race
            val preferred = if (statusRank(candidate) >= statusRank(existing)) candidate else existing
            val mergedPreview = candidate.localPreviewUri ?: existing.localPreviewUri
            val mergedDeliveredAt = candidate.deliveredAt?.takeIf { it.isNotBlank() } ?: existing.deliveredAt
            val mergedReadAt = candidate.readAt?.takeIf { it.isNotBlank() } ?: existing.readAt
            val mergedIsRead = existing.isRead || candidate.isRead || preferred.resolvedStatus == "read"
            return preferred.copy(
                clientId = mergedClientId,
                localPreviewUri = mergedPreview,
                deliveredAt = mergedDeliveredAt,
                readAt = mergedReadAt,
                isRead = mergedIsRead
            )
        }

        internal fun mergeMessages(
            current: List<MessageItem>,
            incoming: List<MessageItem>
        ): List<MessageItem> {
            val byKey = LinkedHashMap<String, MessageItem>()
            val clientToKey = HashMap<String, String>()
            val idToKey = HashMap<String, String>()

            fun upsertIntoMap(item: MessageItem) {
                val byIdKey = idToKey[item.id]
                val byClientKey = item.clientId.takeIf { it.isNotBlank() }?.let { clientToKey[it] }
                val existingKey = byIdKey ?: byClientKey
                if (existingKey != null) {
                    var prev = byKey[existingKey]!!
                    if (byIdKey != null && byClientKey != null && byIdKey != byClientKey) {
                        val duplicateLocal = byKey.remove(byClientKey)
                        if (duplicateLocal != null) {
                            prev = pickFresherMessage(duplicateLocal, prev)
                            idToKey[duplicateLocal.id] = existingKey
                        }
                    }
                    val merged = pickFresherMessage(prev, item)
                    byKey[existingKey] = merged
                    idToKey[merged.id] = existingKey
                    if (merged.clientId.isNotBlank()) clientToKey[merged.clientId] = existingKey
                } else {
                    val newKey = if (item.clientId.isNotBlank()) "c:${item.clientId}" else "i:${item.id}"
                    byKey[newKey] = item
                    idToKey[item.id] = newKey
                    if (item.clientId.isNotBlank()) clientToKey[item.clientId] = newKey
                }
            }

            current.forEach(::upsertIntoMap)
            incoming.forEach(::upsertIntoMap)

            return byKey.values.sortedWith(
                compareBy<MessageItem>(
                    { if (it.id.startsWith("local_")) 1 else 0 },
                    { it.createdAt }
                )
            )
        }
    }
}
