// ─────────────────────────────────────────────────────────────────────────────
//  Crest OceanRenderer Inspector Settings — copy these values in the Editor
// ─────────────────────────────────────────────────────────────────────────────
//
//  [OceanRenderer]
//  Lod Count           : 14
//  Max Scale           : 256
//  Min Scale           : 0.5
//  Lod Data Types      : ✓ Animated Waves  ✓ Sea Floor Depth  ✓ Foam
//                        ✓ Dynamic Waves   ✓ Shadow           ✓ Flow
//                        ✓ Clip Surface    ✓ Level Of Detail   ✓ Albedo
//
//  [ShapeFFT  — Wind Chop]
//  Wind Speed          : 10       (runtime-driven by OceanDynamicsController)
//  Wave Directionality : 0.6
//  Gravity             : 9.81
//  Spectrum Type       : JONSWAP
//  Choppiness          : 1.5
//  Repeat Period       : 256 m
//  High Freq Fade Mult : 1.0
//  Generation Textures : 256
//  Simulation LODs     : 6
//
//  [ShapeFFT  — Ocean Swell  (separate GameObject)]
//  Wind Speed          : 12
//  Wave Directionality : 1.0      (very directional swell)
//  Gravity             : 9.81
//  Spectrum Type       : TMA
//  Choppiness          : 0.9
//  Repeat Period       : 512 m
//  Generation Textures : 128
//  Simulation LODs     : 3
//
//  [LodDataMgrFoam]
//  Foam Fade Rate      : 0.8
//  Wave Speed Foam Str : 6.0
//  Foam Feather        : 0.5
//  Foam Bubble Density : 0.7
//
//  [OceanDepthCache]
//  Camera Size         : 512 m
//  Resolution          : 1024
//  Layer Mask          : SeaFloor
//
// ─────────────────────────────────────────────────────────────────────────────
//  OceanMaterial (Crest/Ocean HDRP) Key Properties
// ─────────────────────────────────────────────────────────────────────────────
//  Normals
//    Normal Map Scale            : 1.0
//    Normal Map Scale Far        : 8.0
//    Normal Strength             : 0.9
//  Scattering
//    Scatter Colour Base         : (0, 0.18, 0.28)
//    Scatter Colour Shadow       : (0, 0.06, 0.12)
//    Scatter Colour Grazing      : (0.1, 0.6, 0.7)
//    Scatter Colour Tips         : (0.7, 0.95, 1.0)
//    Scatter Colour Tip Hypoxia  : (0.12, 0.25, 0.22)
//  Subsurface
//    SSS Strength                : 0.45
//    SSS Sun Falloff             : 5.0
//  Reflections
//    Min Reflectivity            : 0.02
//    SSR Enabled                 : true (HDRP)
//    Specular     (GGX)          : Roughness 0.08
//  Transparency
//    Refraction Strength         : 0.35
//    Depth Fog Density  (R)      : 0.15   <- R absorption
//    Depth Fog Density  (G)      : 0.08
//    Depth Fog Density  (B)      : 0.03
//  Foam
//    Foam Texture                : Crest/Textures/Foam02   (tiled 8x)
//    Foam Normal Strength        : 0.5
//    Foam Feather                : 0.4
//    Foam Albedo                 : (0.90, 0.94, 0.96)
//    Foam Smoothness             : 0.3
//    Foam Bubble Coverage        : 0.35
//    Whitecap Threshold          : 0.6
// ─────────────────────────────────────────────────────────────────────────────

// This file is documentation-only. No runtime code here.
