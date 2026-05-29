attribute vec2 aPosition;
varying vec2 vUV;

void main() {
    // Map NDC [-1,1] to UV [0,1]; (0,0) = bottom-left, (1,1) = top-right
    vUV = aPosition * 0.5 + 0.5;
    gl_Position = vec4(aPosition, 0.0, 1.0);
}
