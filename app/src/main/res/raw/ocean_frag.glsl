precision highp float;
varying vec3  v_World;
varying vec3  v_Normal;
varying float v_Foam;

uniform vec3  u_LightDir;
uniform vec3  u_LightColor;
uniform vec3  u_DeepColor;
uniform vec3  u_ShallowColor;
uniform vec3  u_CamPos;
uniform float u_Roughness;
uniform float u_WindAmp;
uniform float u_Time;
uniform float u_YunseulStr;
uniform float u_WaterlineZ;

void main() {
    // Clip ocean where beach is visible
    if (v_World.z > u_WaterlineZ + 0.5) discard;

    vec3 N = normalize(v_Normal);
    vec3 V = normalize(u_CamPos - v_World);
    vec3 L = u_LightDir;

    // Depth-based water color
    float depth = clamp(1.0 - (v_World.y + 1.0) * 0.45, 0.0, 1.0);
    vec3 water = mix(u_ShallowColor, u_DeepColor, depth);

    // Fresnel (Schlick)
    float cosV   = max(dot(N, V), 0.0);
    float fresnel = 0.02 + 0.98 * pow(1.0 - cosV, 5.0);

    // ── 윤슬: specular corridor ───────────────────────────────────────────
    vec3  H         = normalize(L + V);
    float NdotH     = max(dot(N, H), 0.0);
    float shininess = mix(1200.0, 48.0, u_Roughness);
    float spec      = pow(NdotH, shininess);

    // Project onto horizontal light axis to form the corridor
    vec2  lightDir2D  = normalize(vec2(u_LightDir.x, u_LightDir.z));
    vec2  toFrag      = v_World.xz - u_CamPos.xz;
    float distFromCam = max(length(toFrag), 0.01);
    vec2  fragDir2D   = toFrag / distFromCam;

    // Width of corridor: narrow at horizon, wide at viewer's feet
    float corridorHalf = mix(0.8, 6.0, clamp(distFromCam / 20.0, 0.0, 1.0));
    // Perpendicular component tells us how far off-axis this fragment is
    vec2  perp        = vec2(-lightDir2D.y, lightDir2D.x);
    float offAxis     = abs(dot(perp, fragDir2D)) * distFromCam;
    float corridorMask = smoothstep(corridorHalf, corridorHalf * 0.25, offAxis);

    // Sparkle: high-frequency sine noise simulates micro-facet glints
    float sparkle = 0.0;
    vec2 sp = v_World.xz * 8.0 + u_Time * vec2(0.7, 0.5);
    sparkle += pow(max(sin(sp.x) * cos(sp.y), 0.0), 24.0);
    sp = v_World.xz * 13.0 - u_Time * vec2(0.4, 0.8);
    sparkle += pow(max(cos(sp.x) * sin(sp.y), 0.0), 28.0);
    sp = v_World.xz * 19.0 + u_Time * vec2(0.9, -0.3);
    sparkle += pow(max(sin(sp.x + sp.y), 0.0), 32.0);
    sparkle = clamp(sparkle, 0.0, 1.0);
    float sparkleMasked = sparkle * corridorMask * (1.0 - u_Roughness * 0.8);

    // Combine into 윤슬 intensity
    float yunseul  = spec * corridorMask
                   + sparkleMasked * 3.5
                   + corridorMask * 0.12;
    yunseul *= u_YunseulStr;

    vec3 specColor = u_LightColor * yunseul
                   * mix(2.5, 0.8, u_Roughness)
                   * (0.6 + fresnel * 0.4);

    // Subsurface scatter at wave tips
    float sss    = pow(max(dot(L, -V), 0.0), 3.0) * max(v_World.y, 0.0) * 0.5;
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
