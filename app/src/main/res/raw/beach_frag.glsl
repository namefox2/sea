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

    // ── 갯벌 color palette (Korean tidal flat) ────────────────────────────
    vec3 drySand        = vec3(0.76, 0.68, 0.52);   // warm tan dry sand
    vec3 wetSand        = vec3(0.42, 0.36, 0.26);   // dark tan, recently wet
    vec3 mudflatShallow = vec3(0.28, 0.24, 0.18);   // gray-brown mud
    vec3 mudflatDeep    = vec3(0.18, 0.15, 0.11);   // very dark wet mud
    vec3 tidalPool      = vec3(0.12, 0.16, 0.22);   // dark blue-gray standing water

    // Blend theme sand color with hardcoded palette (theme can tint dry sand)
    drySand = mix(drySand, u_SandDry, 0.35);
    wetSand = mix(wetSand, u_SandWet, 0.25);

    // mudflatFactor: 1.0 at tide=0 (full갯벌), 0.0 above tide=0.28
    float mudflatFactor = clamp(1.0 - u_TidePercent * 3.5, 0.0, 1.0);

    vec3 baseColor = drySand;
    baseColor = mix(baseColor, wetSand,         wetness);
    baseColor = mix(baseColor, mudflatShallow,  mudflatFactor * 0.6);
    baseColor = mix(baseColor, mudflatDeep,     mudflatFactor * wetness);

    // ── Tidal pool puddles (irregular oval) ──────────────────────────────
    float px1  = fract(v_World.x * 0.18 + 0.3);
    float pz1  = fract(v_World.z * 0.22 + 0.1);
    float pool1 = smoothstep(0.38, 0.30,
                     length(vec2(px1 - 0.5, (pz1 - 0.5) * 1.6)));

    float px2  = fract(v_World.x * 0.28 - 0.7);
    float pz2  = fract(v_World.z * 0.14 + 0.5);
    float pool2 = smoothstep(0.35, 0.27,
                     length(vec2((px2 - 0.5) * 1.4, pz2 - 0.5)));

    float poolMask = max(pool1, pool2) * mudflatFactor;
    vec3  poolReflect = mix(tidalPool, u_Horizon * 0.4, 0.5);
    baseColor = mix(baseColor, poolReflect, poolMask * 0.85);

    // ── Mud/sand ripple texture ───────────────────────────────────────────
    // Tidal ripple marks on mudflat
    float ripple = pow(sin(v_World.z * 9.0 + v_World.x * 1.2) * 0.5 + 0.5, 6.0)
                 * pow(sin(v_World.z * 14.0 - v_World.x * 0.7) * 0.5 + 0.5, 4.0);
    baseColor = mix(baseColor, mudflatDeep, ripple * 0.22 * mudflatFactor);
    // Subtle dry sand ripples
    float sandRipple = pow(sin(v_World.z * 6.0 + v_World.x * 2.0) * 0.5 + 0.5, 8.0);
    baseColor = mix(baseColor, drySand * 0.85, sandRipple * 0.15 * (1.0 - mudflatFactor));

    // ── Wet surface sky reflection near waterline ─────────────────────────
    // Specular-like glint: sun stripe across wet sand
    float wetSpec = pow(sin(v_World.z * 3.5 + u_Time * 0.4) * 0.5 + 0.5, 5.0)
                  * pow(sin(v_World.x * 1.8 - u_Time * 0.2) * 0.5 + 0.5, 3.0);
    float reflStr = wetness * (0.28 + wetSpec * 0.30) * (1.0 - u_TidePercent * 0.5);
    baseColor = mix(baseColor, u_Horizon * 0.65, reflStr);

    // ── Rocky outcrops (간조 only: hash-placed stones) ────────────────────
    float rockFactor = clamp(1.0 - u_TidePercent * 5.0, 0.0, 1.0);
    vec3  rockColor  = vec3(0.22, 0.20, 0.18);
    float rockNoise  = step(0.72,
        fract(sin(dot(floor(v_World.xz * 0.4), vec2(127.1, 311.7))) * 43758.5));
    baseColor = mix(baseColor, rockColor, rockNoise * rockFactor * 0.7);

    // ── Animated waterline foam strip ─────────────────────────────────────
    float waveEdge = sin(v_World.x * 3.0 + u_Time * 2.2) * 0.3
                   + cos(v_World.x * 5.0 - u_Time * 1.8) * 0.15;
    float foamEdge = clamp(1.0 - (distToWater - waveEdge) / 0.8, 0.0, 1.0);
    foamEdge *= smoothstep(-0.5, 0.0, distToWater);
    baseColor = mix(baseColor, vec3(0.95, 0.97, 1.0), foamEdge * 0.85);

    // ── Atmospheric distance fog (camera at Z≈18, exponential²) ─────────────
    float fogZ    = max(18.0 - v_World.z, 0.0);
    float fogFact = clamp(1.0 - exp(-0.006 * fogZ * fogZ), 0.0, 0.50);
    baseColor = mix(baseColor, u_Horizon * 0.82, fogFact);

    gl_FragColor = vec4(baseColor, 1.0);
}
