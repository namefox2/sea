precision mediump float;
varying vec3  v_World;
uniform vec3  u_SandDry;
uniform vec3  u_SandWet;
uniform vec3  u_Horizon;
uniform vec3  u_LightDir;
uniform float u_Time;
uniform float u_TidePercent;
uniform float u_WaterlineZ;

// Smooth value noise: bilinear hash interpolation (no grid artifacts)
float bN(vec2 p) {
    vec2 i = floor(p); vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = fract(sin(dot(i,               vec2(127.1, 311.7))) * 43758.5);
    float b = fract(sin(dot(i + vec2(1., 0.), vec2(127.1, 311.7))) * 43758.5);
    float c = fract(sin(dot(i + vec2(0., 1.), vec2(127.1, 311.7))) * 43758.5);
    float d = fract(sin(dot(i + vec2(1., 1.), vec2(127.1, 311.7))) * 43758.5);
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

// Micro-terrain height field: tidal ripples + organic grain
float tidalH(vec2 p) {
    float h  = sin(p.y * 16.0 + p.x * 1.8) * 0.38 + 0.38; // primary tidal ripple
    h       += sin(p.y *  9.5 - p.x * 2.4) * 0.18;          // cross ripple
    h       += bN(p * 5.0) * 0.28 + bN(p * 11.0) * 0.14;    // organic grain
    return h * 0.020;
}

void main() {
    float distToWater = v_World.z - u_WaterlineZ;

    if (distToWater < -0.5) discard;

    float wetness = clamp(1.0 - distToWater / 4.0, 0.0, 1.0);
    wetness = wetness * wetness;

    // ── 갯벌 color palette ────────────────────────────────────────────────
    vec3 drySand        = vec3(0.76, 0.68, 0.52);
    vec3 wetSand        = vec3(0.42, 0.36, 0.26);
    vec3 mudflatShallow = vec3(0.28, 0.24, 0.18);
    vec3 mudflatDeep    = vec3(0.18, 0.15, 0.11);
    vec3 tidalPool      = vec3(0.12, 0.16, 0.22);

    drySand = mix(drySand, u_SandDry, 0.35);
    wetSand = mix(wetSand, u_SandWet, 0.25);

    float mudflatFactor = clamp(1.0 - u_TidePercent * 3.5, 0.0, 1.0);

    vec3 baseColor = drySand;
    baseColor = mix(baseColor, wetSand,        wetness);
    baseColor = mix(baseColor, mudflatShallow, mudflatFactor * 0.6);
    baseColor = mix(baseColor, mudflatDeep,    mudflatFactor * wetness);

    // ── Tidal pool puddles ────────────────────────────────────────────────
    float px1   = fract(v_World.x * 0.18 + 0.3);
    float pz1   = fract(v_World.z * 0.22 + 0.1);
    float pool1 = smoothstep(0.38, 0.30, length(vec2(px1 - 0.5, (pz1 - 0.5) * 1.6)));
    float px2   = fract(v_World.x * 0.28 - 0.7);
    float pz2   = fract(v_World.z * 0.14 + 0.5);
    float pool2 = smoothstep(0.35, 0.27, length(vec2((px2 - 0.5) * 1.4, pz2 - 0.5)));
    float poolMask    = max(pool1, pool2) * mudflatFactor;
    vec3  poolReflect = mix(tidalPool, u_Horizon * 0.4, 0.5);
    baseColor = mix(baseColor, poolReflect, poolMask * 0.85);

    // ── Mud/sand ripple texture ───────────────────────────────────────────
    float ripple = pow(sin(v_World.z * 9.0 + v_World.x * 1.2) * 0.5 + 0.5, 6.0)
                 * pow(sin(v_World.z * 14.0 - v_World.x * 0.7) * 0.5 + 0.5, 4.0);
    baseColor = mix(baseColor, mudflatDeep, ripple * 0.22 * mudflatFactor);
    float sandRipple = pow(sin(v_World.z * 6.0 + v_World.x * 2.0) * 0.5 + 0.5, 8.0);
    baseColor = mix(baseColor, drySand * 0.85, sandRipple * 0.15 * (1.0 - mudflatFactor));

    // ── Wet surface sky reflection ────────────────────────────────────────
    float wetSpec = pow(sin(v_World.z * 3.5 + u_Time * 0.4) * 0.5 + 0.5, 5.0)
                  * pow(sin(v_World.x * 1.8 - u_Time * 0.2) * 0.5 + 0.5, 3.0);
    float reflStr = wetness * (0.28 + wetSpec * 0.30) * (1.0 - u_TidePercent * 0.5);
    baseColor = mix(baseColor, u_Horizon * 0.65, reflStr);

    // ── Rocky outcrops ────────────────────────────────────────────────────
    float rockFactor = clamp(1.0 - u_TidePercent * 5.0, 0.0, 1.0);
    vec3  rockColor  = vec3(0.22, 0.20, 0.18);
    float rockNoise  = step(0.72, fract(sin(dot(floor(v_World.xz * 0.4), vec2(127.1, 311.7))) * 43758.5));
    baseColor = mix(baseColor, rockColor, rockNoise * rockFactor * 0.7);

    // ── Micro-terrain self-shadowing ─────────────────────────────────────
    // Finite-difference normal from procedural height field
    const float E = 0.05;
    float h0 = tidalH(v_World.xz);
    float hX = tidalH(v_World.xz + vec2(E, 0.0));
    float hZ = tidalH(v_World.xz + vec2(0.0, E));
    vec3 terrN = normalize(vec3((h0 - hX) / E, 1.0, (h0 - hZ) / E));

    float NdotL = clamp(dot(terrN, normalize(u_LightDir)), 0.0, 1.0);
    // 갯벌(간조)일수록 그림자가 강하게, 마른 모래는 부드럽게
    float shadowStr = mix(0.38, 0.65, mudflatFactor);
    baseColor *= NdotL * shadowStr + (1.0 - shadowStr);

    // ── Waterline foam strip ──────────────────────────────────────────────
    float waveEdge = sin(v_World.x * 3.0 + u_Time * 2.2) * 0.3
                   + cos(v_World.x * 5.0 - u_Time * 1.8) * 0.15;
    float foamEdge = clamp(1.0 - (distToWater - waveEdge) / 0.8, 0.0, 1.0);
    foamEdge *= smoothstep(-0.5, 0.0, distToWater);
    baseColor = mix(baseColor, vec3(0.95, 0.97, 1.0), foamEdge * 0.85);

    // ── Atmospheric fog ───────────────────────────────────────────────────
    float fogZ    = max(18.0 - v_World.z, 0.0);
    float fogFact = clamp(1.0 - exp(-0.006 * fogZ * fogZ), 0.0, 0.50);
    baseColor = mix(baseColor, u_Horizon * 0.82, fogFact);

    gl_FragColor = vec4(baseColor, 1.0);
}
