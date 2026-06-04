precision mediump float;
varying vec2  v_UV;
uniform vec3  u_Horizon;
uniform vec3  u_Zenith;
uniform vec3  u_LightColor;
uniform vec2  u_LightUV;    // sun/moon position in UV space
uniform float u_IsDark;     // 0=day 1=night
uniform float u_Time;
uniform float u_Aspect;     // width/height — corrects sun disc to a true circle

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise2(vec2 p) {
    vec2 i = floor(p); vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i),             hash(i + vec2(1.0, 0.0)), f.x),
               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

float cloudFBM(vec2 p) {
    return 0.500 * noise2(p)
         + 0.250 * noise2(p * 2.10 + vec2(1.7, 9.2))
         + 0.125 * noise2(p * 4.50 + vec2(8.3, 2.8));
}

void main() {
    // Sky gradient
    vec3 sky = mix(u_Horizon, u_Zenith, pow(v_UV.y, 0.7));

    // ── FBM clouds ────────────────────────────────────────────────────────
    vec2  cUV    = vec2(v_UV.x * 3.2 + u_Time * 0.0035, (v_UV.y - 0.25) * 2.2);
    float cNoise = cloudFBM(cUV);
    float cloud  = smoothstep(0.52, 0.74, cNoise)
                 * smoothstep(0.22, 0.48, v_UV.y)
                 * clamp(1.0 - u_IsDark * 1.3, 0.0, 1.0);
    cloud = clamp(cloud, 0.0, 1.0);

    vec2  toSun     = normalize(u_LightUV - v_UV + vec2(0.0, 0.001));
    float sunFacing = dot(toSun, vec2(0.0, 1.0)) * 0.5 + 0.5;
    vec3  cloudBright = mix(vec3(1.0), u_LightColor * 1.10, 0.25);
    vec3  cloudShade  = mix(u_Horizon * 0.65, vec3(0.82, 0.85, 0.90), 0.45);
    sky = mix(sky, mix(cloudShade, cloudBright, sunFacing), cloud * 0.88);

    // ── Sun/moon disc + glow + bloom ─────────────────────────────────────
    // Correct for aspect ratio so the disc is a true pixel-space circle
    vec2 sunDelta = v_UV - u_LightUV;
    sunDelta.x   *= u_Aspect;
    float d    = length(sunDelta);
    float body = smoothstep(0.032, 0.028, d);
    float glow = smoothstep(0.22, 0.0, d) * mix(0.45, 0.20, u_IsDark);
    float bloom = smoothstep(0.55, 0.0, d) * 0.12
                * clamp(1.0 - u_IsDark * 1.5, 0.0, 1.0);
    sky += u_LightColor * (body * 2.5 + glow + bloom);

    // Stars at night
    if (u_IsDark > 0.5) {
        vec2 sg = floor(v_UV * 110.0);
        float s = hash(sg);
        float twinkle = 0.7 + 0.3 * sin(u_Time * (3.0 + s * 4.0));
        float star = step(0.986, s) * (v_UV.y * 0.6 + 0.4) * twinkle;
        sky += vec3(0.85, 0.90, 1.00) * star;
    }

    // Horizon haze — sky becomes lighter toward the bottom (horizon)
    float haze = pow(1.0 - v_UV.y, 2.2) * 0.55;
    vec3  hazeCol = mix(u_Horizon * 1.20, mix(u_Horizon, vec3(1.0), 0.30) * 1.25, 1.0 - u_IsDark);
    sky = mix(sky, hazeCol, haze);

    gl_FragColor = vec4(sky, 1.0);
}
