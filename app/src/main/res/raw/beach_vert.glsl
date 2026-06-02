precision highp float;
attribute vec3 a_Pos;
uniform mat4 u_MVP;
uniform float u_Tide;
varying vec3  v_World;
varying float v_TideY;
void main() {
    v_World  = a_Pos;
    v_TideY  = u_Tide * 1.4 - 0.7;
    gl_Position = u_MVP * vec4(a_Pos, 1.0);
}
