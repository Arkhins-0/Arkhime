package ani.arkhime.com.connections.crashlytics

class CrashlyticsFactory {
    companion object {
        fun createCrashlytics(): CrashlyticsInterface {
            return CrashlyticsStub()
        }
    }
}