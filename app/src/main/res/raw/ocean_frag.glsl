#if GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
varying vec3  v_World;
varying vec3  v_Normal;
varying float v_Foam;
varying float v_DistToWater;

uniform vec3      u_LightDir;
uniform vec3      u_LightColor;
uniform vec3      u_DeepColor;     // horizon / far  — deep navy
uniform vec3      u_ShallowColor;  // near camera    — bright teal
uniform vec3      u_CamPos;
uniform float     u_Roughness;
uniform float     u_WindAmp;
uniform float     u_Time;
uniform float     u_YunseulStr;
uniform float     u_Tide;
uniform sampler2D u_NormalMap;
uniform vec3      u_HorizonColor;
uniform float     u_WindSurge;
uniform vec3      u_SandDryColor;
uniform vec3      u_SandWetColor;


float h21(vec2 p) {
    p = fract(p * vec2(127.1, 311.7));
    p += dot(p, p + 43.2);
    return fract(p.x * p.y);
}
float vnoise(vec2 p) {
    vec2 i = floor(p); vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(h21(i), h21(i+vec2(1,0)), u.x),
               mix(h21(i+vec2(0,1)), h21(i+vec2(1,1)), u.x), u.y);
}

// 2-D hash → vec2 point in [0,1]² (used by Voronoi below)
vec2 h21v(vec2 p) {
    p = fract(p * vec2(127.1, 311.7));
    p += dot(p, p + 43.2);
    return fract(vec2(p.x * p.y, p.x + p.y));
}
// Voronoi cellular noise — fully unrolled 3×3 neighbourhood, zero loops.
// Loops caused silent no-ops on some GLSL ES 2.0 drivers (md stayed 8.0 →
// sqrt(8)=2.83 > 0.30 → always returns 0). Explicit 9-sample version is
// guaranteed to execute on any conformant GLSL implementation.
float foamCells(vec2 p) {
    vec2 ip = floor(p); vec2 fp = fract(p);
    vec2 rp; vec2 d; float md = 8.0;
    rp=h21v(ip+vec2(-1,-1))*0.80+0.10; d=vec2(-1,-1)+rp-fp; md=min(md,dot(d,d));
    rp=h21v(ip+vec2( 0,-1))*0.80+0.10; d=vec2( 0,-1)+rp-fp; md=min(md,dot(d,d));
    rp=h21v(ip+vec2( 1,-1))*0.80+0.10; d=vec2( 1,-1)+rp-fp; md=min(md,dot(d,d));
    rp=h21v(ip+vec2(-1, 0))*0.80+0.10; d=vec2(-1, 0)+rp-fp; md=min(md,dot(d,d));
    rp=h21v(ip+vec2( 0, 0))*0.80+0.10; d=vec2( 0, 0)+rp-fp; md=min(md,dot(d,d));
    rp=h21v(ip+vec2( 1, 0))*0.80+0.10; d=vec2( 1, 0)+rp-fp; md=min(md,dot(d,d));
    rp=h21v(ip+vec2(-1, 1))*0.80+0.10; d=vec2(-1, 1)+rp-fp; md=min(md,dot(d,d));
    rp=h21v(ip+vec2( 0, 1))*0.80+0.10; d=vec2( 0, 1)+rp-fp; md=min(md,dot(d,d));
    rp=h21v(ip+vec2( 1, 1))*0.80+0.10; d=vec2( 1, 1)+rp-fp; md=min(md,dot(d,d));
    return 1.0 - smoothstep(0.02, 0.30, sqrt(md));
}

void main() {
    float windS = smoothstep(0.0, 1.0, u_WindAmp);   // [0..1] pre-square; used for foam
    float wind  = windS * windS;                       // squared for wave perturbation
    float dist     = length(v_World.xz - u_CamPos.xz);
    float distNorm = clamp(dist / 68.0, 0.0, 1.0);
    float nearFactor = 1.0 - clamp(dist / 20.0, 0.0, 1.0);

    // ── Volume fix: wave undersides (back-faces) are WATER, never black ───────
    // Filled with a lit deep-water colour so a tall wave's underside reads as a
    // continuous body of water rather than a black gap.
    if (!gl_FrontFacing) {
        vec3 under = mix(u_ShallowColor, u_DeepColor, distNorm) * 0.78 + vec3(0.0, 0.02, 0.03);
        gl_FragColor = vec4(under, 1.0);
        return;
    }

    // ── Wave level & height (computed early — needed for depth blend) ─────────
    float minAmp = u_WindAmp * u_WindAmp * 0.62 + u_WindAmp * 0.18 + 0.06;
    float tideY  = u_Tide * 1.4 - 0.7 + u_WindSurge;
    float waveH  = clamp((v_World.y - tideY) / (minAmp * 1.4), -1.0, 1.0);

    // ── 1. Depth-based water colour — smooth, wave-linked, noise-perturbed ────
    //
    // tide-linked normalisation distance: tide=0(간조)→60m, tide=1(만조)→25m.
    // Scaling the range (not just offsetting) means the ENTIRE depth gradient
    // compresses toward shore at high tide — deep colour reaches closer AND
    // shallow zone narrows, matching real physics (high tide = deeper everywhere).
    float normDist = mix(42.0, 17.0, u_Tide);
    float shoreZ   = clamp((-v_DistToWater) / normDist, 0.0, 1.0);

    float waveDepthMod = waveH * 0.10;
    float boundNoise = sin(v_World.x * 0.07 + u_Time * 0.03) * 0.08
                     + sin(v_World.x * 0.19 - u_Time * 0.02 + v_World.z * 0.04) * 0.05;
    float shoreBlend = smoothstep(0.0, 1.0, shoreZ + waveDepthMod + boundNoise);

    float distBias   = distNorm * 0.09; // gentle horizon push toward deep

    // Wide range [0.05 → 0.95]: S-curve spans the full scene without plateauing.
    float depthBlend = smoothstep(0.05, 0.95, shoreBlend + distBias);
    vec3 water = mix(u_ShallowColor, u_DeepColor, depthBlend);

    // Caustics: animated refraction light-patterns visible in the shallow zone.
    // Two overlapping sin×sin patterns give an interference / dappled look.
    float shallowStr = (1.0 - depthBlend) * (1.0 - depthBlend);  // squared → sharp falloff
    float c1 = sin(v_World.x * 3.6 + u_Time * 1.10 + v_World.z * 2.2)
             * sin(v_World.z * 3.1 - u_Time * 0.85 + v_World.x * 1.7);
    float c2 = sin(v_World.x * 5.1 + u_Time * 1.50 + v_World.z * 1.6)
             * sin(v_World.z * 4.3 + u_Time * 0.60 - v_World.x * 2.5);
    float ca = max(c1 * 0.5 + 0.52, 0.0);
    float cb = max(c2 * 0.5 + 0.52, 0.0);
    float caustic = (ca * ca * sqrt(ca) * 0.6 + cb * cb * cb * 0.4) * shallowStr * 0.15;
    water += u_LightColor * caustic;

    // Tidal zone: yellowish-green bridging tint where shallow ocean meets exposed mudflat.
    // tidalFade ensures the tint is ZERO inside the shoreAlpha transition zone (within
    // 4 m of the waterline) — preventing a colored band where the ocean is semi-transparent.
    float tidalZone = clamp((12.0 + v_DistToWater) / 12.0, 0.0, 1.0);
    float tidalFade = clamp(-v_DistToWater / 4.0, 0.0, 1.0);
    water = mix(water, vec3(0.20, 0.54, 0.42), tidalZone * tidalFade * (1.0 - depthBlend) * 0.45);

    // sNoiseF: wave-position noise used here (bridge) and reused in sections 6 & 7.
    float sNoiseF = sin(v_World.x * 0.25 + u_Time * 0.40) * 1.4
                  + sin(v_World.x * 0.11 - u_Time * 0.28) * 0.9
                  + sin(v_World.x * 0.58 + u_Time * 0.62) * 0.5;

    // Shore-edge chroma bridge: upper edge perturbed by sNoiseF*0.25 (±0.7 m) so
    // the 100%-sand completion is a wavy line instead of a constant-Z straight line.
    float shoreEdgeBridge = smoothstep(-6.0, sNoiseF * 0.25, v_DistToWater);
    water = mix(water, u_SandDryColor, shoreEdgeBridge);

    // ── 2. Wave volume shading ────────────────────────────────────────────────
    // crestFac 0.20 (was 0.32): wave-crest colour brightening is now milder because
    // foam (section 6) already whitens crest pixels — the two effects stacked at 0.32
    // pushed G/B channels near 0.90+ before foam was even applied.
    float crestFac  = smoothstep(0.0, 1.0, max( waveH, 0.0)) * 0.20;
    float troughFac = smoothstep(0.0, 1.0, max(-waveH, 0.0)) * 0.30;
    water = mix(water, water * 1.26 + vec3(0.00, 0.04, 0.03), crestFac);
    float troughDepth = clamp(-v_DistToWater / 10.0, 0.0, 1.0);
    water = mix(water, u_DeepColor * 0.55, troughFac * troughDepth);

    // ── Normal maps (4 scales — LOD: uv4 fine layer fades in near camera) ────
    vec2 uv1 = v_World.xz * 0.12 + u_Time * vec2( 0.011,  0.007);
    vec2 uv2 = v_World.xz * 0.06 - u_Time * vec2( 0.006,  0.010);
    vec2 uv3 = v_World.xz * 0.42 + u_Time * vec2( 0.028, -0.021);
    vec2 uv4 = v_World.xz * 2.50 + u_Time * vec2( 0.060, -0.050);
    vec3 nm1 = texture2D(u_NormalMap, uv1).rgb * 2.0 - 1.0;
    vec3 nm2 = texture2D(u_NormalMap, uv2).rgb * 2.0 - 1.0;
    vec3 nm3 = texture2D(u_NormalMap, uv3).rgb * 2.0 - 1.0;
    vec3 nm4 = texture2D(u_NormalMap, uv4).rgb * 2.0 - 1.0;

    float windPert = 0.2 + wind * 0.6;
    vec2  perturb  = (nm1.xz * 0.40 + nm2.xz * 0.30 + nm3.xz * 0.18 + nm4.xz * 0.42 * nearFactor) * windPert;
    vec3  N  = normalize(vec3(v_Normal.x + perturb.x, v_Normal.y, v_Normal.z + perturb.y));
    vec3  Nf = normalize(vec3(v_Normal.x + nm1.x*0.55 + nm3.x*0.35 + nm4.x*0.50*nearFactor,
                               v_Normal.y,
                               v_Normal.z + nm1.z*0.55 + nm3.z*0.35 + nm4.z*0.50*nearFactor) * windPert);
    vec3  V  = normalize(u_CamPos - v_World);
    vec3  L  = u_LightDir;

    // ── 3. Diffuse body + surface micro-texture ──────────────────────────────
    float NdotL = max(dot(N, L), 0.0);
    vec3  col   = water * (NdotL * 0.44 + 0.56);
    // Cross-correlate two normal-map octaves to produce a coherent ripple pattern
    // visible as fine wave surface texture before the major lighting effects apply.
    float waveDetail = (nm1.x * nm3.x + nm1.z * nm3.z) * 0.5 + 0.5;  // 0..1
    col *= 0.80 + 0.20 * waveDetail;

    // ── 4. SSS — backlit crests glow cyan-green ──────────────────────────────
    float sss = pow(max(dot(L, -V), 0.0), 5.0) * max(waveH, 0.0) * 0.9;
    col += vec3(0.01, 0.32, 0.22) * sss;

    // ── 5. 윤슬 — physically based sun reflection corridor ───────────────────
    // The glitter band is defined by the sun direction projected onto the water
    // plane. Constant world-space width → perspective naturally makes it appear
    // narrower at the horizon (correct optics), wider near the camera.
    vec2  lhDir    = normalize(vec2(L.x, L.z));
    vec2  toFrag   = v_World.xz - u_CamPos.xz;
    vec2  perpXZ   = vec2(-lhDir.y, lhDir.x);
    float perpDist = abs(dot(toFrag, perpXZ));

    // Fixed world-space corridor half-width (perspective does the rest).
    float corrHalf  = 4.0;
    float corrMask  = exp(-perpDist * perpDist / (corrHalf * corrHalf));
    // Tighter core: concentrates the sparkle into the centre of the sun path so
    // it reads as a cluster of glints, not a wide white surface.
    float corrSharp = corrMask * corrMask;

    // Path brightening: subtle warm shimmer along the sun path on water.
    // Reduced from 0.35→0.22 (strength) and 0.38→0.22 (inner mix) to prevent
    // the sun corridor from adding too much R at high distNorm, which combined
    // with skyReflect was creating a visible white vertical column below the sun.
    float pathLight = corrMask * (0.05 + 0.22 * distNorm);
    vec3  pathTint  = mix(u_LightColor, vec3(1.0), 0.20 * distNorm);
    col = mix(col, mix(col, pathTint, 0.22), clamp(pathLight, 0.0, 0.38));

    // Half-vector for Blinn-Phong specular.
    vec3  H = normalize(L + V);

    // Near-shore surface ripple highlights — wave-normal variation adds visible texture
    float nearSheen = pow(max(dot(N, H), 0.0), 14.0) * max(0.0, 0.8 - distNorm * 2.5);
    col += u_LightColor * nearSheen * 0.38;

    // Near glints: large, individual — broad lobe from wave macro-normal N.
    float sheenExp = mix(20.0, 9.0, u_Roughness);
    float sheen    = pow(max(dot(N, H), 0.0), sheenExp);

    // Far micro-glints: dense, fine — tight lobe from detailed normal Nf.
    float fineExp  = mix(280.0, 100.0, u_Roughness);
    float glints   = pow(max(dot(Nf, H), 0.0), fineExp);
    glints *= 0.5 + 0.5 * max(waveH, 0.0);  // favour crests

    // Favour the tight micro-glints (small sparkles); keep the broad sheen low
    // so it never spreads into a white sheet — near water shows teal underneath.
    float sparkle = sheen  * (0.45 - distNorm * 0.20) +
                    glints * (0.50 + distNorm * 1.40);

    // Sparkle lives in the tight corridor core; near-field boost via nearFactor.
    float corrBoost = 0.05 + corrSharp * (0.50 + distNorm * 1.20) + nearFactor * 0.15;
    vec3  yunseul   = u_LightColor * u_YunseulStr * sparkle * corrBoost;

    // ── 6. Wave-crest foam ────────────────────────────────────────────────────
    // sNoiseF already declared above (section 1, shoreEdgeBridge) — reused here.
    float waveFront = sNoiseF + 3.5 + waveH * 1.5; // dtw at wave's inland tip
    float frontDist = v_DistToWater - waveFront;    // +ve = dry land, -ve = in water

    float curlT  = u_Time * 0.7;
    vec2  foamUV = v_World.xz + vec2(
        sin(curlT * 1.1 + v_World.z * 0.55) * 0.40,
        -curlT * 0.18
    );

    // Distance-based bubble scale: large dots near camera, finer texture far away.
    float bubScale  = mix(5.0, 9.0, clamp(dist / 40.0, 0.0, 1.0));
    float bub1 = foamCells(foamUV * bubScale);
    float bub2 = foamCells(foamUV * (bubScale * 1.8) + vec2(2.3, 1.7));
    float bub3 = foamCells(foamUV * (bubScale * 2.9) + vec2(5.1, 3.9));
    float densN = vnoise(foamUV * 1.8 + u_Time * vec2(0.08, 0.05)) * 0.55
                + vnoise(foamUV * 4.5 - u_Time * vec2(0.04, 0.09)) * 0.45;
    float density   = smoothstep(0.10, 0.50, densN);
    float bubbleTex = max(bub3, max(bub2 * 0.88, bub1 * 0.74)) * density;
    float bubShape  = smoothstep(0.08, 0.45, bubbleTex);
    float foamGrain = bubShape;

    // Shore foam — all relative to frontDist (wave tip), NOT fixed waterline.
    // This collapses shore foam and edge foam into a single band at the wave front,
    // preventing the double foam+mudflat pattern caused by using two different origins.
    float swash1 = sin(-frontDist * 1.26 - u_Time * 0.72) * 0.5 + 0.5;
    float swash2 = sin(-frontDist * 0.94 - u_Time * 0.55) * 0.5 + 0.5;
    float swashCrest = smoothstep(0.25, 0.65, swash1 * swash2);
    float swashBase = mix(0.08, 0.40, windS);
    float swashMod  = swashBase + (1.0 - swashBase) * swashCrest;
    float foamLifeN = vnoise(foamUV * 0.30 + u_Time * vec2(0.06, 0.04)) * 0.60
                    + vnoise(foamUV * 0.80 - u_Time * vec2(0.03, 0.07)) * 0.40;
    float foamMod   = 0.40 + 0.60 * smoothstep(0.25, 0.72, foamLifeN);
    float shoreDecay = mix(0.40, 0.14, windS);
    float nearShore  = exp(min(frontDist, 0.0) * shoreDecay) * step(frontDist, 0.0);
    float shoreFoam = nearShore * swashMod * foamMod * (0.08 + windS * 0.82) * foamGrain;

    // Open-ocean whitecaps: v_Foam is high far from shore where waves aren't damped.
    float whitecapMask = smoothstep(0.60, 0.90, v_Foam);
    float whitecap     = whitecapMask * foamGrain * (windS * windS * 0.30);
    whitecap *= 1.0 - exp(-abs(v_DistToWater) * 0.40) * 0.90; // suppress near shore

    // Edge foam: concentrated along the irregular wave-front boundary.
    // Tracks waveFront so it follows the actual rendered water edge, not a fixed
    // waterline. Gaussian (±1.5m) creates a natural frothy band at the tip.
    // Edge foam: windS² so it's near-zero at calm and grows quickly with wind.
    float foam = clamp(whitecap + shoreFoam, 0.0, 1.0);

    // Thin water near the wave tip: blend col toward a pale sandy tint in the last
    // 3m before the wave front. Ultra-shallow water over mudflat shows the bottom
    // through — almost no colour of its own.
    // Tinted foam: blue-white near open ocean, warmer near swash tip.
    float foamAlpha = smoothstep(0.03, 0.25, foam);
    vec3  foamColor = mix(col * 1.12, vec3(0.94, 0.97, 1.00), 0.62);
    col = mix(col, foamColor, foamAlpha);

    // Fresnel near-surface sheen (near water only).
    float fres = pow(1.0 - max(dot(N, V), 0.0), 5.0);
    col = mix(col, mix(u_ShallowColor, u_HorizonColor, 0.4) * 0.75,
              fres * 0.14 * (1.0 - distNorm) * (1.0 - foamAlpha));

    // Suppress 윤슬 near shore
    float deepZone = clamp(-v_DistToWater / 12.0, 0.0, 1.0);
    col += yunseul * (1.0 - foamAlpha) * deepZone;

    // ── 7. Shore fade — ocean goes transparent near waterline ────────────────
    // Lower bound shifted -1.5→-3.5 (9 m range, was 7 m): ocean is now ~66% opaque
    // at the waterline (was 90%), so beach bleeds through gradually — no sharp line.
    float waveEdgeShift = waveH * 1.5;
    float shoreAlpha = 1.0 - smoothstep(sNoiseF - 3.5 + waveEdgeShift,
                                         sNoiseF + 5.5 + waveEdgeShift, v_DistToWater);

    // ── 8. Sky reflection + noisy horizon seam ───────────────────────────────
    // Grazing-angle Fresnel: far water reflects sky.
    // Widened smoothstep 0.58→0.92 (width 0.34) → 0.35→0.95 (width 0.60) for a
    // gradual tint instead of a snapping band.  Mix ratio cut 0.28→0.14 to keep
    // Δlum_sky ≤ 0.07 (was +0.131 at dNorm=0.97).
    float skyReflect = smoothstep(0.35, 0.95, distNorm);
    col = mix(col, u_HorizonColor, skyReflect * (1.0 - foamAlpha) * 0.14);

    // Sinusoidal wobble breaks the perfectly-straight horizon line.
    // Gate seam by (1 - skyReflect×0.8) so it doesn't double-brighten on top
    // of skyReflect where both are active near the horizon (dNorm > 0.90).
    float horizNoise = sin(v_World.x * 0.09 + u_Time * 0.012) * 0.022
                     + sin(v_World.x * 0.25 - u_Time * 0.007) * 0.011;
    float seam = smoothstep(0.90, 1.0, distNorm + horizNoise) * (1.0 - corrMask * 0.6);
    col = mix(col, u_HorizonColor * 0.85, seam * (1.0 - skyReflect * 0.8) * 0.55);

    // Foam boost at wave front: shoreAlpha→0 at frontDist=0 so foam needs its own alpha.
    float foamBoostZone = smoothstep(-3.5, 0.0, frontDist)
                        * (1.0 - smoothstep(0.0, 2.0, frontDist));
    float finalAlpha = min(shoreAlpha
                          + foamAlpha * foamBoostZone * (0.18 + windS * 0.62), 1.0);

    // DEBUG: green stripe at ocean wave front (frontDist=0)
    float dbgOcean = exp(-frontDist * frontDist * 3.0);
    col = mix(col, vec3(0.0, 1.0, 0.1), dbgOcean * 0.85);

    // DEBUG: white stripe at frontDist=-3.5 (inner ocean reference)
    float dbgInner = exp(-(frontDist + 3.5) * (frontDist + 3.5) * 3.0);
    col = mix(col, vec3(1.0, 1.0, 1.0), dbgInner * 0.85);

    // DEBUG: purple stripe at shoreEdgeBridge start (v_DistToWater=-6.0)
    float dbgBridge = exp(-(v_DistToWater + 6.0) * (v_DistToWater + 6.0) * 3.0);
    col = mix(col, vec3(0.6, 0.0, 1.0), dbgBridge * 0.85);

    // DEBUG: hot-pink stripe at v_DistToWater=0 (waterline / shoreEdgeBridge=1.0)
    // Hypothesis: straight boundary = this constant-Z plane
    float dbgWLine = exp(-v_DistToWater * v_DistToWater * 3.0);
    col = mix(col, vec3(1.0, 0.15, 0.6), dbgWLine * 0.85);

    // DEBUG: cyan stripe at frontDist=-5.0 (shoreAlpha lower bound — ocean starts fading)
    // Hypothesis: straight boundary = lower edge of transparency zone
    float dbgAlphaLo = exp(-(frontDist + 5.0) * (frontDist + 5.0) * 3.0);
    col = mix(col, vec3(0.0, 1.0, 1.0), dbgAlphaLo * 0.85);

    // DEBUG: lime stripe at frontDist=+2.0 (shoreAlpha upper bound + foamBoostZone end)
    // Hypothesis: straight boundary = where ocean completely disappears
    float dbgAlphaHi = exp(-(frontDist - 2.0) * (frontDist - 2.0) * 3.0);
    col = mix(col, vec3(0.7, 1.0, 0.0), dbgAlphaHi * 0.85);

    float dbgMax = max(max(dbgOcean, dbgInner),
                       max(max(dbgBridge, dbgWLine), max(dbgAlphaLo, dbgAlphaHi)));
    gl_FragColor = vec4(col, max(finalAlpha, dbgMax * 0.85));
}
