precision mediump float;
uniform float u_WindAmp;

void main() {
    // Circular soft droplet using gl_PointCoord
    float d     = length(gl_PointCoord - 0.5) * 2.0;
    float alpha = smoothstep(1.0, 0.0, d)
                * clamp(u_WindAmp * 3.5, 0.0, 1.0)
                * 0.55;
    if (alpha < 0.02) discard;
    gl_FragColor = vec4(0.93, 0.96, 1.0, alpha);
}
