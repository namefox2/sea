#if GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
attribute vec3 a_Pos;
uniform mat4  u_MVP;
uniform float u_Time;
uniform float u_WindAmp;
uniform float u_WindDir;
uniform float u_Tide;

varying vec3  v_World;
varying vec3  v_Normal;
varying float v_Foam;

// Gerstner displacement; accumulates into normal n.
// normalScale exaggerates the normal's XZ tilt for visible lighting contrast
// from a low viewing angle (visual only — geometry unchanged).
vec3 gerstner(vec2 xz0, vec2 dir, float amp, float L, float speed, float t,
              inout vec3 n, float normalScale) {
    float k     = 6.28318 / L;
    float phase = k * dot(dir, xz0) - speed * t;
    float C = cos(phase);
    float S = sin(phase);
    float Q = 0.40;
    vec3 d;
    d.x = Q * amp * dir.x * C;
    d.y = amp * S;
    d.z = Q * amp * dir.y * C;
    n.x -= dir.x * k * amp * C * normalScale;
    n.y -= Q   * k * amp * S;
    n.z -= dir.y * k * amp * C * normalScale;
    return d;
}

void main() {
    float w = u_WindAmp;

    // Non-linear amplitude: gentle ripples when calm, tall swells in strong wind.
    //   w=0.0(0bft)  → amp 0.06 m
    //   w=0.25(3bft) → amp 0.14 m
    //   w=0.92(11bft)→ amp 0.75 m
    float amp = w * w * 0.62 + w * 0.18 + 0.06;
    // Longer, faster swells as wind rises.
    float L0  = mix(6.0, 13.0, w);
    float spd = mix(0.9, 1.7,  w);
    float wd  = u_WindDir;
    float ns  = 1.7 + w * 0.9;     // stronger normal contrast in wind

    vec3 p = a_Pos;
    vec3 n = vec3(0.0, 1.0, 0.0);

    p += gerstner(a_Pos.xz, normalize(vec2(cos(wd),      sin(wd))),      amp*1.00, L0*1.00, spd*1.0, u_Time, n, ns);
    p += gerstner(a_Pos.xz, normalize(vec2(cos(wd+0.45), sin(wd+0.45))), amp*0.55, L0*0.57, spd*1.3, u_Time, n, ns);
    p += gerstner(a_Pos.xz, normalize(vec2(cos(wd-0.30), sin(wd-0.30))), amp*0.30, L0*0.31, spd*1.7, u_Time, n, ns);
    p += gerstner(a_Pos.xz, normalize(vec2(cos(wd+1.10), sin(wd+1.10))), amp*0.16, L0*0.16, spd*2.3, u_Time, n, ns);

    float tideY = u_Tide * 1.4 - 0.7;
    p.y += tideY;

    // Floor: troughs never dip below this, so the surface can't expose anything
    // beneath it. Generous margin (1.3×amp) keeps it watertight for big waves.
    p.y = max(p.y, tideY - amp * 1.3);

    // More whitecaps as wind rises (lower crest threshold).
    v_Foam   = clamp((p.y - (tideY + amp * 0.55)) * 3.5, 0.0, 1.0);
    v_World  = p;
    v_Normal = normalize(n);
    gl_Position = u_MVP * vec4(p, 1.0);
}
