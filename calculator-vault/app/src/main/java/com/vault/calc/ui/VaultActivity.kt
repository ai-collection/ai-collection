package com.vault.calc.ui

import android.os.Bundle
import android.text.InputType
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.vault.calc.R
import com.vault.calc.crypto.SettingsManager
import com.vault.calc.data.VaultItem
import com.vault.calc.databinding.ActivityVaultBinding

class VaultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVaultBinding
    private lateinit var settings: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SettingsManager.get(this)
        applySecureFlag()

        binding = ActivityVaultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_files -> show(VaultListFragment.newInstance(VaultItem.Category.FILE))
                R.id.nav_photos -> show(VaultListFragment.newInstance(VaultItem.Category.PHOTO))
                R.id.nav_network -> show(NetworkFragment())
                else -> return@setOnItemSelectedListener false
            }
            true
        }

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_lock -> { finish(); true }
                R.id.action_change_pin -> { promptChangePin(); true }
                R.id.action_secure -> {
                    val newValue = !settings.secureWindow
                    settings.secureWindow = newValue
                    item.isChecked = newValue
                    applySecureFlag()
                    true
                }
                else -> false
            }
        }
        binding.toolbar.menu.findItem(R.id.action_secure)?.isChecked = settings.secureWindow

        if (savedInstanceState == null) {
            binding.bottomNav.selectedItemId = R.id.nav_files
        }
    }

    private fun applySecureFlag() {
        if (settings.secureWindow) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun show(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, fragment)
            .commit()
    }

    private fun promptChangePin() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = getString(R.string.enter_new_pin)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.change_pin)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val pin = input.text.toString()
                if (pin.length in 4..12 && pin.all { it.isDigit() }) {
                    settings.setPin(pin)
                    Toast.makeText(this, R.string.pin_changed, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, R.string.enter_new_pin, Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
