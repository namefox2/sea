precision highp float;
attribute vec3  a_Pos;
uniform mat4          u_MVP;
uniform mediump float u_WaterlineZ;
varying vec3   v_World;

void main() {
    vec3 p = a_Pos;

    // ── Seaward drape ─────────────────────────────────────────────────────────
    // Problem: the beach mesh Y is elevated above the ocean surface seaward of
    // the waterline, so the beach shader (not the ocean shader) renders those
    // fragments → visible grey band at the mesh boundary.
    //
    // Fix: sink the beach floor below the ocean surface when seaward of the
    // waterline so the ocean depth-test wins and renders cleanly in the
    // underwater zone. The beach only takes over at z ≥ waterlineZ.
    //
    // Ocean surface Y ≈ tideY (−0.7 … +0.7). Draping to −2.5 is always below it.
    float seaDepth = max(u_WaterlineZ - p.z, 0.0);     // > 0 seaward of waterline
    float drapeT   = clamp(seaDepth / 5.0, 0.0, 1.0);  // 0 at waterline, 1 at 5 m out
    p.y = mix(p.y, -2.5, drapeT * drapeT);             // quadratic: gentle near shore, sharp out

    v_World     = p;
    gl_Position = u_MVP * vec4(p, 1.0);
}
