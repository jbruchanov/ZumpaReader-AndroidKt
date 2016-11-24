package com.scurab.android.zumpareader

import com.squareup.otto.Bus
import java.util.*

/**
 * Created by JBruchanov on 19/01/2016.
 */
class BusProvider {

    companion object {
        private val bus = Bus()
        private val registered = HashSet<Any>()

        fun register(obj: Any) {
            if (registered.contains(obj)) {
                throw IllegalStateException("Object %s is already registered in BUS".format(obj.toString()))
            }
            bus.register(obj)
            registered.add(obj)
        }

        fun unregister(obj: Any) {
            bus.unregister(obj)
            registered.remove(obj)
        }

        fun post(event: Any) {
            bus.post(event)
        }

        fun unregisterAll() {
            synchronized(BusProvider::class.java) {
                for (any in registered) {
                    bus.unregister(any)
                }
            }
            registered.clear()
        }
    }
}