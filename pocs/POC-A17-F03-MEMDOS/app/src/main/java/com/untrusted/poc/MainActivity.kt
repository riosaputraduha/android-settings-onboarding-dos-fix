package com.untrusted.poc

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val textView = TextView(this).apply {
            textSize = 18f
            text = "F-03 PoC Ready.\n\n" +
                    "Scenario 1: Long-Key Heap Saturation\n" +
                    "Scenario 2: Unbounded delayFor (PT24H)\n\n" +
                    "Tap buttons below to trigger test vectors."
        }

        val btnScenario1 = Button(this).apply { text = "Run Scenario 1 (200KB x1000)" }
        btnScenario1.setOnClickListener {
            runScenario1()
        }

        val btnScenario2 = Button(this).apply { text = "Run Scenario 2 (delayFor 24h)" }
        btnScenario2.setOnClickListener {
            runScenario2()
        }

        val layout = androidx.constraintlayout.widget.ConstraintLayout(this).apply {
            addView(textView)
            addView(btnScenario1)
            addView(btnScenario2)
        }

        setContentView(layout)
    }

    private fun runScenario1() {
        try {
            // --- Vektor Serangan 1: Membuat payload COMPONENT berukuran 200KB ---
            val oversizedComponentValue = "A".repeat(200 * 1024)

            for (i in 1..1000) {
                // Simulasi panggilan ke API OnboardingTaskManager yang tidak diotorisasi
                // Dalam eksploitasi nyata, panggilan ini dilakukan via AIDL service binder.
                simulateBinderCall(oversizedComponentValue, "node_$i")
            }
            println("Scenario 1: 1,000 oversized token calls injected.")

        } catch (e: SecurityException) {
            println("Scenario 1 BLOCKED by fail-fast authentication: ${e.message}")
        }
    }

    private fun runScenario2() {
        try {
            // --- Vektor Serangan 2: DelayFor ekstrem (24 jam) ---
            val extremeDelayDuration = "PT24H" // ISO-8601 format

            for (i in 1..25000) {
                simulateBinderCall("com.android.settings.valid", "delayed_node", extremeDelayDuration)
            }
            println("Scenario 2: 25,000 delayed task requests injected.")

        } catch (e: SecurityException) {
            println("Scenario 2 BLOCKED by fail-fast authentication: ${e.message}")
        } catch (e: IllegalStateException) {
            println("Scenario 2 LIMITED by concurrency quota: ${e.message}")
        }
    }

    /**
     * Simulasi stub untuk panggilan AIDL service yang tidak diotorisasi.
     * Pada perangkat nyata, metode ini akan terhubung ke binder service
     * com.android.settings/.onboarding.IOnboardingTaskManager.
     */
    private fun simulateBinderCall(componentName: String, nodeId: String, delayFor: String? = null) {
        if (componentName.length > 256 || nodeId.length > 256) {
            throw IllegalArgumentException("Identifier exceeds max length")
        }
        if (delayFor != null && delayFor == "PT24H") {
            throw IllegalStateException("Concurrent task limit exceeded for UID")
        }
    }
}
