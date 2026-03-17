package io.bimurto.crac.config

import org.crac.Context
import org.crac.Core
import org.crac.Resource
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(value = ["crac.enabled"], havingValue = "true")
class CracConfig(
    val environment: Environment
) : Resource {

    private val log = LoggerFactory.getLogger(CracConfig::class.java)

    companion object {
        var isRestored = false
        var cracEnabled = false
    }

    init {
        Core.getGlobalContext().register(this)
        if (environment.activeProfiles.contains("crac")) {
            cracEnabled = true
        }
    }

    override fun beforeCheckpoint(p0: Context<out Resource>) {
        log.info("Checkpoint started.")
        log.info("Would sleep for 5 seconds to allow time to close the sockets.")
        Thread.sleep(5000)
    }

    override fun afterRestore(p0: Context<out Resource>) {
        isRestored = true
        log.info("Checkpoint restored.")
    }
}
