#!/usr/bin/env python3
"""R134 oracle generator for FORECAST.ETS / FORECAST.ETS.CONFINT / FORECAST.ETS.SEASONALITY / FORECAST.ETS.STAT.

Pure-stdlib implementation. Expected values computed using Holt-Winters triple exponential
smoothing (additive seasonality) and simple linear fallback when seasonality=1.

FORECAST.ETS(target, values, timeline, [seasonality], [data_completion], [aggregation])
FORECAST.ETS.CONFINT(target, values, timeline, [confidence], [seasonality], [data_completion], [aggregation])
FORECAST.ETS.SEASONALITY(values, timeline, [data_completion], [aggregation])
FORECAST.ETS.STAT(values, timeline, [seasonality], [data_completion], [aggregation], stat_type)

All return #VALUE! on wrong arity, #NUM! on invalid inputs.
"""
from __future__ import annotations

import math
import sys


def fmt(v) -> str:
    if isinstance(v, str):
        return v
    if isinstance(v, float):
        if v == int(v) and abs(v) < 1e12:
            return f"{int(v)}.0"
        return repr(v)
    return str(v)


def detect_seasonality(ys):
    """Detect seasonality via autocorrelation."""
    n = len(ys)
    max_period = n // 2
    if max_period < 2:
        return 1
    mean = sum(ys) / n
    variance = sum((y - mean) ** 2 for y in ys) / n
    if variance == 0.0:
        return 1
    best_period = 1
    best_acf = 0.0
    for p in range(2, max_period + 1):
        cov = sum((ys[i] - mean) * (ys[i - p] - mean) for i in range(p, n))
        acf = cov / (n * variance)
        if acf > best_acf:
            best_acf = acf
            best_period = p
    return best_period if best_acf > 0.3 else 1


def holt_winters(ys, seasonality, alpha=0.3, beta=0.1, gamma=0.1):
    """Holt-Winters triple exponential smoothing. Returns (level, trend, seasonal)."""
    n = len(ys)
    s = [0.0] * seasonality
    level = ys[0]
    trend = (ys[seasonality] - ys[0]) / seasonality if n > seasonality else 0.0
    for i in range(seasonality):
        total = sum(ys[j] for j in range(i, n, seasonality))
        cnt = len(range(i, n, seasonality))
        s[i] = total / cnt - level if cnt > 0 else 0.0
    for i in range(n):
        prev_level = level
        prev_trend = trend
        s_idx = i % seasonality
        level = alpha * (ys[i] - s[s_idx]) + (1 - alpha) * (prev_level + prev_trend)
        trend = beta * (level - prev_level) + (1 - beta) * prev_trend
        s[s_idx] = gamma * (ys[i] - level) + (1 - gamma) * s[s_idx]
    return level, trend, s


def forecast_linear(ys, ts, target):
    n = len(ys)
    mx = sum(ts) / n
    my = sum(ys) / n
    sxx = sum((t - mx) ** 2 for t in ts)
    sxy = sum((ts[i] - mx) * (ys[i] - my) for i in range(n))
    if sxx == 0.0:
        return my
    slope = sxy / sxx
    intercept = my - slope * mx
    return intercept + slope * target


def confidence_z(confidence):
    """Inverse normal CDF (Acklam's approximation) for two-tailed confidence."""
    p = (1.0 + confidence) / 2.0
    a = [-3.969683028665376e01, 2.209460984245205e02, -2.759285104469687e02,
         1.383577518672690e02, -3.066479806614716e01, 2.506628277459239e00]
    b = [-5.447609879822406e01, 1.615858368580409e02, -1.556989798598866e02,
         6.680131188771972e01, -1.328068155288572e01]
    c = [-7.784894002430293e-03, -3.223964580411365e-01, -2.400758277161838e00,
         -2.549732539343734e00, 4.374664141464968e00, 2.938163982698783e00]
    d = [7.784695709041462e-03, 3.224671290700398e-01, 2.445134137142996e00,
         3.754408661907416e00]
    plow = 0.02425
    phigh = 1 - plow
    if p < plow:
        q = math.sqrt(-2.0 * math.log(p))
        return (((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) / \
               ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1.0)
    elif p <= phigh:
        q = p - 0.5
        r = q * q
        return (((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r + a[4]) * r + a[5]) * q / \
               (((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r + b[4]) * r + 1.0)
    else:
        q = math.sqrt(-2.0 * math.log(1 - p))
        return (((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) / \
               ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1.0)


def render() -> str:
    rows = [("FUNCTION", "ARGS", "EXPECTED")]

    # Test data: seasonal series with period 4
    # Timeline 1..12, values with seasonal pattern
    ts = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12]
    ys = [10.0, 15.0, 12.0, 18.0, 11.0, 16.0, 13.0, 19.0, 12.0, 17.0, 14.0, 20.0]

    # ---- FORECAST.ETS.SEASONALITY ----
    rows.append(("FORECAST.ETS.SEASONALITY", "A1:A12, B1:B12", fmt(detect_seasonality(ys))))
    rows.append(("FORECAST.ETS.SEASONALITY", "A1:A4, B1:B4", fmt(1)))  # too short for seasonality detection
    rows.append(("FORECAST.ETS.SEASONALITY", "A1:A12, B1:B12, 1", fmt(detect_seasonality(ys))))

    # ---- FORECAST.ETS ----
    seasonality = detect_seasonality(ys)  # should be 4
    level, trend, seasonal = holt_winters(ys, seasonality)
    periods_ahead = 13 - ts[-1]  # target 13, last is 12 -> 1 period ahead
    s_idx = (periods_ahead - 1) % seasonality
    fc = level + trend * periods_ahead + seasonal[s_idx]
    rows.append(("FORECAST.ETS", "13, A1:A12, B1:B12", fmt(fc)))
    # Forecast at target=14
    periods_ahead2 = 14 - ts[-1]
    s_idx2 = (periods_ahead2 - 1) % seasonality
    fc2 = level + trend * periods_ahead2 + seasonal[s_idx2]
    rows.append(("FORECAST.ETS", "14, A1:A12, B1:B12", fmt(fc2)))
    # Forecast with explicit seasonality=4
    rows.append(("FORECAST.ETS", "13, A1:A12, B1:B12, 4", fmt(fc)))
    # Linear fallback when seasonality > n/2
    rows.append(("FORECAST.ETS", "13, A1:A3, B1:B3", fmt(forecast_linear(ys[:3], ts[:3], 13))))

    # ---- FORECAST.ETS.CONFINT ----
    # Confidence interval for target=13
    residuals = [abs(ys[i] - level) for i in range(seasonality, len(ys))]
    rmse = math.sqrt(sum(r * r for r in residuals) / len(residuals)) if residuals else 0.0
    z = confidence_z(0.95)
    half_width = z * rmse * math.sqrt(1.0 + periods_ahead / len(ys))
    rows.append(("FORECAST.ETS.CONFINT", "13, A1:A12, B1:B12", fmt(half_width)))
    rows.append(("FORECAST.ETS.CONFINT", "13, A1:A12, B1:B12, 0.90", fmt(confidence_z(0.90) * rmse * math.sqrt(1.0 + periods_ahead / len(ys)))))
    rows.append(("FORECAST.ETS.CONFINT", "12, A1:A12, B1:B12", fmt(0.0)))  # target = last, no confidence width

    # ---- FORECAST.ETS.STAT ----
    # stat_type 1=alpha, 2=beta, 3=gamma, 4=MASE, 5=SMSE, 6=SMPE
    rows.append(("FORECAST.ETS.STAT", "A1:A12, B1:B12, 4, 1, 1, 1", fmt(0.3)))  # alpha
    rows.append(("FORECAST.ETS.STAT", "A1:A12, B1:B12, 4, 1, 1, 2", fmt(0.1)))  # beta
    rows.append(("FORECAST.ETS.STAT", "A1:A12, B1:B12, 4, 1, 1, 3", fmt(0.1)))  # gamma
    # MASE
    if len(ys) > seasonality:
        mae = sum(abs(ys[i] - ys[i - 1]) for i in range(seasonality, len(ys))) / (len(ys) - seasonality)
        naive_mae = sum(abs(ys[i] - ys[i - 1]) for i in range(1, len(ys))) / (len(ys) - 1)
        mase = mae / naive_mae if naive_mae != 0 else 0.0
        rows.append(("FORECAST.ETS.STAT", "A1:A12, B1:B12, 4, 1, 1, 4", fmt(mase)))
    # SMSE
    mse = sum((ys[i] - level) ** 2 for i in range(seasonality, len(ys))) / (len(ys) - seasonality) if len(ys) > seasonality else 0.0
    rows.append(("FORECAST.ETS.STAT", "A1:A12, B1:B12, 4, 1, 1, 5", fmt(mse)))
    # SMPE
    mape = sum(abs((ys[i] - level) / ys[i]) if ys[i] != 0 else 0.0 for i in range(seasonality, len(ys))) / (len(ys) - seasonality) if len(ys) > seasonality else 0.0
    rows.append(("FORECAST.ETS.STAT", "A1:A12, B1:B12, 4, 1, 1, 6", fmt(mape * 100.0)))

    # ---- Refusals ----
    rows.append(("FORECAST.ETS", "13", "#VALUE!"))                    # wrong arity: only 1 arg
    rows.append(("FORECAST.ETS", "13, A1:A12", "#VALUE!"))            # wrong arity: only 2 args
    rows.append(("FORECAST.ETS.SEASONALITY", "A1:A3", "#VALUE!"))      # wrong arity: only 1 arg
    rows.append(("FORECAST.ETS.CONFINT", "13", "#VALUE!"))             # wrong arity: only 1 arg
    rows.append(("FORECAST.ETS.STAT", "A1:A12, B1:B12, 4, 1, 1", "#VALUE!"))  # wrong arity (missing stat_type)
    rows.append(("FORECAST.ETS.CONFINT", "13, A1:A12, B1:B12, 1.5", "#NUM!"))  # confidence > 1
    rows.append(("FORECAST.ETS.CONFINT", "13, A1:A12, B1:B12, 0", "#NUM!"))    # confidence = 0
    rows.append(("FORECAST.ETS.STAT", "A1:A12, B1:B12, 4, 1, 1, 7", "#NUM!"))  # invalid stat_type
    rows.append(("FORECAST.ETS.SEASONALITY", "A1:A12, B1:B12, 0", "#NUM!"))  # invalid data_completion
    rows.append(("FORECAST.ETS", "13, A1:A12, B1:B12, 0", "#NUM!"))    # invalid seasonality

    lines = ["\t".join(str(c) for c in row) for row in rows]
    return "\n".join(lines) + "\n"


def main() -> int:
    path = sys.argv[1] if len(sys.argv) > 1 else None
    text = render()
    if path:
        with open(path, "w", encoding="utf-8") as f:
            f.write(text)
    else:
        sys.stdout.write(text)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
