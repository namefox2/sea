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
uniform float u_WaterlineZ;

varying vec3  v_World;
varying vec3  v_Normal;
varying float v_Foam;

vec3 gerstner(vec2 xz0, vec2 dir, float amp, float L, float speed, float t,
              inout vec3 n, float normalScale) {
    float k     = 6.28318 / L;
    float phase = k * dot(dir, xz0) - speed * t;
    float C = cos(phase);
    float S = sin(phase);
    // Q=0.28: reduced from 0.40 — rounder crests, no hollow wave undersides.
    float Q = 0.28;
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

    float amp = w * w * 0.62 + w * 0.18 + 0.06;
    float L0  = mix(6.0, 13.0, w);
    float spd = mix(0.9, 1.7,  w);
    float wd  = u_WindDir;
    float ns  = 1.7 + w * 0.9;

    vec3 p = a_Pos;
    vec3 n = vec3(0.0, 1.0, 0.0);

    p += gerstner(a_Pos.xz, normalize(vec2(cos(wd),      sin(wd))),      amp*1.00, L0*1.00, spd*1.0, u_Time, n, ns);
    p += gerstner(a_Pos.xz, normalize(vec2(cos(wd+0.45), sin(wd+0.45))), amp*0.55, L0*0.57, spd*1.3, u_Time, n, ns);
    p += gerstner(a_Pos.xz, normalize(vec2(cos(wd-0.30), sin(wd-0.30))), amp*0.30, L0*0.31, spd*1.7, u_Time, n, ns);
    p += gerstner(a_Pos.xz, normalize(vec2(cos(wd+1.10), sin(wd+1.10))), amp*0.16, L0*0.16, spd*2.3, u_Time, n, ns);

    float tideY = u_Tide * 1.4 - 0.7;
    p.y += tideY;

    // Floor clamp: troughs never expose empty space below the mesh.
    p.y = max(p.y, tideY - amp * 2.0);

    // Shore draping: vertices approaching the waterline smoothly sink below the
    // beach surface so the ocean mesh boundary is hidden by the depth test.
    // The fragment shader discards anything past waterlineZ+2 anyway.
    float drapeT = clamp((p.z - (u_WaterlineZ - 3.0)) / 5.0, 0.0, 1.0);
    p.y = mix(p.y, tideY - 2.2, drapeT * drapeT);

    v_Foam   = clamp((p.y - (tideY + amp * 0.55)) * 3.5, 0.0, 1.0);
    v_World  = p;
    v_Normal = normalize(n);
    gl_Position = u_MVP * vec4(p, 1.0);
}
