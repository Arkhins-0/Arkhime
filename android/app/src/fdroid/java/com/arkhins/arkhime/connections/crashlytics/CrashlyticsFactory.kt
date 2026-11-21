package com.arkhins.arkhime.connections.crashlytics

class CrashlyticsFactory {
    companion object {
        fun createCrashlytics(): CrashlyticsInterface {
            return CrashlyticsStub()
        }
    }
}