#!/usr/bin/env python3
"""Tennis Sensor Case v3.1 — Geometry Verification (board rotated 90°)"""
import sys

P = F = 0
issues = []

def ok(cond, desc, detail=""):
    global P, F
    if cond:
        P += 1; print(f"  [PASS] {desc}")
    else:
        F += 1
        m = f"  [FAIL] {desc}" + (f" — {detail}" if detail else "")
        print(m); issues.append((desc, detail))

# ---- Parameters (v3.1) ----
bat_w,bat_l,bat_h = 20,33,6.5
brd_w,brd_l,brd_h = 22,18,12
sw_w,sw_d = 9.5,10
inner_w,inner_l = 22,33
outer_w,outer_l = 28.5,36
sww = (outer_w-inner_w)/2  # 3.25
swl = (outer_l-inner_l)/2  # 1.5
base_floor=3.2; base_h=base_floor+bat_h  # 9.7
lip_h=3; lip_w=1.0; lip_gap=0.15
cover_ceil=1.8; shelf_z=2; air_gap=2
ant_pocket_h=0.5
cover_inner_h=shelf_z+brd_h+air_gap  # 16
cover_h=cover_inner_h+cover_ceil      # 17.8
dl_w,dl_l,dl_h = 24.5,20,2
usbc_w,usbc_h = 9.5,3.5; usbc_z=shelf_z+1
sw_hole_w,sw_hole_h = 6,4
sw_wall_t=1; bracket_lip=2; bracket_rib=2
rail_d=0.8

# Rotated board
sw_bay_depth = inner_l - brd_w        # 11
board_y_front = -inner_l/2            # -16.5 (USB-C end)
board_y_back  = board_y_front + brd_w #   5.5 (antenna end)
board_y_center = (board_y_front+board_y_back)/2  # -5.5

print("="*62)
print(" TENNIS SENSOR CASE v3.2 — 3D PRINT COMPLIANCE VERIFICATION")
print("="*62)

# ---- Board fit ----
print("\n[1] BOARD FIT (rotated: 18mm→X, 22mm→Y)")
ok(brd_l <= inner_w, "Board X (18mm) fits inner_w (22mm)",
   f"{brd_l}≤{inner_w}, gap={inner_w-brd_l}mm")
ok(brd_w <= inner_l, "Board Y (22mm) fits inner_l (33mm)",
   f"{brd_w}≤{inner_l}")
ok(board_y_front >= -inner_l/2, "Board -Y edge within cavity",
   f"{board_y_front}≥{-inner_l/2}")
ok(board_y_back <= inner_l/2, "Board +Y edge within cavity",
   f"{board_y_back}≤{inner_l/2}")

# ---- Switch bay ----
print("\n[2] SWITCH BAY (rear +Y)")
ok(sw_bay_depth >= sw_d, "Bay depth ≥ switch depth",
   f"{sw_bay_depth}≥{sw_d}")
bay_start = board_y_back
bay_end = inner_l/2
ok(abs(bay_end - bay_start - sw_bay_depth) < 0.01,
   "Bay fills rear space correctly",
   f"{bay_start}→{bay_end} = {bay_end-bay_start}mm")
ok(sw_w + 2*sw_wall_t <= inner_w,
   "ㄷ-walls fit within X cavity",
   f"{sw_w+2*sw_wall_t}≤{inner_w}")

# ---- USB-C on -Y wall ----
print("\n[3] USB-C PORT (-Y front wall)")
ok(usbc_w <= inner_w, "USB-C width fits wall",
   f"{usbc_w}≤{inner_w}")
usbc_cut_y_end = -outer_l/2 - 0.5 + swl + 1
ok(usbc_cut_y_end > -inner_l/2,
   "USB-C hole penetrates -Y wall",
   f"cut_end={usbc_cut_y_end:.1f} > inner_y={-inner_l/2}")

# ---- Switch toggle on +Y wall ----
print("\n[4] SWITCH TOGGLE (+Y rear wall)")
sw_hz = shelf_z + brd_h/2 - sw_hole_h/2
sw_cut_end = inner_l/2 - 1 + swl + 2
ok(sw_cut_end > outer_l/2,
   "Toggle hole penetrates +Y wall",
   f"cut_end={sw_cut_end} > outer_y/2={outer_l/2}")
ok(sw_hz >= 0, "Toggle Z ≥ 0", f"Z={sw_hz}")
ok(sw_hz + sw_hole_h <= cover_inner_h, "Toggle within cover")

# ---- 3D Printing Manufacturer Guidelines Compliance ----
print("\n[5] 3D PRINTING MANUFACTURER COMPLIANCE")
ok(sww >= 1.2, "Outer side wall ≥ 1.2mm", f"sww={sww:.2f}mm")
ok(swl >= 1.2, "Outer front/rear wall ≥ 1.2mm", f"swl={swl:.2f}mm")
ok(base_floor - dl_h >= 1.2, "Dual lock residual floor wall ≥ 1.2mm",
   f"{base_floor-dl_h:.1f}mm ≥ 1.2mm")
ok(cover_ceil - ant_pocket_h >= 1.2, "Antenna pocket residual ceiling wall ≥ 1.2mm",
   f"{cover_ceil-ant_pocket_h:.1f}mm ≥ 1.2mm")
ok(rail_d >= 0.8, "Shutter rail depth ≥ 0.8mm", f"{rail_d}mm ≥ 0.8mm")
ok(lip_w >= 0.8, "Snap-fit lip width ≥ 0.8mm", f"{lip_w}mm ≥ 0.8mm")
ok(sw_wall_t >= 0.8, "Switch wall thickness ≥ 0.8mm", f"{sw_wall_t}mm ≥ 0.8mm")

# ---- Bracket reach ----
print("\n[6] BRACKET BOARD SUPPORT")
wall_to_board = inner_w/2 - brd_l/2  # 11 - 9 = 2.0
ok(bracket_lip >= wall_to_board,
   f"Bracket lip reaches board edge",
   f"lip={bracket_lip}mm, gap={wall_to_board}mm")

# ---- Structural ----
print("\n[7] STRUCTURAL")
dl_side = (outer_w - dl_w)/2
ok(dl_side >= 2.0, f"DL side margin ≥ 2mm", f"{dl_side}")
ok(dl_h <= base_floor, "DL pocket within floor")
rd = lip_w + lip_gap
ok(sww - rd >= 1.5, "Cover wall at rebate ≥ 1.5mm",
   f"{sww-rd:.2f}mm")

board_top = shelf_z + brd_h
actual_air = cover_inner_h - board_top
ok(abs(actual_air - air_gap) < 0.01, f"Air gap = 2mm")

# ---- Summary ----
print("\n" + "="*62)
print(f" RESULTS: {P} PASS, {F} FAIL")
print("="*62)
if issues:
    print("\n ISSUES:")
    for i,(d,dt) in enumerate(issues,1):
        print(f"  {i}. {d}" + (f" → {dt}" if dt else ""))
sys.exit(1 if F else 0)
