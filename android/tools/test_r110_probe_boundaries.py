#!/usr/bin/env python3
"""Controls for `check_r110_probe_boundaries.py`.

The gate exists because seventeen green host rounds could not see a compile error that Gradle's module boundary
produces. A gate that is only ever run against today's tree would repeat that mistake one level up, so these controls
attack it from four directions:

1. **The parser and the selection rule.** `testing/build.gradle.kts` is read with regular expressions, and a gate that
   selects nothing passes. The synthetic build file below pins all five exclusions — a second dependency, an
   `include(...)` filter, a `"$it"` loop directory, `extendsFrom(configurations.implementation)`, and two source
   directories — and pins that the one reproducible declaration *is* selected.
2. **The diagnostic reader.** kotlinc's `error:` prefix, an empty log, and the exact wording of the cross-module
   smart-cast message are all parsed. A silently empty `errors` list is the failure mode that matters.
3. **The rule itself, reproduced from scratch.** A two-file, two-compilation Kotlin example — a holder class compiled
   to a jar, a caller compiled against that jar — is built in a temporary folder. The pre-repair shape must fail with
   the message the CI job printed; the repaired shape (a local `val`) must compile. This is the executable statement
   of *why* the R93 repair is right, independent of this repository's product code.
4. **The gate on the real tree.** `verify(only=['r93AutoFilter'])` against the jar `tools/run_r110_checks.py`
   builds, and the refusal of a name the gate does not select.

The compile-level controls need the pinned kotlinc and are skipped — loudly, never silently — when it is absent.
"""
from __future__ import annotations

import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from bootstrap_host_tools import environment  # noqa: E402
from check_r110_probe_boundaries import (  # noqa: E402
    CACHE,
    Fail,
    SMART_CAST,
    diagnostics,
    parse_source_sets,
    selectable,
    verify,
)

KOTLINC = environment(CACHE)['KOTLINC']
JAVA_HOME = environment(CACHE)['JAVA_HOME']
HAVE_COMPILER = Path(KOTLINC).is_file()

PRODUCT_JAR = Path(os.environ.get('R110_PRODUCT_JAR', Path(__file__).resolve().parents[1] / 'testing/build/r110/product.jar'))

HOLDER = '''package holder

class Holder(val values: List<String>? = null, val custom: List<String>? = null, val join: Boolean = false)
'''

# The shape that sat in `testing/src/r93/.../R93Checks.kt` until R110: the null check and the use are on the same
# property, which Kotlin allows inside one compilation unit and refuses across two.
BEFORE = '''package caller

import holder.Holder

object Probe {
    fun kind(holder: Holder): String =
        if (holder.values != null) holder.values.joinToString(",") else "-"
}
'''

# The repair: read the property once, then use the local. Same value, same result, no smart cast needed.
# The same null check and use, with the holder in the *same* compilation unit: Kotlin accepts it. This is why
# seventeen host runners, which compile the product and the probe together, never saw the R93 defect.
ONE_UNIT = '''package together

class Holder(val values: List<String>? = null)

object Probe {
    fun kind(holder: Holder): String =
        if (holder.values != null) holder.values.joinToString(",") else "-"
}
'''

AFTER = '''package caller

import holder.Holder

object Probe {
    fun kind(holder: Holder): String {
        val values = holder.values
        return if (values != null) values.joinToString(",") else "-"
    }
}
'''

SYNTHETIC_BUILD_FILE = '''plugins { kotlin("jvm") }

val repro by sourceSets.creating
kotlin.sourceSets.named("repro") {
    kotlin.srcDir("src/repro/kotlin")
}
dependencies {
    add(repro.implementationConfigurationName, project(":format:ooxml"))
}

val secondDependency by sourceSets.creating
kotlin.sourceSets.named("secondDependency") {
    kotlin.srcDir("src/second/kotlin")
}
dependencies {
    add(secondDependency.implementationConfigurationName, project(":format:ooxml"))
    add(secondDependency.implementationConfigurationName, project(":core:documents"))
}

val filtered by sourceSets.creating
kotlin.sourceSets.named("filtered") {
    kotlin.srcDir("src/filtered/kotlin")
    kotlin.include("**/R110Checks.kt")
}

val looped by sourceSets.creating
kotlin.sourceSets.named("looped") {
    kotlin.srcDir("src/looped/kotlin")
    kotlin.srcDir("../$it/src/main/kotlin")
}

val twoDirectories by sourceSets.creating
kotlin.sourceSets.named("twoDirectories") {
    kotlin.srcDir("src/one/kotlin")
    kotlin.srcDir("../feature/editor/src/main/java")
}

val inherits by sourceSets.creating
kotlin.sourceSets.named("inherits") {
    kotlin.srcDir("src/inherits/kotlin")
}
dependencies {
    add(inherits.implementationConfigurationName, project(":format:ooxml"))
}
configurations[inherits.implementationConfigurationName].extendsFrom(configurations.implementation.get())
'''


def compile_to(files: list[Path], out: Path, classpath: Path | None = None) -> subprocess.CompletedProcess:
    """One kotlinc invocation, with the pinned JDK on `JAVA_HOME` — the wrapper script needs it to find `java`."""
    command = [KOTLINC, '-jvm-target', '17']
    if classpath is not None:
        command += ['-cp', str(classpath)]
    command += [str(path) for path in files] + ['-d', str(out)]
    return subprocess.run(command, capture_output=True, text=True, env={**os.environ, 'JAVA_HOME': JAVA_HOME})


def synthetic_sets() -> dict[str, dict]:
    with tempfile.TemporaryDirectory() as folder:
        build = Path(folder) / 'build.gradle.kts'
        build.write_text(SYNTHETIC_BUILD_FILE, encoding='utf-8')
        return parse_source_sets(build)


class SelectionTest(unittest.TestCase):
    def test_reads_every_declared_set(self):
        sets = synthetic_sets()
        self.assertEqual(
            list(sets),
            ['repro', 'secondDependency', 'filtered', 'looped', 'twoDirectories', 'inherits'],
        )
        self.assertEqual(sets['repro']['dependencies'], [':format:ooxml'])
        self.assertEqual(sets['repro']['directories'], ['src/repro/kotlin'])
        self.assertFalse(sets['repro']['include'])

    def test_selects_only_the_reproducible_declaration(self):
        sets = synthetic_sets()
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / 'src/repro/kotlin').mkdir(parents=True)
            selected, excluded = selectable(sets, root=root)
        self.assertEqual(set(selected), {'repro'})
        # `inherits` is excluded for the reason that comes first; the srcDir under it does not exist on purpose.
        self.assertIn('secondDependency', excluded)
        self.assertIn('an include(...) filter', excluded['filtered'])
        self.assertIn('loop template', excluded['looped'])
        self.assertEqual(excluded['twoDirectories'], '2 source directories')
        self.assertIn('inherits the module implementation configuration', excluded['inherits'])

    def test_the_real_build_file_selects_the_round_probes(self):
        sets = parse_source_sets()
        selected, excluded = selectable(sets)
        for name in ('r93AutoFilter', 'r108Text', 'r107Plural', 'r105Ranges'):
            self.assertIn(name, selected, name)
        self.assertEqual(len(selected) + len(excluded), len(sets))
        self.assertGreaterEqual(len(selected), 56)
        for name, directory in selected.items():
            self.assertTrue((Path(__file__).resolve().parents[1] / 'testing' / directory).is_dir(), name)

    def test_a_set_that_gradle_assembles_from_several_places_is_excluded(self):
        _, excluded = selectable(parse_source_sets())
        self.assertIn('an include(...) filter', excluded['r54'])
        self.assertIn('include(...) filter', excluded['contract'] + ' include(...) filter')
        self.assertEqual(excluded['r60Writer'], 'depends on :format:ooxml, :core:documents')

    def test_a_set_without_a_src_dir_is_refused(self):
        with tempfile.TemporaryDirectory() as folder:
            build = Path(folder) / 'build.gradle.kts'
            build.write_text(
                'val lonely by sourceSets.creating\n'
                'kotlin.sourceSets.named("lonely") {\n    kotlin.srcDir("src/lonely/kotlin")\n}\n'
                'dependencies { add(lonely.implementationConfigurationName, project(":format:ooxml")) }\n',
                encoding='utf-8',
            )
            _, excluded = selectable(parse_source_sets(build))
        self.assertIn('does not exist', excluded['lonely'])


class DiagnosticTest(unittest.TestCase):
    def test_reads_errors_without_the_prefix(self):
        stderr = (
            'warning: something minor\n'
            "probe.kt:131:31: error: smart cast to 'Iterable<T>' is impossible, because 'values' is a public "
            'API property declared in different module.\n'
            'probe.kt:191:42: error: unrelated\n'
        )
        self.assertEqual(
            diagnostics(stderr),
            [
                "smart cast to 'Iterable<T>' is impossible, because 'values' is a public API property declared in "
                'different module.',
                'unrelated',
            ],
        )

    def test_a_clean_log_has_no_errors(self):
        self.assertEqual(diagnostics('warning: only warnings\n'), [])
        self.assertEqual(diagnostics(''), [])

    def test_the_cross_module_message_names_the_property(self):
        message = (
            "smart cast to 'List<XlsxAutoFilter.Custom>' is impossible, because 'custom' is a public API property "
            'declared in different module.'
        )
        self.assertEqual(SMART_CAST.findall(message), ['custom'])

    def test_an_unrelated_error_is_not_counted_as_a_smart_cast(self):
        self.assertEqual(SMART_CAST.findall('unresolved reference: values'), [])


@unittest.skipUnless(HAVE_COMPILER, 'the pinned kotlinc is not installed')
class RuleReproductionTest(unittest.TestCase):
    """The Kotlin rule itself, in two compilation units, with no reference to this repository's product."""

    def build(self, caller_source: str) -> subprocess.CompletedProcess:
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            holder = root / 'Holder.kt'
            holder.write_text(HOLDER, encoding='utf-8')
            compiled_holder = compile_to([holder], root / 'holder.jar')
            self.assertEqual(compiled_holder.returncode, 0, compiled_holder.stderr)
            caller = root / 'Probe.kt'
            caller.write_text(caller_source, encoding='utf-8')
            return compile_to([caller], root / 'caller.jar', classpath=root / 'holder.jar')

    def test_the_pre_repair_shape_fails_across_modules(self):
        completed = self.build(BEFORE)
        self.assertNotEqual(completed.returncode, 0, 'the smart-cast shape compiled across two modules')
        errors = diagnostics(completed.stderr)
        self.assertTrue(errors, completed.stderr)
        self.assertEqual(SMART_CAST.findall('\n'.join(errors)), ['values'])

    def test_the_same_shape_compiles_inside_one_unit(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            both = root / 'Both.kt'
            both.write_text(ONE_UNIT, encoding='utf-8')
            completed = compile_to([both], root / 'both.jar')
        self.assertEqual(completed.returncode, 0, completed.stderr)

    def test_the_repair_compiles_across_modules(self):
        completed = self.build(AFTER)
        self.assertEqual(completed.returncode, 0, completed.stderr)
        self.assertEqual(diagnostics(completed.stderr), [])


@unittest.skipUnless(HAVE_COMPILER, 'the pinned kotlinc is not installed')
@unittest.skipUnless(PRODUCT_JAR.is_file(), 'the R110 product jar has not been built')
class GateTest(unittest.TestCase):
    """The gate on the real tree, against the jar `tools/run_r110_checks.py` builds."""

    def test_the_round_probe_compiles(self):
        compiled, _, casts, _ = verify(only=['r93AutoFilter'], jar=PRODUCT_JAR, quiet=True)
        self.assertEqual(compiled, 1)
        self.assertEqual(casts, 0)

    def test_an_unknown_source_set_is_refused(self):
        with self.assertRaises(Fail):
            verify(only=['noSuchSet'], jar=PRODUCT_JAR, quiet=True)

    def test_a_set_gradle_rejects_is_refused_by_the_gate_too(self):
        with self.assertRaises(Fail):
            verify(only=['r54'], jar=PRODUCT_JAR, quiet=True)


if __name__ == '__main__':
    unittest.main()
