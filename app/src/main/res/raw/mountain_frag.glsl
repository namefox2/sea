precision mediump float;
uniform vec3  u_Color;
uniform vec3  u_LightDir;
uniform vec3  u_RimColor;
uniform vec3  u_AmbientColor;
uniform float u_MaxH;
varying float v_Slope;
varying float v_WorldY;

void main() {
    // Slope normal in world XY plane: n = normalize(-slope, 1)
    float invLen = inversesqrt(v_Slope * v_Slope + 1.0);
    float nx = -v_Slope * invLen;
    float ny = invLen;

    // Horizontal light direction (normalize XY components)
    float lLen = max(sqrt(u_LightDir.x * u_LightDir.x + u_LightDir.y * u_LightDir.y), 0.001);
    float lx = u_LightDir.x / lLen;
    float ly = u_LightDir.y / lLen;

    float NdotL = max(nx * lx + ny * ly, 0.0);

    // Rim light: slope facing away from horizontal light direction
    float rimDot = max(-(nx * lx + ny * ly), 0.0);
    float rim    = rimDot * rimDot * 0.25;

    // Height-based density: base blends more into fog, peaks are clearer
    float heightClear = clamp(v_WorldY / max(u_MaxH * 0.7, 0.1), 0.0, 1.0);

    // Slope shading 0.55..1.0, base of mountain slightly darkened
    float shade = mix(0.55, 1.0, NdotL);
    shade = mix(shade * 0.80, shade, heightClear);

    vec3 col = u_Color * shade
             + u_RimColor * rim
             + u_AmbientColor * (1.0 - NdotL) * 0.12;
    gl_FragColor = vec4(col, 1.0);
}
