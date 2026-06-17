package org.autojs.autojs.core.plugin.center

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginIndexRepositoryTest {

    @Test
    fun parsesPluginManifestAndReleaseIntegrityFields() {
        val entries = PluginIndexRepository().parseIndexJson(
            """
            {
              "plugins": [
                {
                  "packageName": "org.autojs.plugin.ocr",
                  "title": "Paddle OCR",
                  "description": "OCR plugin",
                  "author": "AutoJs6",
                  "manifest": {
                    "type": "ocr",
                    "capabilities": ["ocr", "screen_capture"],
                    "permissions": ["android.permission.INTERNET"],
                    "riskLevel": "medium",
                    "minAutoJsVersion": "6.7.3",
                    "docsUrl": "https://example.com/plugin",
                    "examples": ["https://example.com/sample.js"],
                    "engines": [
                      {
                        "id": "paddle-ocr-pp-ocrv5",
                        "engine": "paddle-ocr",
                        "variant": "v5",
                        "label": "PP-OCRv5"
                      }
                    ]
                  },
                  "releases": [
                    {
                      "versionName": "1.2.0",
                      "versionCode": 12,
                      "apkUrl": "https://example.com/plugin.apk",
                      "apkSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                      "certificateSha256": [
                        "SHA256:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB"
                      ]
                    }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )

        val entry = entries.single()
        assertEquals("org.autojs.plugin.ocr", entry.packageName)
        assertEquals(PluginCapabilityManifest.TYPE_OCR, entry.manifest.pluginType)
        assertEquals(listOf("ocr", "screen_capture"), entry.manifest.capabilities)
        assertEquals(listOf("android.permission.INTERNET"), entry.manifest.permissions)
        assertEquals("medium", entry.manifest.riskLevel)
        assertEquals("6.7.3", entry.manifest.minAutoJsVersion)
        assertEquals("paddle-ocr-pp-ocrv5", entry.manifest.engines.single().id)
        assertEquals("1.2.0", entry.releases.single().versionName)
        assertEquals("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", entry.releases.single().apkSha256)
        assertEquals("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", entry.releases.single().certificateSha256.single())
        assertTrue(PluginIndexSecurity.validateOfficialIndexEntry(entry).isEmpty())
    }

    @Test
    fun supportsLegacyTopLevelReleaseFields() {
        val entries = PluginIndexRepository().parseIndexJson(
            """
            {
              "items": [
                {
                  "packageName": "org.autojs.plugin.legacy",
                  "title": "Legacy",
                  "description": "Legacy plugin",
                  "engine": "paddle-ocr",
                  "variant": "v3",
                  "engineId": "paddle-ocr-v3",
                  "manifest": {
                    "capabilities": ["ocr"],
                    "riskLevel": "medium",
                    "minAutoJsVersion": "6.7.3",
                    "docsUrl": "https://example.com/legacy"
                  },
                  "versionName": "1.0.0",
                  "versionCode": 1,
                  "apkUrl": "https://example.com/legacy.apk",
                  "apkSha256": "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
                  "certificateSha256": "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
                }
              ]
            }
            """.trimIndent(),
        )

        val entry = entries.single()
        assertEquals("paddle-ocr-v3", entry.manifest.engines.single().id)
        assertEquals("1.0.0", entry.releases.single().versionName)
        assertEquals("https://example.com/legacy.apk", entry.releases.single().apkUrl)
    }

    @Test
    fun rejectsOfficialIndexEntriesMissingGovernanceFields() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            PluginIndexRepository().parseIndexJson(
                """
                {
                  "plugins": [
                    {
                      "packageName": "org.autojs.plugin.incomplete",
                      "title": "Incomplete",
                      "description": "Missing governance fields",
                      "manifest": {
                        "capabilities": []
                      },
                      "releases": [
                        {
                          "versionName": "1.0.0",
                          "versionCode": 1,
                          "apkUrl": "https://example.com/plugin.apk"
                        }
                      ]
                    }
                  ]
                }
                """.trimIndent(),
            )
        }

        val message = error.message.orEmpty()
        assertTrue(message.contains("manifest.capabilities"))
        assertTrue(message.contains("manifest.riskLevel"))
        assertTrue(message.contains("manifest.minAutoJsVersion"))
        assertTrue(message.contains("manifest.documentationUrl"))
        assertTrue(message.contains("apkSha256"))
        assertTrue(message.contains("certificateSha256"))
    }

    @Test
    fun rejectsIndexWhenSignedPayloadDigestDoesNotMatch() {
        val error = assertThrows(IllegalStateException::class.java) {
            PluginIndexRepository().parseIndexJson(
                """
                {
                  "signature": {
                    "algorithm": "sha256",
                    "payloadSha256": "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
                  },
                  "plugins": [
                    {
                      "packageName": "org.autojs.plugin.ocr",
                      "title": "Paddle OCR",
                      "description": "OCR plugin"
                    }
                  ]
                }
                """.trimIndent(),
            )
        }

        assertTrue(error.message!!.contains("sha256 mismatch"))
    }

    @Test
    fun mapsPluginFailureReasonsToDiagnosticCodes() {
        val timed = PluginErrorMapper.fromThrowable(IllegalStateException("bindService timeout"), elapsedMillis = 1234L)
        assertEquals(PluginErrorCode.HANDSHAKE_TIMEOUT, timed.code)
        assertEquals(1234L, timed.elapsedMillis)
        assertTrue(timed.causeClass!!.contains("IllegalStateException"))

        assertEquals(
            PluginErrorCode.PLUGIN_NOT_INSTALLED,
            PluginErrorMapper.fromThrowable(IllegalStateException("Plugin x not found in installed apps")).code,
        )
        assertEquals(
            PluginErrorCode.PLUGIN_DISABLED,
            PluginErrorMapper.fromThrowable(IllegalStateException("Plugin x is not enabled")).code,
        )
        assertEquals(
            PluginErrorCode.SIGNATURE_UNTRUSTED,
            PluginErrorMapper.fromThrowable(IllegalStateException("certificate fingerprint mismatch")).code,
        )
        assertEquals(
            PluginErrorCode.VERSION_INCOMPATIBLE,
            PluginErrorMapper.fromThrowable(IllegalStateException("plugin version too low")).code,
        )
    }
}
