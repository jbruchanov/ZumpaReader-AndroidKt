package com.scurab.android.zumpareader

import com.squareup.otto.Bus
import java.util.*

/**
 * Created by JBruchanov on 19/01/2016.
 */
public class BusProvider {

    companion object {
        private val bus = Bus()
        private val registered = HashSet<Any>()

        public fun register(obj : Any) {
            if (registered.contains(obj)) {
                throw IllegalStateException("Object %s is already registered in BUS".format(obj.toString()));
            }
            bus.register(obj);
            registered.add(obj);
        }

        public fun unregister(obj: Any) {
            bus.unregister(obj);
            registered.remove(obj);
        }

        public fun post(event: Any) {
            bus.post(event);
        }

        public fun unregisterAll() {
            synchronized(BusProvider::class.java) {
                for (any in registered) {
                    bus.unregister(any)
                }
            }
            registered.clear();
        }
    }
}