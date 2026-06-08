#if GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
attribute vec2 a_Pos; // x: -1..1, y: -0.5..0.5 (local Z offset)
uniform mat4  u_MVP;
uniform float u_WaterlineZ;
uniform float u_WindAmp;
uniform float u_Time;
uniform float u_Tide;

varying vec2  v_UV;
varying float v_Alpha;

void main() {
    float worldX  = a_Pos.x * 12.0;
    float tideY = u_Tide * 1.4 - 0.7;
    float waveOff =
            sin(worldX*0.8 + u_Time*2.1)*0.35
            + sin(worldX*1.9 - u_Time*1.4)*0.25
            + sin(worldX*3.5 + u_Time*0.9)*0.15
            + sin(worldX*7.0 - u_Time*0.6)*0.08;
    float foamWidth = 4.0;

    float runup =
            sin(u_Time * 0.35) *
            (0.6 + u_WindAmp * 0.6);

    float shoreline =
            u_WaterlineZ + runup;

    float worldZ =
            shoreline +
            a_Pos.y * foamWidth +
            waveOff;
    float worldY =
            tideY
            + 0.12
            + sin(worldX * 2.0 + u_Time * 3.0) * 0.03;

    v_UV    = vec2(a_Pos.x * 6.0 + u_Time * 0.08, a_Pos.y * 5.0);
    v_Alpha = 1.0 - abs(a_Pos.y) * 2.0; // fade front/back edges

    gl_Position = u_MVP * vec4(worldX, worldY, worldZ, 1.0);

//
//    wetSandWidth =
//            0.8 + runup * 0.5;

}
