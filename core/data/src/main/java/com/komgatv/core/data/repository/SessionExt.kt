package com.komgatv.core.data.repository

import com.komgatv.core.network.ServerSessionManager
import snd.komga.client.KomgaClientFactory

/** 연결된 서버가 없으면 NotConnectedException */
internal fun ServerSessionManager.requireClient(): KomgaClientFactory =
    session.value?.komgaClient ?: throw NotConnectedException()

class NotConnectedException : IllegalStateException("Not connected to a Komga server")
