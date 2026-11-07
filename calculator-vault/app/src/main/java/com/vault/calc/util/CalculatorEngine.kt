package com.vault.calc.util

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * A small, dependency-free arithmetic evaluator for the calculator face.
 * Supports + - × ÷ with correct precedence, unary minus, percent and decimals.
 * Display symbols: × ÷ − are accepted alongside * / -.
 */
object CalculatorEngine {

    class CalcException(message: String) : Exception(message)

    fun evaluate(expression: String): String {
        val tokens = tokenize(expression)
        if (tokens.isEmpty()) return ""
        val rpn = toRpn(tokens)
        val result = evalRpn(rpn)
        return format(result)
    }

    private sealed interface Token {
        data class Num(val value: BigDecimal) : Token
        data class Op(val symbol: Char) : Token
    }

    private fun normalize(c: Char): Char = when (c) {
        '×' -> '*'
        '÷' -> '/'
        '−' -> '-'
        else -> c
    }

    private fun tokenize(expr: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val number = StringBuilder()
        var prevWasOperand = false

        fun flushNumber() {
            if (number.isNotEmpty()) {
                tokens.add(Token.Num(BigDecimal(number.toString())))
                number.clear()
                prevWasOperand = true
            }
        }

        var i = 0
        while (i < expr.length) {
            val c = normalize(expr[i])
            when {
                c.isDigit() || c == '.' -> number.append(c)
                c == '%' -> {
                    flushNumber()
                    // Replace last operand with operand/100.
                    val last = tokens.removeLastOrNull() as? Token.Num
                        ?: throw CalcException("Syntax error")
                    tokens.add(Token.Num(last.value.divide(BigDecimal(100))))
                    prevWasOperand = true
                }
                c == '-' && !prevWasOperand -> {
                    // Unary minus: begin a negative number.
                    number.append('-')
                }
                c in charArrayOf('+', '-', '*', '/') -> {
                    flushNumber()
                    tokens.add(Token.Op(c))
                    prevWasOperand = false
                }
                c == ' ' -> { /* ignore */ }
                else -> throw CalcException("Invalid character: $c")
            }
            i++
        }
        flushNumber()
        return tokens
    }

    private fun precedence(op: Char): Int = when (op) {
        '+', '-' -> 1
        '*', '/' -> 2
        else -> 0
    }

    private fun toRpn(tokens: List<Token>): List<Token> {
        val output = mutableListOf<Token>()
        val ops = ArrayDeque<Token.Op>()
        for (t in tokens) {
            when (t) {
                is Token.Num -> output.add(t)
                is Token.Op -> {
                    while (ops.isNotEmpty() && precedence(ops.last().symbol) >= precedence(t.symbol)) {
                        output.add(ops.removeLast())
                    }
                    ops.addLast(t)
                }
            }
        }
        while (ops.isNotEmpty()) output.add(ops.removeLast())
        return output
    }

    private fun evalRpn(rpn: List<Token>): BigDecimal {
        val stack = ArrayDeque<BigDecimal>()
        for (t in rpn) {
            when (t) {
                is Token.Num -> stack.addLast(t.value)
                is Token.Op -> {
                    val b = stack.removeLastOrNull() ?: throw CalcException("Syntax error")
                    val a = stack.removeLastOrNull() ?: throw CalcException("Syntax error")
                    val r = when (t.symbol) {
                        '+' -> a.add(b)
                        '-' -> a.subtract(b)
                        '*' -> a.multiply(b)
                        '/' -> {
                            if (b.signum() == 0) throw CalcException("Can't divide by zero")
                            a.divide(b, MathContext(16, RoundingMode.HALF_UP))
                        }
                        else -> throw CalcException("Syntax error")
                    }
                    stack.addLast(r)
                }
            }
        }
        return stack.removeLastOrNull() ?: throw CalcException("Syntax error")
    }

    private fun format(value: BigDecimal): String {
        val stripped = value.stripTrailingZeros()
        val plain = stripped.toPlainString()
        return if (plain == "-0") "0" else plain
    }
}
