#!/usr/bin/env python3
"""Service-review integration guardrails; actual behavior also needs Android execution."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


class ServiceReviewFreshnessTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.ui = (ROOT / 'feature/media/src/main/java/dev/fusion/media/MediaHub.kt').read_text(encoding='utf-8')
        cls.kernel = (ROOT / 'feature/media/src/main/kotlin/dev/fusion/media/ServiceLink.kt').read_text(encoding='utf-8')
        cls.test = (ROOT / 'app/src/androidTest/java/dev/fusion/shell/R24MapAndServiceDeviceTest.kt').read_text(encoding='utf-8')
        cls.corpus = (ROOT / 'testing/src/r24c/kotlin/dev/fusion/testing/R24CChecks.kt').read_text(encoding='utf-8')

    def test_ui_uses_executable_freshness_state_for_edits_and_current_review(self):
        section = self.ui.split('private fun ServiceLinksScreen', 1)[1]
        self.assertIn('mutableStateOf(ServiceLinkReview.initial())', section)
        self.assertIn('review = review.edit(next)', section)
        self.assertIn('val prepared = before.prepare()', section)
        self.assertIn('if (review === before) review = prepared', section)
        self.assertIn('if (review === before) review = before.refuse(e)', section)
        self.assertIn('Text(review.status, Modifier.testTag("service-status"))', section)

    def test_kernel_binds_same_and_oversized_input_to_existing_state(self):
        state = self.kernel.split('class ServiceLinkReview', 1)[1].split('class PlaybackEpoch', 1)[0]
        self.assertIn('const val MAX_INPUT_UNITS = 2048', state)
        self.assertIn('next.length > MAX_INPUT_UNITS || next == draft', state)
        self.assertIn('ServiceLinkReview(next, null,', state)
        self.assertIn('ServiceLink.parse(draft.trim())', state)
        self.assertIn('if (candidate === expected)', state)

    def test_real_case_checks_invalidation_before_second_one_shot_review(self):
        case = self.test.split('fun providerLinksRequireExplicitDisclosureAndDoNotMasqueradeAsPlayback()', 1)[1].split('@Test fun serviceReviewRejects', 1)[0]
        self.assertEqual(case.count('click("service-preview")'), 2)  # One valid and one invalid input; not a retry.
        edit = case.index('performTextReplacement("https://youtube.com.evil.test/')
        invalidated = case.index('Link changed; review again.', edit)
        refused = case.index('Link refused', invalidated)
        self.assertLess(edit, invalidated)
        self.assertLess(invalidated, refused)
        self.assertIn('service-confirm-open").assertCountEquals(0)', case[invalidated:refused])

    def test_boundary_case_is_an_actual_device_test_and_never_retries_review(self):
        case = self.test.split('fun serviceReviewRejectsOversizeTextAndKeepsStatusForIdenticalText()', 1)[1]
        self.assertIn('"x".repeat(2048)', case)
        self.assertIn('performTextReplacement(limit + "x")', case)
        self.assertEqual(case.count('click("service-preview")'), 1)
        self.assertGreaterEqual(case.count('assertTextContains("Link refused"'), 3)

    def test_external_intent_consumes_current_candidate_before_launch(self):
        section = self.ui.split('private fun ServiceLinksScreen', 1)[1]
        confirm = section.split('confirmButton =', 1)[1].split('dismissButton =', 1)[0]
        self.assertLess(confirm.index('val consumed = review.consume(link)'), confirm.index('context.startActivity'))
        self.assertLess(confirm.index('if (consumed == null)'), confirm.index('context.startActivity'))
        self.assertIn('if (review === consumed)', confirm)
        self.assertIn('Intent.ACTION_VIEW', confirm)
        self.assertIn('service-confirm-open', confirm)
        self.assertIn('review.cancel(link)', section)

    def test_real_jvm_corpus_targets_stale_forged_replayed_and_boundary_tokens(self):
        case = self.corpus.split('private fun serviceReviewFreshness()', 1)[1].split('private fun inputChanges()', 1)[0]
        for check in ('changed.consume(token) == null', 'again.consume(token) == null', 'consumed.consume(newer) == null',
                      'first.consume(ServiceLink.parse(url)) == null', '"😀".repeat(1024)', 'boundary.edit(exactly + "x") === boundary'):
            self.assertIn(check, case)


if __name__ == '__main__': unittest.main()
