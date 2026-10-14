#!/usr/bin/env python3
"""Necessary release-record checks, NOT independent verification of a reviewer's claims.
Trusted owner/reviewers must evaluate the actual referenced reports. Hashes detect changed
bytes; they do not authenticate a reviewer, prove measurements, or license dependencies.
"""
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
from datetime import datetime

GATES = {
    'android_build_lint_and_both_flavors', 'whole_assigned_scope_implemented',
    'mandatory_spike_and_device_acceptance', 'security_privacy_accessibility_review',
    'api37_16k_and_reference_performance', 'license_sbom_closure',
}
EXCEPTIONS = {'CT-04': 'superseded', 'CM-03': 'excluded', 'MS-02': 'excluded', 'MS-03': 'excluded'}
MAX_ARTIFACT_BYTES = 32 * 1024 * 1024


def require(condition, message):
    if not condition:
        raise ValueError(message)


def artifact(root, reference):
    require(isinstance(reference, dict) and set(reference) == {'path', 'sha256'}, 'Evidence needs path and SHA-256, not a truthy flag')
    name, digest = reference['path'], reference['sha256']
    require(isinstance(name, str) and '\\' not in name, 'Invalid evidence path')
    parts = PurePosixPath(name).parts
    require(len(parts) >= 3 and parts[:2] == ('docs', 'release-artifacts') and '..' not in parts and '.' not in parts,
            'Evidence must be inside docs/release-artifacts')
    require(isinstance(digest, str) and re.fullmatch('[0-9a-f]{64}', digest), 'Invalid evidence SHA-256')
    root = Path(root).resolve()
    path = root
    for part in parts:
        path = path / part
        require(not path.is_symlink(), 'Symlink evidence refused')
    require(path.is_file() and 0 < path.stat().st_size <= MAX_ARTIFACT_BYTES, 'Evidence is missing, empty or oversized')
    data = path.read_bytes()
    require(0 < len(data) <= MAX_ARTIFACT_BYTES, 'Evidence grew beyond limit')
    require(hashlib.sha256(data).hexdigest() == digest, 'Evidence digest mismatch')
    return data


def source_fingerprint(root):
    """Native source/build/resources plus scope ledgers; no generated output or evidence self-cycle."""
    root = Path(root)
    excluded = {'.git', '.gradle', '.idea', '.kotlin', '__pycache__', 'build', 'pilot-reports'}
    scope = {'docs/feature-register.json', 'docs/module-audit.json', 'docs/spikes.json'}
    entries = []
    for path in sorted(root.rglob('*')):
        relative = path.relative_to(root)
        if excluded.intersection(relative.parts) or relative.name == 'local.properties' or path.suffix in {'.pyc', '.iml'}:
            continue
        if relative.parts[0] == 'docs' and relative.as_posix() not in scope:
            continue
        require(not path.is_symlink(), 'Symlink source refused for release fingerprint')
        if path.is_file():
            require(len(entries) < 10000 and path.stat().st_size <= MAX_ARTIFACT_BYTES, 'Source fingerprint input exceeds bounds')
            data = path.read_bytes()
            if relative.as_posix() in scope:
                # Reviewed artifact hashes must not create a source/evidence hash cycle.
                def without_attestations(value):
                    if isinstance(value, dict):
                        return {k: without_attestations(v) for k, v in value.items() if k not in {'evidence', 'reviewed_acceptance_record'}}
                    if isinstance(value, list):
                        return [without_attestations(v) for v in value]
                    return value
                data = json.dumps(without_attestations(json.loads(data)), sort_keys=True, separators=(',', ':')).encode()
            entries.append(relative.as_posix() + '\0' + hashlib.sha256(data).hexdigest() + '\n')
    return hashlib.sha256(''.join(entries).encode()).hexdigest()


def verify(root, rows, spikes, audit, included):
    # Explicit checks survive python -O; never rely on assert for a release barrier.
    required = {r['id'] for r in rows if r['id'] not in EXCEPTIONS}
    require(all(r['status'] == EXCEPTIONS.get(r['id'], 'verified') for r in rows),
            'Every in-scope requirement, not just P1, must be verified; exclusions cannot expand silently')
    require(all(s['status'] == 'passed' for s in spikes), 'Every spike requires acceptance')
    gates = audit.get('common_acceptance_gates', {})
    require(set(gates) == GATES and all(value is True for value in gates.values()), 'All common gates are mandatory')
    modules = audit.get('modules', [])
    require(len(modules) == len(set(included)) and {m['module'] for m in modules} == set(included), 'Module ownership mismatch')
    require(all(m['status'] == 'complete' for m in modules), 'Whole modules remain incomplete')
    require(all(m['kind'] == 'exact_name' for m in audit['architecture_mapping']), 'Architecture slots remain absent or non-equivalent')
    record_path = Path(root) / 'docs/release-evidence.json'
    require(record_path.is_file() and not record_path.is_symlink() and record_path.stat().st_size <= 2 * 1024 * 1024,
            'Missing or oversized release evidence record')
    record = json.loads(record_path.read_text())
    require(type(record) is dict and record.get('schema') == 1, 'Unsupported release evidence schema')
    require(record.get('source_sha256') == source_fingerprint(root), 'Release record is not bound to current native source')
    reviews = record.get('reviews')
    require(isinstance(reviews, list) and len(reviews) == len(GATES), 'One reviewed record per common gate required')
    require(all(isinstance(r, dict) for r in reviews) and {r.get('gate') for r in reviews} == GATES, 'Missing/duplicate review gate')
    for review in reviews:
        require(review.get('result') == 'pass', 'Review did not pass')
        require(isinstance(review.get('reviewer'), str) and bool(review['reviewer'].strip()), 'Reviewer attribution missing')
        timestamp = datetime.fromisoformat(review.get('reviewed_at', ''))
        require(timestamp.tzinfo is not None, 'Review timestamp must include timezone')
        artifact(root, review.get('artifact'))
    coverage = record.get('requirements', {})
    require(isinstance(coverage, dict) and set(coverage) == required, 'Evidence coverage must include every in-scope requirement')
    for reference in coverage.values():
        artifact(root, reference)
    for module in modules:
        artifact(root, module.get('reviewed_acceptance_record'))
    for spike in spikes:
        references = spike.get('evidence')
        require(isinstance(references, list) and 1 <= len(references) <= 32, 'Spike evidence is missing or unbounded')
        for reference in references:
            artifact(root, reference)
    devices = record.get('device_reports')
    require(isinstance(devices, list) and 2 <= len(devices) <= 16, 'At least two physical reference-device reports required')
    aliases = set()
    for reference in devices:
        report = json.loads(artifact(root, reference))
        require(report.get('kind') == 'physical-reference-device' and report.get('result') == 'pass', 'Not a passed physical reference-device report')
        require(report.get('source_sha256') == record['source_sha256'], 'Device report is for different source')
        alias = report.get('device_alias')
        require(isinstance(alias, str) and bool(alias.strip()) and alias not in aliases, 'Missing/duplicate physical device alias')
        aliases.add(alias)
    return 'Record integrity/coverage passed. Truth and sufficiency of attributed reviews require trusted human approval; not an automated product proof.'
