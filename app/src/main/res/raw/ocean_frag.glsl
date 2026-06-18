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

void main() {
    float wind = smoothstep(0.0, 1.0, u_WindAmp);
    wind = wind * wind;
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
    // shoreZ: 0 at waterline → 1 at 40 m seaward.  Wider range (was 28 m) spreads
    // the shallow/deep transition over more of the scene so there is no hard edge.
    float shoreZ = clamp((-v_DistToWater) / 40.0, 0.0, 1.0);

    // Wave perturbation: crests appear slightly shallower (lighter),
    // troughs appear slightly deeper — links colour boundary to wave motion.
    float waveDepthMod = waveH * 0.10;

    // Boundary noise: large-scale sinusoidal wobble makes the shallow/deep
    // colour boundary look organic rather than a straight horizontal line.
    float boundNoise = sin(v_World.x * 0.07 + u_Time * 0.03) * 0.08
                     + sin(v_World.x * 0.19 - u_Time * 0.02 + v_World.z * 0.04) * 0.05;

    // Wide smoothstep: transition spans 0.08 → 0.88 (vs. a hard clamp before).
    float shoreBlend = smoothstep(0.0, 1.0, shoreZ + waveDepthMod + boundNoise);
    // High tide = more water overhead even close to camera → push toward deep color.
    float tideBoost  = u_Tide * 0.38;
    // shoreProx: 1 at waterline → 0 at 40 m seaward. Ensures shallow colour near
    // the shore regardless of camera distance (prevents deep-navy seam at waterline).
    float shoreProx  = 1.0 - shoreZ;
    float distFactor = clamp(sqrt(distNorm) + tideBoost, 0.0, 1.0);
    float blendInput = mix(distFactor, shoreBlend, shoreProx * shoreProx);
    float depthBlend = smoothstep(0.04, 0.66, blendInput);
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

    // ── 2. Wave volume shading ────────────────────────────────────────────────
    // smoothstep on waveH converts constructive-interference spikes (waveH jumps
    // hard to +1/-1) into a smooth S-curve ramp so a momentary interference crest
    // brightens gradually rather than flashing white.  Factor 0.32 (was 0.45)
    // also lowers the peak brightness to avoid saturation at the waterline.
    float crestFac  = smoothstep(0.0, 1.0, max( waveH, 0.0)) * 0.32;
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
    col *= 0.87 + 0.13 * waveDetail;

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

    // Path brightening: only the distant water bunches into a bright band; near
    // water stays teal so glitter merely sits on top of the blue.
    float pathLight = corrMask * (0.05 + 0.35 * distNorm);
    vec3  pathTint  = mix(u_LightColor, vec3(1.0), 0.20 * distNorm);
    col = mix(col, mix(col, pathTint, 0.38), clamp(pathLight, 0.0, 0.50));

    // Half-vector for Blinn-Phong specular.
    vec3  H = normalize(L + V);

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
    // Shore band: decays away from waterline but covers a wider strip than before
    // (reference shows wide turbulent foam sheet near the break point).
    float shoreBand = exp(-abs(v_DistToWater) * 0.35);
    float waveMask  = smoothstep(0.42, 0.85, v_Foam);
    // Lacy noise to break foam into patches (not solid white sheet)
    float n1 = fract(sin(v_World.x * 12.3  + v_World.z * 7.7)  * 43758.5453);
    float n2 = fract(sin(v_World.x *  5.1  - v_World.z * 11.3) * 31415.9265);
    float lacyN  = n1 * 0.6 + n2 * 0.4;
    float lacyMask = smoothstep(0.30, 0.70, lacyN);       // punches holes for realism
    float foam  = shoreBand * waveMask * (0.16 + wind * 0.60) * lacyMask;
    float alongWave = sin(v_World.x * 0.2 + u_Time * 2.0);
    foam *= 0.75 + 0.25 * alongWave;

    col = mix(col, vec3(0.96, 0.98, 1.00), foam * 0.58);

    // Fresnel near-surface sheen (near water only).
    float fres = pow(1.0 - max(dot(N, V), 0.0), 5.0);
    col = mix(col, mix(u_ShallowColor, u_HorizonColor, 0.4) * 0.75,
              fres * 0.14 * (1.0 - distNorm) * (1.0 - foam));

    // Suppress 윤슬 near shore — only sparkle in open water (갯벌에서 안 보이도록)
    float deepZone = clamp(-v_DistToWater / 12.0, 0.0, 1.0);
    col += yunseul * (1.0 - foam) * deepZone;

    // ── 7. Shore fade — ocean goes transparent near waterline ────────────────
    // Beach is rendered first (opaque). Ocean fades out with a noisy wavy edge
    // so the beach wave animation shows through naturally.
    float distToWater = v_DistToWater;
    // 3-frequency noise — amplitude kept ≤ ±3 m so alpha zone stays within drape range.
    float shoreNoise = sin(v_World.x * 0.25 + u_Time * 0.40) * 1.4
                     + sin(v_World.x * 0.11 - u_Time * 0.28) * 0.9
                     + sin(v_World.x * 0.58 + u_Time * 0.62) * 0.5;
    // shoreAlpha = ocean fragment opacity (NOT a sky/horizon mixer).
    // Both smoothstep edges shift with the local wave height so the opacity boundary
    // pulses in sync with the wave: crest → fully opaque (ocean advancing over beach),
    // trough → more transparent (beach shows between waves).
    float waveEdgeShift = waveH * 2.5;
    float shoreAlpha = 1.0 - smoothstep(shoreNoise - 0.5 + waveEdgeShift,
                                         shoreNoise + 3.0 + waveEdgeShift, distToWater);

    // ── 8. Sky reflection + noisy horizon seam ───────────────────────────────
    // Grazing-angle Fresnel: far water reflects sky (physically correct).
    float skyReflect = smoothstep(0.58, 0.92, distNorm);
    col = mix(col, u_HorizonColor * 1.05, skyReflect * (1.0 - foam) * 0.38);

    // Sinusoidal wobble breaks the perfectly-straight horizon line.
    float horizNoise = sin(v_World.x * 0.09 + u_Time * 0.012) * 0.022
                     + sin(v_World.x * 0.25 - u_Time * 0.007) * 0.011;
    float seam = smoothstep(0.90, 1.0, distNorm + horizNoise) * (1.0 - corrMask * 0.6);
    col = mix(col, u_HorizonColor * 0.85, seam * 0.55);

    gl_FragColor = vec4(col, shoreAlpha);
}
