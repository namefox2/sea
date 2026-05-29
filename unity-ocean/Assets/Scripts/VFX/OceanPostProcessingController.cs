using UnityEngine;
using UnityEngine.Rendering;
using UnityEngine.Rendering.HighDefinition;

/// <summary>
/// Drives HDRP post-processing stack for cinematic ocean look.
/// Manages: Bloom, Tonemapping, Color Grading, Chromatic Aberration, Lens Distortion.
/// </summary>
public class OceanPostProcessingController : MonoBehaviour
{
    [SerializeField] private Volume _ppVolume;

    [Header("Time of Day Color Grading")]
    [SerializeField] private Gradient _dawnColorFilter;
    [SerializeField] private Gradient _noonColorFilter;
    [SerializeField] private Gradient _duskColorFilter;

    [Header("Bloom")]
    [SerializeField, Range(0f, 5f)]  private float _bloomIntensity     = 0.4f;
    [SerializeField, Range(0f, 1f)]  private float _bloomThreshold     = 0.85f;
    [SerializeField, Range(0f, 1f)]  private float _bloomScatter       = 0.6f;

    [Header("Motion Blur")]
    [SerializeField, Range(0f, 1f)]  private float _motionBlurIntensity = 0.15f;

    [Header("Lens")]
    [SerializeField, Range(0f, 1f)]  private float _vignetteIntensity  = 0.18f;
    [SerializeField, Range(0f, 1f)]  private float _chromaticAberration = 0.04f;
    [SerializeField, Range(-0.5f, 0.5f)] private float _lensDistortion = -0.08f; // barrel

    private Bloom              _bloom;
    private Tonemapping        _tonemapping;
    private ColorAdjustments   _colorAdj;
    private ChromaticAberration _chromatic;
    private Vignette           _vignette;
    private MotionBlur         _motionBlur;
    private LensDistortion     _lensDistortion;
    private DepthOfField       _dof;

    private OceanWeatherSystem _weather;

    private void Start()
    {
        if (_ppVolume == null) return;
        var p = _ppVolume.profile;
        p.TryGet(out _bloom);
        p.TryGet(out _tonemapping);
        p.TryGet(out _colorAdj);
        p.TryGet(out _chromatic);
        p.TryGet(out _vignette);
        p.TryGet(out _motionBlur);
        p.TryGet(out _lensDistortion);
        p.TryGet(out _dof);

        _weather = FindFirstObjectByType<OceanWeatherSystem>();

        ApplyBaseSettings();
    }

    private void ApplyBaseSettings()
    {
        if (_bloom != null)
        {
            _bloom.active = true;
            _bloom.intensity.Override(_bloomIntensity);
            _bloom.threshold.Override(_bloomThreshold);
            _bloom.scatter.Override(_bloomScatter);
            _bloom.tint.Override(new Color(0.9f, 0.97f, 1.0f));
        }

        if (_tonemapping != null)
        {
            _tonemapping.active = true;
            _tonemapping.mode.Override(TonemappingMode.ACES);
        }

        if (_chromatic != null)
        {
            _chromatic.active = true;
            _chromatic.intensity.Override(_chromaticAberration);
        }

        if (_vignette != null)
        {
            _vignette.active = true;
            _vignette.intensity.Override(_vignetteIntensity);
            _vignette.smoothness.Override(0.5f);
        }

        if (_motionBlur != null)
        {
            _motionBlur.active = true;
            _motionBlur.intensity.Override(_motionBlurIntensity);
            _motionBlur.sampleCount.Override(8);
        }

        if (_lensDistortion != null)
        {
            _lensDistortion.active = true;
            _lensDistortion.intensity.Override(_lensDistortion.intensity.value);
            _lensDistortion.xMultiplier.Override(0.8f);
            _lensDistortion.yMultiplier.Override(0.8f);
        }

        // DOF: subtle foreground blur for cinematic feel
        if (_dof != null)
        {
            _dof.active = true;
            _dof.focusMode.Override(DepthOfFieldMode.UsePhysicalCamera);
        }
    }

    private void Update()
    {
        // Storm effect: increase chromatic aberration + vignette
        if (_weather != null)
        {
            // Could hook into weather blend value here
        }
    }
}
