precision highp float;
varying vec3 v_World;
varying vec3 v_Normal;
varying float v_Foam;

uniform vec3  u_LightDir;
uniform vec3  u_LightColor;
uniform vec3  u_DeepColor;
uniform vec3  u_ShallowColor;
uniform vec3  u_CamPos;
uniform float u_Roughness;
uniform float u_WindAmp;

void main() {
    vec3 N = normalize(v_Normal);
    vec3 V = normalize(u_CamPos - v_World);
    vec3 L = u_LightDir;
    vec3 H = normalize(L + V);

    // Depth-based water color
    float depth = clamp(1.0 - (v_World.y + 1.0) * 0.45, 0.0, 1.0);
    vec3 water = mix(u_ShallowColor, u_DeepColor, depth);

    // Fresnel (Schlick)
    float cosV   = max(dot(N, V), 0.0);
    float fresnel = 0.02 + 0.98 * pow(1.0 - cosV, 5.0);

    // Specular (윤슬 corridor)
    float sh   = mix(400.0, 25.0, u_Roughness);
    float spec = pow(max(dot(N, H), 0.0), sh);
    // Elongate along sun direction on water surface
    vec3 viewDir     = normalize(v_World - u_CamPos);
    vec3 sunHoriz    = normalize(vec3(L.x, 0.0, L.z) + vec3(0.001, 0.0, 0.0));
    float corridor   = abs(dot(viewDir, sunHoriz));
    float specPath   = spec * (1.0 + 2.5 * corridor * corridor);
    vec3  specColor  = u_LightColor * specPath * fresnel;

    // Subsurface scatter at wave tips
    float sss = pow(max(dot(L, -V), 0.0), 3.0) * max(v_World.y, 0.0) * 0.5;
    vec3  sssCol = vec3(0.05, 0.70, 0.45) * sss;

    // Foam
    float foamFactor = smoothstep(0.35, 0.65, v_Foam) * clamp(u_WindAmp * 2.5, 0.0, 1.0);
    vec3  foamCol    = vec3(0.95, 0.97, 1.00);

    float NdotL  = max(dot(N, L), 0.0);
    vec3  diffuse = water * (NdotL * 0.65 + 0.35);
    vec3  col     = mix(diffuse + sssCol + specColor, foamCol, foamFactor);

    // Atmospheric fog
    float fogD = length(v_World - u_CamPos);
    float fog  = clamp((fogD - 6.0) / 28.0, 0.0, 0.6);
    col = mix(col, u_LightColor * 0.38, fog);

    gl_FragColor = vec4(col, 1.0);
}
