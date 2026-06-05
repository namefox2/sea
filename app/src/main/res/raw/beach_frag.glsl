precision mediump float;
varying vec3  v_World;
uniform vec3  u_SandDry;
uniform vec3  u_SandWet;
uniform vec3  u_Horizon;
uniform vec3  u_LightDir;
uniform float u_Time;
uniform float u_TidePercent;
uniform float u_WaterlineZ;
uniform float u_WindAmp;
uniform float u_MudflatScale; // 1.0=서해(full 갯벌) 0.35=남해 0.0=동해/제주
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

    // Beach renders up to 3 units seaward of the waterline for the shallow entry zone.
    if (distToWater < -3.0) discard;

    // Camera distance for LOD and light corridor
    vec2  toFragXZ = v_World.xz - u_CamPos.xz;
    float dCam     = max(length(toFragXZ), 0.01);

    // Shallow water factor: 0 at waterline, 1 at -3m
    float shallowFactor = clamp(-distToWater / 3.0, 0.0, 1.0);

    // Wetness: strongest at waterline, fades over 12m of exposed tidal flat.
    // The freshly-exposed mudflat stays dark/wet; drier further from the water.
    float wetness = clamp(1.0 - distToWater / 12.0, 0.0, 1.0);
    wetness = wetness * wetness;

    // ── Color palette ─────────────────────────────────────────────────────
    vec3 drySand        = vec3(0.76, 0.68, 0.52);
    vec3 wetSand        = vec3(0.42, 0.36, 0.26);
    vec3 mudflatShallow = vec3(0.28, 0.24, 0.18);
    vec3 mudflatDeep    = vec3(0.18, 0.15, 0.11);
    vec3 tidalPool      = vec3(0.12, 0.16, 0.22);

    drySand = mix(drySand, u_SandDry, 0.35);
    wetSand = mix(wetSand, u_SandWet, 0.25);

    // Mudflat: appears only on coasts with significant tidal range (서해).
    // u_MudflatScale = 0 for 동해/제주 (no tidal flats), 1 for 서해 (full).
    float mudflatFactor = clamp(1.0 - u_TidePercent * 2.0, 0.0, 1.0) * u_MudflatScale;

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

        // Tidal pools in natural low-lying areas — world-space noise placement,
        // no fract() grid so no rectangular cell boundaries.
        float poolNear = clamp(1.0 - distToWater / 20.0, 0.0, 1.0);
        float poolMask = mudLow * mudflatFactor * poolNear * 0.72;
        vec3  poolReflect = mix(tidalPool, u_Horizon * 0.4, 0.5);
        baseColor = mix(baseColor, poolReflect, poolMask);
    }

    // ── Sand/mud ripple texture — oblique single-direction waves, no sin×sin grid ──
    // Mudflat ripples: single diagonal direction avoids a rectangular bright-spot grid.
    float ripple = pow(sin(v_World.z * 9.0 + v_World.x * 2.3) * 0.5 + 0.5, 5.0)
                 * (0.55 + 0.45 * bN(v_World.xz * 0.55 + vec2(4.1, 2.7)));
    baseColor = mix(baseColor, mudflatDeep, ripple * 0.20 * mudflatFactor);
    // Dry sand ripples: noise-modulated oblique wave, no perpendicular interference.
    float sandRipple = pow(sin(v_World.z * 6.0 + v_World.x * 1.8) * 0.5 + 0.5, 7.0)
                     * (0.5 + 0.5 * bN(v_World.xz * 0.38));
    baseColor = mix(baseColor, drySand * 0.85, sandRipple * 0.14 * (1.0 - mudflatFactor));

    // ── Wet surface sky reflection with light corridor ────────────────────
    vec2  lhDir    = normalize(vec2(u_LightDir.x, u_LightDir.z));
    vec2  perp2    = vec2(-lhDir.y, lhDir.x);
    float pDist    = abs(dot(toFragXZ, perp2));
    float corrW    = mix(0.5, 6.0, clamp(dCam / 18.0, 0.0, 1.0));
    float corrMask = exp(-pDist * pDist / (corrW * corrW));

    // Single oblique wave × smooth noise — avoids the sin(z)×sin(x) rectangular grid.
    float wetSpec = pow(sin(v_World.z * 3.5 + v_World.x * 1.1 + u_Time * 0.4) * 0.5 + 0.5, 5.0)
                  * (0.50 + 0.50 * bN(v_World.xz * 0.72 + u_Time * 0.05));
    float reflStr = wetness * (0.12 + wetSpec * 0.28 * corrMask) * (1.0 - u_TidePercent * 0.5);
    baseColor = mix(baseColor, u_Horizon * 0.65, reflStr);

    // ── Rocky outcrops (간조 시 바위) — smooth noise, no floor() grid ────────
    float rockFactor = clamp(1.0 - u_TidePercent * 5.0, 0.0, 1.0);
    float rockNoise  = smoothstep(0.62, 0.80,
        bN(v_World.xz * 0.30 + vec2(7.3, 2.1)) * bN(v_World.xz * 0.18 + vec2(3.5, 8.8)));
    baseColor = mix(baseColor, vec3(0.22, 0.20, 0.18), rockNoise * rockFactor * 0.65);

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

    // ── Shoaling water entry — matches ocean shader's shoreAqua exactly ─────
    if (shallowFactor > 0.001) {
        vec3 waterTint = vec3(0.45, 0.74, 0.72);
        float caust = sin(v_World.x * 3.8 + u_Time * 1.4) * sin(v_World.z * 4.3 - u_Time * 1.1);
        caust = pow(max(caust * 0.5 + 0.62, 0.0), 3.0) * (1.0 - shallowFactor * 0.85) * 0.14;
        waterTint += waterTint * caust;
        float shallowBlend = smoothstep(0.0, 0.25, shallowFactor) * 0.95;
        baseColor = mix(baseColor, waterTint, shallowBlend);
    }

    // ── Waterline transition: irregular swash foam patches ───────────────
    // Multi-frequency noise sets where the foam tongue reaches along X at each moment.
    // Three octaves: large slow tongues + medium + fine ripple detail.
    float fA = bN(vec2(v_World.x * 0.10, u_Time * 0.12))                          * 1.60;
    float fB = bN(vec2(v_World.x * 0.32, u_Time * 0.18) + vec2(3.1, 1.7))        * 0.80;
    float fC = bN(vec2(v_World.x * 0.70, u_Time * 0.25) + vec2(8.3, 5.2))        * 0.35;
    float foamScale = 1.0 + u_WindAmp * 1.5;
    float foamReach = (fA + fB + fC) / 2.75 * 2.6 * foamScale;

    // Foam band: centred where distToWater ≈ foamReach, half-width ~0.85m.
    float foamFront = 1.0 - smoothstep(0.0, 0.85, abs(distToWater - foamReach));
    foamFront *= smoothstep(-0.3, 0.2, distToWater);  // suppress below waterline

    // Patch mask: two drifting bN fields break the band into disconnected blobs.
    float pA = bN(vec2(v_World.x * 0.38 + u_Time * 0.07, v_World.z * 0.38 - u_Time * 0.04));
    float pB = bN(vec2(v_World.x * 0.22 - u_Time * 0.05, v_World.z * 0.22 + u_Time * 0.03));
    float patchThresh = mix(0.30, 0.18, u_WindAmp);
    float patchMask   = smoothstep(patchThresh, 0.62, pA * 0.55 + pB * 0.45);

    float foamBlend = clamp(foamFront * patchMask, 0.0, 1.0);
    baseColor = mix(baseColor, vec3(0.94, 0.97, 1.00), foamBlend * 0.88);

    // ── Atmospheric fog — linear exponential, scales to 100m depth ───────
    float fogZ    = max(18.0 - v_World.z, 0.0);
    float fogFact = clamp(1.0 - exp(-fogZ * 0.008), 0.0, 0.45);
    baseColor = mix(baseColor, u_Horizon * 0.82, fogFact);

    gl_FragColor = vec4(baseColor, 1.0);
}
