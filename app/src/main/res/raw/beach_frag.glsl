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
uniform float u_MudflatExposure; // 0..1 pre-computed in Kotlin
uniform vec3  u_CamPos;
uniform vec3  u_AmbientColor;
uniform float u_Wetness;
varying float v_DistToWater;

// ── Smooth value noise ─────────────────────────────────────────────────────
float bN(vec2 p) {
    vec2 i = floor(p); vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = fract(sin(dot(i,               vec2(127.1, 311.7))) * 43758.5);
    float b = fract(sin(dot(i + vec2(1., 0.), vec2(127.1, 311.7))) * 43758.5);
    float c = fract(sin(dot(i + vec2(0., 1.), vec2(127.1, 311.7))) * 43758.5);
    float d = fract(sin(dot(i + vec2(1., 1.), vec2(127.1, 311.7))) * 43758.5);
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

// ── Micro-terrain height field ─────────────────────────────────────────────
float tidalH(vec2 p) {
    float h  = sin(p.y * 16.0 + p.x * 1.8) * 0.38 + 0.38;
    h       += sin(p.y *  9.5 - p.x * 2.4) * 0.18;
    h       += bN(p * 5.0) * 0.28 + bN(p * 11.0) * 0.14;
    return h * 0.020;
}

void main() {
    // distToWater: > 0 = landward (exposed), < 0 = seaward (beach draped below ocean)
    float distToWater = v_DistToWater;

    // The vertex shader drapes the beach floor below the ocean surface for
    // distToWater < 0, so those fragments lose the depth test and the ocean
    // renders there. We still discard very far fragments to skip fragment work.
    if (distToWater < -8.0) discard;

    vec2  toFragXZ = v_World.xz - u_CamPos.xz;
    float dCam     = max(length(toFragXZ), 0.01);

    // Wetness: 1.0 at waterline, 0.0 at 12 m inland

    // ── Color palette ─────────────────────────────────────────────────────────
    vec3 drySand        = vec3(0.76, 0.68, 0.52);
    vec3 wetSand        = vec3(0.42, 0.36, 0.26);
    vec3 mudflatShallow = vec3(0.28, 0.24, 0.18);
    vec3 mudflatDeep    = vec3(0.18, 0.15, 0.11);
    vec3 tidalPool      = vec3(0.12, 0.16, 0.22);

    drySand = mix(drySand, u_SandDry, 0.35);
    wetSand = mix(wetSand, u_SandWet, 0.25);

    // Mudflat: fades to zero near/at the waterline, zero underwater
    float mudflatFactor = u_MudflatExposure * smoothstep(-1.5, 2.0, distToWater);

    // ── Base color: sand → wet → mudflat ──────────────────────────────────────
    vec3 baseColor = drySand;
    baseColor = mix(baseColor, wetSand, u_Wetness);
    baseColor = mix(baseColor, mudflatShallow, mudflatFactor * 0.6);
    baseColor = mix(baseColor, mudflatDeep,    mudflatFactor * u_Wetness);

    // ── Domain-warped mudflat system (간조 갯벌) ───────────────────────────────
    if (mudflatFactor > 0.02) {
        vec2 p = v_World.xz * 0.22;
        vec2 w1 = vec2(bN(p), bN(p + vec2(5.2, 1.3)));
        vec2 wp = p + vec2(
            bN(p * 1.7 + w1 * 1.6 + vec2(3.7, 9.2)),
            bN(p * 1.7 + w1 * 1.6 + vec2(8.1, 2.8))
        ) * 2.6;

        float ch1 = abs(sin(wp.y * 3.2 + wp.x * 0.5));
        float ch2 = abs(sin(wp.y * 2.1 - wp.x * 0.9 + 1.7));
        float channelMask = 1.0 - smoothstep(0.0, 0.18, min(ch1, ch2));

        float mudH   = bN(wp * 2.1) * 0.55 + bN(wp * 5.0) * 0.45;
        float mudLow = clamp(0.55 - mudH, 0.0, 1.0);
        mudLow = mudLow * mudLow * 2.0;

        float lod   = clamp(dCam / 14.0, 0.0, 1.0);
        float grain = mix(bN(v_World.xz * 17.0), bN(v_World.xz * 7.0), lod);

        vec3 channelColor = mix(mudflatDeep, tidalPool, 0.4);
        baseColor = mix(baseColor, channelColor,         channelMask * mudflatFactor * 0.65);
        baseColor = mix(baseColor, mudflatShallow * 0.7, mudLow * mudflatFactor * 0.40);
        baseColor += vec3((grain * 0.20 - 0.10) * mudflatFactor * (1.0 - lod * 0.8));

        float poolNear = smoothstep(6.0, 0.0, distToWater);
        float poolMask   = mudLow * mudflatFactor * poolNear * 0.72;
        baseColor = mix(baseColor, mix(tidalPool, u_Horizon * 0.4, 0.5), poolMask);
    }

    // ── Sand/mud ripple textures ───────────────────────────────────────────────
    float ripple = pow(sin(v_World.z * 9.0 + v_World.x * 2.3) * 0.5 + 0.5, 5.0)
                 * (0.55 + 0.45 * bN(v_World.xz * 0.55 + vec2(4.1, 2.7)));
    baseColor = mix(baseColor, mudflatDeep, ripple * 0.20 * mudflatFactor);
    float sandRipple = pow(sin(v_World.z * 6.0 + v_World.x * 1.8) * 0.5 + 0.5, 7.0)
                     * (0.5 + 0.5 * bN(v_World.xz * 0.38));
    baseColor = mix(baseColor, drySand * 0.85, sandRipple * 0.14 * (1.0 - mudflatFactor));

    // ── Wet surface sky reflection ─────────────────────────────────────────────
    vec2  lhDir    = normalize(vec2(u_LightDir.x, u_LightDir.z));
    vec2  perp2    = vec2(-lhDir.y, lhDir.x);
    float pDist    = abs(dot(toFragXZ, perp2));
    float corrW    = mix(0.5, 6.0, clamp(dCam / 18.0, 0.0, 1.0));
    float corrMask = exp(-pDist * pDist / (corrW * corrW));
    float wetSpec  = pow(sin(v_World.z * 3.5 + v_World.x * 1.1 + u_Time * 0.4) * 0.5 + 0.5, 5.0)
                   * (0.50 + 0.50 * bN(v_World.xz * 0.72 + u_Time * 0.05));

    // ── Rocky outcrops ─────────────────────────────────────────────────────────
    float rockFactor = clamp(1.0 - u_TidePercent * 5.0, 0.0, 1.0);
    float rockNoise  = smoothstep(0.62, 0.80,
        bN(v_World.xz * 0.30 + vec2(7.3, 2.1)) * bN(v_World.xz * 0.18 + vec2(3.5, 8.8)));
    baseColor = mix(baseColor, vec3(0.22, 0.20, 0.18), rockNoise * rockFactor * 0.65);

    // ── Micro-terrain self-shadowing (exposed surface only) ───────────────────
    if (distToWater > -1.5) {
        const float E = 0.05;
        float h0 = tidalH(v_World.xz);
        float hX = tidalH(v_World.xz + vec2(E, 0.0));
        float hZ = tidalH(v_World.xz + vec2(0.0, E));
        vec3  terrN = normalize(vec3((h0 - hX) / E, 1.0, (h0 - hZ) / E));
        float NdotL = clamp(dot(terrN, normalize(u_LightDir)), 0.0, 1.0);
        float shadowStr   = mix(0.38, 0.65, mudflatFactor);
        float shadowBlend = smoothstep(-1.5, 0.0, distToWater);
        baseColor *= mix(1.0, NdotL * shadowStr + (1.0 - shadowStr), shadowBlend);
        baseColor += u_AmbientColor * (1.0 - NdotL) * shadowStr * 0.18 * shadowBlend;
    }
    vec3 waterTint = vec3(0.45, 0.74, 0.72);
    // ── Shore aqua: match ocean's shore-transition color at the waterline ──────
    // The ocean shader blends to vec3(0.45, 0.74, 0.72) near the waterline.
    // The beach must show the same color at z = waterlineZ so the geometry seam
    // is invisible — the two meshes hand off at the same hue, not a colour jump.
    // Fades to 0 by 5 m inland so natural sand/mudflat colours take over.
    //vec3  shoreAqua  = vec3(0.45, 0.74, 0.72);
    //float shoreBlend = smoothstep(5.0, 0.0, distToWater) * 0.78;
    //baseColor = mix(baseColor, shoreAqua, shoreBlend);

    float shoreBlend = smoothstep(0.5, -0.5, distToWater);
    baseColor = mix(baseColor, waterTint, shoreBlend * 0.08);

    // ── Caustics ──────────────────────────────────────────────────────────────
    float caust = sin(v_World.x * 3.8 + u_Time * 1.4) * sin(v_World.z * 4.3 - u_Time * 1.1);
    caust = pow(max(caust * 0.5 + 0.62, 0.0), 3.0) * 0.14;

    // ── Wave cycle: noise drives how far each wave runs up the beach ──────────
    // Different x-frequencies per octave → angled, organic wave shapes
    float wA = bN(vec2(v_World.x * 0.08, u_Time * 0.10))                         * 1.80;
    float wB = bN(vec2(v_World.x * 0.24, u_Time * 0.16) + vec2(3.1, 1.7))        * 0.85;
    float wC = bN(vec2(v_World.x * 0.55, u_Time * 0.23) + vec2(8.3, 5.2))        * 0.38;
    float foamScale = 1.0 + u_WindAmp * 1.6;
    float waveReach = (wA + wB + wC) / 3.03 * (5.0 + u_WindAmp*5.0);

    // distToWave > 0: wave has already passed (we are behind the wave front)
    float distToWave = distToWater;// + waveReach;
    float waterSurfaceMask =
            smoothstep(1.5, -1.0, distToWave);

    // ── 1. Shallow water body: advances & retreats with the wave ──────────────
    // Covers the full zone from ocean floor to wave front, not just the waterline.
    // step(-0.5, distToWater) keeps computation on exposed beach mesh only.
    float shorelineMask =
        smoothstep(3.0, -4.0, distToWater);

    float shallowZone =
        smoothstep(2.0, -1.5, distToWave);
    float reflMask = shallowZone * smoothstep(1.0, -0.5, distToWater);

    float reflStr = reflMask * (0.10 + wetSpec * 0.25 * corrMask);

    baseColor = mix(baseColor, u_Horizon * 0.65, reflStr);

    float edgeFade = 0.0;
    float waterAlpha = 0.0;
    if (shallowZone > 0.5) {
        // More opaque near the waterline; thinner film at the wave front
        float depth = clamp(-distToWave / max(waveReach, 0.1), 0.0, 1.0);

        edgeFade = smoothstep(12.5, 0.0, abs(distToWave));

        waterAlpha = mix(0.62, 0.90, depth) * edgeFade;
        baseColor = mix(baseColor, waterTint * (1.0 + caust * 0.5), waterAlpha);
    }

    // ── 2. Swash zone: thin wet film 0..3 m behind wave front ─────────────────
    float swashDist   = max(distToWave, 0.0);
    float swashFactor = smoothstep(3.0, 0.0, swashDist) * step(0.0, distToWater);
    swashFactor *= swashFactor;
    if (swashFactor > 0.001) {
        float shimmer = bN(v_World.xz * 0.55 + vec2(u_Time * 0.07, -u_Time * 0.05)) * 0.40 + 0.60;
        baseColor = mix(baseColor, mix(waterTint, wetSand, 0.30), swashFactor * shimmer * 0.82);
    }

    // ── 3. Wet sand: dark reflective strip 3..7 m behind wave front ───────────
    float wetSandDist   = max(distToWave - 3.0, 0.0);
    float wetSandFactor = smoothstep(2.0, 0.0, wetSandDist) * step(0.0, distToWater);
    if (wetSandFactor > 0.001) {
        float skyRefl = wetSandFactor * 0.12 * (1.0 - u_TidePercent * 0.4);
        baseColor = mix(baseColor, wetSand * 0.80, wetSandFactor * 0.30);
        baseColor = mix(baseColor, u_Horizon * 0.60, skyRefl);
    }

    // ── 4. Foam: patchy clusters at wave front + scattered bubbles in swash ───
    float foamBand = waterSurfaceMask * (1.0 - smoothstep(0.0, 1.0, abs(distToWave)));
    foamBand *= smoothstep(-0.5, 0.3, distToWater);
    foamBand *= shorelineMask;

    // Three noise scales → organic foam clusters, not a solid stripe
    float pA = bN(vec2(v_World.x * 0.40 + u_Time * 0.10, v_World.z * 0.40 - u_Time * 0.06));
    float pB = bN(vec2(v_World.x * 0.70 - u_Time * 0.08, v_World.z * 0.70 + u_Time * 0.05));
    float pC = bN(v_World.xz * 2.20 + vec2(u_Time * 0.28, -u_Time * 0.20));
    float patchThresh = mix(0.28, 0.14, u_WindAmp);
    float patchMask   = smoothstep(patchThresh, 0.68, pA * 0.40 + pB * 0.35 + pC * 0.25);

    // Scattered bubble clusters inside the swash zone
    float bn1 = bN(v_World.xz * 2.8 + vec2(u_Time * 0.18,  u_Time * 0.11));
    float bn2 = bN(v_World.xz * 5.5 - vec2(u_Time * 0.12,  u_Time * 0.22));
    float bubbleMask = smoothstep(0.68, 0.90, bn1 * 0.55 + bn2 * 0.45)
                     * smoothstep(2.5, 0.0, swashDist)
                     * step(0.0, distToWater);

    vec3 shoreWater = mix(baseColor, waterTint, edgeFade);
    float foamBlend = clamp(foamBand * patchMask + bubbleMask * 0.45, 0.0, 1.0);
    baseColor = mix(baseColor, vec3(0.92,0.96,1.0), foamBlend);

    // ── Atmospheric fog ────────────────────────────────────────────────────────
    float fogZ    = max(18.0 - v_World.z, 0.0);
    float fogFact = clamp(1.0 - exp(-fogZ * 0.008), 0.0, 0.45);
    baseColor = mix(baseColor, u_Horizon * 0.82, fogFact);

    gl_FragColor = vec4(baseColor, 1.0);
}
