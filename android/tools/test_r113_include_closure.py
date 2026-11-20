#!/usr/bin/env python3
"""Controls for the R113 include-closure gate.

The gate exists because CI found the r47 break and this sandbox could not. A gate that cannot be shown to fire on
that exact defect is worthless, and a gate that fires on Kotlin scoping it does not model is worse than none, so the
controls come in three groups:

  * the real repository is clean and the declared set counts hold;
  * removing one real r47 include entry from a copy of this repository reproduces the CI failure locally;
  * the two false-positive classes found while building the gate — a nested declaration shadowing a same-package
    name (`CronSpec` inside `CronDagWorkflowScheduler.kt`) and an extension receiver that merely mentions a type
    (`fun Map<String, Any?>.str` in `Json.kt`) — must stay silent.
"""
from __future__ import annotations

import importlib
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
gate = importlib.import_module('check_r113_include_closure')

BUILD = 'testing/build.gradle.kts'
OOXML = 'format/ooxml/src/main/kotlin'


def copy_real_slice(folder: Path) -> Path:
    """A root that holds the real build file and the real ooxml package, so r47 can be judged as CI judges it."""
    (folder / 'testing').mkdir(parents=True, exist_ok=True)
    shutil.copy2(ROOT / BUILD, folder / BUILD)
    shutil.copytree(ROOT / OOXML, folder / OOXML)
    (folder / 'testing/src/r47/kotlin').mkdir(parents=True, exist_ok=True)
    return folder


class RepositoryControls(unittest.TestCase):
    def test_this_repository_is_closed(self):
        found, summary = gate.violations(ROOT)
        self.assertEqual([], found, '\n'.join(found))
        self.assertEqual(102, summary['sets'])
        self.assertEqual(43, summary['with_include'])
        self.assertEqual(3, summary['templates'])

    def test_rules_document_themselves(self):
        self.assertEqual({'R113-1', 'R113-2', 'R113-3', 'R113-4'}, set(gate.RULES))
        for text in gate.RULES.values():
            self.assertGreater(len(text), 20)


class RealDefectControls(unittest.TestCase):
    """The r47 failure, reproduced from a copy of the real build file."""

    def judge(self, replacement) -> list[str]:
        with tempfile.TemporaryDirectory(prefix='r113-') as folder:
            root = copy_real_slice(Path(folder))
            path = root / BUILD
            text = path.read_text(encoding='utf-8')
            mutated = replacement(text)
            self.assertNotEqual(text, mutated, 'the mutation did not apply')
            path.write_text(mutated, encoding='utf-8')
            found, _ = gate.violations(root)
            return [item for item in found if item.startswith('R113-1')]

    def test_dropping_the_validation_entry_reproduces_the_ci_failure(self):
        found = self.judge(lambda text: text.replace('"**/XlsxValidation.kt",', '"**/XlsxValidation.kt.tmp",'))
        self.assertTrue(found, 'dropping a real include entry must be reported')
        self.assertTrue(any('r47' in item for item in found), found)
        self.assertTrue(any('XlsxStyledBook.kt' in item and 'XlsxValidation' in item for item in found), found)

    def test_dropping_a_second_entry_is_reported(self):
        found = self.judge(lambda text: text.replace('"**/XlsxMerges.kt",', '"**/XlsxMerges.kt.tmp",'))
        self.assertTrue(any('r47' in item for item in found), found)


class SyntheticControls(unittest.TestCase):
    BUILD_TEXT = (
        'val r91 by sourceSets.creating\n'
        'kotlin.sourceSets.named("r91") {\n'
        '    kotlin.srcDir("../pkg/src/main/kotlin")\n'
        '    kotlin.include("**/Alpha.kt")\n'
        '}\n'
        'val r92 by sourceSets.creating\n'
        'kotlin.sourceSets.named("r92") {\n'
        '    kotlin.srcDir("../$it/src/main/kotlin")\n'
        '    kotlin.include("**/Alpha.kt")\n'
        '}\n'
    )
    ALPHA = 'package x\nval alpha = Beta\n'
    BETA = 'package x\nobject Beta\n'

    def build(self, folder: Path, alpha: str = ALPHA, beta: str = BETA, build: str | None = None) -> Path:
        (folder / 'testing').mkdir(parents=True, exist_ok=True)
        (folder / 'pkg/src/main/kotlin').mkdir(parents=True, exist_ok=True)
        (folder / BUILD).parent.mkdir(parents=True, exist_ok=True)
        (folder / BUILD).write_text(build if build is not None else self.BUILD_TEXT, encoding='utf-8')
        (folder / 'pkg/src/main/kotlin/Alpha.kt').write_text(alpha, encoding='utf-8')
        (folder / 'pkg/src/main/kotlin/Beta.kt').write_text(beta, encoding='utf-8')
        return folder

    def judge(self, **kwargs) -> list[str]:
        with tempfile.TemporaryDirectory(prefix='r113-') as folder:
            found, _ = gate.violations(self.build(Path(folder), **kwargs))
            return found

    def test_real_gap_fires(self):
        self.assertTrue(any(item.startswith('R113-1') and 'Beta' in item for item in self.judge()))

    def test_closed_tree_is_silent(self):
        build = self.BUILD_TEXT.replace('kotlin.include("**/Alpha.kt")', 'kotlin.include("**/Alpha.kt", "**/Beta.kt")', 1)
        self.assertEqual([], self.judge(build=build))

    def test_nested_shadow_is_silent(self):
        alpha = 'package x\nobject Outer {\n    data class Beta(val v: Int)\n    val alpha = Beta(1)\n}\n'
        self.assertEqual([], self.judge(alpha=alpha))

    def test_extension_receiver_is_not_a_declaration(self):
        beta = 'package x\nfun Beta<String, Any?>.str(key: String): String? = null\nobject Unrelated\n'
        self.assertEqual([], self.judge(beta=beta))

    def test_comment_mention_is_not_a_gap(self):
        alpha = 'package x\n// Beta lives elsewhere but is not used here.\nval alpha = 1\n'
        self.assertEqual([], self.judge(alpha=alpha))

    def test_dead_pattern_fires(self):
        build = self.BUILD_TEXT.replace('kotlin.include("**/Alpha.kt")', 'kotlin.include("**/Alpha.kt", "**/Missing.kt")', 1)
        self.assertTrue(any(item.startswith('R113-2') for item in self.judge(build=build)))

    def test_duplicate_pattern_fires(self):
        build = self.BUILD_TEXT.replace('kotlin.include("**/Alpha.kt")', 'kotlin.include("**/Alpha.kt", "**/Alpha.kt")', 1)
        self.assertTrue(any(item.startswith('R113-3') for item in self.judge(build=build)))

    def test_template_set_is_reported_not_guessed(self):
        _, summary = gate.violations(self.build(Path(tempfile.mkdtemp(prefix='r113-'))))
        self.assertEqual(1, summary['templates'])
        self.assertEqual(1, summary['modelled'])


if __name__ == '__main__':
    unittest.main()
