# Product Requirements Document (PRD)

## Automated LEGO Train Track Layout Generator

---

## 1. Overview

The goal of this project is to design and implement an application capable of automatically generating valid closed-loop LEGO train track layouts based on a user-provided inventory of track pieces (straights, curves, switches, etc.).

The application will:

* Accept available track pieces as input
* Generate one or more valid closed-loop layouts
* Output layouts as SVG graphics suitable for viewing, printing, or further editing
* Use a geometry-accurate model of LEGO track components
* Be extensible for future features (manual editor, optimization, simulation)

The core innovation is **automated layout generation**, which is largely missing from existing tools.

---

## 2. Goals

### Primary Goals

1. Generate valid closed loops from a set of pieces.
2. Ensure geometric correctness (connections align precisely).
3. Produce SVG output suitable for visualization and export.
4. Use a scalable architecture supporting switches and future expansion.

### Secondary Goals

* Generate multiple layout options
* Support constraints (table size, bounding box)
* Detect collisions and invalid overlaps
* Provide deterministic reproducibility (seed-based generation)

---

## 3. Non-Goals (Initial Version)

* Manual drag-and-drop editor
* 3D visualization
* Physics simulation of trains
* Full LEGO part library support
* Mobile optimization

These may be added later.

---

## 4. Target Users

### Primary Users

* LEGO train hobbyists
* Layout designers planning builds
* Exhibition planners

### Secondary Users

* Software developers
* Educational users (STEM / geometry)

---

## 5. Core Feature: Automated Layout Generation

### Input

User provides:

* Count of straight tracks
* Count of curved tracks
* Count and types of switches
* Optional constraints:

    * Maximum width/height
    * Minimum radius
    * Preferred complexity
    * Symmetry preference
    * Random seed

Example:

```
Straights: 12
Curves: 16
Left Switches: 2
Right Switches: 1
Max Table Size: 1200 x 800 mm
```

---

### Output

System produces:

* One or more valid layouts
* Each layout includes:

    * Piece placement
    * Connections
    * Bounding box
    * SVG rendering
    * Metadata

---

## 6. Functional Requirements

### FR1 — Piece Inventory Input

System must allow defining quantities of each track type.

### FR2 — Geometry Engine

System must:

* Represent track pieces with precise geometry
* Support transformations (position + rotation)
* Support connection alignment
* Maintain tolerance thresholds

### FR3 — Automatic Solver

System must:

* Search for piece combinations forming closed loops
* Validate closure conditions:

    * Position tolerance
    * Angle tolerance
* Avoid collisions
* Respect inventory limits

### FR4 — Multiple Solutions

System should generate multiple unique layouts when possible.

### FR5 — SVG Export

System must:

* Render layout to SVG
* Preserve geometric scale
* Include optional styling layers
* Support export to file

### FR6 — Deterministic Mode

Given same input and seed, generator should produce identical output.

---

## 7. Non-Functional Requirements

### Performance

* Generate simple loops (<30 pieces) within 1–2 seconds
* Complex layouts (<100 pieces) within 10 seconds

### Accuracy

* Connection error < 0.5 mm equivalent tolerance
* Angle error < 0.1°

### Extensibility

Architecture must support:

* Additional piece types
* Branching layouts
* Future editor

---

## 8. Recommended Data Model

Architecture uses **Hybrid Transform + Connector Graph Model**.

---

### 8.1 Core Geometry Types

```
Pose
    x: float
    y: float
    rotation: float
```

```
Transform
    dx: float
    dy: float
    dRotation: float
```

---

### 8.2 TrackPiece Definition

```
TrackPieceDefinition
    id
    type
    length
    curvatureRadius
    connectors[]
    svgTemplate
```

---

### 8.3 Connector

```
Connector
    id
    localPose
    direction
```

---

### 8.4 Placed Piece

```
PlacedPiece
    instanceId
    definitionId
    pose
    connectedConnectors[]
```

---

### 8.5 Layout

```
Layout
    placedPieces[]
    connections[]
    boundingBox
    metadata
```

---

## 9. Geometry Representation

Each track piece acts as a rigid transform operator:

```
Pose_out = Pose_in × Transform_piece
```

This allows sequential construction and solver efficiency.

---

## 10. Solver Architecture

### 10.1 Problem Type

Constraint Satisfaction + Search.

Goal:

```
Find sequence of pieces such that final pose ≈ start pose
```

---

### 10.2 Algorithm Options

Initial implementation:

**Backtracking Search with Heuristics**

Future:

* A* search
* Genetic algorithms
* Constraint solvers (SAT/SMT)

---

### 10.3 Solver State

```
SolverState
    currentPose
    remainingPieces
    placedPieces
    openConnectors
```

---

### 10.4 Closure Conditions

Loop is valid when:

```
distance(currentPose, startPose) < tolerance
AND
angleDifference < tolerance
```

---

### 10.5 Heuristics

* Prefer curves when angle mismatch large
* Prefer straights when distance mismatch large
* Early pruning if error exceeds threshold
* Symmetry detection to avoid duplicates

---

## 11. Collision Detection

Requirements:

* Detect overlapping track bodies
* Allow shared endpoints
* Fast bounding-box pre-check
* Precise geometry check only when necessary

---

## 12. SVG Rendering

SVG is well suited because:

* Tracks are 2D geometry
* Infinite scalability
* Easy export and printing
* Human-readable format

---

### 12.1 SVG Requirements

* Real-world scale (mm or studs)
* Group per track piece
* Optional layers:

    * Track centerline
    * Sleepers
    * Bounding box
    * Labels
* Style customization via CSS

---

### 12.2 SVG Structure Example

```
<svg>
  <g id="layout">
    <g class="track-piece" transform="translate(...) rotate(...)">
      <path d="..."/>
    </g>
  </g>
</svg>
```

---

### 12.3 Rendering Strategy

Each piece definition contains:

* SVG path template in local coordinates

Renderer applies transform:

```
globalTransform = piecePose
```

---

## 13. API Design (Optional Future)

### Generate Layout

```
POST /generate
{
  inventory: {...},
  constraints: {...}
}
```

Response:

```
{
  layouts: [
    {
      svg: "...",
      metadata: {...}
    }
  ]
}
```

---

## 14. Success Metrics

* Time to generate layout
* Number of valid layouts found
* User satisfaction (future)
* Geometry accuracy tests passing

---

## 15. Risks

### Technical Risks

* Combinatorial explosion in solver
* Floating point precision errors
* Collision detection complexity

### Mitigations

* Heuristics
* Tolerances
* Spatial indexing
* Incremental validation

---

## 16. Milestones

### Phase 1 — Geometry Foundation

* Piece definitions
* Transform math
* SVG renderer

### Phase 2 — Basic Solver

* Sequential loop generator
* Straights + curves only

### Phase 3 — Advanced Solver

* Switch support
* Multiple layouts
* Constraints

### Phase 4 — Optimization

* Performance improvements
* Collision detection
* Export improvements

---

## 17. Future Enhancements

* Manual editor
* Layout optimization (min parts, max area)
* Room scanning integration
* Train simulation
* Community layout sharing
* AI-assisted generation

---

## 18. Technical Stack (Suggested)

Backend:

* Kotlin / Java / Python

Geometry:

* Custom math (recommended)
* Optional: computational geometry library

Rendering:

* Native SVG generation (no heavy dependencies)

---

## 19. Key Design Principles

1. Geometry correctness first
2. Solver simplicity before optimization
3. Deterministic reproducibility
4. Separation of model and rendering
5. Extensibility for future features

---

## 20. Summary

This system combines:

* Rigid-body geometry
* Graph connectivity
* Constraint solving
* Vector rendering (SVG)

The chosen hybrid data model ensures scalability from a simple MVP to a full professional layout planner.

---

End of Document
