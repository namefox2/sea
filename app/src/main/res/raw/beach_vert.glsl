precision highp float;
attribute vec3  a_Pos;
uniform mat4          u_MVP;
uniform mediump float u_WaterlineZ;
varying vec3   v_World;
varying float v_DistToWater;

void main() {
    vec3 p = a_Pos;
    v_DistToWater = p.z - u_WaterlineZ;

    // Anticipatory drape: beach geometry sinks SEAWARD of the waterline so
    // the ocean mesh wins the depth test where it covers the beach.
    // Drape starts AT the waterline (not 3 m ahead) so the wave runup zone
    // (distToWater > 0) stays visible for the beach wave animation.
    float seaDepth = max(u_WaterlineZ - p.z, 0.0);
    float drapeT = smoothstep(0.0, 5.0, seaDepth);

    p.y = mix(p.y, p.y - 1.2, drapeT);

    v_World     = p;
    gl_Position = u_MVP * vec4(p, 1.0);
}
