# R17 — first close R16's experimental plan

Work only on arena/31a7965e-osb. R16 completion here means the six declared bounded experiments and evidence/reporting, NOT complete Together/Office/performance acceptance.

Evidence threshold before each implementation commit: executable affected host/Python checks, project/source checks, preserved counterexample where applicable. Android source changes remain experimental until exact native CI. No module promotion from queued tests or build success alone.

Observed blockers: old focus counterexample did not reproduce at6506159, preventing actual candidate device tests; profile job succeeded at7a1eee3 but each large annotation was truncated to4096 characters. Preserve both. Classify historical positive/negative/invalid results separately without skipping candidate regressions; cap receipt chunks below4096 and verify reassembly/SHA256. Generate measured first-party-owned ART rules only, rebuild and require actual profile compilation.

Six distinct R16 mechanisms remain: bounded socket capability, direction/session AEAD, causal clock interval, native UI measurement, measured ART profile compilation, LOK feasibility/source boundary. R17 mechanisms will extend this portfolio only after R16 evidence closure. Full user workflow, legal/physical/API37/AX acceptance remains separate. No independent agents, theorem prover or physical lab is available.
