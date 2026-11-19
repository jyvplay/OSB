#!/usr/bin/env python3
"""Controls for the R112 visual-evidence contracts: each rule must fire on its own defect and stay quiet otherwise.

Eight controls run here. One checks the contracts against this repository; the rest build a small synthetic source
tree, mutate exactly one property at a time, and require the *named* rule — and only that rule — to fail. A gate that
cannot be made to fail is not a gate, and a gate that fires on an unrelated edit is noise.
"""
from __future__ import annotations

import importlib
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
checker = importlib.import_module('check_r112_visual_contracts')

EVIDENCE = f'{checker.TEST_DIR}/NativeVisualEvidence.kt'
EDITOR = checker.EDITOR
TEST_FILE = f'{checker.TEST_DIR}/SyntheticDialogTest.kt'
CONTROL_FILE = checker.CONTROL
PRODUCT = 'app/src/main/java/dev/fusion/shell/SyntheticTags.kt'

FILES = {
    EVIDENCE: (
        'object NativeVisualEvidence {\n'
        '    fun assertFullTextVisible(compose: Any, tag: String) {\n'
        '        val node = compose.onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed()\n'
        '        if (hasAnyAncestor(hasScrollAction()).matches(node.fetchSemanticsNode())) node.performScrollTo()\n'
        '        assertTrue("clip", 1f <= 2f)\n'
        '    }\n'
        '}\n'
    ),
    EDITOR: (
        'Column {\n'
        '    Text("safety", Modifier.testTag("writer-structured-safety"))\n'
        '    Column(Modifier.heightIn(max = 248.dp).verticalScroll(state).testTag("writer-structured-scroll")) {\n'
        '        Text("details")\n'
        '    }\n'
        '}\n'
    ),
    TEST_FILE: (
        'class SyntheticDialogTest {\n'
        '    fun review() {\n'
        '        NativeVisualEvidence.captureDialogDisplay(compose, compose.activity, "cap-review",\n'
        '            "writer-structured-confirm", listOf("extra-option"))\n'
        '    }\n'
        '}\n'
    ),
    CONTROL_FILE: (
        'class NativeVisualEvidenceDeviceTest {\n'
        '    fun textFitCheckerAcceptsVisibleTextAndRejectsClippedScrollableText() {\n'
        '        val error = runCatching { NativeVisualEvidence.assertFullTextVisible(compose, "clipped") }.exceptionOrNull()\n'
        '        assertTrue("A clipped disclosure must fail the geometry check, got $error", error is AssertionError)\n'
        '    }\n'
        '}\n'
        'private fun EvidenceFixture() {\n'
        '    Text("long", Modifier.heightIn(max = 32.dp).verticalScroll(rememberScrollState()).testTag("clipped"))\n'
        '}\n'
    ),
    PRODUCT: (
        'package dev.fusion.shell\n'
        'val confirm = Modifier.testTag("writer-structured-confirm")\n'
        'val extra = Modifier.testTag("extra-option")\n'
    ),
}


def build(folder: Path, overrides: dict[str, str] | None = None) -> Path:
    for rel, text in FILES.items():
        path = folder / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(overrides.get(rel, text) if overrides else text, encoding='utf-8')
    return folder


class SyntheticControls(unittest.TestCase):
    def run_root(self, overrides: dict[str, str] | None = None) -> list[str]:
        with tempfile.TemporaryDirectory(prefix='r112-') as folder:
            return checker.violations(build(Path(folder), overrides))

    def rules(self, found: list[str]) -> set[str]:
        return {item.split(':')[0] for item in found}

    def test_unmutated_tree_is_clean(self):
        self.assertEqual([], self.run_root())

    def test_benign_comment_change_is_clean(self):
        overrides = {TEST_FILE: '// an explanatory comment\n' + FILES[TEST_FILE]}
        self.assertEqual([], self.run_root(overrides))

    def test_missing_reveal_fires_rule_1(self):
        text = FILES[EVIDENCE].replace(
            '        if (hasAnyAncestor(hasScrollAction()).matches(node.fetchSemanticsNode())) node.performScrollTo()\n', '')
        self.assertEqual({'R112-1'}, self.rules(self.run_root({EVIDENCE: text})))

    def test_reveal_after_judgement_fires_rule_1(self):
        text = FILES[EVIDENCE].replace(
            '        if (hasAnyAncestor(hasScrollAction()).matches(node.fetchSemanticsNode())) node.performScrollTo()\n'
            '        assertTrue("clip", 1f <= 2f)\n',
            '        assertTrue("clip", 1f <= 2f)\n'
            '        if (hasAnyAncestor(hasScrollAction()).matches(node.fetchSemanticsNode())) node.performScrollTo()\n')
        self.assertEqual({'R112-1'}, self.rules(self.run_root({EVIDENCE: text})))

    def test_private_fit_judgement_fires_rule_2(self):
        text = FILES[TEST_FILE].replace('    fun review() {\n', '    fun review() {\n        val h = node.fetchSemanticsNode().boundsInRoot.height\n')
        self.assertEqual({'R112-2'}, self.rules(self.run_root({TEST_FILE: text})))

    def test_safety_inside_details_fires_rule_3(self):
        text = (
            'Column {\n'
            '    Column(Modifier.heightIn(max = 248.dp).verticalScroll(state).testTag("writer-structured-scroll")) {\n'
            '        Text("safety", Modifier.testTag("writer-structured-safety"))\n'
            '    }\n'
            '}\n'
        )
        self.assertEqual({'R112-3'}, self.rules(self.run_root({EDITOR: text})))

    def test_undefined_capture_tag_fires_rule_4(self):
        text = FILES[TEST_FILE].replace('listOf("extra-option")', 'listOf("extra-optoin")')
        self.assertEqual({'R112-4'}, self.rules(self.run_root({TEST_FILE: text})))

    def test_self_matching_scroll_fires_rule_1(self):
        text = FILES[EVIDENCE].replace(
            'hasAnyAncestor(hasScrollAction()).matches(node.fetchSemanticsNode())',
            'hasScrollAction().matches(node.fetchSemanticsNode())')
        self.assertEqual({'R112-1'}, self.rules(self.run_root({EVIDENCE: text})))

    def test_removed_clip_control_fires_rule_6(self):
        text = FILES[CONTROL_FILE].replace('error is AssertionError', 'error is IllegalArgumentException', 1)
        self.assertEqual({'R112-6'}, self.rules(self.run_root({CONTROL_FILE: text})))

    def test_ancestor_caused_clip_fires_rule_6(self):
        text = FILES[CONTROL_FILE].replace(
            'Modifier.heightIn(max = 32.dp).verticalScroll(rememberScrollState()).testTag("clipped")',
            'Modifier.heightIn(max = 32.dp).testTag("clipped")')
        self.assertEqual({'R112-6'}, self.rules(self.run_root({CONTROL_FILE: text})))

    def test_unbounded_details_fires_rule_5(self):
        text = FILES[EDITOR].replace('.heightIn(max = 248.dp)', '')
        self.assertEqual({'R112-5'}, self.rules(self.run_root({EDITOR: text})))


class RepositoryControls(unittest.TestCase):
    def test_this_repository_satisfies_every_rule(self):
        found = checker.violations(ROOT)
        self.assertEqual([], found, '\n'.join(found))
        self.assertEqual({'R112-1', 'R112-2', 'R112-3', 'R112-4', 'R112-5', 'R112-6'}, set(checker.RULES))

    def test_rules_document_themselves(self):
        for rule, text in checker.RULES.items():
            self.assertRegex(rule, r'^R112-\d$')
            self.assertGreater(len(text), 20)


if __name__ == '__main__':
    unittest.main()
