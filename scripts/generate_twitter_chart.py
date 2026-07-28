#!/usr/bin/env python3
"""Generate readme-assets/twitter-throughput.png — Ghost · KSER · Moshi decode comparison."""

from __future__ import annotations

import matplotlib.pyplot as plt
import numpy as np

OUT = "readme-assets/twitter-throughput.png"

modes = ["String", "Bytes", "Streaming"]
engines = ["Ghost", "KSER", "Moshi"]
colors = {"Ghost": "#7C3AED", "KSER": "#C026D3", "Moshi": "#E11D48"}

throughput = {
    "Ghost": [1.229, 1.052, 0.529],
    "KSER": [0.718, 0.424, 0.193],
    "Moshi": [0.374, 0.275, 0.426],
}
latency_us = {
    "Ghost": [514.0, 600.2, 1193.7],
    "KSER": [879.2, 1488.6, 3269.5],
    "Moshi": [1688.2, 2297.4, 1481.9],
}
memory_kb = {
    "Ghost": [361.1, 621.2, 1268.6],
    "KSER": [1337.6, 4297.0, 1904.9],
    "Moshi": [1708.9, 4668.4, 1708.8],
}

panels = [
    ("Throughput (GB/s) ↑", throughput, "GB/s"),
    ("Latency (µs/op) ↓", latency_us, "µs"),
    ("Memory (KB/op) ↓", memory_kb, "KB"),
]

x = np.arange(len(modes))
width = 0.24

fig, axes = plt.subplots(1, 3, figsize=(14, 4.2), dpi=150)
fig.patch.set_facecolor("#FCFAFF")

for ax, (title, data, unit) in zip(axes, panels):
    ax.set_facecolor("#FCFAFF")
    for i, engine in enumerate(engines):
        offset = (i - 1) * width
        bars = ax.bar(x + offset, data[engine], width, label=engine, color=colors[engine], edgecolor="white", linewidth=0.6)
        for bar in bars:
            h = bar.get_height()
            ax.text(
                bar.get_x() + bar.get_width() / 2,
                h * 1.02 if title.startswith("Throughput") else h + (max(data[e][j] for e in engines for j in range(3)) * 0.02),
                f"{h:.2f}" if unit == "GB/s" else f"{h:.0f}",
                ha="center",
                va="bottom",
                fontsize=7,
                color="#221733",
            )
    ax.set_title(title, fontsize=11, fontweight="bold", color="#221733", pad=10)
    ax.set_xticks(x)
    ax.set_xticklabels(modes, fontsize=10, color="#4C3B63")
    ax.tick_params(axis="y", labelsize=8, colors="#7C6F94")
    ax.spines["top"].set_visible(False)
    ax.spines["right"].set_visible(False)
    ax.spines["left"].set_color("#E6DCFA")
    ax.spines["bottom"].set_color("#E6DCFA")
    ax.grid(axis="y", linestyle="--", alpha=0.35, color="#E6DCFA")

axes[0].legend(loc="upper right", frameon=False, fontsize=9)
fig.suptitle(
    "Twitter macro decode (631 KB) — Ghost · KSER · Moshi",
    fontsize=12,
    fontweight="bold",
    color="#221733",
    y=1.02,
)
fig.tight_layout()
fig.savefig(OUT, bbox_inches="tight", facecolor=fig.get_facecolor())
print(f"Wrote {OUT}")
