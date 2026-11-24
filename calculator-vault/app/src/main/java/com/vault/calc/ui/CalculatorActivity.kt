package com.vault.calc.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.vault.calc.R
import com.vault.calc.crypto.SettingsManager
import com.vault.calc.databinding.ActivityCalculatorBinding
import com.vault.calc.util.CalculatorEngine

/**
 * The public face of the app: a fully working calculator.
 *
 * Hidden unlock: type your PIN (4–12 digits, no operators) and press "=".
 * On first launch, the first such code you confirm becomes your PIN.
 * A wrong code simply behaves like a normal calculation, giving nothing away.
 */
class CalculatorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCalculatorBinding
    private lateinit var settings: SettingsManager

    private var expression: String = ""
    private var justEvaluated: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCalculatorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        settings = SettingsManager.get(this)
        wireButtons()
        render()
    }

    override fun onResume() {
        super.onResume()
        // Leave no trace of a previous unlock attempt when returning.
        if (justEvaluated) clearAll()
    }

    private fun wireButtons() {
        val digits = listOf(
            binding.btn0, binding.btn1, binding.btn2, binding.btn3, binding.btn4,
            binding.btn5, binding.btn6, binding.btn7, binding.btn8, binding.btn9
        )
        digits.forEachIndexed { value, button ->
            button.setOnClickListener { onDigit(value.toString()) }
        }
        binding.btnDot.setOnClickListener { onDot() }
        binding.btnPlus.setOnClickListener { onOperator('+') }
        binding.btnMinus.setOnClickListener { onOperator('−') }
        binding.btnMultiply.setOnClickListener { onOperator('×') }
        binding.btnDivide.setOnClickListener { onOperator('÷') }
        binding.btnPercent.setOnClickListener { onPercent() }
        binding.btnPlusMinus.setOnClickListener { onToggleSign() }
        binding.btnClear.setOnClickListener { clearAll() }
        binding.btnBackspace.setOnClickListener { onBackspace() }
        binding.btnEquals.setOnClickListener { onEquals() }
    }

    private fun onDigit(d: String) {
        if (justEvaluated) { expression = ""; justEvaluated = false }
        expression += d
        render()
    }

    private fun onDot() {
        if (justEvaluated) { expression = ""; justEvaluated = false }
        // Prevent two dots in the current number segment.
        val lastSegment = expression.takeLastWhile { it.isDigit() || it == '.' }
        if (!lastSegment.contains('.')) {
            expression += if (lastSegment.isEmpty()) "0." else "."
            render()
        }
    }

    private fun onOperator(op: Char) {
        if (expression.isEmpty() && op != '−') return
        justEvaluated = false
        val last = expression.lastOrNull()
        if (last != null && last in "+−×÷") {
            // Replace a trailing operator rather than stacking.
            expression = expression.dropLast(1) + op
        } else {
            expression += op
        }
        render()
    }

    private fun onPercent() {
        if (expression.isNotEmpty() && expression.last().isDigit()) {
            expression += "%"
            render()
        }
    }

    private fun onToggleSign() {
        if (expression.isEmpty()) { expression = "−"; render(); return }
        // Toggle the sign of the trailing number segment.
        val idx = expression.indexOfLast { it in "+−×÷" }
        val head = if (idx >= 0) expression.substring(0, idx + 1) else ""
        val tail = if (idx >= 0) expression.substring(idx + 1) else expression
        expression = if (tail.startsWith("−") || (head.isEmpty() && expression.startsWith("−"))) {
            head + tail.removePrefix("−")
        } else {
            "$head−$tail"
        }
        render()
    }

    private fun onBackspace() {
        if (expression.isNotEmpty()) {
            expression = expression.dropLast(1)
            render()
        }
    }

    private fun clearAll() {
        expression = ""
        justEvaluated = false
        render()
    }

    private fun onEquals() {
        if (expression.isEmpty()) return

        if (isSecretCode(expression)) {
            handleSecretCode(expression)
            return
        }

        try {
            val result = CalculatorEngine.evaluate(expression)
            binding.display.text = result.ifEmpty { "0" }
            expression = result
            justEvaluated = true
        } catch (e: CalculatorEngine.CalcException) {
            binding.display.text = e.message
            expression = ""
            justEvaluated = true
        } catch (e: Exception) {
            binding.display.text = getString(R.string.error)
            expression = ""
            justEvaluated = true
        }
    }

    private fun isSecretCode(expr: String): Boolean =
        expr.length in 4..12 && expr.all { it.isDigit() }

    private fun handleSecretCode(code: String) {
        if (!settings.isPinConfigured) {
            confirmCreatePin(code)
        } else if (settings.verifyPin(code)) {
            clearAll()
            openVault()
        } else {
            // Wrong PIN: behave exactly like a normal calculation.
            binding.display.text = code
            expression = code
            justEvaluated = true
        }
    }

    private fun confirmCreatePin(code: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.create_pin_title)
            .setMessage(getString(R.string.create_pin_message, code))
            .setPositiveButton(R.string.set_pin) { _, _ ->
                settings.setPin(code)
                clearAll()
                openVault()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> clearAll() }
            .show()
    }

    private fun openVault() {
        startActivity(Intent(this, VaultActivity::class.java))
    }

    private fun render() {
        binding.display.text = expression.ifEmpty { "0" }
        // Keep the display scrolled to the end for long expressions.
        binding.display.post { binding.displayScroll.fullScroll(View.FOCUS_RIGHT) }
    }
}
