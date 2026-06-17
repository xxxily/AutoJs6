package org.autojs.autojs.core.accessibility.monitor

import java.io.Closeable

class CloseableManager {

    private val mCloseables = hashSetOf<Closeable>()

    fun add(closeable: Closeable) {
        mCloseables.add(closeable)
    }

    fun recycleAll(): Int {
        var recycled = 0
        val iterator = mCloseables.iterator()
        while (iterator.hasNext()) {
            iterator.next().close()
            recycled += 1
        }
        mCloseables.clear()
        return recycled
    }

    fun remove(closeable: Closeable) {
        mCloseables.remove(closeable)
    }

}
