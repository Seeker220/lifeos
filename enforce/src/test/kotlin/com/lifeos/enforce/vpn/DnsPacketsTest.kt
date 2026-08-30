package com.lifeos.enforce.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsPacketsTest {

    @Test
    fun parsesQnameFromUdpDnsQuery() {
        val packet = dnsQuery("www.youtube.com")
        val query = DnsPackets.parseQuery(packet, packet.size)
        assertNotNull(query)
        assertEquals("www.youtube.com", query!!.qname)
        assertEquals(DnsPackets.DNS_PORT, query.dstPort)
        assertEquals(20, query.ipHeaderLength)
    }

    @Test
    fun ignoresNonDnsAndNonUdpPackets() {
        val tcp = dnsQuery("example.com").also { it[9] = 6 }
        assertNull(DnsPackets.parseQuery(tcp, tcp.size))

        val otherPort = dnsQuery("example.com").also {
            it[22] = 0
            it[23] = 80
        }
        assertNull(DnsPackets.parseQuery(otherPort, otherPort.size))

        val runt = ByteArray(10)
        assertNull(DnsPackets.parseQuery(runt, runt.size))
    }

    @Test
    fun nxDomainPayloadEchoesQuestionAndSetsRcode3() {
        val packet = dnsQuery("youtu.be")
        val query = DnsPackets.parseQuery(packet, packet.size)!!
        val payload = DnsPackets.nxDomainPayload(packet, query)

        assertEquals(packet[query.dnsOffset], payload[0]) // transaction id preserved
        assertEquals(packet[query.dnsOffset + 1], payload[1])
        assertEquals(0x80, payload[2].toInt() and 0x80) // QR set
        assertEquals(3, payload[3].toInt() and 0x0F) // NXDOMAIN
        assertEquals(1, payload[5].toInt()) // QDCOUNT
        assertEquals(0, payload[7].toInt()) // ANCOUNT
    }

    @Test
    fun responseSwapsAddressesAndPortsWithValidChecksum() {
        val packet = dnsQuery("youtu.be")
        val query = DnsPackets.parseQuery(packet, packet.size)!!
        val response = DnsPackets.buildResponse(query, DnsPackets.nxDomainPayload(packet, query))

        assertEquals(response.size, u16(response, 2)) // total length matches
        assertEquals(DnsPackets.PROTO_UDP, response[9].toInt() and 0xFF)
        // Source of the reply is the resolver the query was sent to.
        assertEquals(query.dstIp.toList(), response.copyOfRange(12, 16).toList())
        assertEquals(query.srcIp.toList(), response.copyOfRange(16, 20).toList())
        assertEquals(DnsPackets.DNS_PORT, u16(response, 20))
        assertEquals(query.srcPort, u16(response, 22))
        assertTrue("bad header checksum", headerChecksumValid(response))
    }

    private fun headerChecksumValid(packet: ByteArray): Boolean {
        var sum = 0
        for (i in 0 until 20 step 2) sum += u16(packet, i)
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum == 0xFFFF
    }

    private fun u16(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

    /** Builds an IPv4/UDP/DNS query for [host] the way a resolver client would. */
    private fun dnsQuery(host: String): ByteArray {
        val labels = host.split('.')
        val questionSize = labels.sumOf { it.length + 1 } + 1 + 4
        val dnsSize = 12 + questionSize
        val total = 20 + 8 + dnsSize
        val p = ByteArray(total)

        p[0] = ((4 shl 4) or 5).toByte()
        p[2] = ((total shr 8) and 0xFF).toByte()
        p[3] = (total and 0xFF).toByte()
        p[8] = 64
        p[9] = DnsPackets.PROTO_UDP.toByte()
        // 10.7.0.1 -> 10.7.0.2
        p[12] = 10; p[13] = 7; p[14] = 0; p[15] = 1
        p[16] = 10; p[17] = 7; p[18] = 0; p[19] = 2

        p[20] = 0xC0.toByte(); p[21] = 0x35.toByte() // src port 49205
        p[22] = 0; p[23] = 53
        val udpLen = 8 + dnsSize
        p[24] = ((udpLen shr 8) and 0xFF).toByte()
        p[25] = (udpLen and 0xFF).toByte()

        val dns = 28
        p[dns] = 0x12; p[dns + 1] = 0x34 // transaction id
        p[dns + 2] = 0x01 // RD
        p[dns + 5] = 1 // QDCOUNT
        var pos = dns + 12
        for (label in labels) {
            p[pos++] = label.length.toByte()
            for (c in label) p[pos++] = c.code.toByte()
        }
        p[pos++] = 0
        p[pos++] = 0; p[pos++] = 1 // QTYPE A
        p[pos++] = 0; p[pos] = 1 // QCLASS IN
        return p
    }
}
