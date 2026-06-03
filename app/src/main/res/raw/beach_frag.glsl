precision mediump float;
varying vec3  v_World;
uniform vec3  u_SandDry;
uniform vec3  u_SandWet;
uniform vec3  u_Horizon;
uniform vec3  u_LightDir;
uniform float u_Time;
uniform float u_TidePercent;
uniform float u_WaterlineZ;
uniform vec3  u_CamPos;
uniform vec3  u_AmbientColor;

// ── Smooth value noise (bilinear hash, no grid artifacts) ─────────────────
float bN(vec2 p) {
    vec2 i = floor(p); vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = fract(sin(dot(i,               vec2(127.1, 311.7))) * 43758.5);
    float b = fract(sin(dot(i + vec2(1., 0.), vec2(127.1, 311.7))) * 43758.5);
    float c = fract(sin(dot(i + vec2(0., 1.), vec2(127.1, 311.7))) * 43758.5);
    float d = fract(sin(dot(i + vec2(1., 1.), vec2(127.1, 311.7))) * 43758.5);
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

// ── Micro-terrain height field: tidal ripples + organic grain ─────────────
float tidalH(vec2 p) {
    float h  = sin(p.y * 16.0 + p.x * 1.8) * 0.38 + 0.38;
    h       += sin(p.y *  9.5 - p.x * 2.4) * 0.18;
    h       += bN(p * 5.0) * 0.28 + bN(p * 11.0) * 0.14;
    return h * 0.020;
}

void main() {
    float distToWater = v_World.z - u_WaterlineZ;

    // Extended clip: -3.5 below waterline → shallow water zone rendered by beach
    if (distToWater < -3.5) discard;

    // Camera distance for LOD and light corridor
    vec2  toFragXZ = v_World.xz - u_CamPos.xz;
    float dCam     = max(length(toFragXZ), 0.01);

    // Shallow water factor: 0 at waterline, 1 at -3.5 below
    float shallowFactor = clamp(-distToWater / 3.0, 0.0, 1.0);

    // Wetness: 0 far above waterline, 1 right at waterline
    float wetness = clamp(1.0 - distToWater / 4.0, 0.0, 1.0);
    wetness = wetness * wetness;

    // ── Color palette ─────────────────────────────────────────────────────
    vec3 drySand        = vec3(0.76, 0.68, 0.52);
    vec3 wetSand        = vec3(0.42, 0.36, 0.26);
    vec3 mudflatShallow = vec3(0.28, 0.24, 0.18);
    vec3 mudflatDeep    = vec3(0.18, 0.15, 0.11);
    vec3 tidalPool      = vec3(0.12, 0.16, 0.22);

    drySand = mix(drySand, u_SandDry, 0.35);
    wetSand = mix(wetSand, u_SandWet, 0.25);

    float mudflatFactor = clamp(1.0 - u_TidePercent * 3.5, 0.0, 1.0);

    // ── Base color: sand → wet sand → mudflat ─────────────────────────────
    vec3 baseColor = drySand;
    baseColor = mix(baseColor, wetSand,        wetness);
    baseColor = mix(baseColor, mudflatShallow, mudflatFactor * 0.6);
    baseColor = mix(baseColor, mudflatDeep,    mudflatFactor * wetness);

    // ── Domain-Warped Mudflat System (간조 갯벌) ───────────────────────────
    // Only engage at low tide (performance: skip domain warp at high tide)
    if (mudflatFactor > 0.02) {
        vec2 p = v_World.xz * 0.22;

        // Domain warp: distort sampling coordinates for organic channel shapes
        vec2 w1 = vec2(bN(p), bN(p + vec2(5.2, 1.3)));
        vec2 wp = p + vec2(
            bN(p * 1.7 + w1 * 1.6 + vec2(3.7, 9.2)),
            bN(p * 1.7 + w1 * 1.6 + vec2(8.1, 2.8))
        ) * 2.6;

        // Tidal channels: dark curved lines where abs(sin) ≈ 0
        float ch1 = abs(sin(wp.y * 3.2 + wp.x * 0.5));
        float ch2 = abs(sin(wp.y * 2.1 - wp.x * 0.9 + 1.7));
        float channelMask = 1.0 - smoothstep(0.0, 0.18, min(ch1, ch2));

        // Height field: micro-topography — low spots trap water
        float mudH  = bN(wp * 2.1) * 0.55 + bN(wp * 5.0) * 0.45;
        float mudLow = clamp(0.55 - mudH, 0.0, 1.0);
        mudLow = mudLow * mudLow * 2.0;

        // LOD: suppress grain at distance
        float lod   = clamp(dCam / 14.0, 0.0, 1.0);
        float grain = mix(bN(v_World.xz * 17.0), bN(v_World.xz * 7.0), lod);

        // Channel color: dark with water tint
        vec3 channelColor = mix(mudflatDeep, tidalPool, 0.4);

        // Apply mudflat: channels darken, low spots wet, grain adds micro-detail
        baseColor = mix(baseColor, channelColor,   channelMask * mudflatFactor * 0.65);
        baseColor = mix(baseColor, mudflatShallow * 0.7, mudLow * mudflatFactor * 0.40);
        float grainDelta = (grain * 0.20 - 0.10) * mudflatFactor * (1.0 - lod * 0.8);
        baseColor += vec3(grainDelta);
    }

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

    // ── Sand/mud ripple texture ───────────────────────────────────────────
    float ripple = pow(sin(v_World.z * 9.0 + v_World.x * 1.2) * 0.5 + 0.5, 6.0)
                 * pow(sin(v_World.z * 14.0 - v_World.x * 0.7) * 0.5 + 0.5, 4.0);
    baseColor = mix(baseColor, mudflatDeep, ripple * 0.22 * mudflatFactor);
    float sandRipple = pow(sin(v_World.z * 6.0 + v_World.x * 2.0) * 0.5 + 0.5, 8.0);
    baseColor = mix(baseColor, drySand * 0.85, sandRipple * 0.15 * (1.0 - mudflatFactor));

    // ── Wet surface sky reflection with light corridor ────────────────────
    vec2  lhDir    = normalize(vec2(u_LightDir.x, u_LightDir.z));
    vec2  perp2    = vec2(-lhDir.y, lhDir.x);
    float pDist    = abs(dot(toFragXZ, perp2));
    float corrW    = mix(0.5, 6.0, clamp(dCam / 18.0, 0.0, 1.0));
    float corrMask = exp(-pDist * pDist / (corrW * corrW));

    float wetSpec = pow(sin(v_World.z * 3.5 + u_Time * 0.4) * 0.5 + 0.5, 5.0)
                  * pow(sin(v_World.x * 1.8 - u_Time * 0.2) * 0.5 + 0.5, 3.0);
    float reflStr = wetness * (0.12 + wetSpec * 0.30 * corrMask) * (1.0 - u_TidePercent * 0.5);
    baseColor = mix(baseColor, u_Horizon * 0.65, reflStr);

    // ── Rocky outcrops (간조 시 바위) ──────────────────────────────────────
    float rockFactor = clamp(1.0 - u_TidePercent * 5.0, 0.0, 1.0);
    float rockNoise  = step(0.72, fract(sin(dot(floor(v_World.xz * 0.4), vec2(127.1, 311.7))) * 43758.5));
    baseColor = mix(baseColor, vec3(0.22, 0.20, 0.18), rockNoise * rockFactor * 0.7);

    // ── Micro-terrain self-shadowing (above-water only for performance) ───
    if (shallowFactor < 0.95) {
        const float E = 0.05;
        float h0 = tidalH(v_World.xz);
        float hX = tidalH(v_World.xz + vec2(E, 0.0));
        float hZ = tidalH(v_World.xz + vec2(0.0, E));
        vec3 terrN = normalize(vec3((h0 - hX) / E, 1.0, (h0 - hZ) / E));
        float NdotL = clamp(dot(terrN, normalize(u_LightDir)), 0.0, 1.0);
        float shadowStr = mix(0.38, 0.65, mudflatFactor);
        float shadowBlend = 1.0 - shallowFactor;  // fade out shadow for underwater
        baseColor *= mix(1.0, NdotL * shadowStr + (1.0 - shadowStr), shadowBlend);
        baseColor += u_AmbientColor * (1.0 - NdotL) * shadowStr * 0.18 * shadowBlend;
    }

    // ── Shallow water zone: silty floor visible through ocean wave troughs ─
    // (distToWater < 0: beach renders as seafloor under shallow water)
    if (shallowFactor > 0.001) {
        // Sandy/silty seafloor color, darkens with depth
        vec3 seafloor  = mix(wetSand * 0.65, mudflatShallow * 0.7, shallowFactor * 0.6);
        // Water column tint (teal shallow → deep blue)
        vec3 waterTint = mix(vec3(0.14, 0.44, 0.38), vec3(0.06, 0.18, 0.30), shallowFactor);
        // Caustic shimmer
        float caust = sin(v_World.x * 3.8 + u_Time * 1.4) * sin(v_World.z * 4.3 - u_Time * 1.1);
        caust = pow(max(caust * 0.5 + 0.62, 0.0), 3.0) * 0.06;
        vec3 shallowColor = mix(seafloor, waterTint, shallowFactor * 0.72) + waterTint * caust;
        // Ramp very fast: even slightly underwater looks like water, not sand
        float shallowBlend = smoothstep(0.0, 0.30, shallowFactor) * 0.98;
        baseColor = mix(baseColor, shallowColor, shallowBlend);
    }

    // ── Waterline transition: Runup → Foam → Wet Sand ─────────────────────
    // Swash foam (wave-animated, only above waterline)
    float waveEdge = sin(v_World.x * 3.0 + u_Time * 2.2) * 0.35
                   + cos(v_World.x * 5.0 - u_Time * 1.8) * 0.18;
    float foamDist = distToWater - waveEdge;
    float foamEdge = clamp(1.0 - abs(foamDist) / 0.65, 0.0, 1.0);
    foamEdge = foamEdge * foamEdge * foamEdge;
    foamEdge *= smoothstep(-0.8, 0.15, distToWater);  // fade out below waterline
    baseColor = mix(baseColor, vec3(0.95, 0.97, 1.0), foamEdge * 0.88);

    // ── Atmospheric fog ───────────────────────────────────────────────────
    float fogZ    = max(18.0 - v_World.z, 0.0);
    float fogFact = clamp(1.0 - exp(-0.006 * fogZ * fogZ), 0.0, 0.50);
    baseColor = mix(baseColor, u_Horizon * 0.82, fogFact);

    gl_FragColor = vec4(baseColor, 1.0);
}
