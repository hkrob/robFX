package app.robfx

import app.robfx.update.Releases
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleasesTest {
    private fun release(tag: String = "v1.0.3", prerelease: Boolean = false, url: String = "https://github.com/hkrob/robFX/releases/download/v1.0.3/robFX-1.0.3.apk", extra: String = "") =
        """{"tag_name":"$tag","draft":false,"prerelease":$prerelease,"body":"Fixes\n- a","assets":[
            {"name":"robFX-1.0.3.apk.sha256","size":90,"browser_download_url":"https://github.com/x/y.sha256"},
            {"name":"robFX-1.0.3.apk","size":15000000,"browser_download_url":"$url"$extra}]}"""

    @Test fun parsesTheApkAssetAndStripsTheV() {
        val i = Releases.parse(release())!!
        assertEquals("1.0.3", i.version)
        assertTrue(i.apkUrl.endsWith("robFX-1.0.3.apk"))
        assertEquals(15_000_000L, i.sizeBytes)
        assertEquals("Fixes\n- a", i.notes)
        assertNull(i.sha256)
    }

    @Test fun readsGithubsDigestWhenPresent() {
        val sha = "a".repeat(64)
        assertEquals(sha, Releases.parse(release(extra = ""","digest":"sha256:$sha""""))!!.sha256)
        assertNull(Releases.parse(release(extra = ""","digest":"sha256:short""""))!!.sha256)
    }

    @Test fun refusesPrereleasesAndForeignHosts() {
        assertNull(Releases.parse(release(prerelease = true)))
        assertNull(Releases.parse(release(url = "http://github.com/a.apk")))
        assertNull(Releases.parse(release(url = "https://evil.example/robFX.apk")))
        assertNull(Releases.parse("""{"tag_name":"v2","assets":[]}"""))
    }

    @Test fun versionsCompareNumerically() {
        assertTrue(Releases.isNewer("1.0.10", "1.0.9"))
        assertTrue(Releases.isNewer("1.1", "1.0.9"))
        assertTrue(Releases.isNewer("2.0.0", "1.99.99"))
        assertFalse(Releases.isNewer("1.0.2", "1.0.2"))
        assertFalse(Releases.isNewer("1.1", "1.1.0"))
        assertFalse(Releases.isNewer("1.0.1", "1.0.2"))
        assertFalse(Releases.isNewer("v1.0.2-beta", "1.0.2"))
    }
}
