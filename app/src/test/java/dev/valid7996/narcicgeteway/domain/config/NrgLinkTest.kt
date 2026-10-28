package dev.valid7996.narcicgeteway.domain.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NrgLinkTest {
    private fun roundTrip(name: String, source: ProfileSource) {
        val link = NrgLink.build(name, source)
        assertNotNull("$source must produce a link", link)
        assertTrue(NrgLink.isNrgLink(link!!))
        val back = NrgLink.parse(link)
        assertNotNull("$source must parse back", back)
        assertEquals(name, back!!.name)
        assertEquals("$source must survive intact", source, back.source)
    }

    @Test
    @Test
    fun `ikev2 survives`() {
        roundTrip("Home", ProfileSource.Ikev2(Ikev2Profile(server = "vpn.example.com", username = "u", mtu = 1400)))
    }

    @Test
    @Test
    @Test
    @Test
    fun `a chain carries its members inside the link`() {
        val members = listOf(
            "vless://11111111-2222-3333-4444-555555555555@a.example.com:443?type=ws#A",
            "vless://11111111-2222-3333-4444-555555555555@b.example.com:443?type=ws#B",
        )
        val link = NrgLink.build("Chain", ProfileSource.ProxyChain(listOf("id-a", "id-b")), members)!!
        val back = NrgLink.parse(link)!!

        assertEquals(members, back.members)
        assertTrue(back.source is ProfileSource.ProxyChain)
    }

    @Test
    fun `handles claims exactly the sources with no community format`() {
        listOf(
            ProfileSource.Ikev2(Ikev2Profile()),
            ProfileSource.ProxyChain(emptyList()),
        ).forEach { assertTrue("$it should be handled", NrgLink.handles(it)) }

        listOf(
            ProfileSource.Link("vless://x@y:443"),
            ProfileSource.RawJson("{}"),
            ProfileSource.Sealed("zzz"),
            ProfileSource.SniSpoof(SniSpoofProfile(link = "vless://x@y:443", fakeSni = "a.b")),
        ).forEach { assertTrue("$it should NOT be handled", !NrgLink.handles(it)) }
    }

    @Test
    fun `garbage does not parse`() {
        assertNull(NrgLink.parse("vless://x@y:443"))
        assertNull(NrgLink.parse("narcicgeteway://"))
        assertNull(NrgLink.parse("narcicgeteway://!!!not-base64!!!"))
        assertNull(NrgLink.parse("narcicgetway://"))
    }

    @Test
    @Test
    fun `a sealed profile exposes nothing`() {
        val p = VpnProfile(
            id = "i", name = "Locked", protocol = "VLESS", address = "a", port = 1,
            transportLabel = "", source = ProfileSource.Sealed("base64"), addedAt = 0L,
        )
        assertNull(p.rawPayload())
    }

    @Test
    fun `every non-Xray engine now has a share payload`() {
        fun profile(src: ProfileSource) = VpnProfile(
            id = "i", name = "N", protocol = "X", address = "a", port = 1,
            transportLabel = "", source = src, addedAt = 0L,
        )
        listOf(
            ProfileSource.Ikev2(Ikev2Profile(server = "s")),
            ProfileSource.Psiphon(PsiphonProfile()),
        ).forEach {
            assertNotNull("$it had no share payload", profile(it).rawPayload())
        }
    }
}
