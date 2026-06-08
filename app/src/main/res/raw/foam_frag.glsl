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
    vec2 cell = floor(v_UV * 12.0);
    vec2 cellUV = fract(v_UV * 12.0);

    float h = hash21(cell);

    vec2 center = vec2(
            hash21(cell + 1.7),
            hash21(cell + 4.3)
    );

    float smallBubble =
            smoothstep(0.45, 0.1,
                       length(cellUV - center));

    smallBubble *= step(0.35, h);

    vec2 bigCell = floor(v_UV * 4.0);
    vec2 bigUV   = fract(v_UV * 4.0);

    float bh = hash21(bigCell);

    vec2 bigCenter = vec2(
            hash21(bigCell + 10.0),
            hash21(bigCell + 20.0)
    );

    float bigBubble =
            smoothstep(0.65, 0.2,
                       length(bigUV - bigCenter));

    bigBubble *= step(0.55, bh);

    float foam =
            smallBubble * 0.7 +
            bigBubble * 0.5;
    float alpha = v_Alpha * foam;

    if (alpha < 0.02) discard;

    vec3 foamBase =
            mix(vec3(0.93, 0.96, 1.0),
                u_LightColor,
                0.18);

    //gl_FragColor = vec4(foamBase, alpha);
    gl_FragColor = vec4(1.0, 0.0, 0.0, 1.0);
}
