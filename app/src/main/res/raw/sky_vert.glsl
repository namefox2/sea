attribute vec2 a_Pos;
varying vec2 v_UV;
void main() {
    v_UV = a_Pos * 0.5 + 0.5;
    gl_Position = vec4(a_Pos, 0.9999, 1.0);
}
