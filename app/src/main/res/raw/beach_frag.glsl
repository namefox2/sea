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
uniform sampler2D u_NormalMap;
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
    if (distToWater < -20.0) discard;

    vec2  toFragXZ = v_World.xz - u_CamPos.xz;
    float dCam     = max(length(toFragXZ), 0.01);

    // ── Color palette ─────────────────────────────────────────────────────────
    vec3 drySand        = vec3(0.62, 0.52, 0.36);
    vec3 wetSand        = vec3(0.29, 0.22, 0.12);
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
        vec2 warpBase = p * 1.7 + w1 * 1.6;
        vec2 wp = p + vec2(
            bN(warpBase + vec2(3.7, 9.2)),
            bN(warpBase + vec2(8.1, 2.8))
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
    // Vivid turquoise tint matching reference photo shallow water (#00CED1 area)

    // ── Caustics ──────────────────────────────────────────────────────────────
    float caust = sin(v_World.x * 3.8 + u_Time * 1.4) * sin(v_World.z * 4.3 - u_Time * 1.1);
    caust = pow(max(caust * 0.5 + 0.62, 0.0), 3.0) * 0.14;

    // ── Water-surface normals — same normal map as ocean, 3 tiling scales ────
    vec2 uvW1 = v_World.xz * 0.14 + u_Time * vec2( 0.013,  0.009);
    vec2 uvW2 = v_World.xz * 0.07 - u_Time * vec2( 0.007,  0.012);
    vec2 uvW3 = v_World.xz * 0.50 + u_Time * vec2( 0.031, -0.023);
    vec3 wnm1 = texture2D(u_NormalMap, uvW1).rgb * 2.0 - 1.0;
    vec3 wnm2 = texture2D(u_NormalMap, uvW2).rgb * 2.0 - 1.0;
    vec3 wnm3 = texture2D(u_NormalMap, uvW3).rgb * 2.0 - 1.0;
    vec3 waterN = normalize(vec3(
        wnm1.x * 0.50 + wnm2.x * 0.30 + wnm3.x * 0.20,
        1.0,
        wnm1.z * 0.50 + wnm2.z * 0.30 + wnm3.z * 0.20
    ));
    vec3 V   = normalize(u_CamPos - v_World);
    vec3 H   = normalize(u_LightDir + V);
    float wSpec = pow(max(dot(waterN, H), 0.0), 90.0) * 0.38;
    float wFres = pow(1.0 - max(dot(waterN, V), 0.0), 3.0);

    // ── Wave cycles — phase-locked to Gerstner primary wave in ocean_vert ───
    // windS normalises u_WindAmp to [0,1] — beach wave math was designed for
    // this range; raw values like 12 blew minReach to 9.85 m and waveReach to
    // 25 m, making the beach look permanently flooded regardless of wave state.
    float windS = smoothstep(0.0, 1.0, u_WindAmp);

    float wx   = v_World.x * 0.13;
    float L0   = mix(8.0, 16.0, windS);     // must match ocean_vert.glsl
    float spd0 = mix(0.85, 1.65, windS);
    float k0   = 6.28318 / L0;
    float om0  = spd0 * k0;
    float ph1  = bN(vec2(wx,            0.5)) * 3.2;
    float ph2  = bN(vec2(wx * 1.8 + 4.0, 0.5)) * 2.1;
    float t1   = max(sin(k0 * u_WaterlineZ - om0 * u_Time + ph1) * 0.5 + 0.5, 0.0);
    t1 = t1 * t1;
    float k1   = k0 / 0.58;
    float t2   = max(sin(k1 * u_WaterlineZ - om0 * 1.25 * u_Time + ph2) * 0.5 + 0.5, 0.0);
    t2 = t2 * t2;
    float minReach  = 0.25 + windS * 0.8;
    float reachNoise = bN(vec2(wx * 0.7, u_Time * 0.05)) * 0.55
                     + bN(vec2(wx * 0.25 + 3.0, u_Time * 0.03)) * 0.45;
    float waveReach = max(t1 * (1.2 + windS * 2.0) + t2 * (0.5 + windS * 1.0),
                          minReach) * (0.65 + reachNoise * 0.55);

    // Wave gate: 0 between waves, 1 when wave actively arrives.
    // Prevents shallowZone/swash/wetSand from firing on minReach alone.
    float waveActive = smoothstep(0.05, 0.40, t1 + t2 * 0.5);

    // distToWave: <0 = wave is here (wet), >0 = wave tip hasn't arrived yet
    float distToWave       = distToWater - waveReach;
    float waterSurfaceMask = smoothstep(1.5, -1.0, distToWave);

    // Turquoise tint: restricted to seaward side (distToWater < 0) so it stays
    // hidden under the opaque ocean — no visible edge on the exposed beach.
    vec3  waterTint  = vec3(0.28, 0.82, 0.76);
    float shoreBlend = smoothstep(0.0, -1.5, distToWater) * waterSurfaceMask * waveActive;
    baseColor = mix(baseColor, waterTint * 0.85, shoreBlend * 0.18);

    // ── 1. Shallow water body ─────────────────────────────────────────────────
    vec3 bottomColor = baseColor;  // save terrain color — bleeds through shallow water

    float shallowZone   = smoothstep(2.0, -1.5, distToWave);
    float reflMask      = shallowZone * smoothstep(1.0, -0.5, distToWater);
    baseColor = mix(baseColor, u_Horizon * 0.65, reflMask * (0.10 + wetSpec * 0.25 * corrMask));

    float edgeFade   = 0.0;
    float waterAlpha = 0.0;
    if (shallowZone > 0.5) {
        float depth    = clamp(-distToWave / max(waveReach, 0.1), 0.0, 1.0);
        edgeFade   = smoothstep(waveReach * 1.3 + 1.5, 0.0, abs(distToWave));
        // Quadratic alpha: very transparent near surface, opaque in deeper water
        waterAlpha = mix(0.08, 0.58, depth * depth) * edgeFade * waveActive;
        // Water color: muddy yellowish-teal (bridges cool ocean ↔ warm mudflat)
        vec3 muddyTeal = vec3(0.22, 0.58, 0.44);
        float depthAdv = clamp(depth * 1.8, 0.0, 1.0);
        vec3 wCol = mix(muddyTeal, waterTint, depthAdv);
        // Shallow: mudflat bottom shines through; deeper: full water tint + caustics
        vec3 wRef = mix(bottomColor * 0.75 + wCol * 0.25,
                        wCol * (1.0 + caust * 0.4), depthAdv);
        wRef = mix(wRef, u_Horizon * 0.55, wFres * 0.18);
        baseColor  = mix(baseColor, wRef, waterAlpha);
            // Subtle specular glint on runup water
        baseColor += vec3(0.90, 0.95, 1.00) * wSpec * waterAlpha * 0.32;
    }

    // ── 2. Swash zone: thin wet film behind wave tip ───────────────────────────
    float swashDist   = max(distToWave, 0.0);
    // smoothstep instead of step: fades in over 0.5 m so beach colour doesn't
    // snap on at exactly distToWater=0 (that hard snap showed as a straight line
    // through the ocean's ~10% transparency at the waterline).
    float swashFactor = smoothstep(waveReach * 0.55 + 0.5, 0.0, swashDist) * smoothstep(-0.5, 0.0, distToWater) * waveActive;
    swashFactor *= swashFactor;
    if (swashFactor > 0.001) {
        float shimmer  = bN(v_World.xz * 0.55 + vec2(u_Time * 0.07, -u_Time * 0.05)) * 0.40 + 0.60;
        vec3  swashRef = mix(mix(waterTint, wetSand, 0.42), u_Horizon * 0.50, wFres * 0.16);
        baseColor = mix(baseColor, swashRef * shimmer, swashFactor * 0.68);
        baseColor += vec3(0.90, 0.95, 1.00) * pow(max(dot(waterN, H), 0.0), 50.0) * swashFactor * 0.15;
    }

    // ── 3. Wet sand: dark reflective strip behind swash ───────────────────────
    // Reference shows very dark wet sand with sky reflection — make it prominent.
    float wetSandFactor = smoothstep(5.0, 0.0, max(distToWave - 1.0, 0.0)) * smoothstep(-0.5, 0.0, distToWater) * waveActive;
    if (wetSandFactor > 0.001) {
        baseColor = mix(baseColor, wetSand * 0.72, wetSandFactor * 0.68);
        // Wet sand sky reflection + sun glint through ripple normal
        vec3 wetRefl = mix(u_Horizon * 0.58, vec3(0.90, 0.95, 1.00), wSpec * 0.35);
        baseColor = mix(baseColor, wetRefl, wetSandFactor * 0.22 * (1.0 - u_TidePercent * 0.3));
    }


    // ── Seaward boundary color-match ────────────────────────────────────────
    // Very subtle tint on the seaward side so the beach colour doesn't contrast
    // too sharply when seen through semi-transparent ocean near waves.
    // Extends 4 m landward so the wave-run zone stays slightly lit even
    // when the wave has receded — prevents the sudden dark-stripe reveal.
    float seaTint = smoothstep(4.0, -5.0, distToWater);
    baseColor = mix(baseColor, vec3(0.20, 0.56, 0.48), seaTint * 0.25);

    // ── Atmospheric fog ────────────────────────────────────────────────────────
    float fogZ    = max(18.0 - v_World.z, 0.0);
    float fogFact = clamp(1.0 - exp(-fogZ * 0.008), 0.0, 0.45);
    baseColor = mix(baseColor, u_Horizon * 0.82, fogFact);

    gl_FragColor = vec4(baseColor, 1.0);
}
