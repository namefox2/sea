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
uniform float u_WindSurge;
varying float v_DistToWater;

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

    // 🔥 핵심: 감쇠된 바람
    float wind = smoothstep(0.0, 1.0, w);
    wind = wind * wind; // 더 부드럽게

    float amp = mix(0.06, 0.22, wind);
    float L0  = mix(6.0, 13.0, w);
    float spd = mix(0.9, 1.7,  w);
    float wd  = u_WindDir;
    float ns  = 1.7 + w * 0.9;

    vec3 p = a_Pos;
    vec3 n = vec3(0.0, 1.0, 0.0);

    float shoreZone =
            smoothstep(
                    u_WaterlineZ - 6.0,
                    u_WaterlineZ + 2.0,
                    p.z
            );
    float depthZ = a_Pos.z;

    float depthFactor = smoothstep(
            u_WaterlineZ + 20.0,
            u_WaterlineZ - 10.0,
            depthZ
    );
    depthFactor = clamp(depthFactor, 0.15, 1.0);

    p += gerstner(a_Pos.xz, normalize(vec2(cos(wd),      sin(wd))),      amp*depthFactor, L0*1.00, spd*1.0, u_Time, n, ns);
    p += gerstner(a_Pos.xz, normalize(vec2(cos(wd+0.45), sin(wd+0.45))), amp*depthFactor, L0*0.57, spd*1.3, u_Time, n, ns);
    p += gerstner(a_Pos.xz, normalize(vec2(cos(wd-0.30), sin(wd-0.30))), amp*depthFactor, L0*0.31, spd*1.7, u_Time, n, ns);
    p += gerstner(a_Pos.xz, normalize(vec2(cos(wd+1.10), sin(wd+1.10))), amp*depthFactor, L0*0.16, spd*2.3, u_Time, n, ns);

    float shallowWidth = 22.0;

    float shallowT = smoothstep(
            u_WaterlineZ - shallowWidth,
            u_WaterlineZ + 3.0,
            a_Pos.z
    );

    vec3 waveOffset = p - a_Pos;
    n = mix(
            vec3(0.0, 1.0, 0.0),
            n,
            1.0 - shallowT * 0.85
    );
    p = a_Pos + waveOffset;

    float runup =
            sin(u_Time * 0.35)
            * (1.5 + wind * 2.0);

    p.z += shoreZone * runup;

    // tideY: base water-plane Y position from tide + wind surge.
    // Wind piles water up (storm surge), raising the entire water surface.
    float tideY = u_Tide * 1.4 - 0.7 + u_WindSurge;
    p.y += tideY - shallowT * 0.15;

    // Floor clamp: troughs never expose empty space below the mesh.
    p.y = max(p.y, tideY - amp * 2.0);

    // Shore draping: vertices approaching the waterline smoothly sink below the
    // beach surface so the ocean mesh boundary is hidden by the depth test.
    // The fragment shader discards anything past waterlineZ+2 anyway.

    float waveFade = 1.0 - shallowT;
    float shoreWaveRetain = mix(0.08, 0.45, u_Tide);

    p.y = tideY +
          (p.y - tideY) *
          mix(1.0, shoreWaveRetain, shallowT);

    v_Foam   = clamp((p.y - (tideY + amp * 0.55)) * 3.5, 0.0, 1.0);
    v_World  = p;
    v_Normal = normalize(n);

    // Sink ocean clip-position past the waterline so beach shader wins there.
    // X-varying noise makes the boundary wavy instead of a straight horizontal line.
    // Same frequencies used in ocean_frag shore blend → geometry and colour align.
    float wlNoise = sin(a_Pos.x * 0.25 + u_Time * 0.40) * 2.0
                  + sin(a_Pos.x * 0.11 - u_Time * 0.28) * 1.3;
    float pastWL = clamp((a_Pos.z - u_WaterlineZ - wlNoise) / 1.5, 0.0, 1.0);
    gl_Position = u_MVP * vec4(p.x, p.y - pastWL * 1.5, p.z, 1.0);

    float distToWater = a_Pos.z - u_WaterlineZ;
    v_DistToWater = distToWater;
}
