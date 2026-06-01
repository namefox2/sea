precision highp float;
varying vec2 vUV;   // (0,0)=bottom-left  (1,1)=top-right

// ── Uniforms ──────────────────────────────────────────────────────────────────
uniform float uTime;
uniform float uTide;      // 0..1
uniform float uWind;      // 0..12
uniform float uWindDir;   // radians
uniform float uAspect;    // width / height
uniform vec3  uSkyTop;
uniform vec3  uSkyBot;
uniform vec3  uSeaTop;
uniform vec3  uSeaBot;
uniform vec3  uSun;
uniform vec3  uMtn;
uniform vec3  uFlat;
uniform float uDark;      // 0=day  1=night

// ── Fixed scene geometry ──────────────────────────────────────────────────────
// Horizon is always at HORIZON_Y. Only the shoreline moves with tide.
const float HORIZON_Y = 0.62;
const float MOON_X    = 0.72;   // fixed horizontal position
const float MOON_Y    = 0.820;  // fixed vertical position

// ── Noise ─────────────────────────────────────────────────────────────────────
float hash(vec2 p) {
    p = fract(p * vec2(234.34, 435.345));
    p += dot(p, p + 34.23);
    return fract(p.x * p.y);
}
float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i),                   hash(i + vec2(1.0, 0.0)), f.x),
               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}
float fbm(vec2 p) {
    float v = 0.0, a = 0.5;
    mat2 rot = mat2(0.80, 0.60, -0.60, 0.80);
    for (int i = 0; i < 4; i++) {
        v += noise(p) * a;
        p  = rot * p * 2.1 + vec2(1.7, 9.2);
        a *= 0.5;
    }
    return v;
}

// ── Gerstner wave cascade ─────────────────────────────────────────────────────
// Returns height displacement and 2D screen-space normal gradient via out params.
// These affect SHADING ONLY — they never shift boundary positions.
void computeWaves(vec2 uv, float wind, out float wH, out vec2 wN) {
    float wx  = uv.x * uAspect;
    vec2  pos = vec2(wx, uv.y);
    float ws  = 0.20 + wind * 0.80;
    float cdx = cos(uWindDir);
    float cdy = sin(uWindDir) * 0.22;

    wH = 0.0;
    wN = vec2(0.0);

    // Wave 1 — long swell
    vec2  d1  = normalize(vec2(cdx, cdy));
    float ph1 = 3.0 * dot(d1, pos) - 0.50 * uTime;
    wH += 0.016 * ws * sin(ph1);
    wN += 0.016 * ws * 3.0 * cos(ph1) * d1;

    // Wave 2 — medium, off-axis
    vec2  d2  = normalize(vec2(cdx + 0.38, cdy));
    float ph2 = 6.5 * dot(d2, pos) - 0.78 * uTime;
    wH += 0.010 * ws * sin(ph2);
    wN += 0.010 * ws * 6.5 * cos(ph2) * d2;

    // Wave 3 — chop
    vec2  d3  = normalize(vec2(cdx - 0.50, cdy + 0.18));
    float ph3 = 13.5 * dot(d3, pos) - 1.28 * uTime;
    wH += 0.006 * ws * sin(ph3);
    wN += 0.006 * ws * 13.5 * cos(ph3) * d3;

    // Wave 4 — micro ripple
    vec2  d4  = normalize(vec2(cdx + 0.72, cdy - 0.22));
    float ph4 = 26.0 * dot(d4, pos) - 1.90 * uTime;
    wH += 0.003 * ws * sin(ph4);
    wN += 0.003 * ws * 26.0 * cos(ph4) * d4;
}

// ── Layer: Sky ────────────────────────────────────────────────────────────────
vec3 renderSky(vec2 uv) {
    float t   = clamp((uv.y - HORIZON_Y) / (1.0 - HORIZON_Y), 0.0, 1.0);
    vec3  col = mix(uSkyBot, uSkyTop, t * t);
    if (uDark < 0.5) {
        vec2  cv = vec2(uv.x * uAspect * 3.2 + uTime * 0.012, t * 4.2 + 1.5);
        float cd = max(0.0, fbm(cv) - 0.42) * 2.8;
        col = mix(col, vec3(0.88, 0.92, 0.97) * 1.02, cd * 0.70);
    }
    return col;
}

// ── Layer: Stars ──────────────────────────────────────────────────────────────
vec3 renderStars(vec2 uv, vec3 base) {
    if (uDark < 0.5) return base;
    float t = clamp((uv.y - HORIZON_Y) / (1.0 - HORIZON_Y), 0.0, 1.0);
    if (t <= 0.0) return base;
    vec2  sv     = uv * vec2(uAspect * 30.0, 36.0);
    float sn     = hash(floor(sv));
    float tw     = 0.5 + 0.5 * sin(uTime * sn * 4.2 + sn * 6.2832);
    // Mask stars behind moon disc
    vec2  uvA    = vec2(uv.x * uAspect, uv.y);
    float dMoon  = length(uvA - vec2(MOON_X * uAspect, MOON_Y));
    float mMask  = 1.0 - smoothstep(0.030, 0.058, dMoon);
    float star   = step(0.955, sn) * tw * (t * 0.90 + 0.22) * 0.92 * (1.0 - mMask);
    return base + vec3(star);
}

// ── Layer: Moon / Sun ─────────────────────────────────────────────────────────
vec3 renderMoon(vec2 uv, vec3 base) {
    vec2  uvA  = vec2(uv.x * uAspect, uv.y);
    float d    = length(uvA - vec2(MOON_X * uAspect, MOON_Y));
    float sunR = uDark > 0.5 ? 0.038 : 0.044;
    float disc = 1.0 - smoothstep(sunR * 0.85, sunR * 1.15, d);
    float hR   = uDark > 0.5 ? 9.0 : 6.0;
    float glow = exp(-d * hR) * (uDark > 0.5 ? 0.65 : 0.65);
    float limb = uDark > 0.5
                 ? 1.0 - smoothstep(sunR * 0.55, sunR, d) * 0.35
                 : 1.0;
    float sbStr = 0.0;
    if (uDark < 0.5) {
        float ang = atan(uv.y - MOON_Y, (uv.x - MOON_X) * uAspect);
        float r1  = pow(max(0.0, cos(ang * 8.0)),  14.0);
        float r2  = pow(max(0.0, cos(ang * 14.0)), 10.0);
        sbStr = (r1 * 0.55 + r2 * 0.40) * exp(-d * 13.0) * (1.0 - disc) * 0.9;
    }
    float inten = uDark > 0.5 ? 1.15 : 1.85;
    float alpha = clamp(disc + glow * (1.0 - disc) + sbStr * (1.0 - disc), 0.0, 1.0);
    return mix(base, uSun * inten * limb, alpha);
}

// ── Layer: Mountains ──────────────────────────────────────────────────────────
vec3 renderMountains(vec2 uv, vec3 base) {
    float wx  = uv.x * uAspect;
    float t   = clamp((uv.y - HORIZON_Y) / (1.0 - HORIZON_Y), 0.0, 1.0);
    float mh  = exp(-pow((wx - uAspect * 0.10) * 4.5, 2.0)) * 0.10
              + exp(-pow((wx - uAspect * 0.29) * 6.0, 2.0)) * 0.07
              + exp(-pow((wx - uAspect * 0.52) * 5.2, 2.0)) * 0.09
              + exp(-pow((wx - uAspect * 0.72) * 7.0, 2.0)) * 0.06
              + exp(-pow((wx - uAspect * 0.88) * 5.5, 2.0)) * 0.08
              + noise(vec2(wx * 7.2, 1.0)) * 0.012;
    float mTop = HORIZON_Y + 0.005 + mh * 0.44;
    if (uv.y < mTop) {
        float mb = smoothstep(mTop - 0.004, mTop + 0.007, uv.y);
        return mix(uMtn * (0.84 + t * 0.20), base, mb);
    }
    return base;
}

// ── Layer: Ocean ──────────────────────────────────────────────────────────────
// depth 0 = near shore, 1 = at horizon (far).
vec3 renderOcean(vec2 uv, float shoreY, float wind, float wH, vec2 wN) {
    float waterH = HORIZON_Y - shoreY;
    float depth  = clamp((uv.y - shoreY) / max(0.001, waterH), 0.0, 1.0);

    // Wave height shifts perceived depth for color shimmer
    float wVis = clamp(depth + wH * 2.2, 0.0, 1.0);

    // Surface normal from Gerstner gradients
    vec3 norm = normalize(vec3(wN.x * 0.85, 1.0, wN.y * 0.55));

    // Fresnel
    float fresnel = clamp(
        mix(pow(1.0 - clamp(norm.y, 0.0, 1.0), 4.0), 0.95, depth),
        0.0, 1.0);

    // Water body + sky reflection
    vec3 deepCol = mix(uSeaBot, uSeaTop, wVis * 0.78);
    vec3 skyRef  = mix(uSkyBot, uSkyTop, depth * 0.55 + 0.2 + norm.x * 0.15);
    vec3 col     = mix(deepCol, skyRef, fresnel * 0.72);

    // ── 윤슬: specular reflection under the moon ──────────────────────────────
    vec3  sunDir  = normalize(vec3((MOON_X - 0.5) * 0.9, 0.62, 0.6));
    vec3  viewDir = normalize(vec3(0.0, 0.45 + depth * 0.55, 1.0));
    vec3  halfV   = normalize(sunDir + viewDir);
    float sPow    = uDark > 0.5
                    ? (65.0 + wind * 120.0)
                    : (200.0 + wind * 420.0);
    float spec    = pow(max(0.0, dot(norm, halfV)), sPow);
    // Reflection column — widens with wind
    float colW   = 0.24 + wind * 0.16;
    float sunPth = exp(-pow((uv.x - MOON_X) * uAspect * colW * 2.8, 2.0));
    spec *= (0.10 + 0.90 * sunPth) * (1.0 - (1.0 - depth) * 0.38);
    float mFac = uDark > 0.5 ? 5.0 : 2.8;
    col += uSun * spec * mFac;

    // Glitter sparkles along reflection band
    float gN    = noise(vec2(uv.x * 210.0, uv.y * 390.0 - uTime * 3.4));
    float gBand = exp(-pow((uv.x - MOON_X) * uAspect * (4.2 - wind * 1.2), 2.0));
    col += uSun * step(0.968, gN) * gBand * 3.0;

    // Caustics in very shallow near-viewer water
    if (depth > 0.65) {
        float wx   = uv.x * uAspect;
        vec2  cauv = vec2(wx * 4.4 + uTime * 0.26, uv.y * 3.4 - uTime * 0.18);
        float c    = max(0.0, noise(cauv) + noise(cauv * 1.6 + vec2(2.3, 1.9)) - 1.05);
        col += uSun * pow(c, 2.0) * (depth - 0.65) * 2.0 * (1.0 - uDark * 0.85);
    }

    // Atmospheric haze toward horizon
    vec3 haze = mix(uSkyBot, uSeaTop * 1.05, 0.30);
    col = mix(col, haze, pow(1.0 - depth, 3.5) * 0.28);

    return col;
}

// ── Layer: Foam ───────────────────────────────────────────────────────────────
vec3 renderFoam(vec2 uv, float shoreY, float wind, float wH, vec2 wN, vec3 base) {
    float wx     = uv.x * uAspect;
    float waterH = HORIZON_Y - shoreY;
    float depth  = clamp((uv.y - shoreY) / max(0.001, waterH), 0.0, 1.0);

    float psc = 1.0 + (1.0 - depth) * 2.4;
    float spd = 0.10 + wind * 0.24;
    float wdx = cos(uWindDir), wdy = sin(uWindDir);
    vec2 w1 = vec2(wx * psc * 0.76 + uTime * spd * wdx,
                   uv.y * psc * 1.25 + uTime * spd * wdy * 0.45);

    // Whitecaps
    float foamTh = 0.66 - wind * 0.11;
    float fN     = fbm(w1 * 1.5 + vec2(uTime * 0.07, 0.0));
    float foam   = max(0.0, fN - foamTh) / max(0.001, 1.0 - foamTh) * wind;
    foam *= 1.0 - (1.0 - depth) * 0.55;
    vec3 col = mix(base, vec3(0.92, 0.95, 1.00), foam * 0.85);

    // Shore break at fixed waterline
    float shD = smoothstep(shoreY, shoreY + 0.058, uv.y);
    float shF = (1.0 - shD) * 0.65 * (0.5 + 0.5 * sin(wx * 22.0 + uTime * 3.2));
    col += vec3(shF * 0.50);

    return col;
}

// ── Layer: Tidal Flat ─────────────────────────────────────────────────────────
vec3 renderTidalFlat(vec2 uv, float shoreY) {
    float wx = uv.x * uAspect;
    float ft = clamp(uv.y / max(0.001, shoreY), 0.0, 1.0);

    vec3  mud = mix(uFlat * 0.65, uFlat, ft);
    float tex = fbm(vec2(wx * 3.8, ft * 3.0 + uTime * 0.004)) * 0.08 - 0.04;
    float ch  = smoothstep(0.52, 0.78, fbm(vec2(wx * 6.5, ft * 3.2)));
    mud = mix(mud, mud * 0.50, ch * 0.5) + tex;
    float pool = step(0.62, fbm(vec2(wx * 1.6, ft * 4.0 + 6.0)));
    mud = mix(mud, uSeaBot * 0.5, pool * (1.0 - ft) * 0.55);
    mud = mix(mud, uSeaTop * 0.45, pow(ft, 2.0) * 0.40);
    // Moonlight sheen on wet sand
    float mSheen = exp(-pow((uv.x - MOON_X) * uAspect * 3.5, 2.0)) * uDark * 0.24;
    mud += uSun * mSheen * ft;

    return clamp(mud, 0.0, 1.0);
}

// ── Main: layer compositing ───────────────────────────────────────────────────
// Fixed boundaries: HORIZON_Y (sky/sea line) — NEVER moves.
// Moving boundary: shoreY (flat/water line) — moves with tide only.
// Waves affect color and shading only; they never shift boundaries.
void main() {
    float wind   = uWind / 12.0;
    float shoreY = max(0.002, (1.0 - uTide) * 0.300 - 0.010);

    vec3 col;

    if (vUV.y >= HORIZON_Y + 0.020) {
        // ── Pure sky ─────────────────────────────────────────────────────────
        col = renderSky(vUV);
        col = renderStars(vUV, col);
        col = renderMoon(vUV, col);
        col = renderMountains(vUV, col);

    } else if (vUV.y >= HORIZON_Y - 0.008) {
        // ── Horizon blend band (~28px, fixed) ────────────────────────────────
        vec3 skyCol = renderSky(vUV);
        skyCol = renderStars(vUV, skyCol);
        skyCol = renderMoon(vUV, skyCol);
        skyCol = renderMountains(vUV, skyCol);

        float wH; vec2 wN;
        computeWaves(vUV, wind, wH, wN);
        vec3 seaCol = renderOcean(vUV, shoreY, wind, wH, wN);
        seaCol = renderFoam(vUV, shoreY, wind, wH, wN, seaCol);

        // Static foam burst right at the horizon line (no wave jitter)
        float wx = vUV.x * uAspect;
        float lf = fbm(vec2(wx * 9.0 + uTime * 0.12, 2.5)) - 0.38;
        float bb = smoothstep(HORIZON_Y - 0.006, HORIZON_Y + 0.022, vUV.y);
        seaCol = mix(seaCol, vec3(0.92, 0.96, 1.0),
                     max(0.0, lf) * 2.5 * wind * (1.0 - bb));
        col = mix(seaCol, skyCol, bb);

    } else if (uTide > 0.97 || vUV.y >= shoreY + 0.024) {
        // ── Open water ───────────────────────────────────────────────────────
        float wH; vec2 wN;
        computeWaves(vUV, wind, wH, wN);
        col = renderOcean(vUV, shoreY, wind, wH, wN);
        col = renderFoam(vUV, shoreY, wind, wH, wN, col);

    } else if (vUV.y >= shoreY - 0.010) {
        // ── Shore blend band (moves with tide) ───────────────────────────────
        vec3 flatCol = renderTidalFlat(vUV, shoreY);
        float wH; vec2 wN;
        computeWaves(vUV, wind, wH, wN);
        vec3 waterCol = renderOcean(vUV, shoreY, wind, wH, wN);
        waterCol = renderFoam(vUV, shoreY, wind, wH, wN, waterCol);
        float b = smoothstep(shoreY - 0.010, shoreY + 0.024, vUV.y);
        col = mix(flatCol, waterCol, b);

    } else {
        // ── Tidal flat ───────────────────────────────────────────────────────
        col = renderTidalFlat(vUV, shoreY);
    }

    gl_FragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
