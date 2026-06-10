package org.autojs.autojs.runtime.api

import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.ScriptableExtensions.prop
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicLong

class Ipc(private val scriptRuntime: ScriptRuntime) {

    fun publish(topic: String, payload: Any? = UNDEFINED, replyTo: String? = null, correlationId: String? = null): Message {
        return ScriptIpcBus.publish(scriptRuntime.ownerId, topic, payload, replyTo, correlationId)
    }

    fun request(topic: String, payload: Any? = UNDEFINED, replyTo: String = "ipc.reply.${UUID.randomUUID()}", correlationId: String? = null): Message {
        return publish(topic, payload, replyTo, correlationId)
    }

    fun reply(source: Any?, payload: Any? = UNDEFINED): Message {
        val sourceMessage = Message.from(source)
        val replyTopic = sourceMessage.replyTo
            ?: throw IllegalArgumentException("IPC source message ${sourceMessage.id} has no replyTo")
        return publish(replyTopic, payload, correlationId = sourceMessage.id)
    }

    fun subscribe(topic: String, callback: BaseFunction): String {
        return ScriptIpcBus.subscribe(scriptRuntime.ownerId, topic, scriptRuntime, callback)
    }

    fun unsubscribe(subscriptionId: String) = ScriptIpcBus.unsubscribe(subscriptionId)

    fun messages(topic: String? = null, limit: Int = DEFAULT_MESSAGE_LIMIT, clear: Boolean = false): List<Message> {
        return ScriptIpcBus.messages(topic, limit, clear)
    }

    fun clear(topic: String? = null) = ScriptIpcBus.clear(topic)

    fun recycle() {
        ScriptIpcBus.removeOwner(scriptRuntime.ownerId)
    }

    data class Message(
        val id: String,
        val topic: String,
        val payload: Any?,
        val sender: String,
        val replyTo: String?,
        val correlationId: String?,
        val timestamp: Long,
    ) {
        fun toNativeObject(): NativeObject = newNativeObject().also { obj ->
            obj.put("id", obj, id)
            obj.put("topic", obj, topic)
            obj.put("payload", obj, payload)
            obj.put("sender", obj, sender)
            obj.put("replyTo", obj, replyTo ?: UNDEFINED)
            obj.put("correlationId", obj, correlationId ?: UNDEFINED)
            obj.put("timestamp", obj, timestamp.toDouble())
        }

        companion object {
            fun from(source: Any?): Message = when (source) {
                is Message -> source
                is NativeObject -> Message(
                    id = Context.toString(source.prop("id")),
                    topic = Context.toString(source.prop("topic")),
                    payload = source.prop("payload"),
                    sender = Context.toString(source.prop("sender")),
                    replyTo = source.prop("replyTo").takeUnless { it.isJsNullish() }?.let { Context.toString(it) },
                    correlationId = source.prop("correlationId").takeUnless { it.isJsNullish() }?.let { Context.toString(it) },
                    timestamp = Context.toNumber(source.prop("timestamp")).toLong(),
                )
                else -> throw IllegalArgumentException("IPC reply source must be an IPC Message object")
            }
        }
    }

    private data class Subscriber(
        val id: String,
        val ownerId: String,
        val topic: String,
        val scriptRuntime: ScriptRuntime,
        val callback: BaseFunction,
    )

    private object ScriptIpcBus {

        private val nextMessageId = AtomicLong()
        private val inbox = ConcurrentLinkedDeque<Message>()
        private val subscribers = ConcurrentHashMap<String, Subscriber>()

        fun publish(sender: String, topic: String, payload: Any?, replyTo: String?, correlationId: String?): Message {
            require(topic.isNotBlank()) { "IPC topic must not be blank" }
            val message = Message(
                id = nextMessageId.incrementAndGet().toString(),
                topic = topic,
                payload = payload,
                sender = sender,
                replyTo = replyTo,
                correlationId = correlationId,
                timestamp = System.currentTimeMillis(),
            )
            inbox.addFirst(message)
            trimInbox()
            subscribers.values
                .filter { it.topic == topic || it.topic == "*" }
                .forEach { it.deliver(message) }
            return message
        }

        fun subscribe(ownerId: String, topic: String, scriptRuntime: ScriptRuntime, callback: BaseFunction): String {
            require(topic.isNotBlank()) { "IPC topic must not be blank" }
            val id = "${ownerId}:${UUID.randomUUID()}"
            subscribers[id] = Subscriber(id, ownerId, topic, scriptRuntime, callback)
            return id
        }

        fun unsubscribe(subscriptionId: String) = subscribers.remove(subscriptionId) != null

        fun messages(topic: String?, limit: Int, clear: Boolean): List<Message> {
            val effectiveLimit = limit.coerceIn(1, MAX_INBOX_SIZE)
            val matched = inbox
                .asSequence()
                .filter { topic.isNullOrBlank() || it.topic == topic }
                .take(effectiveLimit)
                .toList()
            if (clear) {
                inbox.removeAll(matched.toSet())
            }
            return matched
        }

        fun clear(topic: String?): Int {
            val matched = inbox.filter { topic.isNullOrBlank() || it.topic == topic }.toSet()
            inbox.removeAll(matched)
            return matched.size
        }

        fun removeOwner(ownerId: String) {
            subscribers.entries.removeIf { it.value.ownerId == ownerId }
        }

        private fun Subscriber.deliver(message: Message) {
            runCatching {
                scriptRuntime.bridges.call(callback, callback, arrayOf(message.toNativeObject()))
            }
        }

        private fun trimInbox() {
            while (inbox.size > MAX_INBOX_SIZE) {
                inbox.pollLast()
            }
        }
    }

    companion object {
        private const val DEFAULT_MESSAGE_LIMIT = 50
        private const val MAX_INBOX_SIZE = 500
    }
}
