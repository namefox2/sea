attribute vec2 aXZ;

uniform mat4  uVP;
uniform vec3  uCamPos;
uniform float uTime;
uniform float uWind;      // Beaufort 0-12
uniform float uWindDir;   // radians
uniform float uTide;      // 0-1

varying vec3  vWorldPos;
varying vec3  vNorm;
varying float vDepth;
varying float vFoam;

vec2 rot2(vec2 d, float a) {
    float c = cos(a), s = sin(a);
    return vec2(d.x * c - d.y * s, d.x * s + d.y * c);
}

// Single Gerstner wave accumulated into pos/nrm (xz0 = undisplaced grid position)
void addGerstner(vec2 xz0, inout vec3 pos, inout vec3 nrm,
                 vec2 dir, float amp, float wlen, float Q) {
    float k     = 6.28318 / wlen;
    float omega  = sqrt(9.8 * k);
    float phase  = k * dot(dir, xz0) - omega * uTime;
    float c      = cos(phase);
    float s      = sin(phase);
    float kA     = k * amp;

    pos.x += Q * amp * dir.x * c;
    pos.y += amp * s;
    pos.z += Q * amp * dir.y * c;

    // Normal accumulation: dN from Gerstner surface derivative
    nrm.x -= dir.x * kA * c;
    nrm.y -= Q * kA * s;
    nrm.z -= dir.y * kA * c;
}

void main() {
    float ws  = uWind / 12.0;          // 0–1 normalized wind
    float ws2 = ws * ws;

    vec3 pos = vec3(aXZ.x, 0.0, aXZ.y);
    vec3 nrm = vec3(0.0, 1.0, 0.0);   // accumulate Gerstner normals from (0,1,0)

    // 6 Gerstner waves — primary swell + chop layers
    vec2 d0 = rot2(vec2(1.0, 0.0), uWindDir);
    addGerstner(aXZ, pos, nrm, d0, 0.55 * ws + 0.05, 22.0, 0.60);

    vec2 d1 = rot2(vec2(1.0, 0.0), uWindDir + 0.38);
    addGerstner(aXZ, pos, nrm, d1, 0.28 * ws + 0.03, 14.0, 0.55);

    vec2 d2 = rot2(vec2(1.0, 0.0), uWindDir - 0.52);
    addGerstner(aXZ, pos, nrm, d2, 0.18 * ws2 + 0.02, 8.5, 0.50);

    vec2 d3 = rot2(vec2(1.0, 0.0), uWindDir + 1.10);
    addGerstner(aXZ, pos, nrm, d3, 0.12 * ws2 + 0.01, 6.0, 0.45);

    vec2 d4 = rot2(vec2(1.0, 0.0), uWindDir - 1.30);
    addGerstner(aXZ, pos, nrm, d4, 0.08 * ws2,         4.0, 0.40);

    vec2 d5 = rot2(vec2(1.0, 0.0), uWindDir + 0.75);
    addGerstner(aXZ, pos, nrm, d5, 0.05 * ws,           2.5, 0.35);

    // Tide shifts whole mesh height; exposes or hides the shoreline
    pos.y += (uTide - 0.5) * 2.0;

    // Shallow/deep blend: near shore (z≈0) is shallow, far is deep
    float shoreFactor = 1.0 - clamp(aXZ.y / 28.0, 0.0, 1.0);
    vDepth = clamp(shoreFactor * 0.7 + max(0.0, pos.y) * 0.12, 0.0, 1.0);

    // Foam: wave crests + shore surf
    float crest     = max(0.0, pos.y - (0.40 * ws + 0.08)) * 3.0;
    float shoreZone = clamp(1.0 - aXZ.y / 5.0, 0.0, 1.0);
    vFoam = clamp(crest + shoreZone * 0.55 * ws, 0.0, 1.0);

    vWorldPos = pos;
    vNorm     = normalize(nrm);

    gl_Position = uVP * vec4(pos, 1.0);
}
