#if GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
varying vec2  v_UV;
varying float v_Alpha;
uniform float u_Time;

float hash21(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

void main() {
    // Cellular bubble foam
    vec2  cell   = floor(v_UV * 4.0);
    vec2  cellUV = fract(v_UV * 4.0);
    float h      = hash21(cell);
    float phase  = fract(h + u_Time * (0.8 + h * 1.5));

    float dist   = length(cellUV - 0.5) * 2.0;
    float bubble = smoothstep(phase + 0.1, phase, dist)
                 * smoothstep(0.0, 0.15, phase)
                 * smoothstep(1.0, 0.70, phase)
                 * step(0.3, h); // only 70% of cells have foam

    // Fine cross-hatch texture for foam body
    float fine = pow(sin(v_UV.x * 28.0 + u_Time * 0.5) * 0.5 + 0.5, 6.0)
               * pow(sin(v_UV.y * 19.0 - u_Time * 0.3) * 0.5 + 0.5, 4.0);

    float foam  = clamp(bubble * 1.6 + fine * 0.25, 0.0, 1.0);
    float alpha = v_Alpha * foam;
    if (alpha < 0.02) discard;
    gl_FragColor = vec4(0.93, 0.96, 1.0, alpha);
}
