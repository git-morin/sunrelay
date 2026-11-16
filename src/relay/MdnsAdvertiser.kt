package dev.morin.sunrelay.relay

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import dev.morin.sunrelay.sunshine.SunshineProbe

internal class MdnsAdvertiser(context: Context, private val log: (String) -> Unit) {
    private val nsd = context.getSystemService(NsdManager::class.java)
    private var listener: NsdManager.RegistrationListener? = null

    fun start() {
        val info = NsdServiceInfo().apply {
            serviceName = "SunRelay"
            serviceType = "_nvstream._tcp"
            port = SunshineProbe.PORT
        }
        val l = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = log("mDNS: advertised ${info.serviceName}")
            override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) = log("mDNS: register failed ($code)")
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) {}
        }
        listener = l
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, l) }.onFailure { log("mDNS: ${it.message}") }
    }

    fun stop() {
        listener?.let { runCatching { nsd.unregisterService(it) } }
        listener = null
    }
}
