#if GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
attribute vec3 a_Pos;
uniform mat4 u_MVP;
uniform float u_Time;
uniform float u_WindAmp;
uniform float u_WindDir;
uniform float u_Tide;

varying vec3 v_World;
varying vec3 v_Normal;
varying float v_Foam;

vec3 gerstner(vec2 xz0, vec2 dir, float amp, float L, float speed, float t, inout vec3 n) {
    float k     = 6.28318 / L;
    float phase = k * dot(dir, xz0) - speed * t;
    float C = cos(phase); float S = sin(phase);
    float Q = 0.30;
    vec3 d;
    d.x = Q * amp * dir.x * C;
    d.y = amp * S;
    d.z = Q * amp * dir.y * C;
    n.x -= dir.x * k * amp * C;
    n.y -= Q   * k * amp * S;
    n.z -= dir.y * k * amp * C;
    return d;
}

void main() {
    float amp = u_WindAmp * 0.30 + 0.04;
    float wd  = u_WindDir;
    vec3 p    = a_Pos;
    vec3 n    = vec3(0.0, 1.0, 0.0);

    p += gerstner(a_Pos.xz, normalize(vec2(cos(wd),       sin(wd))),       amp*1.00, 7.0, 1.1, u_Time, n);
    p += gerstner(a_Pos.xz, normalize(vec2(cos(wd+0.45),  sin(wd+0.45))),  amp*0.55, 4.0, 1.5, u_Time, n);
    p += gerstner(a_Pos.xz, normalize(vec2(cos(wd-0.30),  sin(wd-0.30))),  amp*0.28, 2.2, 2.0, u_Time, n);
    p += gerstner(a_Pos.xz, normalize(vec2(cos(wd+1.10),  sin(wd+1.10))),  amp*0.14, 1.1, 2.7, u_Time, n);

    // Tide shifts the entire ocean surface
    float tideY = u_Tide * 1.4 - 0.7;
    p.y += tideY;

    // Floor: prevent troughs from punching through and exposing beach below
    p.y = max(p.y, tideY - amp * 1.2);

    // Foam mask: wave crests above tide+amp threshold
    v_Foam   = clamp((p.y - (tideY + amp * 0.75)) * 5.0, 0.0, 1.0);
    v_World  = p;
    v_Normal = normalize(n);
    gl_Position = u_MVP * vec4(p, 1.0);
}
