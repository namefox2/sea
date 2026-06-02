precision mediump float;
varying vec3  v_World;
uniform vec3  u_SandDry;
uniform vec3  u_SandWet;
uniform vec3  u_Horizon;
uniform float u_Time;
uniform float u_TidePercent;
uniform float u_WaterlineZ;

void main() {
    float distToWater = v_World.z - u_WaterlineZ;

    // Ocean renders where beach is below waterline
    if (distToWater < -0.5) discard;

    // Wetness: 1.0 right at waterline, 0.0 four meters up the beach
    float wetness = clamp(1.0 - distToWater / 4.0, 0.0, 1.0);
    wetness = wetness * wetness;

    vec3 sandColor = mix(u_SandDry, u_SandWet, wetness);

    // Wet sand reflects sky near waterline
    float reflStr = wetness * 0.32 * (1.0 - u_TidePercent * 0.6);
    sandColor = mix(sandColor, u_Horizon * 0.7, reflStr);

    // ── 갯벌 features (간조 only) ─────────────────────────────────────────
    float mudflatFactor = clamp(1.0 - u_TidePercent * 4.0, 0.0, 1.0);

    // Tidal pools: elongated oval shapes scattered on mudflat
    vec2  pUV   = v_World.xz * 0.4;
    vec2  pFrac = fract(pUV) - 0.5;
    float pool  = smoothstep(0.38, 0.30, length(vec2(pFrac.x, pFrac.y * 0.55)));

    vec2  pUV2   = v_World.xz * vec2(0.3, 0.7) + vec2(2.3, 1.7);
    vec2  pFrac2 = fract(pUV2) - 0.5;
    float pool2  = smoothstep(0.38, 0.30, length(vec2(pFrac2.x * 0.7, pFrac2.y)));

    float poolMask = max(pool, pool2) * mudflatFactor;
    sandColor = mix(sandColor, u_Horizon * 0.45, poolMask * 0.7);

    // Sand ripple marks (tidal channels)
    float ripple = sin(v_World.z * 12.0 + v_World.x * 0.8) * 0.5 + 0.5;
    ripple = pow(ripple, 8.0) * 0.18 * mudflatFactor;
    sandColor = mix(sandColor, u_SandWet * 0.7, ripple);

    // ── Animated waterline foam strip ─────────────────────────────────────
    float waveEdge = sin(v_World.x * 3.0 + u_Time * 2.2) * 0.3
                   + cos(v_World.x * 5.0 - u_Time * 1.8) * 0.15;
    float foamEdge = clamp(1.0 - (distToWater - waveEdge) / 0.8, 0.0, 1.0);
    // Fade right at the hard discard edge to avoid clipping artifact
    foamEdge *= smoothstep(-0.5, 0.0, distToWater);
    sandColor = mix(sandColor, vec3(0.95, 0.97, 1.0), foamEdge * 0.85);

    gl_FragColor = vec4(sandColor, 1.0);
}
