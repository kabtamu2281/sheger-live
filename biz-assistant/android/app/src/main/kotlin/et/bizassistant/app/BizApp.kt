package et.bizassistant.app

import android.app.Application
import et.bizassistant.app.data.AppDatabase
import et.bizassistant.app.data.Repository
import et.bizassistant.app.net.BackendClient

class BizApp : Application() {
    lateinit var repository: Repository
        private set
    lateinit var backend: BackendClient
        private set

    override fun onCreate() {
        super.onCreate()
        repository = Repository(AppDatabase.create(this))
        backend = BackendClient(this, BuildConfig.BACKEND_URL)
    }
}
