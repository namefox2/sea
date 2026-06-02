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
    // Clip ocean where beach is visible (perspective-near side)
    if (v_World.z > u_WaterlineZ + 0.5) discard;

    vec3 N = normalize(v_Normal);
    vec3 V = normalize(u_CamPos - v_World);
    vec3 L = u_LightDir;

    // Depth-based water color
    float depth  = clamp(1.0 - (v_World.y + 1.0) * 0.45, 0.0, 1.0);
    vec3  water  = mix(u_ShallowColor, u_DeepColor, depth);

    // Fresnel (Schlick)
    float cosV   = max(dot(N, V), 0.0);
    float fresnel = 0.02 + 0.98 * pow(1.0 - cosV, 5.0);

    // ── 윤슬 corridor mask ────────────────────────────────────────────────
    vec2  lightHorizDir   = normalize(vec2(u_LightDir.x, u_LightDir.z));
    vec2  toFrag          = v_World.xz - u_CamPos.xz;
    float distFromCam     = max(length(toFrag), 0.01);
    vec2  fragToHoriz     = toFrag / distFromCam;

    // Corridor width: narrow at horizon (far), wide at camera (near) — perspective
    float corridorHalfWidth = mix(0.3, 5.5, clamp(distFromCam / 18.0, 0.0, 1.0));
    vec2  perp              = vec2(-lightHorizDir.y, lightHorizDir.x);
    float perpDist          = abs(dot(fragToHoriz * distFromCam, perp));
    float corridorMask      = smoothstep(corridorHalfWidth, corridorHalfWidth * 0.1, perpDist);

    // ── Hash-based flickering sparkles ───────────────────────────────────
    // Layer 1: slow large sparkles (~1 Hz)
    vec2  s1p  = floor(v_World.xz * 3.5 + u_Time * vec2(0.3, 0.8));
    float s1   = fract(sin(dot(s1p, vec2(127.1, 311.7))) * 43758.5453);
    float s1t  = fract(s1 * 7.0 + u_Time * (0.8 + s1 * 1.2));
    float sp1  = pow(max(1.0 - abs(s1t - 0.5) * 4.0, 0.0), 2.0) * step(0.55, s1);

    // Layer 2: medium sparkles (~3 Hz)
    vec2  s2p  = floor(v_World.xz * 7.0 - u_Time * vec2(0.5, 0.4));
    float s2   = fract(sin(dot(s2p, vec2(269.5, 183.3))) * 43758.5453);
    float s2t  = fract(s2 * 5.0 + u_Time * (1.5 + s2 * 2.5));
    float sp2  = pow(max(1.0 - abs(s2t - 0.5) * 6.0, 0.0), 2.0) * step(0.60, s2);

    // Layer 3: fast micro sparkles (~8 Hz, densest near camera)
    vec2  s3p  = floor(v_World.xz * 14.0 + u_Time * vec2(1.1, -0.7));
    float s3   = fract(sin(dot(s3p, vec2(419.2, 371.9))) * 43758.5453);
    float s3t  = fract(s3 * 3.0 + u_Time * (3.0 + s3 * 4.0));
    float sp3  = pow(max(1.0 - abs(s3t - 0.5) * 8.0, 0.0), 2.0) * step(0.65, s3);

    // Combine: brighter near camera (perspective)
    float sparkle = (sp1 * 1.0 + sp2 * 1.8 + sp3 * 2.5)
                  * (0.5 + clamp(1.0 - distFromCam / 18.0, 0.0, 0.5));
    sparkle = clamp(sparkle, 0.0, 1.0);

    // ── Broad specular glow under the sparkles ────────────────────────────
    vec3  H         = normalize(L + V);
    float NdotH     = max(dot(N, H), 0.0);
    float broadSpec = pow(NdotH, mix(180.0, 32.0, u_Roughness)) * corridorMask * 0.6;

    // ── Combine 윤슬 ──────────────────────────────────────────────────────
    vec3 yunseulColor = u_LightColor
                      * (broadSpec + sparkle * corridorMask * 3.5)
                      * u_YunseulStr;

    // Subsurface scatter at wave tips
    float sss    = pow(max(dot(L, -V), 0.0), 3.0) * max(v_World.y, 0.0) * 0.5;
    vec3  sssCol = vec3(0.05, 0.70, 0.45) * sss;

    // Foam
    float foamFactor = smoothstep(0.35, 0.65, v_Foam) * clamp(u_WindAmp * 2.5, 0.0, 1.0);
    vec3  foamCol    = vec3(0.95, 0.97, 1.00);

    float NdotL  = max(dot(N, L), 0.0);
    vec3  diffuse = water * (NdotL * 0.65 + 0.35);
    vec3  col     = mix(diffuse + sssCol, foamCol, foamFactor);

    // 윤슬 added on top — visible even at low sun angle (most dramatic at dawn/dusk)
    col += yunseulColor * (0.5 + fresnel * 0.5);

    // Atmospheric fog
    float fogD = length(v_World - u_CamPos);
    float fog  = clamp((fogD - 6.0) / 28.0, 0.0, 0.6);
    col = mix(col, u_LightColor * 0.38, fog);

    gl_FragColor = vec4(col, 1.0);
}
