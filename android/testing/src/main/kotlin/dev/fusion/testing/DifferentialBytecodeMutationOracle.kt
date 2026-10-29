// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

/**
 * **Mutation-Adequacy Oracle Auditor** computing mutation kill score (`killed / nonEquivalentMutants`)
 * across boundary-flip (`<` <-> `<=`), off-by-one (`+1` <-> `-1`), and sign-negation arithmetic
 * mutants to guard against vacuous contract assertions!
 */
object DifferentialBytecodeMutationOracle {
    const val SCOPE =
        "Mutation-testing adequacy scorer evaluating boundary, off-by-one, and arithmetic mutant kill rates against contract suites."

    data class MutationCampaignReport(
        val totalMutants: Int,
        val killedMutants: Int,
        val survivedMutantNames: List<String>,
        val mutationKillRatio: Double,
    )

    fun <I, O> runMutationAudit(
        referenceFn: (I) -> O,
        namedMutants: Map<String, (I) -> O>,
        testInputs: List<I>,
    ): MutationCampaignReport {
        require(namedMutants.isNotEmpty() && testInputs.isNotEmpty())
        val expected = testInputs.map { referenceFn(it) }
        val survived = ArrayList<String>()
        var killed = 0

        for ((name, mutantFn) in namedMutants) {
            var caught = false
            for ((idx, inp) in testInputs.withIndex()) {
                val differs = try {
                    mutantFn(inp) != expected[idx]
                } catch (_: Throwable) {
                    true
                }
                if (differs) { caught = true; break }
            }
            if (caught) killed++ else survived.add(name)
        }
        return MutationCampaignReport(
            totalMutants = namedMutants.size,
            killedMutants = killed,
            survivedMutantNames = survived,
            mutationKillRatio = killed.toDouble() / namedMutants.size.toDouble(),
        )
    }
}
