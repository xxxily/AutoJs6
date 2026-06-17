package org.autojs.autojs.external

import android.content.Context
import android.content.Intent
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.ScriptExecutionListener
import org.autojs.autojs.model.script.PathChecker
import org.autojs.autojs.script.JavaScriptFileSource
import org.autojs.autojs.script.ScriptSource
import org.autojs.autojs.script.SequenceScriptSource
import org.autojs.autojs.script.StringScriptSource
import org.autojs.autojs.util.WorkingDirectoryUtils
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Created by Stardust on Apr 1, 2017.
 * Modified by SuperMonster003 as of Mar 25, 2023.
 * Transformed by SuperMonster003 on Mar 25, 2023.
 */
object ScriptIntents {

    const val EXTRA_KEY_PATH = "path"
    const val EXTRA_KEY_PRE_EXECUTE_SCRIPT = "script"
    const val EXTRA_KEY_LOOP_TIMES = "loop"
    const val EXTRA_KEY_LOOP_INTERVAL = "interval"
    const val EXTRA_KEY_DELAY = "delay"
    const val EXTRA_KEY_JSON = "json"

    const val EXTRA_KEY_TRUSTED_SOURCE = "org.autojs.autojs.extra.TRUSTED_SCRIPT_SOURCE"
    const val EXTRA_KEY_TRUST_TOKEN = "org.autojs.autojs.extra.SCRIPT_TRUST_TOKEN"
    private const val PREF_KEY_TRUST_TOKEN = "external_script_execution_trust_token"

    @JvmStatic
    fun isTaskerJsonObjectValid(json: JSONObject) = json.has(EXTRA_KEY_PATH) || json.has(EXTRA_KEY_PRE_EXECUTE_SCRIPT)

    @JvmStatic
    fun handleIntent(context: Context?, intent: Intent): ScriptExecution? = handleIntent(context, intent, null)

    @JvmStatic
    fun handleIntent(context: Context?, intent: Intent, listener: ScriptExecutionListener?): ScriptExecution? {
        if (!isTrusted(intent)) {
            return null
        }
        return handleTrustedIntent(context, intent, listener)
    }

    @JvmStatic
    fun handleTrustedIntent(context: Context?, intent: Intent): ScriptExecution? = handleTrustedIntent(context, intent, null)

    @JvmStatic
    fun handleTrustedIntent(context: Context?, intent: Intent, listener: ScriptExecutionListener?): ScriptExecution? {
        var path = getPath(intent)
        var script = intent.getStringExtra(EXTRA_KEY_PRE_EXECUTE_SCRIPT)

        if (intent.hasExtra(EXTRA_KEY_JSON)) {
            try {
                JSONObject(intent.getStringExtra(EXTRA_KEY_JSON)!!).let {
                    if (it.has(EXTRA_KEY_PATH)) {
                        path = it[EXTRA_KEY_PATH] as String
                    }
                    if (it.has(EXTRA_KEY_PRE_EXECUTE_SCRIPT)) {
                        script = it[EXTRA_KEY_PRE_EXECUTE_SCRIPT] as String
                    }
                }
            } catch (e: JSONException) {
                /* Ignored. */
            }
        }
        ExecutionConfig().let { config ->
            config.apply {
                delay = intent.getLongExtra(EXTRA_KEY_DELAY, 0)
                loopTimes = intent.getIntExtra(EXTRA_KEY_LOOP_TIMES, 1)
                interval = intent.getLongExtra(EXTRA_KEY_LOOP_INTERVAL, 0)
                config.workingDirectory = WorkingDirectoryUtils.path
                setArgument("intent", intent)
            }
            var source: ScriptSource? = null
            when {
                path == null && script != null -> source = StringScriptSource(script)
                path != null && PathChecker(context).checkAndToastError(path) -> {
                    JavaScriptFileSource(path).let { fss: JavaScriptFileSource ->
                        source = script?.let { SequenceScriptSource(fss.name, StringScriptSource(it), fss) } ?: fss
                    }
                    File(path).parent?.let { config.workingDirectory = it }
                }
            }
            return source?.let { AutoJs.instance.scriptEngineService.execute(it, listener, config) }
        }
        return null
    }

    @JvmStatic
    fun markTrusted(intent: Intent): Intent = intent
        .putExtra(EXTRA_KEY_TRUSTED_SOURCE, true)
        .putExtra(EXTRA_KEY_TRUST_TOKEN, trustToken())

    @JvmStatic
    fun isTrusted(intent: Intent): Boolean {
        val token = intent.getStringExtra(EXTRA_KEY_TRUST_TOKEN) ?: return false
        return token == trustToken()
    }

    private fun trustToken(): String {
        Pref.getStringOrNull(PREF_KEY_TRUST_TOKEN)?.let { return it }
        return UUID.randomUUID().toString().also { Pref.putStringSync(PREF_KEY_TRUST_TOKEN, it) }
    }

    private fun getPath(intent: Intent) = intent.data?.path ?: intent.getStringExtra(EXTRA_KEY_PATH)

}
