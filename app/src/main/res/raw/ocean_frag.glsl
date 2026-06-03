#if GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
varying vec3  v_World;
varying vec3  v_Normal;
varying float v_Foam;

uniform vec3      u_LightDir;
uniform vec3      u_LightColor;
uniform vec3      u_DeepColor;
uniform vec3      u_ShallowColor;
uniform vec3      u_CamPos;
uniform float     u_Roughness;
uniform float     u_WindAmp;
uniform float     u_Time;
uniform float     u_YunseulStr;
uniform float     u_WaterlineZ;
uniform sampler2D u_NormalMap;
uniform vec3      u_HorizonColor;

void main() {
    // Clip ocean where beach is visible (perspective-near side)
    if (v_World.z > u_WaterlineZ + 0.5) discard;

    // ── Normal map: two UV scrolls blended, perturb Gerstner normal ─────────
    vec2 uv1   = v_World.xz * 0.15 + u_Time * vec2( 0.012,  0.008);
    vec2 uv2   = v_World.xz * 0.07 - u_Time * vec2( 0.007,  0.011);
    vec3 nm1   = texture2D(u_NormalMap, uv1).rgb * 2.0 - 1.0;
    vec3 nm2   = texture2D(u_NormalMap, uv2).rgb * 2.0 - 1.0;
    // nm.x → world X perturbation, nm.z → world Z perturbation
    vec2 perturb = (nm1.xz + nm2.xz) * 0.35 * u_WindAmp;
    vec3 N = normalize(vec3(v_Normal.x + perturb.x, v_Normal.y, v_Normal.z + perturb.y));
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

    // Corridor: Gaussian falloff — smooth natural edge (vs sharp smoothstep)
    float corridorHalfWidth = mix(0.3, 5.5, clamp(distFromCam / 18.0, 0.0, 1.0));
    vec2  perp              = vec2(-lightHorizDir.y, lightHorizDir.x);
    float perpDist          = abs(dot(fragToHoriz * distFromCam, perp));
    float corridorMask      = exp(-perpDist * perpDist / (corridorHalfWidth * corridorHalfWidth));

    // ── Sin-interference sparkle pattern (no grid artifacts) ─────────────
    float w1 = sin(v_World.x *  8.3 + v_World.z *  5.7 + u_Time * 2.1);
    float w2 = sin(v_World.x * 13.1 - v_World.z *  9.3 + u_Time * 3.4);
    float w3 = sin(v_World.x * 21.7 + v_World.z * 14.9 - u_Time * 1.8);
    float w4 = sin(v_World.x *  5.2 - v_World.z * 18.6 + u_Time * 4.2);
    float sparkle = pow(clamp(w1 * w2 * w3 * w4 * 2.0 + 0.5, 0.0, 1.0), 6.0);
    sparkle *= 0.5 + clamp(1.0 - distFromCam / 18.0, 0.0, 0.5);

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

    // ── Sky reflection (Fresnel-weighted, suppressed under foam) ─────────────
    // At grazing angles Fresnel→1: water acts as mirror → reflect horizon sky
    vec3 skyReflColor = mix(u_ShallowColor * 0.5, u_HorizonColor, 0.55);
    col = mix(col, skyReflColor * 0.88, fresnel * 0.62 * (1.0 - foamFactor));

    // 윤슬 added on top — visible even at low sun angle (most dramatic at dawn/dusk)
    col += yunseulColor * (0.5 + fresnel * 0.5);

    // ── Exponential² atmospheric fog ─────────────────────────────────────────
    float fogD    = length(v_World - u_CamPos);
    float fogFact = clamp(1.0 - exp(-0.002 * fogD * fogD), 0.0, 0.92);
    vec3  fogCol  = mix(u_ShallowColor * 0.3, u_HorizonColor, 0.70);
    col = mix(col, fogCol, fogFact);

    gl_FragColor = vec4(col, 1.0);
}
