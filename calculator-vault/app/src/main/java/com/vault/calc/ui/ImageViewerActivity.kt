package com.vault.calc.ui

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.vault.calc.crypto.SettingsManager
import com.vault.calc.crypto.VaultCrypto
import com.vault.calc.data.VaultRepository
import com.vault.calc.databinding.ActivityImageViewerBinding
import com.vault.calc.util.BitmapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shows a decrypted image fully in-memory; nothing is written to disk in clear text. */
class ImageViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImageViewerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (SettingsManager.get(this).secureWindow) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }
        binding = ActivityImageViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val id = intent.getStringExtra(EXTRA_ID)
        if (id == null) { finish(); return }

        binding.fullImage.setOnClickListener { finish() }

        lifecycleScope.launch {
            val bmp = runCatching {
                val file = VaultRepository.get(this@ImageViewerActivity).blobFile(id)
                val bytes = withContext(Dispatchers.IO) { VaultCrypto.decryptToBytes(file) }
                withContext(Dispatchers.Default) {
                    // Cap decode size so very large photos don't blow up memory.
                    BitmapUtils.decodeSampled(bytes, 2048, 2048)
                }
            }.getOrNull()
            binding.progress.visibility = View.GONE
            if (bmp != null) binding.fullImage.setImageBitmap(bmp) else finish()
        }
    }

    companion object {
        const val EXTRA_ID = "item_id"
    }
}
