// ─────────────────────────────────────────────────────────────────────────────
//  HDRP Asset Settings (HDRenderPipelineAsset)
// ─────────────────────────────────────────────────────────────────────────────
//
//  Rendering
//    Color Buffer Format    : R16G16B16A16 (HDR)
//    Lit Shader Mode        : Both (deferred/forward)
//    Motion Vectors         : true (for motion blur)
//    Opaque Object Sorting  : None
//
//  Shadows
//    Max Shadow Distance    : 500 m
//    Directional Shadow Res : 2048
//    Cascade Count          : 4
//    Cascades               : 0.05 / 0.15 / 0.35 / 1.0
//    PCF Quality            : High
//
//  Reflections
//    Screen Space Reflections : true
//    SSR Max Iterations       : 64
//    SSR Ray Max Length       : 50 m
//    SSR Thickness Offset     : 0.02 m
//    Planar Reflections       : true (for calm sea)
//
//  Ambient Occlusion
//    SSAO Quality       : High
//    SSAO Radius        : 0.5 m
//    SSAO Max Samples   : 16
//
//  Global Illumination
//    Screen Space GI    : true
//    SSGI Raycount      : 2
//
//  Volumetrics
//    Volumetric Fog     : true
//    Volumetric Fog Budget : 0.333
//    Resol Depth Ratio  : 0.5
//
//  Post-Processing
//    Anti-Aliasing      : TAAU (Temporal Anti-Aliasing Upsampling)
//    Exposure           : Physical Camera mode
//
// ─────────────────────────────────────────────────────────────────────────────
//  PhysicallyBasedSky Volume Override (Global Sky Volume)
// ─────────────────────────────────────────────────────────────────────────────
//  Type                     : Physically Based Sky
//  Exposure                 : 0
//  Multiplier               : 1
//  Update Mode              : On Changed
//  Planet Radius            : 6378 km
//  Atmosphere Thickness     : 80 km
//  Air Maximum Altitude     : 55 km
//  Ozone Layer              : true  Concentration 1  Diameter 25 km
//  Color Saturation         : 1.0
//  Alpha Saturation         : 1.0
//  Alpha Multiplier         : 1.0
//
// ─────────────────────────────────────────────────────────────────────────────
//  Exposure Volume Override
// ─────────────────────────────────────────────────────────────────────────────
//  Mode                 : Automatic (Histogram)
//  Compensation         : 0
//  Limit Min            : -5 EV
//  Limit Max            :  16 EV
//  Adaptation Speed     : 3.0
//
// ─────────────────────────────────────────────────────────────────────────────
//  Bloom Volume Override
// ─────────────────────────────────────────────────────────────────────────────
//  Intensity            : 0.4
//  Threshold            : 0.85
//  Scatter              : 0.6
//  Tint                 : (0.9, 0.97, 1.0)
//  Quality              : High (8 iterations)
//  High Quality Filtering : true
//
// ─────────────────────────────────────────────────────────────────────────────
//  Screen Space Reflections Override
// ─────────────────────────────────────────────────────────────────────────────
//  Enabled              : true
//  Minimum Smoothness   : 0.5
//  Smoothness Fade Start : 0.8
//  Weight               : 1.0
//  Accumulation Factor  : 0.9   (temporal smoothing)
//
// ─────────────────────────────────────────────────────────────────────────────

// Documentation only.
