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
uniform float uDark;      // 0 = day,  1 = night

// ── Noise / FBM ───────────────────────────────────────────────────────────────
float hash(vec2 p) {
    p = fract(p * vec2(234.34, 435.345));
    p += dot(p, p + 34.23);
    return fract(p.x * p.y);
}
float noise(vec2 p) {
    vec2 i = floor(p); vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i+vec2(1,0)), f.x),
               mix(hash(i+vec2(0,1)), hash(i+vec2(1,1)), f.x), f.y);
}
float fbm(vec2 p) {
    float v = 0.0, a = 0.5;
    mat2 rot = mat2(0.80, 0.60, -0.60, 0.80);
    for (int i = 0; i < 4; i++) { v += noise(p)*a; p = rot*p*2.1+vec2(1.7,9.2); a *= 0.5; }
    return v;
}

// ── Main ──────────────────────────────────────────────────────────────────────
void main() {
    float wind  = uWind / 12.0;
    float wx    = vUV.x * uAspect;

    // Sun/moon disc position (fixed at upper-right for natural look)
    float sunX  = 0.72;
    float sunY0 = 0.70;   // relative sky position (0=horizon 1=top)

    // Horizon & tidal-flat top
    float horizonY = 0.62 + uTide * 0.08;          // rises with tide
    float flatTopY = max(0.0, (1.0 - uTide) * 0.28 - 0.02);

    // Animated horizon wave displacement
    float wD = (sin(wx*12.0 + uTime*0.80)*0.014
              + sin(wx*23.0 - uTime*1.30)*0.008
              + sin(wx*41.0 + uTime*2.10)*0.003) * wind;
    float hY = horizonY + wD;

    vec3 col;

    // ── TIDAL FLAT (갯벌 / wet sand) ──────────────────────────────────────────
    if (vUV.y < flatTopY - 0.006) {
        float ft = vUV.y / max(0.001, flatTopY);
        vec3 mud = mix(uFlat * 0.65, uFlat, ft);
        float tex = fbm(vec2(wx*3.8, ft*3.0 + uTime*0.004))*0.08 - 0.04;
        float ch  = smoothstep(0.52, 0.78, fbm(vec2(wx*6.5, ft*3.2)));
        mud = mix(mud, mud*0.50, ch*0.5) + tex;
        // Tidal pools
        float pool = step(0.62, fbm(vec2(wx*1.6, ft*4.0+6.0)));
        mud = mix(mud, uSeaBot*0.5, pool*(1.0-ft)*0.55);
        // Wet sheen near waterline
        mud = mix(mud, uSeaTop*0.45, pow(ft, 2.0)*0.40);
        // Moonlight on wet sand (night)
        float moonSheen = exp(-pow((vUV.x - sunX)*uAspect*3.5, 2.0)) * uDark * 0.18;
        mud += uSun * moonSheen * ft;
        col = clamp(mud, 0.0, 1.0);

    // ── FLAT→WATER EDGE ───────────────────────────────────────────────────────
    } else if (vUV.y < flatTopY + 0.020) {
        float b = smoothstep(flatTopY-0.006, flatTopY+0.020, vUV.y);
        col = mix(uFlat*0.80, uSeaBot*0.85, b);

    // ── WATER ─────────────────────────────────────────────────────────────────
    } else if (vUV.y < hY + 0.018) {
        float waterH = hY - flatTopY;
        float depth  = clamp(1.0-(hY-vUV.y)/max(0.001,waterH), 0.0, 1.0);

        // Wave oscillation
        float swell   = sin(wx*2.0 + uTime*0.38)*0.045;
        float chop    = sin(wx*16.0+ uTime*2.60)*0.006;
        float bigWave = (sin(wx*5.0+uTime*0.82)*0.016
                       + sin(wx*13.0-uTime*1.45)*0.009
                       + sin(wx*28.0+uTime*2.30)*0.004) * (0.4+wind*1.6);
        depth = clamp(depth + swell + chop + bigWave, 0.0, 1.0);

        // Perspective-scaled wave UVs for normal estimation
        float psc = 1.0+(1.0-depth)*2.4;
        float spd = 0.10+wind*0.24;
        float wdx = cos(uWindDir), wdy = sin(uWindDir);
        vec2 w1 = vec2(wx*psc*0.76+uTime*spd*wdx, vUV.y*psc*1.25+uTime*spd*wdy*0.45);
        vec2 w2 = vec2(wx*psc*0.52-uTime*spd*0.56, vUV.y*psc*0.67+uTime*0.040);

        // Surface normal via FBM gradient
        float eps = 0.022; float h00 = fbm(w1); float ns = wind*0.44;
        vec3 norm = normalize(vec3(
            ((fbm(w1+vec2(eps,0.))-h00)+(fbm(w2+vec2(eps,0.))-fbm(w2))*0.4)*ns, 1.0,
            ((fbm(w1+vec2(0.,eps))-h00)+(fbm(w2+vec2(0.,eps))-fbm(w2))*0.4)*ns));

        // Fresnel
        float fresnel = clamp(mix(pow(1.0-clamp(norm.y,0.,1.),4.0), 0.95, depth), 0.,1.);

        // Water body + sky reflection
        vec3 deepCol = mix(uSeaBot, uSeaTop, depth*0.78);
        vec3 skyRef  = mix(uSkyBot, uSkyTop, depth*0.55+0.2+norm.x*0.15);
        col = mix(deepCol, skyRef, fresnel*0.72);

        // ── 윤슬: moonlight/sunlight specular path on water ──────────────────
        float sunElev = uDark > 0.5 ? 0.22 : 0.60;
        vec3 sunDir   = normalize(vec3((sunX-0.5)*0.9, sunElev, 0.6));
        vec3 viewDir  = normalize(vec3(0.0, 0.45+depth*0.55, 1.0));
        vec3 halfVec  = normalize(sunDir+viewDir);
        // Night: softer (lower power), Day: sharper highlight
        float sPow    = uDark > 0.5 ? (60.0+wind*120.0) : (200.0+wind*400.0);
        float spec    = pow(max(0.0, dot(norm, halfVec)), sPow);

        // Reflection column aligned to sun/moon x position
        float sunPath = exp(-pow((vUV.x-sunX)*uAspect*0.28, 2.0));
        spec *= (0.18+0.82*sunPath)*(1.0-(1.0-depth)*0.35);

        float moonFactor = uDark > 0.5 ? 3.5 : 2.8;   // moon reflection brighter
        col += uSun * spec * moonFactor;

        // Fine glitter sparkles along reflection path
        float glitterN = noise(vec2(vUV.x*200.0, vUV.y*380.0-uTime*3.2));
        float refBand  = exp(-pow((vUV.x-sunX)*uAspect*3.8, 2.0));
        col += uSun * step(0.972, glitterN) * refBand * 2.2;

        // ── Foam / whitecaps ─────────────────────────────────────────────────
        float foamTh = 0.66 - wind*0.11;
        float fNoise = fbm(w1*1.5 + vec2(uTime*0.07, 0.0));
        float foam   = max(0.0, fNoise-foamTh)/(1.0-foamTh)*wind;
        foam *= (1.0-(1.0-depth)*0.55);
        col = mix(col, vec3(0.92,0.95,1.00), foam*0.85);

        // Shore foam — animated crests near waterline
        float shoreDist = smoothstep(flatTopY+0.01, flatTopY+0.06, vUV.y);
        float shoreFoam = (1.0-shoreDist)*0.6*(0.5+0.5*sin(wx*22.0+uTime*3.2));
        col += vec3(1.0)*shoreFoam*0.45;

        // Caustics in shallow near-viewer water
        if (depth > 0.60) {
            vec2 cauv = vec2(wx*4.4+uTime*0.26, vUV.y*3.4-uTime*0.18);
            float c   = max(0.0, noise(cauv)+noise(cauv*1.6+vec2(2.3,1.9))-1.05);
            col += uSun * pow(c,2.0)*(depth-0.60)*1.8*(1.0-uDark*0.85);
        }

        // Atmospheric haze toward horizon
        vec3 haze = mix(uSkyBot, uSeaTop*1.05, 0.30);
        col = mix(col, haze, pow(1.0-depth, 3.5)*0.30);

    // ── WATER→SKY TRANSITION ──────────────────────────────────────────────────
    } else if (vUV.y < hY + 0.032) {
        float b = smoothstep(hY+0.016, hY+0.030, vUV.y);
        vec3 seaSurf = uSeaTop*1.05;
        // Waterline foam burst
        float lf = fbm(vec2(wx*9.0+uTime*0.12, 2.5))-0.38;
        seaSurf = mix(seaSurf, vec3(0.92,0.96,1.0), max(0.0,lf)*2.5*wind*(1.0-b));
        col = mix(seaSurf, uSkyBot, b);

    // ── SKY ───────────────────────────────────────────────────────────────────
    } else {
        float skyT = clamp((vUV.y-hY-0.032)/(1.0-hY-0.032), 0.0, 1.0);
        col = mix(uSkyBot, uSkyTop, skyT*skyT);

        // Mountain silhouette
        float mh = exp(-pow((wx-uAspect*0.10)*4.5,2.))*0.10
                 + exp(-pow((wx-uAspect*0.29)*6.0,2.))*0.07
                 + exp(-pow((wx-uAspect*0.52)*5.2,2.))*0.09
                 + exp(-pow((wx-uAspect*0.72)*7.0,2.))*0.06
                 + exp(-pow((wx-uAspect*0.88)*5.5,2.))*0.08
                 + noise(vec2(wx*7.2,1.0))*0.012;
        float mTop = hY+0.032+mh*0.44;
        if (vUV.y < mTop+0.006) {
            float mb = smoothstep(mTop-0.003, mTop+0.006, vUV.y);
            col = mix(uMtn*(0.86+skyT*0.16), col, mb);
        }

        // Sun / Moon disc + corona
        float skyFrac = skyT;
        float moonH   = hY+0.035+(1.0-hY-0.035)*(uDark>0.5 ? 0.52 : 0.78);
        float sunR    = uDark>0.5 ? 0.032 : 0.040;    // moon slightly larger
        float dSun    = length(vec2((vUV.x-sunX)*uAspect, vUV.y-moonH));
        float disc    = 1.0-smoothstep(sunR*0.85, sunR*1.15, dSun);
        // Strong halo for moon
        float haloR   = uDark>0.5 ? 10.0 : 6.0;
        float glow    = exp(-dSun*haloR)*(uDark>0.5 ? 0.55 : 0.65);

        // Starburst rays (day)
        float sbStr = 0.0;
        if (uDark < 0.5) {
            float ang = atan(vUV.y-moonH, (vUV.x-sunX)*uAspect);
            float r1 = pow(max(0.0,cos(ang*8.0)),14.0);
            float r2 = pow(max(0.0,cos(ang*14.0)),10.0);
            sbStr = (r1*0.55+r2*0.40)*exp(-dSun*13.0)*(1.0-disc)*0.9;
        }
        // Moon atmospheric rim darkening
        float limb = uDark>0.5 ? 1.0-smoothstep(sunR*0.55, sunR, dSun)*0.35 : 1.0;
        col = mix(col, uSun*(uDark>0.5?1.10:1.85)*limb,
                  disc+glow*(1.0-disc)+sbStr*(1.0-disc));

        // Stars — brighter and more twinkly at night
        if (uDark > 0.5) {
            vec2 sv  = vUV * vec2(uAspect*30.0, 36.0);
            float sn = hash(floor(sv));
            float tw = 0.5+0.5*sin(uTime*sn*4.2+sn*6.28);
            float stMask = 1.0-disc-glow*0.5;       // don't draw stars on moon disc
            col += vec3(step(0.955, sn)*tw*(skyT*0.90+0.22)*0.90)*max(0.0,stMask);
        }

        // Clouds (day)
        if (uDark < 0.5) {
            vec2 cv = vec2(vUV.x*uAspect*3.2+uTime*0.012, skyT*4.2+1.5);
            float cd = max(0.0, fbm(cv)-0.42)*2.8;
            col = mix(col, vec3(0.88,0.92,0.97)*1.02, cd*0.70);
        }
    }

    gl_FragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
