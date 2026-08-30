package com.lifeos.enforce.vpn

import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.lifeos.core.Domains
import com.lifeos.core.LifeOsLog
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Answers DNS queries arriving on the tunnel: blocked names get NXDOMAIN, everything
 * else is forwarded to a real resolver over a protected socket so normal browsing is
 * untouched. Only the sink DNS address is routed into the tunnel, so no other traffic
 * reaches this loop.
 */
class DnsFilter(
    private val vpn: VpnService,
    private val fd: ParcelFileDescriptor,
    blockedDomains: List<String>,
    private val upstream: String = UPSTREAM_DNS,
) {
    private val blocked = blockedDomains.toList()
    private val input = FileInputStream(fd.fileDescriptor)
    private val output = FileOutputStream(fd.fileDescriptor)
    private val writeLock = Any()
    private val forwarders = Executors.newFixedThreadPool(4) { r ->
        Thread(r, "lifeos-dns-forward").apply { isDaemon = true }
    }

    @Volatile
    private var running = true

    fun run() {
        val buf = ByteArray(BUFFER)
        LifeOsLog.d(TAG, "dns filter up domains=$blocked upstream=$upstream")
        try {
            while (running && !Thread.currentThread().isInterrupted) {
                val read = input.read(buf)
                if (read <= 0) break
                val query = DnsPackets.parseQuery(buf, read) ?: continue
                val packet = buf.copyOf(read)
                if (Domains.matches(query.qname, blocked)) {
                    LifeOsLog.d(TAG, "dns NXDOMAIN ${query.qname}")
                    val payload = DnsPackets.nxDomainPayload(packet, query)
                    writeToTunnel(DnsPackets.buildResponse(query, payload))
                } else {
                    forwarders.execute { forward(packet, query) }
                }
            }
        } catch (e: IOException) {
            if (running) LifeOsLog.d(TAG, "dns loop ended: ${e.message}")
        } finally {
            closeStreams()
        }
    }

    fun stop() {
        running = false
        forwarders.shutdownNow()
        closeStreams()
    }

    private fun forward(packet: ByteArray, query: DnsPackets.Query) {
        runCatching {
            DatagramSocket().use { socket ->
                if (!vpn.protect(socket)) {
                    LifeOsLog.d(TAG, "protect() failed; dropping ${query.qname}")
                    return
                }
                socket.soTimeout = TIMEOUT_MS
                val payload = packet.copyOfRange(query.dnsOffset, query.dnsOffset + query.dnsLength)
                socket.send(
                    DatagramPacket(payload, payload.size, InetAddress.getByName(upstream), DnsPackets.DNS_PORT),
                )
                val inBuf = ByteArray(BUFFER)
                val reply = DatagramPacket(inBuf, inBuf.size)
                socket.receive(reply)
                writeToTunnel(DnsPackets.buildResponse(query, inBuf.copyOf(reply.length)))
            }
        }.onFailure {
            LifeOsLog.d(TAG, "dns forward failed ${query.qname}: ${it.message}")
        }
    }

    private fun writeToTunnel(bytes: ByteArray) {
        if (!running) return
        runCatching {
            synchronized(writeLock) {
                output.write(bytes)
                output.flush()
            }
        }.onFailure {
            LifeOsLog.d(TAG, "tunnel write failed: ${it.message}")
        }
    }

    private fun closeStreams() {
        runCatching { input.close() }
        runCatching { output.close() }
        runCatching { forwarders.awaitTermination(200, TimeUnit.MILLISECONDS) }
    }

    companion object {
        private const val TAG = "LifeOS/Vpn"
        private const val BUFFER = 4096
        private const val TIMEOUT_MS = 4000
        const val UPSTREAM_DNS = "8.8.8.8"
    }
}
