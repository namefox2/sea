#if GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
uniform float u_WindAmp;
uniform vec3  u_LightColor;

void main() {
    // Circular soft droplet using gl_PointCoord
    float d     = length(gl_PointCoord - 0.5) * 2.0;
    float alpha = smoothstep(1.0, 0.0, d)
                * clamp(u_WindAmp * 3.5, 0.0, 1.0)
                * 0.55;
    if (alpha < 0.02) discard;
    vec3 sprayBase = mix(vec3(0.93, 0.96, 1.0), u_LightColor, 0.15);
    gl_FragColor = vec4(sprayBase, alpha);
}
