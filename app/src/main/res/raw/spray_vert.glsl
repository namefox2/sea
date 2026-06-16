#if GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
attribute vec2  a_XZ;       // grid position (world XZ)
uniform mat4   u_MVP;
uniform float  u_Time;
uniform float  u_WindAmp;
uniform float  u_WindDir;
uniform float  u_Tide;
uniform float  u_WaterlineZ;

void main() {
    float amp = u_WindAmp * 0.30 + 0.04;

    // Simplified wave height at this XZ position
    vec2  wd = vec2(cos(u_WindDir), sin(u_WindDir));
    float k  = 6.28318 / 7.0;
    float ph = k * dot(wd, a_XZ) - 1.1 * u_Time;
    float wH = amp * sin(ph);
    float tY = u_Tide * 1.4 - 0.7;

    // Per-particle random values
    float h1  = fract(sin(dot(a_XZ, vec2(127.1, 311.7))) * 43758.5);
    float h2  = fract(sin(dot(a_XZ, vec2(269.5, 183.3))) * 43758.5);
    float lt  = 1.2 + h1 * 0.9;          // lifetime (s)
    float pT  = fract(u_Time / lt + h2);  // 0..1 within lifetime

    // Spawn only at wave crests, seaward of waterline, die in first 80% of lifetime
    float seaward = step(a_XZ.y, u_WaterlineZ + 1.5);  // 0 if on land/beach
    float active = step(amp * 0.25, wH) * step(pT, 0.8) * seaward;

    // Rise + slight horizontal drift
    float riseY  = pT * 0.85 * (0.6 + h1 * 0.6);
    float driftX = (h1 - 0.5) * 0.3;

    vec3 activePos   = vec3(a_XZ.x + driftX, tY + wH + riseY, a_XZ.y);
    vec3 inactivePos = vec3(0.0, -1000.0, 0.0);
    vec3 finalPos    = activePos * active + inactivePos * (1.0 - active);

    // Point size: larger near camera, fades at end of lifetime
    float camDist  = length(a_XZ - vec2(0.0, 18.0));
    float baseSize = mix(5.5, 1.0, clamp(camDist / 14.0, 0.0, 1.0));
    gl_PointSize   = baseSize * max(0.0, (0.65 - pT) * 2.5) * active;

    gl_Position = u_MVP * vec4(finalPos, 1.0);
}
