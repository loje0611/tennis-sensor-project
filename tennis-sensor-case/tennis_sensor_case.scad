// ============================================================
// Ultra-Compact Screwless Tennis Sensor Case  v3.1
// Board rotated 90°: sensor Y-axis → racket handle direction
// USB-C on -Y front wall · Switch on +Y rear wall
// ============================================================

render_part = "cover"; // "base" | "cover" | "shutter" | "assembly"

$fn = 64;

// ---- Color ----
case_color = [0.5, 0.5, 0.5];

// ---- Component Dimensions ----
bat_w  = 20.0;  bat_l  = 33.0;  bat_h  = 6.5; // 602030 LiPo Battery (6.0mm x 20.0mm x 30.0mm + PCM, 3.7V 300mAh)
brd_w  = 22.0;  brd_l  = 18.0;  brd_h  = 12.0;
sw_w   = 9.5;   sw_d   = 10.0;

// ---- Envelope ----
inner_w = 22.0;  inner_l = 33.0;
outer_w = 28.5;  outer_l = 36.0;
wall    = 1.5;   fillet  = 1.5;
side_wall_w = (outer_w - inner_w) / 2;  // 3.25
side_wall_l = (outer_l - inner_l) / 2;  // 1.5

// ---- Base Z Stack ----
base_floor    = 3.2;  // 3.2mm floor (dl_h=2.0mm -> 1.2mm residual wall compliant)
base_cavity_h = bat_h;
base_h        = base_floor + base_cavity_h;  // 9.7

// ---- Snap-fit lip (±X walls only) ----
lip_h   = 3.0;
lip_w   = 1.0;
lip_gap = 0.15;
detent_bump = 0.3;
detent_len  = 10.0;
detent_th   = 0.8;
detent_z    = lip_h - 1.5;

// ---- Cover Z Stack ----
cover_ceil    = 1.8;  // 1.8mm ceil (ant_pocket_h=0.5mm -> 1.3mm residual wall compliant)
shelf_z       = 2.0;
shelf_thick   = 1.0;
air_gap       = 2.0;
ant_pocket_h  = 0.5;
ant_pocket_w  = 18.0;   // X (board short axis)
ant_pocket_l  = 14.0;   // Y (antenna region)
cover_inner_h = shelf_z + brd_h + air_gap;  // 16.0
cover_h       = cover_inner_h + cover_ceil;  // 17.8

// ---- 3M Dual Lock Pocket ----
dl_w = 24.5;  dl_l = 20.0;  dl_h = 2.0;

// ---- Ports ----
sw_hole_w = 6.0;  sw_hole_h = 4.0;
usbc_w    = 9.5;  usbc_h    = 3.5;
usbc_z    = shelf_z + 1.0;

// ---- Switch bay ----
sw_wall_t = 1.0;

// ---- Board orientation (ROTATED 90°) ----
//   MPU-6050 sensor Y-axis → case Y → racket handle direction
//   brd_l (18mm, short edge with USB-C) → X axis
//   brd_w (22mm, long edge)             → Y axis
//   USB-C faces -Y wall (front)
//   Antenna end faces +Y (near switch bay)

sw_bay_depth   = inner_l - brd_w;          // 33 - 22 = 11mm
board_y_front  = -inner_l / 2;              // -16.5 (USB-C end)
board_y_back   = board_y_front + brd_w;     //   5.5 (antenna end)
board_y_center = (board_y_front + board_y_back) / 2;  // -5.5

// ---- Bracket ----
bracket_lip   = 2.0;
bracket_rib_w = 2.0;

// ============================================================
// SHAPE HELPERS
// ============================================================
module box_fillet_bottom(w, l, h, r) {
    hull() {
        for (x = [-w/2+r, w/2-r])
            for (y = [-l/2+r, l/2-r]) {
                translate([x, y, r])       sphere(r=r);
                translate([x, y, h-0.005]) cylinder(r=r, h=0.01);
            }
    }
}

module box_fillet_top(w, l, h, r) {
    hull() {
        for (x = [-w/2+r, w/2-r])
            for (y = [-l/2+r, l/2-r]) {
                translate([x, y, 0])   cylinder(r=r, h=0.01);
                translate([x, y, h-r]) sphere(r=r);
            }
    }
}

// ============================================================
// BASE
// ============================================================
module base_final() {
    color(case_color)
    union() {
        difference() {
            box_fillet_bottom(outer_w, outer_l, base_h, fillet);
            translate([-inner_w/2, -inner_l/2, base_floor])
                cube([inner_w, inner_l, base_cavity_h + 0.1]);
            translate([-dl_w/2, -dl_l/2, -0.01])
                cube([dl_w, dl_l, dl_h + 0.01]);
        }

        // ±X snap-fit lips
        translate([inner_w/2, -inner_l/2, base_h])
            cube([lip_w, inner_l, lip_h]);
        translate([-inner_w/2 - lip_w, -inner_l/2, base_h])
            cube([lip_w, inner_l, lip_h]);

        // Detent bumps
        translate([inner_w/2 + lip_w, -detent_len/2, base_h + detent_z])
            cube([detent_bump, detent_len, detent_th]);
        translate([-inner_w/2 - lip_w - detent_bump, -detent_len/2,
                   base_h + detent_z])
            cube([detent_bump, detent_len, detent_th]);
    }
}

// ============================================================
// COVER
// ============================================================
module cover_shell() {
    rd = lip_w + lip_gap;

    difference() {
        box_fillet_top(outer_w, outer_l, cover_h, fillet);

        // Main cavity
        translate([-inner_w/2, -inner_l/2, -0.01])
            cube([inner_w, inner_l, cover_inner_h + 0.02]);

        // ±X lip rebates
        translate([inner_w/2, -inner_l/2 - 0.01, -0.01])
            cube([rd, inner_l + 0.02, lip_h + 0.3]);
        translate([-inner_w/2 - rd, -inner_l/2 - 0.01, -0.01])
            cube([rd, inner_l + 0.02, lip_h + 0.3]);

        // Detent grooves
        gd = detent_bump + 0.1;
        gz = detent_z - 0.1;
        gh = detent_th + 0.2;
        translate([inner_w/2 + rd, -detent_len/2 - 0.5, gz])
            cube([gd, detent_len + 1.0, gh]);
        translate([-inner_w/2 - rd - gd, -detent_len/2 - 0.5, gz])
            cube([gd, detent_len + 1.0, gh]);

        // Antenna pocket (near board +Y end / antenna area)
        ant_y_center = board_y_back - ant_pocket_l/2;
        translate([-ant_pocket_w/2, ant_y_center, cover_inner_h])
            cube([ant_pocket_w, ant_pocket_l, ant_pocket_h + 0.01]);
    }
}

module board_brackets() {
    bw = bracket_rib_w;
    bl = bracket_lip;
    corners = [
        [ inner_w/2,  board_y_back,  -1, -1],
        [ inner_w/2,  board_y_front, -1,  1],
        [-inner_w/2,  board_y_back,   1, -1],
        [-inner_w/2,  board_y_front,  1,  1],
    ];
    for (c = corners) {
        xw = c[0]; yp = c[1]; xd = c[2]; yd = c[3];
        translate([
            xd > 0 ? xw : xw - bw,
            yd > 0 ? yp : yp - bw, 0
        ]) cube([bw, bw, shelf_z + shelf_thick]);
        translate([
            xd > 0 ? xw : xw - bl,
            yd > 0 ? yp : yp - bw, shelf_z
        ]) cube([bl, bw, shelf_thick]);
    }
}

module switch_bay_walls() {
    by = board_y_back;
    t  = sw_wall_t;
    h  = brd_h * 0.7;
    translate([-sw_w/2 - t, by,     shelf_z]) cube([t, sw_bay_depth, h]);
    translate([ sw_w/2,     by,     shelf_z]) cube([t, sw_bay_depth, h]);
    translate([-sw_w/2 - t, by - t, shelf_z]) cube([sw_w + 2*t, t, h]);
}

module cover_final() {
    color(case_color)
    difference() {
        union() {
            cover_shell();
            board_brackets();
            switch_bay_walls();
        }

        // Clip to dome
        difference() {
            cube([200, 200, 200], center=true);
            box_fillet_top(outer_w, outer_l, cover_h, fillet);
        }

        // Switch toggle hole — +Y rear wall ONLY
        sw_hz = shelf_z + brd_h/2 - sw_hole_h/2;
        translate([-sw_hole_w/2, inner_l/2 - 1, sw_hz])
            cube([sw_hole_w, side_wall_l + 2, sw_hole_h]);

        // USB-C port — -Y front wall ONLY (board USB-C end)
        translate([-usbc_w/2, -outer_l/2 - 0.5, usbc_z])
            cube([usbc_w, side_wall_l + 1, usbc_h]);

        // Switch bay floor open
        translate([-sw_w/2, board_y_back, -0.01])
            cube([sw_w, sw_bay_depth + side_wall_l + 1, shelf_z + 0.02]);
    }
}

// ============================================================
// USB-C SHUTTER  (flush with -Y front wall, slides along X)
// ============================================================
module shutter() {
    color(case_color) {
        sh_d = 2.0;
        sh_w = usbc_w + 4.0;
        sh_h = usbc_h + 3.0;
        rail_h = 0.8;
        rail_d = 0.8;

        difference() {
            translate([-sh_w/2, -outer_l/2 - sh_d, 0])
                cube([sh_w, sh_d, sh_h]);
            translate([0, -outer_l/2 - sh_d - 0.1, sh_h/2])
                rotate([-90, 0, 0])
                    cylinder(r=1.2, h=sh_d + 0.2);
        }
        // Top rail
        translate([-sh_w/2, -outer_l/2 - rail_d, sh_h])
            cube([sh_w, rail_d, rail_h]);
        // Bottom rail
        translate([-sh_w/2, -outer_l/2 - rail_d, -rail_h])
            cube([sh_w, rail_d, rail_h]);
    }
}

// ============================================================
// ASSEMBLY
// ============================================================
module assembly() {
    base_final();
    translate([0, 0, base_h]) cover_final();
    translate([0, 0, base_h + usbc_z - 1.0])
        shutter();
}

// ============================================================
// RENDER SELECTION
// ============================================================
if (render_part == "base")          base_final();
else if (render_part == "cover")    cover_final();
else if (render_part == "shutter")  shutter();
else if (render_part == "assembly") assembly();
