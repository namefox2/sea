#if GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
varying vec2  v_UV;
varying float v_Alpha;
uniform float u_Time;
uniform vec3  u_LightColor;

float hash21(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

void main() {
    // Cellular bubble foam
//    vec2  cell   = floor(v_UV * 4.0);
//    vec2  cellUV = fract(v_UV * 4.0);
//    float h      = hash21(cell);
//    float phase  = fract(h + u_Time * (0.8 + h * 1.5));
//
//    float dist   = length(cellUV - 0.5) * 2.0;
//    float bubble = smoothstep(phase + 0.1, phase, dist)
//                 * smoothstep(0.0, 0.15, phase)
//                 * smoothstep(1.0, 0.70, phase)
//                 * step(0.3, h); // only 70% of cells have foam

    float foam1 =
            sin(v_UV.x * 18.0 + u_Time * 0.8) *
            sin(v_UV.y * 11.0 - u_Time * 0.4);

    float foam2 =
            sin(v_UV.x * 31.0 - u_Time * 0.5) *
            sin(v_UV.y * 23.0 + u_Time * 0.7);

    float bubble =
            smoothstep(0.4, 0.8, foam1 * foam2 * 0.5 + 0.5);

    // Fine cross-hatch texture for foam body
    float fine = pow(sin(v_UV.x * 28.0 + u_Time * 0.5) * 0.5 + 0.5, 6.0)
               * pow(sin(v_UV.y * 19.0 - u_Time * 0.3) * 0.5 + 0.5, 4.0);

    float foam =
            bubble * 0.8 +
            fine * 0.2;
    float alpha = v_Alpha * foam;
    if (alpha < 0.02) discard;
    vec3 foamBase = mix(vec3(0.93, 0.96, 1.0), u_LightColor, 0.18);
    gl_FragColor = vec4(foamBase, alpha);
}
