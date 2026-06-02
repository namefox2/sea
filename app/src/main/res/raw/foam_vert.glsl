attribute vec2 a_Pos; // x: -1..1, y: -0.5..0.5 (local Z offset)
uniform mat4  u_MVP;
uniform float u_WaterlineZ;
uniform float u_WindAmp;
uniform float u_Time;

varying vec2  v_UV;
varying float v_Alpha;

void main() {
    float worldX  = a_Pos.x * 12.0;
    float waveOff = sin(worldX * 0.80 + u_Time * 2.10) * 0.35 * u_WindAmp
                  + cos(worldX * 1.30 - u_Time * 1.70) * 0.20 * u_WindAmp;
    float worldZ  = u_WaterlineZ + a_Pos.y + waveOff;
    float worldY  = 0.06; // slightly above wave surface

    v_UV    = vec2(a_Pos.x * 6.0 + u_Time * 0.08, a_Pos.y * 5.0);
    v_Alpha = 1.0 - abs(a_Pos.y) * 2.0; // fade front/back edges

    gl_Position = u_MVP * vec4(worldX, worldY, worldZ, 1.0);
}
