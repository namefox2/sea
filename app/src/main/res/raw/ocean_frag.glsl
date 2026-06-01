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
uniform vec3  uSun;       // sun / moon color
uniform vec3  uMtn;       // mountain silhouette color
uniform vec3  uFlat;      // tidal flat (갯벌) color
uniform float uDark;      // 0 = day,  1 = night

// ── Noise / FBM ───────────────────────────────────────────────────────────────
float hash(vec2 p) {
    p = fract(p * vec2(234.34, 435.345));
    p += dot(p, p + 34.23);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0, a = 0.5;
    mat2 rot = mat2(0.80, 0.60, -0.60, 0.80);
    for (int i = 0; i < 4; i++) {
        v += noise(p) * a;
        p = rot * p * 2.1 + vec2(1.7, 9.2);
        a *= 0.5;
    }
    return v;
}

// ── Main ──────────────────────────────────────────────────────────────────────
void main() {
    float wind = uWind / 12.0;
    float wx   = vUV.x * uAspect;   // aspect-corrected x
    // Scene Y divisions (UV: 0=bottom, 1=top)
    float horizonY =  0.65;        // 0.57–0.65 depending on tide
    float flatTopY = (1.0 - uTide) * 0.20;         // 0 at high tide, 0.27 at low tide

    // Animated wave displacement of the horizon line
    float wD = (sin(wx * 14.0 + uTime * 0.85)  * 0.013
              + sin(wx * 25.0 - uTime * 1.35)  * 0.007
              + sin(wx * 43.0 + uTime * 2.1)   * 0.003) * wind;
    float hY = horizonY + wD;

    vec3 col;
    float sunX = 0.70;

    // ── TIDAL FLAT (갯벌) ──────────────────────────────────────────────────────
    if (vUV.y < flatTopY - 0.008) {
        float ft = vUV.y / max(0.001, flatTopY);           // 0=bottom 1=near water
        vec3 mud  = mix(uFlat * 0.70, uFlat, ft);
        // Muddy texture
        float tex = fbm(vec2(wx * 3.6, ft * 2.8 + uTime * 0.004)) * 0.07 - 0.035;
        float channels = smoothstep( 0.55, 0.80, fbm(vec2(wx * 7.0, ft * 3.0)));

        mud = mix( mud, mud * 0.55, channels * 0.5);
        mud += tex;
        // Tidal pools (dark patches)
        float pool = step(0.63, fbm(vec2(wx * 1.7, ft * 4.2 + 6.0)));
        mud = mix(mud, uSeaBot * 0.55, pool * (1.0 - ft) * 0.55);
        // Wet sheen near waterline
        mud = mix(mud, uSeaTop * 0.45, pow(ft, 2.2) * 0.38);
        col = clamp(mud, 0.0, 1.0);

    // ── FLAT→WATER EDGE ───────────────────────────────────────────────────────
    } else if (vUV.y < flatTopY + 0.018) {
        float b = smoothstep(flatTopY - 0.008, flatTopY + 0.018, vUV.y);
        col = mix(uFlat * 0.82, uSeaBot * 0.9, b);

    // ── WATER ─────────────────────────────────────────────────────────────────
    } else if (vUV.y < hY + 0.015) {
        float waterH = hY - flatTopY;
        // depth: 0 = near viewer (bottom/shallow),  1 = horizon (far/deep)
        float depth  = clamp(1.0 - (hY - vUV.y) / max(0.001, waterH), 0.0, 1.0);

        float swell = sin(wx * 2.0 + uTime * 0.4) * 0.04;
        float chop = sin(wx * 15.0 + uTime * 2.5) * 0.005;
        float wave = swell + chop;

        float bigWave =
              sin(wx * 5.0 + uTime * 0.8) * 0.015
            + sin(wx * 13.0 - uTime * 1.4) * 0.008
            + sin(wx * 27.0 + uTime * 2.2) * 0.004;

        bigWave *= (0.4 + wind * 1.6);

        depth += bigWave;
        depth = clamp(depth,0.0,1.0);

        // Perspective-scaled wave UV
        float psc = 1.0 + (1.0 - depth) * 2.2;
        float spd = 0.10 + wind * 0.22;
        float wdx = cos(uWindDir), wdy = sin(uWindDir);

        vec2 w1 = vec2(wx * psc * 0.75 + uTime * spd * wdx,
                       vUV.y * psc * 1.2  + uTime * spd * wdy * 0.45);
        vec2 w2 = vec2(wx * psc * 0.50 - uTime * spd * 0.55,
                       vUV.y * psc * 0.65 + uTime * 0.038);

        // Surface normals from FBM gradient
        float eps = 0.022;
        float h00 = fbm(w1);
        float ns   = wind * 0.42;
        vec3 norm = normalize(vec3(
            ((fbm(w1 + vec2(eps, 0.0)) - h00) + (fbm(w2 + vec2(eps, 0.0)) - fbm(w2)) * 0.4) * ns,
            1.0,
            ((fbm(w1 + vec2(0.0, eps)) - h00) + (fbm(w2 + vec2(0.0, eps)) - fbm(w2)) * 0.4) * ns
        ));

        // Fresnel: near horizon = reflective, near viewer = transparent
        float fresnelBase = pow(1.0 - clamp(norm.y, 0.0, 1.0), 4.0);
        float fresnel     = mix(fresnelBase, 0.95, depth);
        fresnel = clamp(fresnel, 0.0, 1.0);

        // Water body color (depth-dependent)
        vec3 deepCol = mix(uSeaBot, uSeaTop, depth * 0.75);
        // Sky reflection
        vec3 skyRef  = mix(uSkyBot, uSkyTop, depth * 0.55 + 0.2 + norm.x * 0.15);
        col = mix(deepCol, skyRef, fresnel * 0.72);

        // 윤슬 specular highlight
        float sunElev = uDark > 0.5 ? 0.28 : 0.62;
        vec3 sunDir   = normalize(vec3(cos(uWindDir + 1.0) * 0.45, sunElev,
                                       sin(uWindDir + 1.0) * 0.45));
        vec3 viewDir  = normalize(vec3(0.0, 0.5 + depth * 0.5, 1.0));
        vec3 halfVec  = normalize(sunDir + viewDir);
        float sPow    = 150.0 + wind * 350.0;
        float spec    = pow(max(0.0, dot(norm, halfVec)), sPow);
        // Confine glitter to sun reflection path on water
        float azFrac  = fract((uWindDir + 1.0) / 6.2832);
        float sunPath = exp(-pow((vUV.x - azFrac) * uAspect * 0.32, 2.0));
        spec *= (0.22 + 0.78 * sunPath) * (1.0 - (1.0 - depth) * 0.4);
        float moonFactor = uDark > 0.5 ? 1.8 : 2.8;

        col += uSun * spec * moonFactor;

        float glitterNoise = noise(vec2(vUV.x * 180.0,vUV.y * 350.0- uTime * 3.0));

        float reflectionBand = exp(-pow((vUV.x - sunX)* uAspect* 4.0,2.0));

        float glitter = step(0.975, glitterNoise) * reflectionBand;

        col += uSun * glitter * 1.8;

        // Foam / whitecaps
        float foamTh = 0.67 - wind * 0.10;
        float fNoise = fbm(w1 * 1.45 + vec2(uTime * 0.065, 0.0));
        float foam   = max(0.0, fNoise - foamTh) / (1.0 - foamTh) * wind;
        foam *= (1.0 - (1.0 - depth) * 0.6);
        col = mix(col, vec3(0.91, 0.94, 0.99), foam * 0.82);
        float shoreFoam = smoothstep(flatTopY + 0.01, flatTopY + 0.05, vUV.y);

        shoreFoam = 1.0 - shoreFoam;

        shoreFoam *= 0.5 + 0.5 * sin( wx * 25.0 + uTime * 3.0 );

        col += vec3(1.0) * shoreFoam * 0.35;

        // Caustics in very shallow water (near viewer)
        if (depth > 0.62) {
            vec2 cauv = vec2(wx * 4.2 + uTime * 0.24, vUV.y * 3.2 - uTime * 0.17);
            float c   = max(0.0, noise(cauv) + noise(cauv * 1.5 + vec2(2.3, 1.9)) - 1.05);
            col += uSun * pow(c, 2.2) * (depth - 0.62) * 1.6 * (1.0 - uDark * 0.82);
        }

        // Atmospheric perspective toward horizon
        vec3 haze = mix(uSkyBot, uSeaTop * 1.08, 0.32);
        col = mix(col, haze, pow(1.0 - depth, 3.8) * 0.32);

    // ── WATER→SKY TRANSITION ──────────────────────────────────────────────────
    } else if (vUV.y < hY + 0.030) {
        float b = smoothstep(hY + 0.015, hY + 0.028, vUV.y);
        vec3 seaSurf = uSeaTop * 1.04;
        // Foam at waterline
        float lf = fbm(vec2(wx * 8.5 + uTime * 0.11, 2.0)) - 0.40;
        seaSurf = mix(seaSurf, vec3(0.91, 0.95, 1.0), max(0.0, lf) * 2.2 * wind * (1.0 - b));
        col = mix(seaSurf, uSkyBot, b);

    // ── SKY ───────────────────────────────────────────────────────────────────
    } else {
        float skyT = clamp((vUV.y - hY - 0.030) / (1.0 - hY - 0.030), 0.0, 1.0);
        col = mix(uSkyBot, uSkyTop, skyT * skyT);

        // Mountain silhouette just above horizon
        float mh  = exp(-pow((wx - uAspect*0.11)*4.5, 2.0)) * 0.095
                  + exp(-pow((wx - uAspect*0.30)*6.0, 2.0)) * 0.068
                  + exp(-pow((wx - uAspect*0.53)*5.2, 2.0)) * 0.085
                  + exp(-pow((wx - uAspect*0.73)*7.0, 2.0)) * 0.057
                  + exp(-pow((wx - uAspect*0.89)*5.5, 2.0)) * 0.074
                  + noise(vec2(wx * 7.0, 1.0)) * 0.011;
        float mTop = hY + 0.030 + mh * 0.42;
        if (vUV.y < mTop + 0.005) {
            float mb = smoothstep(mTop - 0.003, mTop + 0.005, vUV.y);
            col = mix(uMtn * (0.88 + skyT * 0.14), col, mb);
        }

        // Sun disc + corona
        float sunY = hY + 0.04 + (1.0 - hY - 0.04) * (uDark > 0.5 ? 0.48 : 0.76);
        float sunR = uDark > 0.5 ? 0.026 : 0.036;
        float dSun = length(vec2((vUV.x - sunX) * uAspect, vUV.y - sunY));
        float disc  = 1.0 - smoothstep(sunR * 0.88, sunR * 1.12, dSun);
        float glow  = exp(-dSun * (uDark > 0.5 ? 14.0 : 6.5)) * (uDark > 0.5 ? 0.30 : 0.62);

        // Starburst rays (day only)
        float sbStr = 0.0;
        if (uDark < 0.5) {
            float ang = atan(vUV.y - sunY, (vUV.x - sunX) * uAspect);
            float r1 = pow(max(0.0, cos(ang * 8.0)), 14.0);
            float r2 = pow(max(0.0, cos(ang * 14.0)), 10.0);
            sbStr = (r1 * 0.55 + r2 * 0.40) * exp(-dSun * 13.0) * (1.0 - disc) * 0.9;
        }

        col = mix(col, uSun * (uDark > 0.5 ? 1.15 : 1.8), disc + glow * (1.0 - disc) + sbStr * (1.0 - disc));

        // Stars (night)
        if (uDark > 0.5) {
            vec2 sv  = vUV * vec2(uAspect * 26.0, 32.0);
            float sn = hash(floor(sv));
            float tw = 0.5 + 0.5 * sin(uTime * sn * 3.8 + sn * 6.28);
            col += vec3(step(0.964, sn) * tw * (skyT * 0.85 + 0.18) * 0.78);
        }

        // Clouds (day)
        if (uDark < 0.5) {
            vec2 cv = vec2(vUV.x * uAspect * 3.0 + uTime * 0.011, skyT * 4.0 + 1.5);
            float cd = max(0.0, fbm(cv) - 0.43) * 2.7;
            vec3 cloudC = mix(vec3(0.88, 0.91, 0.96), vec3(0.98, 0.98, 1.0), 0.5);
            col = mix(col, cloudC, cd * 0.68);
        }
    }

    gl_FragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
