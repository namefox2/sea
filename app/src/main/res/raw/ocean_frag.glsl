#if GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
varying vec3  v_World;
varying vec3  v_Normal;
varying float v_Foam;

uniform vec3      u_LightDir;
uniform vec3      u_LightColor;
uniform vec3      u_DeepColor;     // horizon / far  — deep navy
uniform vec3      u_ShallowColor;  // near camera    — bright teal
uniform vec3      u_CamPos;
uniform float     u_Roughness;
uniform float     u_WindAmp;
uniform float     u_Time;
uniform float     u_YunseulStr;
uniform float     u_WaterlineZ;
uniform float     u_Tide;
uniform sampler2D u_NormalMap;
uniform vec3      u_HorizonColor;

// ── Smooth value noise (bilinear) — used only for the shore-foam fringe ──────
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
    // Ocean covers up to 2 units past the waterline so the shore transition
    // is owned by the ocean shader — no hard Z clip visible against the beach.
    if (v_World.z > u_WaterlineZ + 2.0) discard;

    float dist     = length(v_World.xz - u_CamPos.xz);
    float distNorm = clamp(dist / 68.0, 0.0, 1.0);

    // ── Volume fix: back-faces (under wave crests) show deep water ───────────
    if (!gl_FrontFacing) {
        gl_FragColor = vec4(mix(u_ShallowColor * 0.5, u_DeepColor * 0.9, distNorm), 1.0);
        return;
    }

    // ── 1. Base colour — strictly monotonic near→far ──────────────────────────
    //   near = bright teal (u_ShallowColor)
    //   far  = deep navy   (u_DeepColor)
    //   pow(distNorm, 0.72) compresses the gradient near camera so the teal
    //   zone feels wide and naturally transitions to navy mid-ocean.
    vec3 water = mix(u_ShallowColor, u_DeepColor, pow(distNorm, 0.72));

    // ── 2. Wave volume shading ────────────────────────────────────────────────
    // minAmp matches the vertex shader floor so waveH is in [-1, 1].
    float minAmp = u_WindAmp * 0.28 + 0.08;
    float tideY  = u_Tide * 1.4 - 0.7;
    float waveH  = clamp((v_World.y - tideY) / (minAmp * 1.5), -1.0, 1.0);

    // Crests: warmer (subsurface backscatter through thin water layer)
    water = mix(water, water * 1.28 + vec3(0.00, 0.04, 0.03), max(waveH, 0.0) * 0.55);
    // Troughs: darker — water column below is deep
    water = mix(water, u_DeepColor * 0.42, max(-waveH, 0.0) * 0.60);

    // ── Normal maps (3 scales to reduce tiling and cover near+far) ───────────
    vec2 uv1 = v_World.xz * 0.12 + u_Time * vec2( 0.011,  0.007);
    vec2 uv2 = v_World.xz * 0.06 - u_Time * vec2( 0.006,  0.010);
    vec2 uv3 = v_World.xz * 0.42 + u_Time * vec2( 0.028, -0.021);  // fine ripples
    vec3 nm1 = texture2D(u_NormalMap, uv1).rgb * 2.0 - 1.0;
    vec3 nm2 = texture2D(u_NormalMap, uv2).rgb * 2.0 - 1.0;
    vec3 nm3 = texture2D(u_NormalMap, uv3).rgb * 2.0 - 1.0;

    float windPert = u_WindAmp + 0.15;   // baseline micro-ripple even at 0 bft
    vec2  perturb  = (nm1.xz * 0.40 + nm2.xz * 0.30 + nm3.xz * 0.18) * windPert;
    vec3  N  = normalize(vec3(v_Normal.x + perturb.x, v_Normal.y, v_Normal.z + perturb.y));
    // Finer normal for micro-specular glints (high-frequency)
    vec3  Nf = normalize(vec3(v_Normal.x + nm1.x*0.55 + nm3.x*0.35,
                               v_Normal.y,
                               v_Normal.z + nm1.z*0.55 + nm3.z*0.35) * windPert);
    vec3  V  = normalize(u_CamPos - v_World);
    vec3  L  = u_LightDir;

    // ── 3. Diffuse body (NdotL) ───────────────────────────────────────────────
    float NdotL = max(dot(N, L), 0.0);
    vec3  col   = water * (NdotL * 0.38 + 0.62);

    // ── 4. SSS approximation — backlit crests glow cyan-green ────────────────
    float sss = pow(max(dot(L, -V), 0.0), 5.0) * max(waveH, 0.0) * 0.9;
    col += vec3(0.01, 0.32, 0.22) * sss;

    // ── 5. 윤슬 — pure specular, no cellular hash, no tiling pattern ──────────
    // Real water sparkle = many micro-wave facets each specularly reflecting the sun.
    // We approximate with two lobe widths (coarse Gerstner + fine texture normal).
    // The sun corridor concentrates glints along the reflection path.
    vec2  lhDir    = normalize(vec2(L.x, L.z));
    vec2  toFrag   = v_World.xz - u_CamPos.xz;
    vec2  perpXZ   = vec2(-lhDir.y, lhDir.x);
    float perpDist = abs(dot(toFrag, perpXZ));
    float corrHalf = mix(9.0, 4.0, distNorm);       // wide near camera, narrow far
    float corrMask = exp(-perpDist * perpDist / (corrHalf * corrHalf));

    vec3  H      = normalize(L + V);
    // Broad sheen: smooth Blinn-Phong on the coarse Gerstner normal
    float sheen  = pow(max(dot(N,  H), 0.0), mix(55.0, 22.0, u_Roughness));
    // Tight glints: high-exponent specular on the fine normal map normal
    // Near camera: small exponent → large glints.  Far: large exponent → small points.
    float expFine = mix(180.0, 600.0, distNorm);
    float glints  = pow(max(dot(Nf, H), 0.0), expFine);

    // Outside the corridor: faint glints everywhere (0.25 baseline so near water
    // always has some sparkle). Inside corridor: full glints + extra brightness.
    float sparkle = sheen * 0.30 + glints * 2.2;
    float corridorBoost = 0.25 + corrMask * 1.75;
    vec3  yunseul = u_LightColor * u_YunseulStr * sparkle * corridorBoost;

    // The corridor itself is lighter (sun/moon reflection path)
    col = mix(col, col + u_LightColor * 0.35, corrMask * 0.5);

    // ── 6. Wave-crest foam ────────────────────────────────────────────────────
    float foam = smoothstep(0.45, 0.80, v_Foam) * clamp(u_WindAmp * 2.0, 0.0, 1.0);
    col = mix(col, vec3(0.94, 0.97, 1.00), foam * 0.65);

    // Fresnel near-surface sheen (restricted to near water to keep far dark)
    float fres = pow(1.0 - max(dot(N, V), 0.0), 5.0);
    col = mix(col, mix(u_ShallowColor, u_HorizonColor, 0.4) * 0.75,
              fres * 0.14 * (1.0 - distNorm) * (1.0 - foam));

    col += yunseul * (1.0 - foam);

    // ── 7. Shore transition — ocean fades to pale aqua toward the beach ───────
    // The ocean now renders 2 units past the waterline so there is no hard
    // Z-clip seam; both shaders produce the same colour at the boundary.
    // shoreProx = 0 at 9 units from waterline, 1 at/past waterline.
    float shoreProx = clamp((v_World.z - (u_WaterlineZ - 9.0)) / 11.0, 0.0, 1.0);
    shoreProx = pow(shoreProx, 1.1);
    vec3 shoreAqua = vec3(0.45, 0.74, 0.72);   // matches beach water-entry tint exactly
    col = mix(col, shoreAqua, shoreProx * 0.95);

    // Animated foam fringe right at the waterline
    float fringe = smoothstep(0.75, 1.0, shoreProx)
                 * (0.45 + 0.55 * vnoise(v_World.xz * 2.8 + u_Time * 0.55));
    col = mix(col, vec3(0.94, 0.97, 1.00), fringe * 0.42);

    // ── 8. Thin horizon atmospheric seam (last) ───────────────────────────────
    float seam = smoothstep(0.92, 1.0, distNorm);
    col = mix(col, u_HorizonColor * 0.55, seam * 0.50);

    gl_FragColor = vec4(col, 1.0);
}
