package com.netwatch.android

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class GatewayModelsTest {
    @Test
    fun privateAddressPolicyMatchesRfc1918Only() {
        assertTrue(PairingPayload.isPrivateIpv4("10.1.2.3"))
        assertTrue(PairingPayload.isPrivateIpv4("172.16.0.1"))
        assertTrue(PairingPayload.isPrivateIpv4("172.31.255.254"))
        assertTrue(PairingPayload.isPrivateIpv4("192.168.100.2"))
        assertFalse(PairingPayload.isPrivateIpv4("172.32.0.1"))
        assertFalse(PairingPayload.isPrivateIpv4("127.0.0.1"))
        assertFalse(PairingPayload.isPrivateIpv4("8.8.8.8"))
        assertFalse(PairingPayload.isPrivateIpv4("router.lan"))
        assertFalse(PairingPayload.isPrivateIpv4("192.168.001.2"))
    }

    @Test
    fun qrPayloadRequiresProtocolPinSecretPrivateHostAndShortExpiry() {
        val raw = JSONObject()
            .put("version", 1)
            .put("host", "192.168.1.40")
            .put("port", 42117)
            .put("pairing_secret", "A".repeat(32))
            .put("server_spki_sha256", "B".repeat(43))
            .put("expires_at", Instant.now().plusSeconds(300).toString())
            .toString()
        assertEquals("192.168.1.40", PairingPayload.parse(raw).host)
    }

    @Test(expected = IllegalArgumentException::class)
    fun qrPayloadRejectsPublicHost() {
        val raw = JSONObject()
            .put("version", 1)
            .put("host", "203.0.113.4")
            .put("port", 42117)
            .put("pairing_secret", "A".repeat(32))
            .put("server_spki_sha256", "B".repeat(43))
            .put("expires_at", Instant.now().plusSeconds(300).toString())
            .toString()
        PairingPayload.parse(raw)
    }

    @Test
    fun homePayloadKeepsDesktopRailsAndRemoteArtwork() {
        val payload = JSONObject("""{
          "movies":[{"id":11,"type":"movie","title":"One","poster":"/remote/v1/artwork/w500/one.jpg"}],
          "recent_tv":[{"id":22,"type":"tv","title":"Two","is_anime":false,"backdrop":"/remote/v1/artwork/w780/two.jpg"}]
        }""")
        val sections = GatewayRepository.parseHome(payload)
        assertEquals(listOf("Trending Movies", "Recent TV"), sections.map { it.title })
        assertEquals("/remote/v1/artwork/w500/one.jpg", sections.first().items.first().artworkPath)
        assertTrue(sections.last().items.first().isSeries)
    }

    @Test
    fun seasonResponseReturnsSpecificEpisodesWithMetadata() {
        val payload = JSONObject("""{
          "season_number":2,
          "episodes":[
            {"episode_number":3,"name":"The Third","overview":"Specific episode","runtime":47,"rating":8.4,"still":"/remote/v1/artwork/w500/still.jpg"},
            {"episode_number":4,"name":"The Fourth"}
          ]
        }""")
        val episodes = GatewayRepository.parseEpisodes(payload)
        assertEquals(listOf(3, 4), episodes.map { it.number })
        assertEquals("The Third", episodes.first().name)
        assertEquals("Specific episode", episodes.first().overview)
        assertEquals(47, episodes.first().runtime)
    }
}
