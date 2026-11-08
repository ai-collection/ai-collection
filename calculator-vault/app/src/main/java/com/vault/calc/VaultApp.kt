package com.vault.calc

import android.app.Application
import com.vault.calc.crypto.VaultCrypto

class VaultApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Ensure the Keystore-backed master key exists as early as possible.
        VaultCrypto.ensureKey()
    }
}
