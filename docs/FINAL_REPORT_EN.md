# Security Remediation Final Report: F-03 (Heap Exhaustion DoS)

| Document Information | Details |
|---|---|
| **Finding ID** | F-03 |
| **Vulnerability Title** | Onboarding Task Manager Accepts Unverified Caller Identity Leading to Heap Exhaustion DoS |
| **Target Component** | `android.onboarding` Task Manager (`com.android.settings`, UID 1000) |
| **Affected Target Build** | Android 17 userdebug (`emulator-5566`, SettingsGoogle 107 MB system_ext) |
| **Initial Severity** | **MEDIUM** (Local Denial of Service) |
| **Final Status** | **RESOLVED / MITIGATED** |
| **Completion Date** | October 21, 2026 |

---

## 1. Executive Summary

Vulnerability **F-03** allows an unprivileged, third-party application without special permissions (*zero-permission app*, UID `10xxx`) to crash the core Android system process `com.android.settings` (UID 1000 / `system`). The exploitation is executed by flooding the `IOnboardingTaskManager.startTask` IPC Binder interface with arbitrary token identities and extreme delay durations, causing severe Java heap exhaustion until the Android Runtime (ART) aborts due to `java.lang.OutOfMemoryError`.

Through a four-phase remediation lifecycle, the root cause was analyzed, isolated, and comprehensively resolved via an AOSP system-level patch (`patches/F-03-mitigation.patch`). The fix has been validated using an automated test suite (`tests/OnboardingTaskManagerSecurityTest.kt`) alongside dynamic heap resilience benchmarks. All identified exploit vectors have been successfully mitigated with zero regression impact on standard system functionality.

---

## 2. Threat Analysis & Exploitation Vectors

```
[ Attacker (UID 10xxx, Zero-Permission) ]
                   │
                   ▼ IPC Binder startTask() Call
[ OnboardingTaskManagerService ]
   ├─► Check 1: Caller Identity Verification (Pre-Patch: Accepted without certificate validation)
   ├─► Check 2: Token Resolution (Pre-Patch: Invalid tokens still committed 'Failure' state to memory)
   └─► Check 3: Task Execution (Pre-Patch: delayFor honoured without duration limits or per-caller quotas)
                   │
                   ▼
[ com.android.settings Java Heap Inflates > 192 MB ] ──► [ OOM Crash (System DoS) ]
```

### A. Vector 1: Long-Key Heap Saturation
The attacker transmits 1,000 Binder calls with oversized `COMPONENT` string parameters of ~200 KB per invocation. Because the system stores failure records for unregistered tokens into an unbounded `ConcurrentHashMap`, the Java Heap surges to 173 MB (PSS 227 MB), rapidly approaching the process growth limit threshold of 192 MB.

### B. Vector 2: Unbounded Delay Coroutine Accumulation
The attacker dispatches 25,000 Binder calls specifying `{delayFor="PT24H"}` within approximately 37 seconds. The `DiagnosisProviderTask` component retains active coroutines/threads and associated context objects in memory without upper time limits or caller-level concurrency constraints, triggering:
```text
FATAL EXCEPTION: binder:2403_7
Process: com.android.settings, PID: 2403
java.lang.OutOfMemoryError: Failed to allocate a 24 byte allocation
    with 19520 free bytes and 19KB until OOM, target footprint 201326592,
    growth limit 201326592
```

---

## 3. Remediation Strategy & Patch Architecture

The remediation is consolidated in the unified patch file `patches/F-03-mitigation.patch`, implementing five defense-in-depth architectural pillars:

### 1. Fail-Fast Caller Authentication
* Leverages `Binder.getCallingUid()` to reliably determine the tamper-proof caller process identity.
* Validates that the caller's package is installed on the device and matches the digital signature of the system or official SetupWizard packages via `PackageManager.checkSignatures()`.
* Unauthorized callers are immediately rejected with a `SecurityException` prior to any object allocation or state instantiation.

### 2. Token Validation Without Stray State Allocation
* Verifies tokens against `nodeRegistry.findNode(token)` upfront.
* If a token is unregistered, the system throws an `IllegalArgumentException` and **no longer** writes a `Failure` status entry into `taskStateManager`.

### 3. Input Clamping & Sanitization
* The string length of `COMPONENT` and `NODE_ID` is strictly clamped to a maximum of 256 characters (`MAX_IDENTIFIER_LENGTH = 256`).
* Any input exceeding this boundary is rejected immediately with an `IllegalArgumentException`.

### 4. Bounded LRU Cache & TTL Eviction
* Replaces the unbounded map structures in `OnboardingTaskStateManager` with a fixed-capacity `android.util.LruCache` (maximum 100 entries).
* Implements a 10-minute Time-to-Live (TTL) expiration window to purge stale entries automatically.

### 5. Delay Duration Clamping & Concurrency Quotas
* Clamps `delayFor` in `DiagnosisProviderTask` to a maximum ceiling of 60 seconds (`MAX_DELAY_MILLIS = 60_000L`).
* Enforces a concurrency quota of at most 5 pending tasks per UID concurrently. A 6th invocation from the same UID is rejected with an `IllegalStateException`.

---

## 4. Verification & Regression Testing Results

### A. Automated Test Suite Results (`tests/OnboardingTaskManagerSecurityTest.kt`)
All unit and integration test scenarios achieved a **PASS** status:

| Test ID | Scenario | Result |
|---|---|---|
| `SEC-01` | Invocations from an unprivileged, zero-permission application | **PASS** (Rejected via `SecurityException`, 0 state allocations) |
| `SEC-02` | Invocations with identifiers exceeding 256 characters | **PASS** (Rejected via `IllegalArgumentException`) |
| `SEC-03` | Invocations with unregistered tokens | **PASS** (Rejected, state manager remains clean) |
| `SEC-04` | Invocations requesting extreme delay (`PT24H`) | **PASS** (Automatically clamped to 60 seconds) |
| `SEC-05` | Invocations exceeding the concurrency quota of 5 tasks per UID | **PASS** (Rejected via `IllegalStateException`) |
| `FUNC-01`| Legitimate invocation from SetupWizard (UID 1000) | **PASS** (Task completed normally in 42 ms) |
| `BOUND-01`| Invocations with identifier length exactly at the 256-character boundary | **PASS** (Processed successfully) |
| `BOUND-02`| Ingestion of 150 entries into the state manager | **PASS** (Oldest 50 entries evicted from LRU cache) |

### B. Dynamic Heap Benchmark & Stress Test Results

| Metric Parameter | Pre-Patch (Exploitation) | Post-Patch (Mitigated) | Status |
|---|---|---|---|
| **Dalvik Heap Allocation** | Surges up to 173 MB (OOM limit: 192 MB) | Stable at ~21.2 MB | **Secure (-87.7%)** |
| **Total PSS** | 227 MB (Triggered ART runtime abort) | 34.6 MB | **Secure (-84.7%)** |
| **PSS Delta per 1,000 calls** | +192 MB | +40 KB | **Stable** |
| **Process PID Health** | Fatal crash `OutOfMemoryError` | Zero crashes (0 crashes) | **PASS** |

---

## 5. Deployment Instructions

For release engineering teams and AOSP / SettingsGoogle maintainers:
1. Ensure the working tree is checked out to the target Android 17 branch.
2. Apply the patch file:
   ```bash
   git apply patches/F-03-mitigation.patch
   ```
3. Run the automated test suite for local verification:
   ```bash
   atest SettingsRoboTests:OnboardingTaskManagerSecurityTest
   ```
4. Build the system image target:
   ```bash
   m SettingsGoogle
   ```

---

## 6. Conclusions & Long-Term Architectural Recommendations

1. **Fail-Fast IPC Architecture:** All Binder system services handling resource-intensive allocations must enforce caller identity and signature validation before allocating any heap objects or data structures.
2. **Mandatory Bounded Collections:** Eliminate unbounded in-memory data structures (`HashMap`, `ArrayList`) within privileged system processes (UID 1000) for externally supplied data. Use capacity-bounded constructs such as `LruCache` with quota controls.
3. **System-Wide Input Clamping Audits:** Enforce explicit, defensive upper bounds on all IPC-supplied parameters, including execution timeouts, duration intervals, string keys, and payload buffers.
