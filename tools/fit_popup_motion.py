"""Fit the original popup bounds with endpoint-constrained analytic curves.

Requires numpy; --plot also requires matplotlib. Run from any directory:
    python tools/fit_popup_motion.py --plot
The CSV is a frozen reference, never loaded by the Android application.
"""

import argparse
import csv
import json
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[1]
REFERENCE = ROOT / "app/src/test/resources/popup-motion-keyframes.csv"
OUTPUT = ROOT / "artifacts/popup-curve-fit"
ORIGIN = np.array([553.0, 1665.0, 553.0, 1665.0])
SCALE = np.array([638.0, 475.0, 638.0, 475.0])
EDGES = ["left", "top", "right", "bottom"]


def fit(t, y, degree=7):
    # y(t) = end + (1-t)^2 * (start-end + sum(a_k * t^k)).
    # Both endpoints are exact and y'(1)=0, without a final-frame snap.
    basis = (1 - t[:, None]) ** 2 * t[:, None] ** np.arange(1, degree - 1)
    baseline = y[-1] + (1 - t[:, None]) ** 2 * (y[0] - y[-1])
    return np.linalg.lstsq(basis, y - baseline, rcond=None)[0]


def evaluate(t, start, end, coefficients):
    polynomial = np.broadcast_to(coefficients[-1], (len(t), 4)).copy()
    for coefficient in coefficients[-2::-1]:
        polynomial = coefficient + t[:, None] * polynomial
    return end + (1 - t[:, None]) ** 2 * (start - end + t[:, None] * polynomial)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--plot", action="store_true")
    args = parser.parse_args()
    with REFERENCE.open(encoding="utf-8") as source:
        rows = list(csv.DictReader(source))
    OUTPUT.mkdir(parents=True, exist_ok=True)
    result = {}
    if args.plot:
        import matplotlib

        matplotlib.use("Agg")
        import matplotlib.pyplot as plt

        fig, axes = plt.subplots(4, 2, figsize=(12, 12), constrained_layout=True)
        fig.suptitle("Popup bounds: original keyframes vs analytic fit", fontsize=17)

    for column, direction in enumerate(("open", "close")):
        samples = np.array([
            [float(row[k]) for k in ("millis", *EDGES)]
            for row in rows if row["direction"] == direction
        ])
        duration = samples[-1, 0]
        t = samples[:, 0] / duration
        y = (samples[:, 1:] - ORIGIN) / SCALE
        coefficients = fit(t, y)
        error = (evaluate(t, y[0], y[-1], coefficients) - y) * SCALE
        dense_t = np.linspace(0, 1, 10001)
        dense = evaluate(dense_t, y[0], y[-1], coefficients)
        assert np.all(dense[:, 2] > dense[:, 0])
        assert np.all(dense[:, 3] > dense[:, 1])
        result[direction] = {
            "duration_ms": int(duration),
            "degree": 7,
            "start": y[0].tolist(),
            "end": y[-1].tolist(),
            "coefficients": {edge: coefficients[:, i].tolist() for i, edge in enumerate(EDGES)},
            "rmse_px": dict(zip(EDGES, np.sqrt(np.mean(error**2, axis=0)).tolist())),
            "max_error_px": dict(zip(EDGES, np.max(np.abs(error), axis=0).tolist())),
            "overall_rmse_px": float(np.sqrt(np.mean(error**2))),
        }
        print(direction, "RMSE (px):", result[direction]["rmse_px"])
        print(direction, "max error (px):", result[direction]["max_error_px"])
        for i, edge in enumerate(EDGES):
            values = ", ".join(f"{v:.12f}" for v in coefficients[:, i])
            print(f"    {edge} = PopupMotionCurve(start.{edge}, end.{edge}, {values}),")
            if args.plot:
                ax = axes[i, column]
                ax.plot(dense_t * duration, dense[:, i] * SCALE[i], color="#246bce", label="Analytic degree 7")
                ax.scatter(samples[:, 0], y[:, i] * SCALE[i], color="#e27626", s=20, zorder=3, label="Original keyframes")
                ax.set_title(f"{direction.capitalize()} / {edge}")
                ax.set_xlabel("Time (ms)")
                ax.set_ylabel("Position relative to reference origin (px)")
                ax.grid(alpha=0.2)
                if i == 0:
                    ax.legend(fontsize=8)
        print("degree comparison (overall RMSE / max error in px):")
        for degree in range(4, 10):
            candidate = fit(t, y, degree)
            residual = (evaluate(t, y[0], y[-1], candidate) - y) * SCALE
            print(degree, round(float(np.sqrt(np.mean(residual**2))), 4), round(float(np.max(np.abs(residual))), 4))
    (OUTPUT / "coefficients.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    if args.plot:
        fig.savefig(OUTPUT / "curves.png", dpi=150)
        plt.close(fig)


if __name__ == "__main__":
    main()
