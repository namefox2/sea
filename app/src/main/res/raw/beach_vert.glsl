precision highp float;
attribute vec3 a_Pos;
uniform mat4 u_MVP;
varying vec3 v_World;
void main() {
    v_World     = a_Pos;
    gl_Position = u_MVP * vec4(a_Pos, 1.0);
}
