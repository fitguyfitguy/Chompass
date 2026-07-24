package app.chompass.services.update

sealed class AndroidUpdateState {
    object Idle : AndroidUpdateState()
    object Checking : AndroidUpdateState()
    data class UpToDate(val current: String, val latest: String?) : AndroidUpdateState()
    data class Available(val current: String, val latest: String) : AndroidUpdateState()
    data class Failed(val current: String) : AndroidUpdateState()
}
