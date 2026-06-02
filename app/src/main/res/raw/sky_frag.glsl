precision mediump float;
varying vec2 v_UV;
uniform vec3 u_Horizon;
uniform vec3 u_Zenith;
uniform vec3 u_LightColor;
uniform vec2 u_LightUV;   // sun/moon position in UV space
uniform float u_IsDark;   // 0=day 1=night
uniform float u_Time;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

void main() {
    // Sky gradient
    vec3 sky = mix(u_Horizon, u_Zenith, pow(v_UV.y, 0.7));

    // Sun/moon disc + glow
    float d = length(v_UV - u_LightUV);
    float body = smoothstep(0.032, 0.028, d);
    float glow = smoothstep(0.22, 0.0, d) * mix(0.45, 0.20, u_IsDark);
    sky += u_LightColor * (body * 2.5 + glow);

    // Stars at night
    if (u_IsDark > 0.5) {
        vec2 sg = floor(v_UV * 110.0);
        float s = hash(sg);
        float twinkle = 0.7 + 0.3 * sin(u_Time * (3.0 + s * 4.0));
        float star = step(0.986, s) * (v_UV.y * 0.6 + 0.4) * twinkle;
        sky += vec3(0.85, 0.90, 1.00) * star;
    }

    // Horizon haze
    float haze = pow(1.0 - v_UV.y, 3.0) * 0.35;
    sky = mix(sky, u_Horizon * 1.3, haze);

    gl_FragColor = vec4(sky, 1.0);
}
