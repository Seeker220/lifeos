package com.lifeos.enforce.vpn

/**
 * Minimal IPv4/UDP/DNS codec for the DNS-filtering tunnel.
 *
 * The tunnel only routes the sink DNS address, so every packet we read is a DNS
 * query. We answer blocked names ourselves and forward the rest, which is why we
 * only need to parse question sections and re-wrap payloads.
 */
object DnsPackets {
    const val PROTO_UDP = 17
    const val DNS_PORT = 53
    private const val IPV4 = 4
    private const val MIN_IP_HEADER = 20
    private const val UDP_HEADER = 8
    private const val DNS_HEADER = 12

    data class Query(
        val ipHeaderLength: Int,
        val srcIp: ByteArray,
        val dstIp: ByteArray,
        val srcPort: Int,
        val dstPort: Int,
        val dnsOffset: Int,
        val dnsLength: Int,
        val qname: String,
    ) {
        // Arrays in a data class: identity equals is fine here, we never compare queries.
        override fun equals(other: Any?): Boolean = this === other
        override fun hashCode(): Int = System.identityHashCode(this)
    }

    fun parseQuery(packet: ByteArray, length: Int): Query? {
        if (length < MIN_IP_HEADER + UDP_HEADER + DNS_HEADER) return null
        val versionAndIhl = packet[0].toInt() and 0xFF
        if ((versionAndIhl shr 4) != IPV4) return null
        val ipHeaderLength = (versionAndIhl and 0x0F) * 4
        if (ipHeaderLength < MIN_IP_HEADER || length < ipHeaderLength + UDP_HEADER) return null
        if ((packet[9].toInt() and 0xFF) != PROTO_UDP) return null

        val srcIp = packet.copyOfRange(12, 16)
        val dstIp = packet.copyOfRange(16, 20)
        val udp = ipHeaderLength
        val srcPort = readU16(packet, udp)
        val dstPort = readU16(packet, udp + 2)
        if (dstPort != DNS_PORT) return null

        val udpLength = readU16(packet, udp + 4)
        val dnsOffset = udp + UDP_HEADER
        // Trust the smaller of the UDP length and what we actually read.
        val declared = udpLength - UDP_HEADER
        val available = length - dnsOffset
        val dnsLength = minOf(declared, available).takeIf { it >= DNS_HEADER } ?: return null

        val qname = readQName(packet, dnsOffset, dnsLength) ?: return null
        return Query(ipHeaderLength, srcIp, dstIp, srcPort, dstPort, dnsOffset, dnsLength, qname)
    }

    /** Reads the first question's name. Pointers are not valid in questions, so labels only. */
    private fun readQName(packet: ByteArray, dnsOffset: Int, dnsLength: Int): String? {
        if (readU16(packet, dnsOffset + 4) < 1) return null
        var pos = dnsOffset + DNS_HEADER
        val end = dnsOffset + dnsLength
        val out = StringBuilder()
        while (pos < end) {
            val len = packet[pos].toInt() and 0xFF
            if (len == 0) return out.toString().lowercase().ifEmpty { null }
            // 0xC0 marks a compression pointer, which must not appear in a question.
            if (len and 0xC0 != 0) return null
            pos++
            if (pos + len > end) return null
            if (out.isNotEmpty()) out.append('.')
            for (i in 0 until len) {
                out.append((packet[pos + i].toInt() and 0xFF).toChar())
            }
            pos += len
        }
        return null
    }

    /** NXDOMAIN answer echoing the original question, which is what a resolver expects. */
    fun nxDomainPayload(packet: ByteArray, query: Query): ByteArray {
        val questionEnd = questionEnd(packet, query) ?: (query.dnsOffset + query.dnsLength)
        val questionLength = questionEnd - (query.dnsOffset + DNS_HEADER)
        val out = ByteArray(DNS_HEADER + questionLength)
        out[0] = packet[query.dnsOffset]
        out[1] = packet[query.dnsOffset + 1]
        val recursionDesired = packet[query.dnsOffset + 2].toInt() and 0x01
        out[2] = (0x80 or recursionDesired).toByte() // QR=1
        out[3] = 0x83.toByte() // RA=1, RCODE=3 (NXDOMAIN)
        writeU16(out, 4, 1) // QDCOUNT
        // ANCOUNT/NSCOUNT/ARCOUNT stay zero.
        System.arraycopy(packet, query.dnsOffset + DNS_HEADER, out, DNS_HEADER, questionLength)
        return out
    }

    private fun questionEnd(packet: ByteArray, query: Query): Int? {
        var pos = query.dnsOffset + DNS_HEADER
        val end = query.dnsOffset + query.dnsLength
        while (pos < end) {
            val len = packet[pos].toInt() and 0xFF
            if (len == 0) {
                val afterName = pos + 1
                val afterType = afterName + 4 // QTYPE + QCLASS
                return if (afterType <= end) afterType else null
            }
            pos += 1 + len
        }
        return null
    }

    /** Wraps a DNS payload in IPv4+UDP, answering the sender of [query]. */
    fun buildResponse(query: Query, dnsPayload: ByteArray): ByteArray {
        val total = MIN_IP_HEADER + UDP_HEADER + dnsPayload.size
        val out = ByteArray(total)
        out[0] = ((IPV4 shl 4) or 5).toByte()
        out[1] = 0
        writeU16(out, 2, total)
        writeU16(out, 4, 0) // identification
        writeU16(out, 6, 0) // flags + fragment offset
        out[8] = 64 // TTL
        out[9] = PROTO_UDP.toByte()
        writeU16(out, 10, 0) // checksum placeholder
        System.arraycopy(query.dstIp, 0, out, 12, 4) // answer comes from the sink
        System.arraycopy(query.srcIp, 0, out, 16, 4)
        writeU16(out, 10, checksum(out, 0, MIN_IP_HEADER))

        val udp = MIN_IP_HEADER
        writeU16(out, udp, query.dstPort)
        writeU16(out, udp + 2, query.srcPort)
        writeU16(out, udp + 4, UDP_HEADER + dnsPayload.size)
        writeU16(out, udp + 6, 0) // UDP checksum is optional over IPv4
        System.arraycopy(dnsPayload, 0, out, udp + UDP_HEADER, dnsPayload.size)
        return out
    }

    private fun checksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        while (i < offset + length - 1) {
            sum += readU16(data, i)
            i += 2
        }
        if (i < offset + length) sum += (data[i].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv() and 0xFFFF
    }

    private fun readU16(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

    private fun writeU16(data: ByteArray, offset: Int, value: Int) {
        data[offset] = ((value shr 8) and 0xFF).toByte()
        data[offset + 1] = (value and 0xFF).toByte()
    }
}
