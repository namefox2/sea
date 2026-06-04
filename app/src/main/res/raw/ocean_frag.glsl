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
uniform vec3      u_DeepColor;     // far / horizon — deepest navy
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

void main() {
    if (v_World.z > u_WaterlineZ - 0.5) discard;

    // Horizontal distance from camera — the ONLY driver of base colour.
    // near = 0, far = 1. (No Y / wave height in the colour, by design.)
    float dist     = length(v_World.xz - u_CamPos.xz);
    float distNorm = clamp(dist / 70.0, 0.0, 1.0);

    // ── Volume fix: wave undersides (back-faces) are deep water, never sky ────
    // The user must never perceive "under" the surface — only continuous ocean.
    if (!gl_FrontFacing) {
        gl_FragColor = vec4(mix(u_ShallowColor * 0.45, u_DeepColor * 0.85, distNorm), 1.0);
        return;
    }

    // ── 1. Continuous depth colour — strictly monotonic ──────────────────────
    //   near  = bright teal (u_ShallowColor)
    //   far   = deepest navy (u_DeepColor)
    // A single mix from near→far guarantees "밝음 → 진함" with no reversal.
    float depthT = pow(distNorm, 0.82);
    vec3  water  = mix(u_ShallowColor, u_DeepColor, depthT);

    // ── 2. Wave volume: troughs (below local mean level) read as deeper water ─
    // Mean sea level rides with the tide, so measure displacement relative to it.
    float tideY  = u_Tide * 1.4 - 0.7;
    float waveH  = v_World.y - tideY;            // + = crest, − = trough
    float trough = clamp(-waveH * 1.4, 0.0, 1.0);
    water = mix(water, u_DeepColor * 0.55, trough * 0.55);

    // ── Normal map perturbation (small ripples on top of the Gerstner normal) ─
    vec2 uv1   = v_World.xz * 0.15 + u_Time * vec2( 0.012,  0.008);
    vec2 uv2   = v_World.xz * 0.07 - u_Time * vec2( 0.007,  0.011);
    vec3 nm1   = texture2D(u_NormalMap, uv1).rgb * 2.0 - 1.0;
    vec3 nm2   = texture2D(u_NormalMap, uv2).rgb * 2.0 - 1.0;
    vec2 perturb = (nm1.xz + nm2.xz) * 0.35 * u_WindAmp;
    vec3 N = normalize(vec3(v_Normal.x + perturb.x, v_Normal.y, v_Normal.z + perturb.y));
    vec3 V = normalize(u_CamPos - v_World);
    vec3 L = u_LightDir;

    // Base shaded body — diffuse modelling gives the surface its 3D volume.
    float NdotL = max(dot(N, L), 0.0);
    vec3  col   = water * (NdotL * 0.45 + 0.55);

    // ── 3. 윤슬 — present across the WHOLE ocean ──────────────────────────────
    //   far   = small, faint glints
    //   near  = large, bright glints
    // A base layer covers everything; the sun corridor adds extra concentration.
    vec2  lhDir       = normalize(vec2(L.x, L.z));
    vec2  toFrag      = v_World.xz - u_CamPos.xz;
    vec2  perp        = vec2(-lhDir.y, lhDir.x);
    float perpDist    = abs(dot(toFrag, perp));
    float corrHalf    = mix(11.0, 6.0, distNorm);              // wide near, tight far
    float corrMask    = exp(-perpDist * perpDist / (corrHalf * corrHalf));

    // Glint size scales with distance: low frequency (big) near, high (small) far.
    float freq = mix(3.5, 16.0, distNorm);
    float g1   = sin(v_World.x * freq        + v_World.z * freq * 0.8 + u_Time * 2.0);
    float g2   = sin(v_World.x * freq * 1.7  - v_World.z * freq * 1.3 - u_Time * 2.7);
    float glint = pow(clamp(g1 * g2 * 1.3 + 0.35, 0.0, 1.0), mix(2.5, 6.0, distNorm));

    // Brightness: strong near, dim-but-alive far (never zero).
    float glintBright = mix(1.9, 0.40, distNorm);
    // Base sparkle everywhere + corridor boost.
    float sparkle = glint * glintBright * (0.45 + corrMask * 1.4);

    // Broad sun sheen rides the corridor.
    vec3  H    = normalize(L + V);
    float spec = pow(max(dot(N, H), 0.0), mix(140.0, 30.0, u_Roughness)) * corrMask;

    vec3 yunseul = u_LightColor * u_YunseulStr * (sparkle + spec * 0.7);

    // ── Foam on wave crests ──────────────────────────────────────────────────
    float foam = smoothstep(0.4, 0.7, v_Foam) * clamp(u_WindAmp * 2.5, 0.0, 1.0);
    col = mix(col, vec3(0.95, 0.97, 1.00), foam);

    // Wet sheen (Fresnel) — kept to NEAR water only so the far field stays dark.
    float fres = pow(1.0 - max(dot(N, V), 0.0), 5.0);
    vec3  sheen = mix(u_ShallowColor, u_HorizonColor, 0.5) * 0.7;
    col = mix(col, sheen, fres * 0.20 * (1.0 - distNorm) * (1.0 - foam));

    // 윤슬 added on top (visible even at low sun).
    col += yunseul * (1.0 - foam);

    // ── Thin atmospheric seam ONLY at the very horizon line ──────────────────
    // Keeps the far ocean dark; just softens the ocean↔sky edge so there is no
    // hard cut. (Matches the sky-at-horizon clear colour ≈ horizon * 0.5.)
    float seam = smoothstep(0.92, 1.0, distNorm);
    col = mix(col, u_HorizonColor * 0.5, seam * 0.55);

    gl_FragColor = vec4(col, 1.0);
}
