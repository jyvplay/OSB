"""Strict reviewed wrapper-properties subset, NOT general Java-properties parsing or cache attestation."""
EXPECTED = {
    'distributionBase': 'GRADLE_USER_HOME',
    'distributionPath': 'wrapper/dists',
    'distributionUrl': r'https\://services.gradle.org/distributions/gradle-9.3.1-bin.zip',
    'networkTimeout': '10000',
    'validateDistributionUrl': 'true',
    'zipStoreBase': 'GRADLE_USER_HOME',
    'zipStorePath': 'wrapper/dists',
    # Actually retrieved official GitHub v9.3.1 asset347704202 digest; retained upstream metadata.
    'distributionSha256Sum': 'b266d5ff6b90eada6dc3b20cb090e3731302e553a27c5d3e4df1f0d76beaff06',
}


def verify(text):
    if len(text) > 8192: raise ValueError('Wrapper-properties budget exceeded')
    values = {}
    for line in text.splitlines():
        if not line or line.startswith(('#', '!')): continue
        key, separator, value = line.partition('=')
        if not separator or line != line.strip() or key in values or key not in EXPECTED:
            raise ValueError('Noncanonical, duplicate or unknown wrapper property')
        values[key] = value
    if values != EXPECTED: raise ValueError('Unreviewed wrapper distribution, cache path or checksum')
    return values
