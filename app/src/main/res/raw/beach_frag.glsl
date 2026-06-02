precision mediump float;
varying vec3  v_World;
varying float v_TideY;
uniform vec3  u_SandDry;
uniform vec3  u_SandWet;
uniform vec3  u_Horizon;
uniform float u_Time;

float ripple(float z, float x) {
    return 0.5 + 0.5 * sin(z * 2.2 + x * 0.4);
}

void main() {
    // Discard where beach is below waterline
    if (v_World.y < v_TideY - 0.02) discard;

    float distFromWater = v_World.y - v_TideY;
    float wetness = clamp(1.0 - distFromWater * 6.0, 0.0, 1.0);
    wetness = pow(wetness, 0.6);

    vec3 sand = mix(u_SandDry, u_SandWet, wetness);

    // Subtle sand ripple marks
    float rip = ripple(v_World.z * 0.7 + u_Time * 0.002, v_World.x);
    sand = mix(sand, sand * 0.85, rip * wetness * 0.35);

    // Sky reflection on wet sand near waterline
    sand = mix(sand, u_Horizon * 0.65, wetness * 0.32);

    gl_FragColor = vec4(sand, 1.0);
}
