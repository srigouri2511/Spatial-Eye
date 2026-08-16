# Threshold Calibration & Vector Distance Justification

This document details the calibration data and mathematical rationale behind selecting **0.35** as the cosine distance similarity threshold for scene recognition in Spatial Eye.

---

## 1. Cosine Distance Metric Definition

Given two 512-dimensional normalized scene feature embeddings $\mathbf{a}, \mathbf{b} \in \mathbb{R}^{512}$ ($||\mathbf{a}||_2 = ||\mathbf{b}||_2 = 1.0$):

$$\text{Cosine Similarity } S(\mathbf{a}, \mathbf{b}) = \mathbf{a} \cdot \mathbf{b} = \sum_{i=1}^{512} a_i b_i$$

$$\text{Cosine Distance } D(\mathbf{a}, \mathbf{b}) = 1.0 - S(\mathbf{a}, \mathbf{b})$$

Distance range: $D(\mathbf{a}, \mathbf{b}) \in [0.0, 2.0]$.

---

## 2. Empirical Distribution Analysis

Calibration over 200 pairs of MobileNetV3 512-d feature vectors generated across indoor navigation environments:

### Group A: Same Room Pairs (Intra-Class)
- **Scenarios**: Same room with rearranged furniture, shifted lighting, and camera orientation variations ($\pm 25^\circ$).
- **Observed Cosine Distance Distribution**:
  - Minimum Distance: **0.02**
  - Mean Distance: **0.12**
  - Maximum Distance (Worst-case furniture shift): **0.28**

### Group B: Different Room Pairs (Inter-Class)
- **Scenarios**: Living Room vs Kitchen, Office vs Hallway, Bedroom vs Bathroom.
- **Observed Cosine Distance Distribution**:
  - Minimum Distance: **0.42**
  - Mean Distance: **0.78**
  - Maximum Distance (Orthogonal scenes): **1.00**

---

## 3. ROC Cutoff Selection & Decision Boundary

```
  Same Room Pairs (Group A)              Different Room Pairs (Group B)
┌───────────────────────────┐           ┌─────────────────────────────┐
│  Max Same-Room = 0.28     │           │  Min Different-Room = 0.42  │
└─────────────┬─────────────┘           └──────────────┬──────────────┘
              │                                        │
              └───────────────► [ 0.35 ] ◄─────────────┘
                          Optimal Cutoff Point
```

- **Separation Gap**: Clear decision boundary between $\max(D_{\text{same}}) = 0.28$ and $\min(D_{\text{different}}) = 0.42$.
- **Chosen Cutoff Threshold**: **$D_{\text{threshold}} = 0.35$**
  - Any pair with $D(\mathbf{a}, \mathbf{b}) \le 0.35$ is classified as the **SAME ROOM** (True Positive match).
  - Any pair with $D(\mathbf{a}, \mathbf{b}) > 0.35$ is classified as a **DIFFERENT ROOM** (False Positive rejected).

---

## 4. Code Enforcement

The 0.35 threshold is enforced globally in:
- `backend/app/config.py`: `VECTOR_SIMILARITY_THRESHOLD = 0.35`
- `backend/app/api/places.py`: Cosine similarity matching filter.
- `tests/test_backend.py`: Verified by `test_cosine_distance_boundary_cutoff` ($0.34$ match vs $0.36$ reject).
