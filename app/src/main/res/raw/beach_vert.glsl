precision highp float;
attribute vec3  a_Pos;
uniform mat4          u_MVP;
uniform mediump float u_WaterlineZ;
varying vec3   v_World;
varying float v_DistToWater;

void main() {
    vec3 p = a_Pos;
    v_DistToWater = p.z - u_WaterlineZ;

    // Drape starts 7 m LANDWARD of waterlineZ so the beach is already below ocean
    // level across the full alpha-fade zone (shoreNoise up to ±3 m → max extent ≈ 5 m).
    // Linear ramp (not smoothstep) guarantees enough Y-sink even at the far edge.
    float seaDepth = max(u_WaterlineZ + 7.0 - p.z, 0.0);
    float drapeT   = clamp(seaDepth / 10.0, 0.0, 1.0);
    p.y -= drapeT * 2.8;

    v_World     = p;
    gl_Position = u_MVP * vec4(p, 1.0);
}
