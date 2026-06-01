precision highp float;

uniform vec3  uCamPos;
uniform vec3  uSunDir;       // normalized, world-space toward light
uniform vec3  uSkyTop;
uniform vec3  uSkyBot;
uniform vec3  uSeaDeep;
uniform vec3  uSeaShallow;
uniform vec3  uSunColor;
uniform float uTime;
uniform float uWind;
uniform float uDark;         // 0=day, 1=night

varying vec3  vWorldPos;
varying vec3  vNorm;
varying float vDepth;        // 0=deep, 1=shallow
varying float vFoam;

// ── Noise for sub-vertex ripple perturbation ──────────────────────────────────
float hash(vec2 p) {
    p = fract(p * vec2(234.34, 435.345));
    p += dot(p, p + 34.23);
    return fract(p.x * p.y);
}
float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i),                      hash(i + vec2(1.0, 0.0)), f.x),
               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

void main() {
    vec3 V = normalize(uCamPos - vWorldPos);

    // Sub-vertex micro-normal from noise (high-freq ripples)
    float mAmp = 0.055 + uWind * 0.007;
    vec2  mUV  = vWorldPos.xz * 3.8 + uTime * 0.14;
    float nx   = noise(mUV)               * 2.0 - 1.0;
    float nz   = noise(mUV + vec2(1.7, 3.1)) * 2.0 - 1.0;
    vec3  N    = normalize(vNorm + vec3(nx * mAmp, 0.0, nz * mAmp));

    // ── Fresnel reflection (Schlick, F0=0.02 for water) ──────────────────────
    float cosV   = max(0.0, dot(N, V));
    float fresnel = 0.02 + 0.98 * pow(1.0 - cosV, 5.0);

    // ── Sky reflection colour ─────────────────────────────────────────────────
    vec3  R     = reflect(-V, N);
    float skyT  = clamp(R.y * 2.0 + 0.25, 0.0, 1.0);
    vec3  refl  = mix(uSkyBot, uSkyTop, skyT * skyT);

    // ── Water base colour (depth + shore blend) ───────────────────────────────
    vec3 waterCol = mix(uSeaDeep, uSeaShallow, vDepth * 0.65);

    // ── Blinn-Phong specular ──────────────────────────────────────────────────
    vec3  H        = normalize(V + uSunDir);
    float NdotH    = max(0.0, dot(N, H));
    float shininess = uDark > 0.5 ? 130.0 : 90.0;
    float sunVis   = max(0.0, dot(N, uSunDir));
    float spec     = pow(NdotH, shininess) * sunVis;
    float specStr  = uDark > 0.5 ? 1.30 : 1.90;
    vec3 specular  = uSunColor * spec * specStr;

    // ── Moon glitter sparkles (night only) ────────────────────────────────────
    float glitter = 0.0;
    if (uDark > 0.5) {
        float gHash = hash(floor(vWorldPos.xz * 20.0 + uTime * 0.9));
        float gTw   = 0.5 + 0.5 * sin(uTime * gHash * 5.0 + gHash * 6.28);
        glitter = step(0.90, gHash) * gTw * sunVis * 2.8;
    }

    // ── Ambient ───────────────────────────────────────────────────────────────
    float ambStr = uDark > 0.5 ? 0.16 : 0.32;
    vec3 ambient = waterCol * ambStr;

    // ── Combine via Fresnel ───────────────────────────────────────────────────
    vec3 col = mix(waterCol + ambient, refl, fresnel * 0.88);
    col += specular;
    col += uSunColor * glitter * 0.55;

    // ── Foam ──────────────────────────────────────────────────────────────────
    float foamNoise = noise(vWorldPos.xz * 5.5 + uTime * 0.20);
    float foamAmt   = clamp(vFoam + foamNoise * 0.28 - 0.18, 0.0, 1.0);
    vec3  foamCol   = mix(vec3(0.86, 0.93, 1.0), vec3(1.0), foamAmt);
    col = mix(col, foamCol, foamAmt * foamAmt);

    // ── Atmospheric distance fog ──────────────────────────────────────────────
    float dist   = length(vWorldPos - uCamPos);
    float fogFac = 1.0 - exp(-dist * 0.011);
    vec3  fogCol = mix(uSkyBot, uSkyTop, 0.30);
    col = mix(col, fogCol, clamp(fogFac, 0.0, 0.90));

    gl_FragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
