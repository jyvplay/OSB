#!/usr/bin/env python3
"""Calibrate an installed external reader against its own writer; no product code."""
import hashlib
import json
from pathlib import Path
from icalendar import Calendar, Event, __version__
rows = []
for text in ['a\\;b', 'a\\\\b', 'a\\nb', 'a\\b']:
    calendar = Calendar()
    calendar.add('version', '2.0')
    event = Event()
    event.add('summary', text)
    calendar.add_component(event)
    wire = calendar.to_ical()
    actual = str(Calendar.from_ical(wire).walk('VEVENT')[0]['summary'])
    rows.append({'input': text, 'wire': wire.decode(), 'actual': actual, 'equal': actual == text})
report = {'reader': 'icalendar ' + __version__,
          'experiment': 'Library own writer then own reader, independent of Kotlin exporter',
          'script_sha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(), 'cases': rows}
path = Path(__file__).resolve().parents[1] / 'docs/verification' / ('r13-calendar-reader-' + __version__ + '.json')
path.write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps(report))
