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
    // Q=0.18: rounder crests → swell-like, not choppy peaks.
    float Q = 0.18;
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

    float amp = mix(0.05, 0.50, wind);
    float L0  = mix(8.0, 16.0, w);   // longer wavelength → swell look
    float spd = mix(0.85, 1.65, w);
    // windDir is "where wind comes FROM"; waves travel in the opposite direction.
    float wd  = u_WindDir + 3.14159;
    float ns  = 1.5 + w * 0.8;

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

    // Primary swell dominates — all components nearly aligned so waves roll in
    // together rather than creating cross-chop. Max angle spread ±0.25 rad (14°).
    p += gerstner(a_Pos.xz, normalize(vec2(cos(wd),       sin(wd))),       amp       *depthFactor, L0,       spd,      u_Time, n, ns);
    p += gerstner(a_Pos.xz, normalize(vec2(cos(wd+0.18),  sin(wd+0.18))),  amp*0.38  *depthFactor, L0*0.58,  spd*1.25, u_Time, n, ns);
    p += gerstner(a_Pos.xz, normalize(vec2(cos(wd-0.14),  sin(wd-0.14))),  amp*0.20  *depthFactor, L0*0.33,  spd*1.58, u_Time, n, ns);
    p += gerstner(a_Pos.xz, normalize(vec2(cos(wd+0.25),  sin(wd+0.25))),  amp*0.10  *depthFactor, L0*0.16,  spd*2.20, u_Time, n, ns);

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
    gl_Position = u_MVP * vec4(p, 1.0);

    float distToWater = a_Pos.z - u_WaterlineZ;
    v_DistToWater = distToWater;
}
