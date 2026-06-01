precision highp float;
varying vec2 vUV;   // (0,0)=bottom-left  (1,1)=top-right

uniform float       uTime;
uniform float       uDark;
uniform float       uAspect;
uniform float       uTide;
uniform float       uWind;
uniform float       uWindDir;
uniform vec3        uSkyTop;
uniform vec3        uSkyBot;
uniform vec3        uSun;
uniform vec3        uMtn;
uniform vec3        uFlat;
uniform vec3        uSeaDeep;
uniform sampler2D   uMtnTex;

// Screen-space constants (match 3D camera projection empirically)
const float HORIZON_Y  = 0.575;
const float MTN_ZONE_H = 0.18;
const float MOON_X     = 0.72;
const float MOON_Y     = 0.84;

// ── Noise ─────────────────────────────────────────────────────────────────────
float hash(vec2 p) {
    p = fract(p * vec2(234.34, 435.345));
    p += dot(p, p + 34.23);
    return fract(p.x * p.y);
}
float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i),                    hash(i + vec2(1.0, 0.0)), f.x),
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

// ── Sky gradient ──────────────────────────────────────────────────────────────
vec3 renderSky(vec2 uv) {
    float t   = clamp((uv.y - HORIZON_Y) / (1.0 - HORIZON_Y), 0.0, 1.0);
    vec3  col = mix(uSkyBot, uSkyTop, t * t);
    if (uDark < 0.5) {
        vec2  cv = vec2(uv.x * uAspect * 3.2 + uTime * 0.012 * cos(uWindDir),
                        t * 4.2 + 1.5);
        float cd = max(0.0, fbm(cv) - 0.42) * 2.8;
        col = mix(col, mix(uSkyBot * 1.1, vec3(1.0), 0.15), cd * 0.72);
    }
    return col;
}

// ── Stars ─────────────────────────────────────────────────────────────────────
vec3 renderStars(vec2 uv, vec3 base) {
    if (uDark < 0.5) return base;
    float t = clamp((uv.y - HORIZON_Y) / (1.0 - HORIZON_Y), 0.0, 1.0);
    if (t <= 0.0) return base;
    vec2  sv    = uv * vec2(uAspect * 32.0, 38.0);
    float sn    = hash(floor(sv));
    float tw    = 0.5 + 0.5 * sin(uTime * sn * 4.2 + sn * 6.2832);
    float dMoon = length(vec2(uv.x * uAspect - MOON_X * uAspect, uv.y - MOON_Y));
    float mMask = 1.0 - smoothstep(0.030, 0.060, dMoon);
    float star  = step(0.953, sn) * tw * (t * 0.88 + 0.24) * (1.0 - mMask);
    return base + vec3(star);
}

// ── Moon / Sun disc ───────────────────────────────────────────────────────────
vec3 renderMoon(vec2 uv, vec3 base) {
    float d    = length(vec2((uv.x - MOON_X) * uAspect, uv.y - MOON_Y));
    float sunR = uDark > 0.5 ? 0.040 : 0.046;
    float disc = 1.0 - smoothstep(sunR * 0.85, sunR * 1.15, d);
    float glow = exp(-d * (uDark > 0.5 ? 9.0 : 6.0)) * (uDark > 0.5 ? 0.70 : 0.68);
    float limb = uDark > 0.5 ? 1.0 - smoothstep(sunR * 0.55, sunR, d) * 0.35 : 1.0;
    float sbStr = 0.0;
    if (uDark < 0.5) {
        float ang = atan(uv.y - MOON_Y, (uv.x - MOON_X) * uAspect);
        float r1  = pow(max(0.0, cos(ang * 8.0)),  14.0);
        float r2  = pow(max(0.0, cos(ang * 14.0)), 10.0);
        sbStr = (r1 * 0.55 + r2 * 0.40) * exp(-d * 13.0) * (1.0 - disc) * 0.9;
    }
    float inten = uDark > 0.5 ? 1.20 : 1.90;
    float alpha = clamp(disc + glow * (1.0 - disc) + sbStr * (1.0 - disc), 0.0, 1.0);
    return mix(base, uSun * inten * limb, alpha);
}

// ── Mountain silhouette (PNG texture) ─────────────────────────────────────────
vec3 renderMountain(vec2 uv, vec3 base) {
    float zTop = HORIZON_Y + MTN_ZONE_H;
    if (uv.y < HORIZON_Y || uv.y > zTop) return base;
    float tex_v = 1.0 - (uv.y - HORIZON_Y) / MTN_ZONE_H;
    vec4  samp  = texture2D(uMtnTex, vec2(uv.x, tex_v));
    float ridge = 1.0 - tex_v;
    vec3  mCol  = uMtn * (0.80 + ridge * 0.24) + uSun * uDark * ridge * 0.06;
    return mix(base, mCol, samp.a);
}

// ── Tidal flat hint at bottom of screen ──────────────────────────────────────
// The ocean mesh covers the water area. Below the mesh's near edge (at very low
// tide) the sky shader fills in with flat/beach colour.
vec3 renderTidalFlat(vec2 uv) {
    float ft = uv.y / max(0.001, (1.0 - uTide) * 0.22);
    ft = clamp(ft, 0.0, 1.0);
    float wx  = uv.x * uAspect;
    vec3  mud = mix(uFlat * 0.62, uFlat, ft);
    float tex = fbm(vec2(wx * 4.0, ft * 3.5 + uTime * 0.004)) * 0.08 - 0.04;
    float ch  = smoothstep(0.52, 0.78, fbm(vec2(wx * 6.5, ft * 3.2)));
    mud = mix(mud, mud * 0.50, ch * 0.5) + tex;
    // Moonlight sheen on wet surface
    float ms = exp(-pow((uv.x - MOON_X) * uAspect * 3.5, 2.0)) * uDark * 0.26;
    mud += uSun * ms * ft;
    return clamp(mud, 0.0, 1.0);
}

// ── Main ─────────────────────────────────────────────────────────────────────
void main() {
    vec3 col;

    // Tidal flat strip at bottom (exposed when tide is low)
    float flatTop = (1.0 - uTide) * 0.22;
    if (uTide < 0.96 && vUV.y < flatTop - 0.012) {
        col = renderTidalFlat(vUV);

    } else if (uTide < 0.96 && vUV.y < flatTop + 0.018) {
        // Blend edge between flat and sea/sky background
        vec3 flatCol = renderTidalFlat(vUV);
        float b = smoothstep(flatTop - 0.012, flatTop + 0.018, vUV.y);
        col = mix(flatCol, uSeaDeep * 0.55, b);

    } else if (vUV.y < HORIZON_Y - 0.008) {
        // Below horizon: sea background (mostly covered by ocean mesh)
        col = mix(uSeaDeep * 0.55, uSeaDeep * 0.85, vUV.y / HORIZON_Y);

    } else if (vUV.y < HORIZON_Y + 0.016) {
        // Horizon blend band
        vec3 skyCol = renderSky(vUV);
        skyCol = renderStars(vUV, skyCol);
        skyCol = renderMoon(vUV, skyCol);
        skyCol = renderMountain(vUV, skyCol);
        float b = smoothstep(HORIZON_Y - 0.006, HORIZON_Y + 0.018, vUV.y);
        // Horizon foam burst
        float wx = vUV.x * uAspect;
        float lf = fbm(vec2(wx * 9.0 + uTime * 0.14, 2.5)) - 0.38;
        vec3 seaEdge = mix(uSeaDeep * 0.9, vec3(0.92, 0.96, 1.0),
                           max(0.0, lf) * 2.5 * (uWind / 12.0));
        col = mix(seaEdge, skyCol, b);

    } else {
        // Pure sky
        col = renderSky(vUV);
        col = renderStars(vUV, col);
        col = renderMoon(vUV, col);
        col = renderMountain(vUV, col);
    }

    gl_FragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
