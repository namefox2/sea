precision highp float;
attribute vec3  a_Pos;
uniform mat4          u_MVP;
uniform mediump float u_WaterlineZ;
varying vec3   v_World;
varying float v_DistToWater;

void main() {
    vec3 p = a_Pos;
    v_DistToWater = p.z - u_WaterlineZ;

    // Anticipatory drape: beach geometry starts sinking 3 m AHEAD of the waterline
    // so the animated waterlineZ can advance that far inland — ocean wins depth
    // test wherever beach has sunk below the water surface.
    // Linear ramp (not quadratic) gives enough Y drop for ocean to win even 1-2 m
    // in front of the current waterlineZ.  Drape target -3.0 is well below any wave.
    float seaDepth =
        max(u_WaterlineZ + 3.0 - p.z, 0.0);

    float drapeT = smoothstep(0.0, 3.0, seaDepth);

    p.y = mix(p.y, p.y - 1.2, drapeT);

    v_World     = p;
    gl_Position = u_MVP * vec4(p, 1.0);
}
