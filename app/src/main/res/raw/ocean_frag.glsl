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

// One layer of discrete, twinkling glints (sparse points — reads as sparkle,
// NOT a foam wash). density = fraction of cells lit, radius = glint size.
float glintLayer(vec2 p, float t, float density, float radius) {
    vec2  cell = floor(p);
    vec2  f    = fract(p) - 0.5;
    float rnd  = hash21(cell);
    float on   = step(1.0 - density, rnd);
    vec2  off  = (vec2(hash21(cell + 3.1), hash21(cell + 6.7)) - 0.5) * 0.7;
    float d    = length(f - off);
    float tw   = 0.45 + 0.55 * sin(t * 4.5 + rnd * 60.0);   // per-glint twinkle
    return on * smoothstep(radius, 0.0, d) * max(tw, 0.0);
}

void main() {
    if (v_World.z > u_WaterlineZ - 0.5) discard;

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

    // ── Gentle rolling swell shading — gives 3D volume even at low wind so the
    //    mid/far ocean never looks like a flat painted band. ──────────────────
    vec2  swellDir = normalize(vec2(0.35, 1.0));
    float swell  = sin(dot(v_World.xz, swellDir) * 0.55 - u_Time * 0.8) * 0.5 + 0.5;
    swell = mix(swell, vnoise(v_World.xz * 0.45 - u_Time * 0.05), 0.5);
    water *= 0.86 + 0.26 * swell;

    // ── 2. Trough darkening (relative to the tide-shifted mean level) ─────────
    float tideY  = u_Tide * 1.4 - 0.7;
    float waveH  = v_World.y - tideY;
    float trough = clamp(-waveH * 1.4, 0.0, 1.0);
    water = mix(water, u_DeepColor * 0.60, trough * 0.40);

    // ── Surface normal (baseline detail even at 0 wind → no faceted grid) ─────
    vec2 uv1   = v_World.xz * 0.15 + u_Time * vec2( 0.012,  0.008);
    vec2 uv2   = v_World.xz * 0.07 - u_Time * vec2( 0.007,  0.011);
    vec3 nm1   = texture2D(u_NormalMap, uv1).rgb * 2.0 - 1.0;
    vec3 nm2   = texture2D(u_NormalMap, uv2).rgb * 2.0 - 1.0;
    vec2 perturb = (nm1.xz + nm2.xz) * 0.30 * (u_WindAmp + 0.18);
    vec3 N = normalize(vec3(v_Normal.x + perturb.x, v_Normal.y, v_Normal.z + perturb.y));
    vec3 V = normalize(u_CamPos - v_World);
    vec3 L = u_LightDir;

    float NdotL = max(dot(N, L), 0.0);
    vec3  col   = water * (NdotL * 0.40 + 0.60);

    // ── Sun/moon reflection corridor ─────────────────────────────────────────
    vec2  lhDir    = normalize(vec2(L.x, L.z));
    vec2  toFrag   = v_World.xz - u_CamPos.xz;
    vec2  perp     = vec2(-lhDir.y, lhDir.x);
    float perpDist = abs(dot(toFrag, perp));
    float corrHalf = mix(8.0, 3.5, distNorm);                 // wide near, tight far
    float corrMask = exp(-perpDist * perpDist / (corrHalf * corrHalf));

    // The reflected-light band of water is lighter (sun/moon path on the sea).
    vec3 litWater = mix(col, mix(col, u_LightColor, 0.6), 0.55);
    col = mix(col, litWater, corrMask);

    // ── 3. 윤슬 — discrete twinkling glints, emphasised along the sun line ────
    // Near glints are only slightly larger than far ones (small growth ratio).
    float density = 0.035 + corrMask * 0.42;                  // strongly denser on the sun line
    float gA = glintLayer(v_World.xz * mix(2.4, 5.5, distNorm),        u_Time,       density,        mix(0.20, 0.13, distNorm));
    float gB = glintLayer(v_World.xz * mix(3.6, 9.0, distNorm) + 17.0, u_Time * 1.3, density * 0.8,  mix(0.15, 0.09, distNorm));
    float glints = max(gA, gB);
    float glintBright = mix(1.35, 0.7, distNorm) * (1.0 + corrMask * 1.3); // brighter on the sun line

    // Smooth specular sheen riding the corridor (soft glow under the glints).
    vec3  H    = normalize(L + V);
    float spec = pow(max(dot(N, H), 0.0), mix(110.0, 30.0, u_Roughness)) * corrMask;

    vec3 yunseul = u_LightColor * u_YunseulStr * (glints * glintBright + spec * 0.35);

    // ── Foam on wind-driven crests (subtle — not a wash) ─────────────────────
    float foam = smoothstep(0.45, 0.75, v_Foam) * clamp(u_WindAmp * 1.8, 0.0, 1.0);
    col = mix(col, vec3(0.95, 0.97, 1.00), foam * 0.7);

    // Near wet sheen (Fresnel) — restricted to near water so far stays deep.
    float fres  = pow(1.0 - max(dot(N, V), 0.0), 5.0);
    vec3  sheen = mix(u_ShallowColor, u_HorizonColor, 0.5) * 0.7;
    col = mix(col, sheen, fres * 0.14 * (1.0 - distNorm) * (1.0 - foam));

    col += yunseul * (1.0 - foam);

    // ── Shoreward shoaling: soften the ocean→beach edge into a gradient ───────
    // As the surface nears the waterline it shallows: lightens to a pale aqua
    // and grows a soft foam fringe, so there is no hard teal/sand boundary.
    float shoreProx = clamp(1.0 - (u_WaterlineZ - v_World.z) / 8.0, 0.0, 1.0);
    shoreProx = pow(shoreProx, 1.3);
    vec3  paleAqua  = vec3(0.45, 0.74, 0.72);   // identical to the beach water-entry tint
    col = mix(col, paleAqua, shoreProx * 0.92);
    float fringe = smoothstep(0.70, 1.0, shoreProx)
                 * (0.45 + 0.55 * vnoise(v_World.xz * 3.0 + u_Time * 0.6));
    col = mix(col, vec3(0.95, 0.97, 1.0), fringe * 0.40);

    // ── Thin atmospheric haze at the horizon line only ───────────────────────
    float seam = smoothstep(0.90, 1.0, distNorm);
    col = mix(col, u_HorizonColor * 0.55, seam * 0.5);

    gl_FragColor = vec4(col, 1.0);
}
