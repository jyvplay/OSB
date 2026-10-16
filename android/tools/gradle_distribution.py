"""Strict reviewed wrapper-properties subset, NOT general Java-properties parsing or cache attestation."""
EXPECTED = {
    'distributionBase': 'GRADLE_USER_HOME',
    'distributionPath': 'wrapper/dists',
    'distributionUrl': r'https\://services.gradle.org/distributions/gradle-8.13-bin.zip',
    'networkTimeout': '10000',
    'validateDistributionUrl': 'true',
    'zipStoreBase': 'GRADLE_USER_HOME',
    'zipStorePath': 'wrapper/dists',
    # Actually fetched from https://downloads.gradle.org/distributions/gradle-8.13-bin.zip.sha256.
    'distributionSha256Sum': '20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78',
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
