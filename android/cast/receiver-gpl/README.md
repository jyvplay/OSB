# Separate GPL receiver app (planned, not implemented here)

Per the licensing gate, any AirPlay receiver that depends on UxPlay / RPiPlay or
other GPL code must ship as a **separate application and source repository**.
Nothing in the main APK loads or links to GPL receiver code.

This placeholder documents the boundary only. It is **not** a Gradle module in
this tree and it does not count as implemented functionality.
