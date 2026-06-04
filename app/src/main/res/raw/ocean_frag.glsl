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
uniform vec3      u_DeepColor;     // far / horizon — deeper blue
uniform vec3      u_ShallowColor;  // near camera — bright teal
uniform vec3      u_CamPos;
uniform float     u_Roughness;
uniform float     u_WindAmp;
uniform float     u_Time;
uniform float     u_YunseulStr;
uniform float     u_WaterlineZ;
uniform float     u_Tide;
uniform sampler2D u_NormalMap;
uniform vec3      u_HorizonColor;

// ── Irregular value noise — used for sun-glitter so it is NOT a regular grid ──
float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 345.45));
    p += dot(p, p + 34.345);
    return fract(p.x * p.y);
}
float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

void main() {
    if (v_World.z > u_WaterlineZ - 0.5) discard;

    // Horizontal distance from camera drives the colour depth gradient.
    float dist     = length(v_World.xz - u_CamPos.xz);
    float distNorm = clamp(dist / 70.0, 0.0, 1.0);

    // ── Volume fix: wave undersides are deep water, never empty sky ───────────
    if (!gl_FrontFacing) {
        gl_FragColor = vec4(mix(u_ShallowColor * 0.45, u_DeepColor * 0.85, distNorm), 1.0);
        return;
    }

    // ── 1. Depth colour — near bright teal → far deep blue, continuous ────────
    float depthT = pow(distNorm, 0.85);
    vec3  water  = mix(u_ShallowColor, u_DeepColor, depthT);

    // ── 2. Trough darkening (relative to the tide-shifted mean level) ─────────
    float tideY  = u_Tide * 1.4 - 0.7;
    float waveH  = v_World.y - tideY;            // + crest, − trough
    float trough = clamp(-waveH * 1.4, 0.0, 1.0);
    water = mix(water, u_DeepColor * 0.60, trough * 0.45);

    // ── Surface normal ───────────────────────────────────────────────────────
    // Keep a small baseline perturbation even at 0 wind so the low-poly mesh
    // facets never read as a checkerboard on flat water.
    vec2 uv1   = v_World.xz * 0.15 + u_Time * vec2( 0.012,  0.008);
    vec2 uv2   = v_World.xz * 0.07 - u_Time * vec2( 0.007,  0.011);
    vec3 nm1   = texture2D(u_NormalMap, uv1).rgb * 2.0 - 1.0;
    vec3 nm2   = texture2D(u_NormalMap, uv2).rgb * 2.0 - 1.0;
    vec2 perturb = (nm1.xz + nm2.xz) * 0.30 * (u_WindAmp + 0.18);
    vec3 N = normalize(vec3(v_Normal.x + perturb.x, v_Normal.y, v_Normal.z + perturb.y));
    vec3 V = normalize(u_CamPos - v_World);
    vec3 L = u_LightDir;

    // Diffuse body shading — gives the surface its 3D volume.
    float NdotL = max(dot(N, L), 0.0);
    vec3  col   = water * (NdotL * 0.40 + 0.60);

    // ── 3. 윤슬 — natural sun-glitter corridor with IRREGULAR sparkle ─────────
    // The sun-reflection path narrows toward the horizon (perspective). Sparkle
    // is concentrated in that corridor and is subtle/irregular elsewhere.
    vec2  lhDir    = normalize(vec2(L.x, L.z));
    vec2  toFrag   = v_World.xz - u_CamPos.xz;
    vec2  perp     = vec2(-lhDir.y, lhDir.x);
    float perpDist = abs(dot(toFrag, perp));
    float corrHalf = mix(9.0, 4.0, distNorm);                  // wide near, tight far
    float corrMask = exp(-perpDist * perpDist / (corrHalf * corrHalf));

    // Irregular glitter: product of two animated noise layers, sparse threshold.
    // Cells are larger near the camera (big glints) and finer toward the horizon.
    float glScale = mix(3.0, 11.0, distNorm);
    vec2  gp      = v_World.xz * glScale;
    float n1      = vnoise(gp + vec2(u_Time * 0.70, -u_Time * 0.50));
    float n2      = vnoise(gp * 1.6 - vec2(u_Time * 0.45,  u_Time * 0.65));
    float glint   = smoothstep(0.66, 0.98, n1 * n2 * 1.7);

    // Smooth specular sheen — the soft bright glow that underlies the sparkles.
    vec3  H    = normalize(L + V);
    float spec = pow(max(dot(N, H), 0.0), mix(120.0, 28.0, u_Roughness));

    // Concentrate in the corridor; keep a faint sparkle on the calm sides.
    float corridor   = 0.12 + corrMask * 0.88;
    float glitterAmt = glint * corridor * mix(1.0, 0.5, distNorm);
    vec3  yunseul    = u_LightColor * u_YunseulStr * (glitterAmt * 0.85 + spec * corrMask * 0.55);

    // ── Foam on wind-driven crests ───────────────────────────────────────────
    float foam = smoothstep(0.4, 0.7, v_Foam) * clamp(u_WindAmp * 2.5, 0.0, 1.0);
    col = mix(col, vec3(0.95, 0.97, 1.00), foam);

    // Near wet sheen (Fresnel) — restricted to near water so far stays deep.
    float fres   = pow(1.0 - max(dot(N, V), 0.0), 5.0);
    vec3  sheen  = mix(u_ShallowColor, u_HorizonColor, 0.5) * 0.7;
    col = mix(col, sheen, fres * 0.16 * (1.0 - distNorm) * (1.0 - foam));

    col += yunseul * (1.0 - foam);

    // ── Thin atmospheric haze at the horizon line only ───────────────────────
    float seam = smoothstep(0.90, 1.0, distNorm);
    col = mix(col, u_HorizonColor * 0.55, seam * 0.5);

    gl_FragColor = vec4(col, 1.0);
}
