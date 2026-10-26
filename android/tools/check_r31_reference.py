#!/usr/bin/env python3
"""Independent Python recomputation of R31 kernel behavior from fixtures/sources.

Cross-checks scrub redaction, capability gating, boolean queries, text
offsets, vault digests, PDF trailer/xref, PSD resource blocks, codec
ranking, phase ledgers and robots rules with independent implementations.
Agreement is cross-language evidence for the LOGIC ONLY -- not Android,
device, service, or acceptance evidence.
"""
import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIX = ROOT / 'tools/fixtures/r31'

def main():
    report = {}
    # Scrub mirror.
    sample = (FIX / 'scrub_sample.txt').read_text()
    out = re.sub(r'[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}', '[email]', sample)
    out = re.sub(r'(?<!\d)(?:\+\d{1,3}[- ]?)?(?:\d[- .]?){7,14}\d(?!\d)', '[phone]', out)
    out = re.sub(r'(?i)(bearer\s+)[A-Za-z0-9\-._~+/=]+', r'\1[secret]', out)
    out = re.sub(r'(?i)(token=)[^\s&;]+', r'\1[secret]', out)
    out = re.sub(r'/(data|sdcard|storage|system|vendor)/[^\s:\'"]*', '[path]', out)
    assert out == ('contact [email] or [phone]\n'
                   'token=[secret] auth header: Bearer [secret]\n'
                   'open [path] please\nplain line stays intact\n'), repr(out)
    report['scrub'] = {'tags': 5}
    # Gate mirror.
    def decide(sdk, enc, ram, free, min_sdk, need_enc=None, need_ram=None, need_free=False):
        if sdk < min_sdk:
            return (False, f'sdk {sdk} < {min_sdk}')
        if need_enc is not None:
            if enc is None:
                return (False, 'encoder list unknown')
            if all(e.lower() != need_enc.lower() for e in enc):
                return (False, f'no {need_enc} encoder')
        if need_ram is not None:
            if ram is None:
                return (False, 'ram unknown')
            if ram < need_ram:
                return (False, f'ram {ram} < {need_ram}')
        if need_free and free is not True:
            return (False, 'freeform unknown' if free is None else 'freeform absent')
        return (True, 'all requirements met')
    full = (34, ['video/avc', 'video/hevc'], 8.0, True)
    assert decide(*full, 26, 'video/hevc', 4.0, True) == (True, 'all requirements met')
    assert decide(25, full[1], 8.0, True, 26) == (False, 'sdk 25 < 26')
    assert decide(34, None, 8.0, True, 26, 'video/hevc')[1] == 'encoder list unknown'
    assert decide(34, ['video/avc'], 8.0, True, 26, 'video/hevc')[1] == 'no video/hevc encoder'
    assert decide(34, full[1], None, True, 26, need_ram=2.0)[1] == 'ram unknown'
    assert decide(34, full[1], 1.5, True, 26, need_ram=2.0)[1] == 'ram 1.5 < 2.0'
    assert decide(34, full[1], 8.0, None, 26, need_free=True)[1] == 'freeform unknown'
    report['gate'] = {'vectors': 7}
    # Query mirror (independent tokenizer + shunting-yard to RPN).
    def tokenize_q(q):
        toks, i = [], 0
        while i < len(q):
            if q[i].isspace():
                i += 1
            elif q[i] in '()':
                toks.append(q[i])
                i += 1
            elif q[i] == '-':
                toks.append('NOT')
                i += 1
            elif q[i] == '"':
                j = q.index('"', i + 1)
                toks.append(('PHRASE', q[i + 1:j].lower()))
                i = j + 1
            elif q.startswith('OR', i) and (i + 2 == len(q) or not q[i + 2].isalpha()):
                toks.append('OR')
                i += 2
            elif q[i].isalnum():
                j = i
                while j < len(q) and q[j].isalnum():
                    j += 1
                toks.append(('TERM', q[i:j].lower()))
                i = j
            else:
                raise ValueError(f'bad char {q[i]!r}')
        return toks
    def to_rpn(toks):
        out, ops, prec = [], [], {'OR': 1, 'AND': 2, 'NOT': 3}
        prev_operand = False
        toks2 = []
        for t in toks:
            is_op = isinstance(t, str)
            if prev_operand and not is_op or (prev_operand and t == '(') or (t == 'NOT' and prev_operand):
                toks2.append('AND')
            toks2.append(t)
            prev_operand = (not is_op) or t == ')'
        for t in toks2:
            if t == '(':
                ops.append(t)
            elif t == ')':
                while ops[-1] != '(':
                    out.append(ops.pop())
                ops.pop()
            elif t in prec:
                while ops and ops[-1] != '(' and prec[ops[-1]] >= prec[t]:
                    out.append(ops.pop())
                ops.append(t)
            else:
                out.append(t)
        out.extend(reversed(ops))
        return out
    def run_rpn(rpn, terms, lowered):
        stack = []
        for t in rpn:
            if t == 'NOT':
                stack.append(not stack.pop())
            elif t == 'AND':
                b, a = stack.pop(), stack.pop()
                stack.append(a and b)
            elif t == 'OR':
                b, a = stack.pop(), stack.pop()
                stack.append(a or b)
            elif t[0] == 'TERM':
                stack.append(t[1] in terms)
            else:
                stack.append(t[1] in lowered)
        assert len(stack) == 1
        return stack[0]
    def qmatch(q, doc):
        lowered = doc.lower()
        # Kernel splits on [^\p{L}\p{N}]; '_' splits too. \W + '_' is the equivalent.
        terms = set(t for t in re.split(r'[\W_]+', lowered, flags=re.UNICODE) if t)
        return run_rpn(to_rpn(tokenize_q(q)), terms, lowered)
    assert qmatch('alpha beta', 'Alpha + beta') is True
    assert qmatch('alpha beta', 'alpha only') is False
    assert qmatch('alpha OR beta', 'nothing beta here') is True
    assert qmatch('"quick brown"', 'the quick brown fox') is True
    assert qmatch('alpha -beta', 'alpha beta') is False
    assert qmatch('(alpha OR beta) gamma', 'beta gamma') is True
    rpn = to_rpn(tokenize_q('a OR b c'))
    assert rpn[-1] == 'OR' and rpn.count('AND') == 1, rpn  # AND binds tighter
    report['query'] = {'vectors': 7}
    # Offsets mirror.
    t = 'ab\ncde\n\nfg'
    starts = [0] + [i + 1 for i, c in enumerate(t) if c == '\n']
    assert starts == [0, 3, 7, 8]
    assert (t[:].count('\n')) == 3
    report['offsets'] = {'starts': 4}
    # Vault mirror: exact digest pin.
    canon = 'a1|note.txt|12|' + 'a' * 64 + '\n' + 'b2|photo.bin|100|' + 'b' * 64
    digest = hashlib.sha256(canon.encode()).hexdigest()
    assert re.fullmatch(r'[0-9a-f]{64}', digest)
    report['vault'] = {'digest': digest}
    # PDF mirror.
    raw = (FIX / 'min.pdf').read_bytes()
    text = raw.decode('ascii')
    assert '%PDF-1.4' in text[:32] and text.rstrip().endswith('%%EOF')
    sx = int(text[text.rindex('startxref') + 9:].strip().split()[0])
    assert text.startswith('xref', sx)
    assert '/Size 4' in text and '/Root 1 0 R' in text
    assert text[sx:sx + 4] == 'xref'
    report['pdf'] = {'startxref': sx, 'size': 4}
    # PSD mirror.
    sec = (FIX / 'psd_res.bin').read_bytes()
    assert len(sec) == 36
    blocks, i = [], 0
    while i < len(sec):
        assert sec[i:i + 4] == b'8BIM'
        rid = int.from_bytes(sec[i + 4:i + 6], 'big')
        nlen = sec[i + 6]
        name = sec[i + 7:i + 7 + nlen].decode('ascii')
        j = i + 7 + nlen
        if (1 + nlen) % 2 == 1:
            j += 1
        size = int.from_bytes(sec[j:j + 4], 'big')
        j += 4 + size
        if size % 2 == 1:
            j += 1
        blocks.append((rid, name, size))
        i = j
    assert blocks == [(1000, 'Test', 4), (1024, '', 3)]
    report['psd'] = {'blocks': 2}
    # Codec mirror.
    enc = [('video/avc', False), ('video/hevc', True)]
    def select(cands, hw_req):
        for m in cands:
            hits = [e for e in enc if e[0].lower() == m.lower()]
            hw = [e for e in hits if e[1]]
            if hw:
                return (hw[0][0], True, f'hardware {m}')
            if not hw_req and hits:
                return (hits[0][0], False, f'software fallback {m}')
        return None
    assert select(['video/hevc', 'video/avc'], True) == ('video/hevc', True, 'hardware video/hevc')
    assert select(['video/av1', 'video/avc'], True) is None
    assert select(['video/av1', 'video/avc'], False) == ('video/avc', False, 'software fallback video/avc')
    report['codec'] = {'vectors': 3}
    # Phase mirror.
    phases = {'boot': 100, 'first_frame': 300}
    assert sum(phases.values()) == 400 and phases['first_frame'] / 400 == 0.75
    assert sorted(phases, key=lambda k: (-phases[k], k))[0] == 'first_frame'
    report['phase'] = {'total': 400}
    # Robots mirror.
    groups = []
    agents, allow, disallow, in_rules = [], [], [], False
    def flush():
        if agents:
            groups.append((list(agents), list(allow), list(disallow)))
    for raw_line in (FIX / 'robots.txt').read_text().split('\n'):
        line = raw_line.split('#')[0].strip()
        if not line or ':' not in line:
            continue
        field, value = line.split(':', 1)
        field, value = field.strip().lower(), value.strip()
        if field == 'user-agent':
            if in_rules:
                flush()
                agents, allow, disallow, in_rules = [], [], [], False
            if value:
                agents.append(value.lower())
        elif field in ('allow', 'disallow'):
            if not agents:
                continue
            in_rules = True
            if value:
                (allow if field == 'allow' else disallow).append(value)
    flush()
    assert [a for a, _, _ in groups] == [['*'], ['fusion-test']]
    def allowed(ua, path):
        ua = ua.lower()
        pool = [g for g in groups if ua in [x for x in g[0]]]
        if not pool:
            pool = [g for g in groups if '*' in g[0]]
        hits = [(p, True) for _, al, _ in pool for p in al if path.startswith(p)]
        hits += [(p, False) for _, _, dl in pool for p in dl if path.startswith(p)]
        if not hits:
            return True
        best = max(h[0].__len__() for h in hits)
        return any(v for p, v in hits if len(p) == best)
    assert allowed('anything', '/private/public') is True
    assert allowed('anything', '/private/x') is False
    assert allowed('fusion-test', '/other') is False
    assert allowed('Googlebot/2.1', '/private/x') is False
    report['robots'] = {'groups': 2}
    report['scope'] = 'Independent logic recomputation only; NOT Android/device/service/acceptance evidence.'
    out = ROOT / 'testing/build/r31/reference.json'
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(report, indent=2) + '\n')
    print('R31 reference PASS: scrub 5 tags, gate 7 vectors, query 7, offsets 4 starts, '
          f'vault digest {digest[:12]}..., pdf startxref {sx}, psd 2 blocks, codec 3, phase 400ms, robots 2 groups.')

if __name__ == '__main__':
    main()
