package com.paul.protocol.todevice

class SaveSettings(val settings: Map<String, Any>) : Protocol {
    override fun type(): ProtocolType {
        return ProtocolType.PROTOCOL_SAVE_SETTINGS
    }

    override fun payload(): List<Any> {
        return mutableListOf(settings)
    }
}