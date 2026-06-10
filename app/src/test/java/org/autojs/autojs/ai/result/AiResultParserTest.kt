package org.autojs.autojs.ai.result

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiResultParserTest {

    @Test
    fun parsesPlainJsonResult() {
        val result = AiResultParser.parse(
            """
            {
              "intent": "create_script",
              "summary": "Create a simple script",
              "files": [
                {
                  "path": "main.js",
                  "language": "javascript",
                  "operation": "create",
                  "content": "toast('ok');"
                }
              ],
              "usedApis": [
                {
                  "name": "toast",
                  "doc": "dialogs.html",
                  "reason": "Show completion"
                }
              ],
              "requirements": ["notifications"],
              "risks": [],
              "warnings": [],
              "verificationSteps": ["Run the script"]
            }
            """.trimIndent(),
        )

        assertEquals("create_script", result.intent)
        assertEquals("Create a simple script", result.summary)
        assertEquals("main.js", result.files.single().path)
        assertEquals("toast('ok');", result.files.single().content)
        assertEquals("toast", result.usedApis.single().name)
        assertEquals(listOf("notifications"), result.requirements)
    }

    @Test
    fun parsesFencedJsonResult() {
        val result = AiResultParser.parse(
            """
            Here is the result:

            ```json
            {"intent":"explain","summary":"Explain script","files":[]}
            ```
            """.trimIndent(),
        )

        assertEquals("explain", result.intent)
        assertEquals("Explain script", result.summary)
        assertTrue(result.files.isEmpty())
    }

    @Test
    fun rejectsResponseWithoutJsonObject() {
        val error = assertThrows(AiResultException::class.java) {
            AiResultParser.parse("no structured result")
        }

        assertEquals("AI response does not contain a JSON object", error.message)
    }
}
