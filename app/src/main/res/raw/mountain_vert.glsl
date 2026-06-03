precision mediump float;
attribute vec3  a_Pos;
attribute float a_Slope;
uniform   mat4  u_MVP;
varying   float v_Slope;
varying   float v_WorldY;
void main() {
    gl_Position = u_MVP * vec4(a_Pos, 1.0);
    v_Slope  = a_Slope;
    v_WorldY = a_Pos.y;
}
