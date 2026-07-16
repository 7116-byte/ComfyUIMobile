package com.local.comfyuimobile.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.NetworkCapabilities
import com.local.comfyuimobile.model.ServerProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.Inet4Address

class LanScanner(private val context: Context, private val client: ComfyClient) {
    suspend fun scan(): List<ServerProfile> = withContext(Dispatchers.IO) {
        val addresses = privateInterfaceAddresses()
        if (addresses.isEmpty()) return@withContext emptyList()
        val semaphore = Semaphore(32)
        coroutineScope {
            addresses.flatMap { LanAddress.subnet24(it.address.hostAddress.orEmpty()) }
                .distinct()
                .map { host ->
                    async {
                        semaphore.withPermit {
                            runCatching { client.probe("http://$host:8188").second }.getOrNull()
                        }
                    }
                }
                .awaitAll()
                .filterNotNull()
                .distinctBy { it.baseUrl }
        }
    }

    private fun privateInterfaceAddresses(): List<LinkAddress> {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        return manager.allNetworks.filter { network ->
            manager.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }.flatMap { network ->
            manager.getLinkProperties(network)?.linkAddresses.orEmpty()
        }.filter { link ->
            val address = link.address
            address is Inet4Address && LanAddress.isTrustedHost(address.hostAddress.orEmpty()) &&
                !address.isLoopbackAddress && link.prefixLength >= 16
        }
    }
}
